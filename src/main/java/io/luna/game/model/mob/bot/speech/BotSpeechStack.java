package io.luna.game.model.mob.bot.speech;

import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.brain.BotPersonality;
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler.ChatColor;
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler.ChatEffect;
import io.luna.game.model.mob.bot.speech.BotGeneralSpeechPool.GeneralSpeech;
import io.luna.util.Rational;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages a buffer of {@link BotSpeech} requests. This class facilitates when and how often bots will speak (but not
 * what they say), and helps keeps messages synchronized by handling both contextual, general, and manually pushed speech
 * requests in one place.
 */
public final class BotSpeechStack {

    /**
     * The maximum amount of time this stack will wait for a request before executing and moving onto the next.
     */
    private static final int MAX_DELAY_TICKS = 250;

    /**
     * Approximately how many general speech requests will be pushed per hour (assuming perfect conditions, 1.0 social). Increasing this number increases the chance
     * of general speech requests being pushed when the conditions are right.
     */
    private static final int FILLER_PER_HOUR = 120;

    /**
     * Determines how many {@link BotSpeech} requests will be queued before throttling occurs. Lowering this
     * value usually results in less messages from the {@link BotGeneralSpeechPool} being spoken, but a value too
     * low will cause more important contextual messages to be throttled. Raising the value as more
     * {@link BotSpeechContextInjector} types are registered is reccomended.
     * <p>
     * Reccomended minimum value: 5
     * Minimum value: 1
     */
    private static final int MAX_LENGTH = 10;

    /**
     * The buffer of speech requests.
     */
    private final Deque<BotSpeech> buffer;

    /**
     * The last successful {@link #MAX_LENGTH} executed speech requests.
     */
    private final Deque<BotSpeech> previous;

    /**
     * The speech manager
     */
    private final BotSpeechManager speechManager;

    /**
     * The bot this speech stack is for.
     */
    private final Bot bot;

    private Rational fillerRatio;

    /**
     * If speech from the {@link BotGeneralSpeechPool} is disabled.
     */
    private boolean disableGeneral;

    /**
     * If speech from {@link BotSpeechContextInjector} types is disabled.
     */
    private boolean disableInjection;

    /**
     * If this bot should stop talking completely.
     */
    private boolean disableAll;

    /**
     * Creates a new {@link BotSpeechStack}
     *
     * @param bot
     */
    public BotSpeechStack(Bot bot, BotSpeechManager speechManager) {
        this.bot = bot;
        this.speechManager = speechManager;
        buffer = new ArrayDeque<>(MAX_LENGTH);
        previous = new ArrayDeque<>(MAX_LENGTH);
        fillerRatio = new Rational(FILLER_PER_HOUR, 6000);
    }

    public void process() {
        BotSpeech speech = buffer.peek();
        if (speech == null) {
            // Only rebuild stack if players around.
            rebuild();
        } else if (speech.delay > 0) {
            if (speech.delay > MAX_DELAY_TICKS) {
                speech.delay = MAX_DELAY_TICKS;
            }
            speech.delay--;
        } else if (speech.delay == 0) {
            bot.getOutput().chat(speech.getText(), speech.getColor(), speech.getEffect());
            if (previous.size() > MAX_LENGTH) {
                previous.poll();
            }
            previous.add(buffer.poll());
        }
    }

    /**
     * Forwards to {@link #pushTail(BotSpeech)}.
     */
    public void push(BotSpeech speech) {
        pushTail(speech);
    }

    /**
     * Pushes a {@link BotSpeech} request to the <strong>tail</strong> of the stack. The request will be processed
     * <strong>after</strong> all existing requests in the stack. Use this for low-priority messages, like ones
     * sent using {@link #pushGeneral()}.
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
     * Pushes a {@link BotSpeech} request to the <strong>head</strong> of the stack. The request will be processed
     * <strong>before</strong> all existing requests in the stack. Use this for high-priority messages, like ones
     * sent from {@link BotSpeechContextInjector} types.
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

    public BotSpeech peek() {
        return buffer.peek();
    }

    public void pushGeneral() {
        if (!disableGeneral && !bot.getPersonality().isIntelligent()) {
            String phrase = speechManager.getGeneralSpeechPool().take(bot, GeneralSpeech.selectContextFor(bot));
            push(new BotSpeech(phrase, ChatColor.YELLOW, ChatEffect.NONE));
        }
    }

    // speech stack cleared on script change
    public void clear() {
        buffer.clear();
    }

    private void rebuild() {
        // Only rebuild stack if real players are nearby.
        BotPersonality personality = bot.getPersonality();
        if (!bot.getLocalHumans().isEmpty() && personality.isIntelligent()) {
            // Scale filler per hour to social score.
            long fillerPerHour = (long) (FILLER_PER_HOUR * personality.getSocial());
            fillerRatio = new Rational(fillerPerHour, fillerRatio.getDenominator());

            // Scale initial stack requests to social score.
            int maxLoops = (int) (MAX_LENGTH * personality.getSocial());
            for (int loops = 0; loops < maxLoops; loops++) {
                pushGeneral();
            }
        }
    }

    private BotSpeech delay(BotSpeech request) {
        if (request.delay == -1) {
            double social = bot.getPersonality().getSocial();
            int min = 50;
            int baseDelay = (int) (MAX_DELAY_TICKS - social * (MAX_DELAY_TICKS - min));
            int jitter = ThreadLocalRandom.current().nextInt(20, 80);
            request.delay = baseDelay + jitter;
        }
        return request;
    }

    public void setDisableGeneral(boolean disableGeneral) {
        this.disableGeneral = disableGeneral;
    }

    public void setDisableInjection(boolean disableInjection) {
        this.disableInjection = disableInjection;
    }

    public void setDisableAll(boolean disableAll) {
        this.disableAll = disableAll;
    }
}
