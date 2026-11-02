package io.luna.game.model.mob.bot.speech;

import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.speech.BotGeneralSpeechPool.GeneralSpeech;
import io.luna.util.StringUtils;
import io.luna.net.codec.ByteMessage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.random.RandomGenerator;

/**
 * A buffer of {@link BotSpeech} requests that manages when and how bots speak.
 * <p>
 * This stack controls <i>frequency</i> and <i>timing</i> of speech, not the actual phrase selection (which is
 * delegated to {@link BotSpeechPool}).
 * <p>
 * It combines contextual, general, and manually pushed speech into a single synchronized stream, enforcing throttling
 * and timing logic so that bots sound natural.
 *
 * @author lare96
 */
public final class BotSpeechStack {

    /**
     * The maximum amount of ticks this stack will wait before forcibly executing the current request.
     */
    private static final int MAX_DELAY_TICKS = 250;

    /**
     * Approximate amount of general speech requests to push per hour under ideal (1.0 social) conditions.
     * <p>
     * Increasing this increases the rate of spontaneous filler messages.
     */
    private static final int FILLER_PER_HOUR = 60;

    /**
     * Maximum number of {@link BotSpeech} requests queued before throttling.
     * <p>
     * Increasing allows for more pending requests but can delay contextual responses.
     * Recommended minimum: 5.
     */
    private static final int MAX_LENGTH = 10;

    /**
     * Maximum ordinary queue ages in 600 ms game ticks: three minutes for idle filler and six for contextual speech.
     * Contextual speech retains enough time for the existing automatic delay, including low-social requests.
     */
    private static final int GENERAL_MAX_AGE_TICKS = 300;
    private static final int CONTEXTUAL_MAX_AGE_TICKS = 600;

    /**
     * Distinguishes automatically selected filler from speech supplied by external gameplay callers.
     *
     * @author lare96
     */
    private enum Producer {
        GENERAL,
        CONTEXTUAL
    }

    /**
     * A bounded ordinary request with its original enqueue time, independent of its remaining typing delay.
     *
     * @author lare96
     */
    private record QueuedSpeech(BotSpeech speech, long enqueueTick, Producer producer) {}

    /**
     * Cumulative ordinary-queue outcomes for one producer. Dropped requests never entered the queue; eviction,
     * expiration and cancellation count requests that were previously accepted.
     *
     * @author lare96
     */
    public record ProducerDiagnostics(long attempted, long accepted, long dropped, long evicted, long expired,
                                      long canceled) {}

    /**
     * An immutable game-thread snapshot. Age and time use 600 ms game ticks; depth covers the ordinary queue only.
     * General attempts count due shared opportunities, including manual filler requests, rather than every tick.
     * Output acceptance means simulated packet submission, including reserved conversation turns; only
     * publicDelivered counts callbacks from the normal public-chat handler. These totals need not match because
     * successfully submitted packets can still be suppressed by native chat validation.
     *
     * @author lare96
     */
    public record Diagnostics(long ticks, ProducerDiagnostics general, ProducerDiagnostics contextual,
                              long headEvictions, long outputAccepted, long outputFailed, long publicDelivered,
                              int depth, long oldestAgeTicks, int maximumDepth) {}

    /**
     * Mutable counters confined to the owning game thread; snapshots never expose this state.
     *
     * @author lare96
     */
    private static final class ProducerCounters {
        private long attempted;
        private long accepted;
        private long dropped;
        private long evicted;
        private long expired;
        private long canceled;

        private ProducerDiagnostics snapshot() {
            return new ProducerDiagnostics(attempted, accepted, dropped, evicted, expired, canceled);
        }
    }

    /**
     * The buffer of speech requests.
     */
    private final Deque<QueuedSpeech> buffer;

    private final ProducerCounters generalCounters = new ProducerCounters();
    private final ProducerCounters contextualCounters = new ProducerCounters();
    private long headEvictions;
    private long outputAccepted;
    private long outputFailed;
    private long publicDelivered;
    private int maximumDepth;

    /**
     * The record of the most recent {@link #MAX_LENGTH} executed speech requests.
     */
    private final Deque<String> previous;

    /**
     * The bot this speech stack is for.
     */
    private final Bot bot;

    /**
     * The game-tick clock and next shared opportunity for automatic or explicitly requested filler.
     */
    private long tick;
    private long nextFillerTick;
    private double fillerSocial = -1;
    private BotSpeech pendingFiller;
    private final RandomGenerator random;

    /**
     * If speech from the {@link BotGeneralSpeechPool} is disabled.
     */
    private boolean disableGeneral;

    /**
     * Activity-owned filler suppression, independent of the external general-speech setting.
     */
    private final Set<Object> generalSuppressors = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Suppresses filler for one activity without overwriting another activity or an external mute.
     */
    public void suppressGeneral(Object owner) {
        if (owner != null) {
            generalSuppressors.add(owner);
            cancelFiller();
        }
    }

    /**
     * Releases only this activity's filler suppression.
     */
    public void releaseGeneral(Object owner) {
        generalSuppressors.remove(owner);
    }

    /**
     * If this bot should stop talking completely.
     */
    private boolean disableAll;

    /**
     * Exclusive conversation ownership and its live eligibility check, confined to the game thread.
     */
    private Object conversationOwner;
    private BooleanSupplier conversationValid;

    /**
     * The single reserved turn, retained until acknowledged or cancelled.
     */
    private BotSpeech conversationSpeech;
    private String conversationText;
    private boolean conversationSent;

    /** Context stays attached until a simulated chat packet reaches the normal reader. */
    private record ConversationDelivery(String text, Object owner) {}

    private final Deque<ConversationDelivery> conversationDeliveries = new ArrayDeque<>();

    /**
     * Drops queued packets while all speech is disabled, and turns whose live conversation ended before delivery.
     */
    public boolean validateConversationDelivery(String text) {
        var iterator = conversationDeliveries.iterator();
        while (iterator.hasNext()) {
            ConversationDelivery delivery = iterator.next();
            if (delivery.text().equals(text.trim())) {
                iterator.remove();
                return ownsConversation(delivery.owner());
            }
        }
        return !disableAll;
    }

    /**
     * Claims conversational speech without changing existing mute flags or discarding ordinary queued speech.
     */
    public boolean reserveConversation(Object owner, BooleanSupplier valid) {
        if (owner == null || valid == null || disableAll || conversationOwner != null) return false;
        conversationOwner = owner;
        conversationValid = valid;
        return true;
    }

    /**
     * Tests ownership and current eligibility before another line can be queued.
     */
    public boolean ownsConversation(Object owner) {
        return conversationOwner == owner && owner != null && !disableAll && conversationValid.getAsBoolean();
    }

    /**
     * Queues exactly one bounded-delay turn. Ordinary requests wait until the conversation releases its reservation.
     */
    public boolean speakConversation(Object owner, BotSpeech speech) {
        if (!ownsConversation(owner) || conversationSpeech != null || speech.getDelay() < 0 ||
                speech.getDelay() > 30 || speech.isDelivered()) return false;
        conversationSpeech = speech;
        conversationText = normalizeChat(speech.getText());
        conversationSent = false;
        return true;
    }

    /**
     * Releases only the matching conversation, preserving normal queued requests and external speech settings.
     */
    public void releaseConversation(Object owner) {
        if (conversationOwner != owner) return;
        conversationOwner = null;
        conversationValid = null;
        conversationSpeech = null;
        conversationText = null;
        conversationSent = false;
    }

    /**
     * Acknowledges a matching turn only after the normal public-chat handler publishes it.
     */
    public void acknowledgeConversation(String text) {
        if (conversationSpeech != null && conversationSent && ownsConversation(conversationOwner) &&
                conversationText.equals(text.trim())) {
            conversationSpeech.acknowledge();
            conversationSpeech = null;
            conversationSent = false;
        }
    }

    /**
     * Records only text that reached the public-chat update, including contextual and conversation lines.
     */
    public void acknowledgeSpeech(String text) {
        acknowledgeConversation(text);
        publicDelivered++;
        if (previous.size() >= MAX_LENGTH) previous.removeFirst();
        previous.addLast(normalizeChat(text));
    }

    /**
     * Uses the native 80-character codec so case, unsupported characters and packing padding match received chat.
     */
    public static String normalizeChat(String text) {
        ByteMessage bytes = ByteMessage.raw();
        try {
            StringUtils.packText(text, bytes);
            return StringUtils.unpackText(bytes.getBytes(bytes.content().readableBytes())).trim();
        } finally {
            bytes.releaseAll();
        }
    }

    /**
     * Reports effective general-speech suppression from external settings and active activities.
     */
    public boolean isGeneralDisabled() {
        return disableGeneral || !generalSuppressors.isEmpty();
    }

    /**
     * Exposes external all-speech suppression; conversations must respect it.
     */
    public boolean isAllDisabled() {
        return disableAll;
    }

    /**
     * Creates a new {@link BotSpeechStack}.
     *
     * @param bot The bot this speech stack is for.
     */
    public BotSpeechStack(Bot bot) {
        this.bot = bot;
        random = ThreadLocalRandom.current();
        buffer = new ArrayDeque<>(MAX_LENGTH);
        previous = new ArrayDeque<>(MAX_LENGTH);
    }

    /**
     * Processes the stack, ticking down speech delays and executing queued messages when ready.
     * <p>
     * This is called once per game tick.
     */
    public void process() {
        tick++;
        refreshFillerRate();
        expireQueue();
        if (disableAll) {
            clear();
            releaseConversation(conversationOwner);
            return;
        }
        if (isGeneralDisabled() || bot.getLocalHumans().isEmpty()) cancelFiller();
        if (conversationOwner != null) {
            if (!conversationValid.getAsBoolean()) {
                releaseConversation(conversationOwner);
            } else {
                if (conversationSpeech != null && !conversationSent) {
                    if (conversationSpeech.delay > 0) {
                        conversationSpeech.delay--;
                    } else {
                        conversationSent = true;
                        if (conversationDeliveries.size() >= MAX_LENGTH) conversationDeliveries.removeFirst();
                        ConversationDelivery delivery = new ConversationDelivery(conversationText, conversationOwner);
                        conversationDeliveries.addLast(delivery);
                        if (!transmit(conversationSpeech)) {
                            conversationDeliveries.remove(delivery);
                            releaseConversation(conversationOwner);
                        }
                    }
                }
                return;
            }
        }
        maybePushFiller();

        QueuedSpeech queued = buffer.peek();
        if (queued == null) {
            return;
        }
        BotSpeech speech = queued.speech();

        if (speech.delay > 0) {
            if (speech.delay > MAX_DELAY_TICKS) {
                speech.delay = MAX_DELAY_TICKS;
            }
            speech.delay--;
        } else if (speech.delay == 0) {
            transmit(speech);
            buffer.remove(queued);
            if (speech == pendingFiller) pendingFiller = null;
        }
    }

    /**
     * Forwards to {@link #pushTail(BotSpeech)}.
     *
     * @param speech The speech request.
     */
    public void push(BotSpeech speech) {
        pushTail(speech);
    }

    /**
     * Adds a {@link BotSpeech} request to the <strong>tail</strong> of the stack.
     * <p>
     * Lower-priority messages (e.g. general chatter) should use this.
     *
     * @param speech The speech request.
     */
    public void pushTail(BotSpeech speech) {
        contextualCounters.attempted++;
        if (disableAll || buffer.size() >= MAX_LENGTH) {
            contextualCounters.dropped++;
            return;
        }
        buffer.addLast(new QueuedSpeech(delay(speech), tick, Producer.CONTEXTUAL));
        contextualCounters.accepted++;
        maximumDepth = Math.max(maximumDepth, buffer.size());
    }

    /**
     * Adds a {@link BotSpeech} request to the <strong>head</strong> of the stack.
     * <p>
     * Higher-priority messages (e.g. contextual reactions) should use this.
     *
     * @param speech The speech request.
     */
    public void pushHead(BotSpeech speech) {
        contextualCounters.attempted++;
        if (disableAll) {
            contextualCounters.dropped++;
            return;
        }
        if (buffer.size() >= MAX_LENGTH) {
            // If buffer is full drop last request in the stack to make room.
            QueuedSpeech evicted = buffer.removeLast();
            counters(evicted.producer()).evicted++;
            if (evicted.speech() == pendingFiller) pendingFiller = null;
            headEvictions++;
        }
        buffer.addFirst(new QueuedSpeech(delay(speech), tick, Producer.CONTEXTUAL));
        contextualCounters.accepted++;
        maximumDepth = Math.max(maximumDepth, buffer.size());
    }

    /**
     * @return The next {@link BotSpeech} scheduled to execute.
     */
    public BotSpeech peek() {
        QueuedSpeech queued = buffer.peek();
        return queued == null ? null : queued.speech();
    }

    /**
     * Possibly pushes a filler message depending on the bot's social personality score and nearby players.
     * <p>
     * Does nothing if {@link #disableGeneral} is true or if no humans are near.
     */
    public void maybePushFiller() {
        pushFiller();
    }

    /**
     * Pushes a filler {@link BotSpeech} message selected from the {@link BotGeneralSpeechPool}.
     * <p>
     * These are idle, general phrases unrelated to specific contexts.
     */
    public void pushFiller() {
        refreshFillerRate();
        if (fillerSocial <= 0 || tick < nextFillerTick) return;
        // Consume each due opportunity once even when policy, capacity or the current corpus rejects it.
        scheduleFiller();
        generalCounters.attempted++;
        if (disableAll || isGeneralDisabled() || conversationOwner != null || bot.getLocalHumans().isEmpty() ||
                pendingFiller != null || buffer.size() >= MAX_LENGTH) {
            generalCounters.dropped++;
            return;
        }
        String phrase = selectFiller();
        if (phrase == null || phrase.isBlank()) {
            generalCounters.dropped++;
            return;
        }
        pendingFiller = new BotSpeech(phrase);
        buffer.addLast(new QueuedSpeech(delay(pendingFiller), tick, Producer.GENERAL));
        generalCounters.accepted++;
        maximumDepth = Math.max(maximumDepth, buffer.size());
    }

    /**
     * Avoids pending and recent public text, relaxing oldest history first for genuinely small pools.
     */
    private String selectFiller() {
        Set<String> pending = new HashSet<>();
        for (QueuedSpeech queued : buffer) pending.add(normalizeChat(queued.speech().getText()));
        Deque<String> recent = new ArrayDeque<>(previous);
        GeneralSpeech context = GeneralSpeech.selectContextFor(bot);
        while (true) {
            Set<String> excluded = new HashSet<>(pending);
            excluded.addAll(recent);
            String phrase = bot.getManager().getGeneralPool().take(bot, context,
                    candidate -> !excluded.contains(normalizeChat(candidate)));
            if (phrase != null || recent.isEmpty()) return phrase;
            recent.removeFirst();
        }
    }

    /**
     * Clears the current buffer of pending {@link BotSpeech} requests.
     * <p>
     * Typically called when switching scripts.
     */
    public void clear() {
        for (QueuedSpeech queued : buffer) counters(queued.producer()).canceled++;
        buffer.clear();
        pendingFiller = null;
    }

    /**
     * Samples bounded counters and ordinary queue ages on the game thread without retaining message text.
     */
    public Diagnostics getDiagnostics() {
        long oldestAge = 0;
        for (QueuedSpeech queued : buffer) oldestAge = Math.max(oldestAge, tick - queued.enqueueTick());
        return new Diagnostics(tick, generalCounters.snapshot(), contextualCounters.snapshot(), headEvictions,
                outputAccepted, outputFailed, publicDelivered, buffer.size(), oldestAge, maximumDepth);
    }

    /**
     * Returns the mutable counters belonging to a queued request's actual producer.
     */
    private ProducerCounters counters(Producer producer) {
        return producer == Producer.GENERAL ? generalCounters : contextualCounters;
    }

    /**
     * Removes expired ordinary requests even while a reserved conversation pauses normal queue processing.
     */
    private void expireQueue() {
        var iterator = buffer.iterator();
        while (iterator.hasNext()) {
            QueuedSpeech queued = iterator.next();
            int limit = queued.producer() == Producer.GENERAL ? GENERAL_MAX_AGE_TICKS : CONTEXTUAL_MAX_AGE_TICKS;
            if (tick - queued.enqueueTick() >= limit) {
                iterator.remove();
                counters(queued.producer()).expired++;
                if (queued.speech() == pendingFiller) pendingFiller = null;
            }
        }
    }

    /**
     * Removes only general requests when their audience or activity eligibility changes.
     */
    private void cancelFiller() {
        var iterator = buffer.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().producer() == Producer.GENERAL) {
                iterator.remove();
                generalCounters.canceled++;
            }
        }
        pendingFiller = null;
    }

    /**
     * Records simulated packet submission separately from the eventual public-chat callback.
     */
    private boolean transmit(BotSpeech speech) {
        boolean accepted = bot.getOutput().chat(speech.getText(), speech.getColor(), speech.getEffect());
        if (accepted) outputAccepted++;
        else outputFailed++;
        return accepted;
    }

    /**
     * Refreshes timing when the live personality changes, without banking missed opportunities.
     */
    private void refreshFillerRate() {
        double social = social();
        if (social != fillerSocial) {
            fillerSocial = social;
            scheduleFiller();
        }
    }

    /**
     * Schedules one jittered opportunity around the nominal personality-weighted rate.
     */
    private void scheduleFiller() {
        if (fillerSocial <= 0) {
            nextFillerTick = Long.MAX_VALUE;
            return;
        }
        long interval = Math.max(1L,
                Math.round(6000.0 / (FILLER_PER_HOUR * fillerSocial) * random.nextDouble(0.5, 1.5)));
        nextFillerTick = interval > Long.MAX_VALUE - tick ? Long.MAX_VALUE : tick + interval;
    }

    /**
     * Returns a finite social trait suitable for timing and rate calculations.
     */
    private double social() {
        double social = bot.getPersonality().getSocial();
        return Double.isFinite(social) ? Math.clamp(social, 0, 1) : 0;
    }

    /**
     * Applies a randomized delay to a {@link BotSpeech} based on the bot's social trait.
     *
     * @param request The speech request.
     * @return The modified request.
     */
    private BotSpeech delay(BotSpeech request) {
        if (request.delay == -1) {
            int baseDelay = (int) Math.round(200 - social() * 150);
            int spread = Math.max(5, baseDelay / 5);
            request.delay = random.nextInt(baseDelay - spread, baseDelay + spread + 1);
        }
        return request;
    }

    /**
     * Enables or disables general (non-contextual) speech.
     *
     * @param disableGeneral True to disable general speech.
     */
    public void setDisableGeneral(boolean disableGeneral) {
        this.disableGeneral = disableGeneral;
        if (disableGeneral) cancelFiller();
    }

    /**
     * Enables or disables all speech entirely.
     *
     * @param disableAll True to disable all speech.
     */
    public void setDisableAll(boolean disableAll) {
        this.disableAll = disableAll;
        if (disableAll) {
            clear();
            releaseConversation(conversationOwner);
        }
    }
}
