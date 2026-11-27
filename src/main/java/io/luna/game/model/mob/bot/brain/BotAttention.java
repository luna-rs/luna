package io.luna.game.model.mob.bot.brain;

import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.MobReference;
import io.luna.game.model.mob.bot.Bot;

import static java.util.Objects.requireNonNull;

/**
 * The things a {@link Bot} has noticed and not yet reacted to.
 * <p>
 * Each kind of {@link Notice} has one waiting place. Whoever spots something worth a reaction offers it here with a
 * delay, and the bot deals with it on its own turn, after its reflexes. At most one notice is handled per tick, and
 * only while its source is still in view and the bot is alive.
 * <p>
 * A reaction that speaks or records something leaves the running script alone, unlike a reflex, which pauses it.
 * Nothing here is saved.
 *
 * @author TheLining
 */
public final class BotAttention {

    /**
     * The kinds of thing a bot can notice, in the order they are handled.
     */
    public enum Notice {

        /**
         * A real player said this bot's name in public chat.
         */
        NAMED,

        /**
         * A real player greeted this bot or the people around it.
         */
        GREETING,

        /**
         * A real player asked this bot or the people around it what they are working toward.
         */
        QUESTION;

        /**
         * All kinds, read once.
         */
        private static final Notice[] ALL = values();
    }

    /**
     * What a bot does about a notice once it is due.
     */
    @FunctionalInterface
    public interface Reaction {

        /**
         * Reacts to a notice whose source is still in view.
         *
         * @param bot The bot reacting.
         * @param source Who or what the notice is about.
         * @return {@code true} if the notice is dealt with, or {@code false} to try again next tick until it expires.
         */
        boolean react(Bot bot, Mob source);
    }

    /**
     * A notice waiting to be handled.
     *
     * @param source A reference to the source, which does not keep a logged out player in memory.
     * @param dueTick The first world tick the bot may react.
     * @param expiresTick The last world tick the bot may react.
     * @param reaction What the bot does.
     */
    private record Pending(MobReference source, long dueTick, long expiresTick, Reaction reaction) {
    }

    /**
     * The bot.
     */
    private final Bot bot;

    /**
     * The waiting notice of each kind, by {@link Notice#ordinal()}.
     */
    private final Pending[] pending = new Pending[Notice.ALL.length];

    /**
     * How many notices are waiting, so a bot with none does no work.
     */
    private int waiting;

    /**
     * Creates a new {@link BotAttention}.
     *
     * @param bot The bot.
     */
    public BotAttention(Bot bot) {
        this.bot = bot;
    }

    /**
     * Offers a notice to this bot. A notice of the same kind that is still waiting keeps its place, so the new one
     * is turned away.
     *
     * @param kind The kind of notice.
     * @param source Who or what it is about.
     * @param delayTicks How many ticks pass before the bot may react.
     * @param lifetimeTicks How many more ticks the bot may still react in, if it is busy when the delay ends.
     * @param reaction What the bot does.
     * @return {@code true} if the notice was taken.
     */
    public boolean offer(Notice kind, Mob source, int delayTicks, int lifetimeTicks, Reaction reaction) {
        requireNonNull(reaction);
        MobReference reference = MobReference.capture(source);
        if (reference == null || delayTicks < 0 || lifetimeTicks < 0) {
            return false;
        }
        long now = bot.getWorld().getCurrentTick();
        Pending held = pending[kind.ordinal()];
        if (held != null) {
            if (now <= held.expiresTick) {
                return false;
            }
            waiting--;
        }
        long dueTick = now + delayTicks;
        pending[kind.ordinal()] = new Pending(reference, dueTick, dueTick + lifetimeTicks, reaction);
        waiting++;
        return true;
    }

    /**
     * @param kind The kind of notice.
     * @return Whether a notice of this kind is waiting.
     */
    public boolean isWaiting(Notice kind) {
        Pending held = pending[kind.ordinal()];
        return held != null && bot.getWorld().getCurrentTick() <= held.expiresTick;
    }

    /**
     * Handles at most one due notice. Expired notices, and notices whose source has gone or is out of view, are
     * dropped.
     */
    public void process() {
        if (waiting == 0 || !bot.isAlive()) {
            return;
        }
        long now = bot.getWorld().getCurrentTick();
        for (int index = 0; index < pending.length; index++) {
            Pending held = pending[index];
            if (held == null || now < held.dueTick) {
                continue;
            }
            Mob source = now > held.expiresTick ? null : held.source.resolve();
            if (source == null || !source.isViewableFrom(bot)) {
                clear(index);
                continue;
            }
            if (held.reaction.react(bot, source)) {
                clear(index);
                return;
            }
        }
    }

    /**
     * Empties one waiting place.
     */
    private void clear(int index) {
        pending[index] = null;
        waiting--;
    }
}
