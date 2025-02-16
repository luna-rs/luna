package io.luna.game.model.mob.bot;

/**
 * Holds settings parsed from the "bots" section in {@code ./data/luna.json} file.
 *
 * @author lare96
 */
public final class BotSettings {

    private final int idealCount;
    private final boolean keepOnline;

    /**
     * The amount of persistent bots the {@link BotScheduleService} will try to keep online.
     */
    public int idealCount() {
        return idealCount;
    }

    /**
     * If persistent bots should always be kept online and never logout (until the server restarts).
     */
    public boolean keepOnline() {
        return keepOnline;
    }

    // Never instantiated.
    private BotSettings(int idealCount, boolean keepOnline) {
        this.idealCount = idealCount;
        this.keepOnline = keepOnline;
    }
}