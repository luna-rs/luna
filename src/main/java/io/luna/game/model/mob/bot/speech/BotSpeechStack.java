package io.luna.game.model.mob.bot.speech;

import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.brain.BotPersonality;
import io.luna.game.model.mob.bot.speech.BotGeneralSpeechPool.GeneralSpeech;
import io.luna.util.RandomUtils;
import io.luna.util.Rational;
import io.luna.util.StringUtils;
import io.luna.net.codec.ByteMessage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

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
     * The buffer of speech requests.
     */
    private final Deque<BotSpeech> buffer;

    /**
     * The record of the most recent {@link #MAX_LENGTH} executed speech requests.
     */
    private final Deque<BotSpeech> previous;

    /**
     * The bot this speech stack is for.
     */
    private final Bot bot;

    /**
     * The precomputed filler chance ratio.
     */
    private Rational fillerRatio;

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
        if (owner != null) generalSuppressors.add(owner);
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

    /** Drops a queued turn if its conversation or live context ended before packet delivery. */
    public boolean validateConversationDelivery(String text) {
        var iterator = conversationDeliveries.iterator();
        while (iterator.hasNext()) {
            ConversationDelivery delivery = iterator.next();
            if (delivery.text().equals(text.trim())) {
                iterator.remove();
                return ownsConversation(delivery.owner());
            }
        }
        return true;
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
        buffer = new ArrayDeque<>(MAX_LENGTH);
        previous = new ArrayDeque<>(MAX_LENGTH);
    }

    /**
     * Processes the stack, ticking down speech delays and executing queued messages when ready.
     * <p>
     * This is called once per game tick.
     */
    public void process() {
        if (disableAll) {
            buffer.clear();
            releaseConversation(conversationOwner);
            return;
        }
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
                        if (!bot.getOutput().chat(conversationSpeech.getText(), conversationSpeech.getColor(),
                                conversationSpeech.getEffect())) {
                            conversationDeliveries.remove(delivery);
                            releaseConversation(conversationOwner);
                        }
                    }
                }
                return;
            }
        }
        maybePushFiller();

        BotSpeech speech = buffer.peek();
        if (speech == null) {
            // Only rebuild stack if players nearby.
            rebuild();
            return;
        }

        if (speech.delay > 0) {
            if (speech.delay > MAX_DELAY_TICKS) {
                speech.delay = MAX_DELAY_TICKS;
            }
            speech.delay--;
        } else if (speech.delay == 0) {
            bot.getOutput().chat(speech.getText(), speech.getColor(), speech.getEffect());
            if (previous.size() >= MAX_LENGTH) {
                previous.poll();
            }
            previous.add(buffer.poll());
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
        if (buffer.size() < MAX_LENGTH) {
            // If buffer is full drop low-priority requests.
            buffer.add(delay(speech));
        }
    }

    /**
     * Adds a {@link BotSpeech} request to the <strong>head</strong> of the stack.
     * <p>
     * Higher-priority messages (e.g. contextual reactions) should use this.
     *
     * @param speech The speech request.
     */
    public void pushHead(BotSpeech speech) {
        if (buffer.size() >= MAX_LENGTH) {
            // If buffer is full drop last request in the stack to make room.
            buffer.removeLast();
        }
        buffer.addFirst(delay(speech));
    }

    /**
     * @return The next {@link BotSpeech} scheduled to execute.
     */
    public BotSpeech peek() {
        return buffer.peek();
    }

    /**
     * Possibly pushes a filler message depending on the bot's social personality score and nearby players.
     * <p>
     * Does nothing if {@link #disableGeneral} is true or if no humans are near.
     */
    public void maybePushFiller() {
        if (isGeneralDisabled() || bot.getLocalHumans().isEmpty()) {
            return;
        }
        // Scale filler ratio to social score.
        if (fillerRatio == null) {
            long fillerPerHour = (long) (FILLER_PER_HOUR * bot.getPersonality().getSocial());
            fillerRatio = new Rational(fillerPerHour, 6000);
        }

        // Roll chance based on fillerRatio.
        if (RandomUtils.roll(fillerRatio)) {
            pushFiller();
        }
    }

    /**
     * Pushes a filler {@link BotSpeech} message selected from the {@link BotGeneralSpeechPool}.
     * <p>
     * These are idle, general phrases unrelated to specific contexts.
     */
    public void pushFiller() {
        if (!isGeneralDisabled()) {
            String phrase = bot.getManager().getGeneralPool().take(bot, GeneralSpeech.selectContextFor(bot));
            push(new BotSpeech(phrase));
        }
    }

    /**
     * Clears the current buffer of pending {@link BotSpeech} requests.
     * <p>
     * Typically called when switching scripts.
     */
    public void clear() {
        buffer.clear();
    }

    /**
     * Rebuilds the buffer with filler messages when nearby players are present.
     * <p>
     * Used to rebuild the speech stack after idle periods.
     */
    private void rebuild() {
        // Only rebuild stack if real players are nearby.
        BotPersonality personality = bot.getPersonality();
        if (!bot.getLocalHumans().isEmpty() && !isGeneralDisabled()) {
            // Scale initial stack requests to social score.
            int maxLoops = Math.max((int) (MAX_LENGTH * personality.getSocial()), 1);
            for (int loops = 0; loops < maxLoops; loops++) {
                pushFiller();
            }
        }
    }

    /**
     * Applies a randomized delay to a {@link BotSpeech} based on the bot's social trait.
     *
     * @param request The speech request.
     * @return The modified request.
     */
    private BotSpeech delay(BotSpeech request) {
        if (request.delay == -1) {
            double social = bot.getPersonality().getSocial();
            int min = 50;
            int baseDelay = (int) (MAX_DELAY_TICKS - social * (MAX_DELAY_TICKS - min));
            int low = (int) Math.max(10 + 30 * (1 - social), 5);
            int high = (int) Math.max(40 + 70 * social, low + 5);
            int jitter = ThreadLocalRandom.current().nextInt(low, high);
            request.delay = baseDelay + jitter;
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
    }

    /**
     * Enables or disables all speech entirely.
     *
     * @param disableAll True to disable all speech.
     */
    public void setDisableAll(boolean disableAll) {
        this.disableAll = disableAll;
    }
}
