package io.luna.game.model.mob.combat.attack;

import api.bot.action.BotCombatActionHandler;
import api.combat.npc.NpcCombatHandler;
import game.player.Sound;
import engine.combat.prayer.CombatPrayer;
import engine.combat.status.hooks.PoisonStatusEffect;
import io.luna.game.model.mob.combat.CombatRestriction;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageAction;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.net.msg.out.SoundMessageWriter;

import java.time.Instant;

/**
 * Represents a prepared combat attack from one {@link Mob} onto another. It essentially models a single combat turn,
 * swing, or attack attempt.
 * <p>
 * Subclasses are responsible for calculating a base damage result in {@link #calculateDamage(Mob)} and for
 * implementing the concrete attack behavior in {@link #attack()}. This base class handles the shared combat pipeline,
 * including damage preparation, optional cancellation through {@link #onAttack(CombatDamage)}, launch audio,
 * combat timer refreshes,
 * attack delay application, and recipient-owned projectile timing callbacks.
 *
 * @param <T> The mob type performing this attack.
 * @author lare96
 */
public abstract class CombatAttack<T extends Mob> {

    /**
     * The mob performing the attack.
     */
    protected final T attacker;

    /**
     * The mob being attacked.
     * <p>
     * This reference is supplied when the attack is created and is retained through delayed delivery.
     */
    protected final Mob victim;

    /**
     * The interaction policy that must be satisfied in order for this attack to proceed.
     */
    protected final InteractionPolicy interactionPolicy;

    /**
     * The number of ticks that must pass before the attacker can attack again.
     */
    protected int delay;

    /**
     * The final prepared damage for this attack.
     * <p>
     * This value is assigned during {@link #apply()} after the result from {@link #calculateDamage(Mob)} has been
     * passed through {@link #onAttack(CombatDamage)}. Defensive changes and health capping belong to each delivered
     * hit and do not replace this launch value. Secondary recipients receive independently resolved damage while
     * sharing the source attack's launch context.
     */
    protected CombatDamage nextDamage;

    /**
     * If this attack should ignore the attack delay (attack instantly, do not reset attack delay after).
     */
    private boolean ignoreAttackDelay;

    /**
     * A timestamp set when this attack is applied using {@link #apply()}.
     */
    private Instant timestamp;

    /**
     * The world tick at which this attack committed, or {@code -1} before {@link #apply()} succeeds.
     * Gameplay recency checks use this value independently of the wall-clock timestamp.
     */
    private long launchTick = -1;

    /**
     * Smite state captured at launch, shared by every delivered hit from this attack.
     */
    private boolean smiteActive;

    /**
     * Ordinary NPC poison severity captured when the attack commits. Transformation or removal during flight
     * cannot change an already launched hit. Custom poison attacks leave this at zero in their NPC definition.
     */
    private int npcPoisonSeverity;

    /**
     * Returns the launch-time Smite state. Secondary attack wrappers may delegate to their original source.
     */
    public boolean isSmiteActive() {
        return smiteActive;
    }

    /**
     * Creates a new {@link CombatAttack}.
     *
     * @param attacker The mob performing the attack.
     * @param victim The mob receiving the attack.
     * @param interactionPolicy The interaction policy that must be satisfied in order for this attack to proceed.
     * @param delay The attack delay, in ticks, applied after execution.
     */
    public CombatAttack(T attacker, Mob victim, InteractionPolicy interactionPolicy, int delay) {
        this.attacker = attacker;
        this.victim = victim;
        this.interactionPolicy = interactionPolicy;
        this.delay = delay;
    }

    /**
     * Executes the attack-specific behavior.
     * <p>
     * Implementations should perform the concrete combat actions here, such as playing animations, launching
     * projectiles, queuing hitsplats, displaying graphics, or triggering special effects.
     * <p>
     * When invoked through {@link #apply()}, {@link #nextDamage} is expected to already be prepared and non-null and
     * is typically queued for application within a {@link CombatDamageAction}.
     */
    public abstract void attack();

    /**
     * Calculates the base damage for this attack against the specified target.
     * <p>
     * This method is called during {@link #apply()} before {@link #onAttack(CombatDamage)} is invoked.
     *
     * @param other The target being attacked.
     * @return The calculated damage result for the attack.
     */
    public abstract CombatDamage calculateDamage(Mob other);

    /**
     * Applies the shared combat state updates and executes the attack.
     * <p>
     * This method calculates the base damage, passes it through {@link #onAttack(CombatDamage)} to allow subclasses
     * to modify or cancel the result, then applies the standard combat delay and timer updates before invoking {@link #attack()}.
     * Attacks that ignore the attack delay preserve the existing cooldown instead of replacing it. An optional
     * player launch sound is captured before preparation can consume equipment, then played once immediately before
     * the attack behavior. Misses and accurate zero hits still play the sound; cancellation suppresses it.
     * NPC launch sounds are selected after preparation, allowing attack hooks to choose their committed sound,
     * and sent only to the player being attacked.
     * <p>
     * If {@link #onAttack(CombatDamage)} returns {@code null}, the attack is treated as cancelled and {@link #attack()}
     * will not be executed.
     */
    public final void apply() {
        if (!CombatRestriction.permits(attacker, victim)) {
            attacker.getCombat().setTarget(null);
            return;
        }
        Sound attackSound = getAttackSound();
        nextDamage = onAttack(calculateDamage(victim));
        if (nextDamage == null) {
            attacker.getCombat().setTarget(null);
            return;
        }
        if (attacker instanceof Player && victim instanceof Player) {
            BotCombatActionHandler.observePvpAttack((Player) attacker, (Player) victim);
        }
        if (!ignoreAttackDelay) {
            attacker.getCombat().setAttackDelay(delay);
        }
        attacker.getCombat().setLastCombatWith(victim);
        attacker.getCombat().resetCombatTimer();
        victim.getCombat().markAttackedBy(attacker);
        timestamp = Instant.now();
        launchTick = attacker.getWorld().getCurrentTick();
        npcPoisonSeverity = attacker instanceof Npc npc ? npc.combatDef().getPoisonSeverity() : 0;
        smiteActive = attacker instanceof Player player && player.skill(Skill.PRAYER).getLevel() > 0 &&
                player.getCombat().getPrayers().isActive(CombatPrayer.SMITE);
        if (attackSound != null && attacker instanceof Player player) {
            player.playSound(attackSound);
            if (victim instanceof Player other && other != player) {
                other.playSound(attackSound);
            }
        }
        if (attacker instanceof Npc && victim instanceof Player player) {
            int soundId = getNpcAttackSound();
            if (soundId >= 0) {
                player.queue(new SoundMessageWriter(soundId, 1, 0));
            }
        }
        attack();
    }

    /**
     * Selects an NPC launch sound only after its attack commits. Ordinary melee and ranged attacks use their
     * respective definition sounds; custom attacks remain silent here unless they opt in.
     *
     * @return The cache sound id, or {@code -1} to leave playback to the attack implementation.
     */
    protected int getNpcAttackSound() {
        return -1;
    }

    /**
     * Selects an optional player attack launch sound before damage preparation or ammunition consumption.
     * <p>
     * The captured sound is sent once to the attacking player and a player victim only after the attack commits,
     * including inaccurate and zero-damage attacks. Cancelled attacks remain silent. The default leaves spell,
     * NPC, and custom attack audio on their existing paths; specials may override ordinary weapon selection.
     *
     * @return The launch sound, or {@code null} to suppress shared launch playback.
     */
    protected Sound getAttackSound() {
        return null;
    }

    /**
     * Intercepts the calculated damage before the attack executes.
     * <p>
     * Subclasses may override this to modify, replace, or cancel the pending damage result.
     *
     * @param damage The calculated damage result.
     * @return The final damage to use, or {@code null} to cancel the attack.
     */
    public CombatDamage onAttack(CombatDamage damage) {
        return damage;
    }

    /**
     * Computes the delivered damage used for player combat experience after health and defensive caps.
     * NPC hook metadata may reduce or boost damage experience; player targets retain normal allocation.
     *
     * @param damage The applied hit whose recipient and capped damage determine experience.
     * @return Non-negative experience damage, before the captured stance distributes it to skills.
     */
    protected final double experienceDamage(CombatDamage damage) {
        double multiplier = damage.getVictim() instanceof Npc npc ?
                NpcCombatHandler.INSTANCE.damageExperienceMultiplier(npc) : 1.0;
        return Math.max(0, damage.getRawAmount()) * multiplier;
    }

    /**
     * Called after a hit from this attack has been applied and recorded in combat history.
     * <p>
     * The supplied hit has passed defensive hooks and health capping. Its recipient may differ from {@link #victim}
     * for area or secondary hits. Use this argument for delivered damage and effects; {@link #nextDamage} retains
     * the primary launch result. Nested callbacks may publish a newer hit before this callback returns.
     *
     * @param damage The applied hit, including its actual recipient, amount, and original accuracy result.
     */
    public void onDamageApplied(CombatDamage damage) {

    }

    /**
     * Applies configured NPC poison after defensive hooks and health capping, independently of custom hit callbacks.
     * Every accurate positive hit poisons a surviving player, matching the reference's lack of an extra chance roll.
     * Status insertion handles antipoison and stronger-poison refresh without restarting the existing countdown.
     * Only the original NPC's delivered hits qualify; secondary recipients use their own applied damage.
     *
     * @param damage The delivered hit, not the original launch roll.
     */
    public final void applyNpcPoison(CombatDamage damage) {
        if (npcPoisonSeverity > 0 && damage.getAttacker() == attacker && damage.isAccurate() &&
                damage.getRawAmount() > 0 && damage.getVictim() instanceof Player player && player.isAlive()) {
            player.getStatus().add(new PoisonStatusEffect(player, npcPoisonSeverity), false);
        }
    }

    /**
     * Called when a projectile-based attack is launched.
     * <p>
     * Subclasses may override this to display a projectile or perform other logic that should occur at launch time.
     */
    public void onProjectileLaunched() {

    }

    /**
     * Called when a projectile-based attack is considered to have reached its target.
     * <p>
     * Subclasses may override this to apply impact graphics, queue damage, or perform other logic that should occur
     * on projectile arrival.
     */
    public void onProjectileReached() {

    }

    /**
     * Called when the recipient's projectile event finishes or is discarded.
     * <p>
     * Normal completion follows the arrival callback and does not imply that damage was delivered. Recipient
     * cleanup and arrival failures report interruption. The legacy two-event scheduler reports completion once
     * for the whole flight, even when both its launch and arrival events are discarded.
     *
     * @param interrupted Whether the event was discarded or its arrival callback failed.
     */
    public void onProjectileFinished(boolean interrupted) {

    }

    /**
     * Schedules the arrival of an already displayed projectile on the victim's incoming combat queue.
     * <p>
     * The deadline is relative to the current world tick and survives changes to the attacker's action queue.
     * Both delivery and interruption notify {@link #onProjectileFinished(boolean)}.
     *
     * @param arrivalDelay The non-negative delay in game ticks until the event is due.
     * @throws IllegalArgumentException If {@code arrivalDelay} is negative.
     */
    protected final void scheduleProjectileArrival(int arrivalDelay) {
        victim.getCombat().getImpacts().schedule(arrivalDelay, this::onProjectileReached, this::onProjectileFinished);
    }

    /**
     * Retains the legacy launch and arrival offsets while giving both callbacks to the recipient's tick queue.
     * Execution counter zero corresponds to the tick after the attack, so offsets receive one additional tick.
     * Recipient removal discards the flight and invokes the finish hook once, even if both callbacks are pending.
     *
     * @param launchTicks The legacy execution counter on which the projectile is displayed.
     * @param arrivalTicks The later legacy execution counter on which it arrives.
     */
    protected final void launchProjectile(int launchTicks, int arrivalTicks) {
        /**
         * Shares a single completion notification between the legacy launch and arrival events.
         *
         * @author lare96
         */
        class Flight {
            /**
             * Whether this flight's completion hook has already been notified.
             */
            private boolean finished;

            /**
             * Notifies the owning attack once for the complete flight.
             *
             * @param interrupted Whether an event was discarded or its callback failed.
             */
            void finish(boolean interrupted) {
                if (!finished) {
                    finished = true;
                    onProjectileFinished(interrupted);
                }
            }
        }
        Flight flight = new Flight();
        var impacts = victim.getCombat().getImpacts();
        impacts.schedule(launchTicks + 1, () -> {
            if (!flight.finished) {
                onProjectileLaunched();
            }
        }, interrupted -> {
            if (interrupted) {
                flight.finish(true);
            }
        });
        impacts.schedule(arrivalTicks + 1, () -> {
            if (!flight.finished) {
                onProjectileReached();
            }
        }, flight::finish);
    }

    /**
     * @return The attacking mob.
     */
    public T getAttacker() {
        return attacker;
    }

    /**
     * @return The victim.
     */
    public Mob getVictim() {
        return victim;
    }

    /**
     * @return The interaction policy that must be satisfied.
     */
    public InteractionPolicy getInteractionPolicy() {
        return interactionPolicy;
    }

    /**
     * @return The attack delay, in ticks.
     */
    public int getDelay() {
        return delay;
    }

    /**
     * Sets the attack delay, in ticks.
     *
     * @param delay The new delay.
     */
    public void setDelay(int delay) {
        this.delay = delay;
    }

    /**
     * Gets the primary launch damage before recipient-side defensive changes and health capping.
     *
     * @return The prepared launch hit, or {@code null} if not yet assigned or if the attack was cancelled.
     */
    public CombatDamage getNextDamage() {
        return nextDamage;
    }

    /**
     * @return {@code true} if this attack should ignore the attack delay.
     */
    public boolean isIgnoreAttackDelay() {
        return ignoreAttackDelay;
    }

    /**
     * Sets if this attack should ignore the attack delay.
     *
     * @param ignoreAttackDelay The new value.
     */
    public void setIgnoreAttackDelay(boolean ignoreAttackDelay) {
        this.ignoreAttackDelay = ignoreAttackDelay;
    }

    /**
     * @return The timestamp set when this attack is applied, or {@code null} if this attack hasn't been applied yet.
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * @return The world tick at which this attack committed, or {@code -1} if it has not committed.
     */
    public long getLaunchTick() {
        return launchTick;
    }
}
