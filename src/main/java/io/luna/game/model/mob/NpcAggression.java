package io.luna.game.model.mob;

import com.google.common.collect.ImmutableSet;
import io.luna.game.model.World;
import io.luna.game.model.mob.NpcAggressionProfile.NpcAggressionPolicy;

import java.time.Duration;

/**
 * Handles automatic NPC aggression targeting logic.
 * <p>
 * This class is responsible for deciding when an {@link Npc} should automatically initiate combat against nearby
 * {@link Player}s. Aggression is controlled by the attacker's configured {@link NpcAggressionProfile}, the applicable
 * {@link NpcAggressionPolicy}, the player's aggression tolerance state, and a small set of contextual checks such as
 * distance, lock state, life state, and multi-combat rules.
 * <p>
 * God-affiliated aggression policies are backed by static item id sets that represent equipment which protects a
 * player from aggression by followers of a specific god.
 *
 * @author lare96
 */
public class NpcAggression {

    /**
     * Default aggression radius, in tiles, used by standard aggressive NPCs.
     */
    private static final int DEFAULT_AGGRESSION_RADIUS = 5;

    /**
     * Equipment ids considered Saradomin-aligned for aggression immunity checks.
     */
    private static final ImmutableSet<Integer> SARADOMIN_ITEMS = ImmutableSet.of(
            1716, // Unblessed symbol
            1718, // Holy symbol
            2412, // Saradomin cape
            2415, // Saradomin staff
            2661, // Saradomin plate
            2663, // Saradomin legs
            2665, // Saradomin full
            2667, // Saradomin kite
            3479, // Saradomin skirt
            3839, // Damaged book
            3840, // Holy book
            4037, // Saradomin banner
            4041, // Hooded cloak
            4513, // Castlewars hood
            6762  // Saradomin mjolnir
    );

    /**
     * Equipment ids considered Guthix-aligned for aggression immunity checks.
     */
    private static final ImmutableSet<Integer> GUTHIX_ITEMS = ImmutableSet.of(
            2413, // Guthix cape
            2416, // Guthix staff
            2669, // Guthix platebody
            2671, // Guthix platelegs
            2673, // Guthix full helm
            2675, // Guthix kiteshield
            3480, // Guthix plateskirt
            3843, // Damaged book
            3844, // Book of balance
            6760  // Guthix mjolnir
    );

    /**
     * Equipment ids considered Zamorak-aligned for aggression immunity checks.
     */
    private static final ImmutableSet<Integer> ZAMORAK_ITEMS = ImmutableSet.of(
            1033, // Zamorak robe
            1035, // Zamorak robe
            1722, // Unpowered symbol
            1724, // Unholy symbol
            2414, // Zamorak cape
            2417, // Zamorak staff
            2653, // Zamorak platebody
            2655, // Zamorak platelegs
            2657, // Zamorak full helm
            2659, // Zamorak kiteshield
            3478, // Zamorak plateskirt
            3841, // Damaged book
            3842, // Unholy book
            4039, // Zamorak banner
            4042, // Hooded cloak
            4515, // Castlewars hood
            6764  // Zamorak mjolnir
    );

    /**
     * The NPC performing aggression checks.
     */
    private final Npc attacker;

    /**
     * Creates a new aggression controller for an NPC.
     *
     * @param attacker The NPC whose automatic aggression should be processed.
     */
    public NpcAggression(Npc attacker) {
        this.attacker = attacker;
    }

    /**
     * Processes one aggression pass for the attacker.
     * <p>
     * This will only attempt a scan when the attacker:
     * <ul>
     *     <li>Has an aggression profile configured</li>
     *     <li>Is not locked</li>
     *     <li>Can currently attack</li>
     *     <li>Does not already have a combat target</li>
     * </ul>
     * If all conditions pass, nearby viewable players are scanned and the first valid candidate encountered by the
     * scan may be attacked.
     */
    public void process() {
        if (attacker.getAggressionProfile() != null &&
                !attacker.isLocked() &&
                attacker.getCombat().isAttackable() &&
                attacker.getCombat().getTarget() == null) {
            scanPlayers(attacker.getWorld(), attacker.getAggressionProfile().getPolicy());
        }
    }

    /**
     * Scans viewable players and attempts to aggress any valid target.
     * <p>
     * The effective scan radius is based on the attacker's aggression policy:
     * <ul>
     *     <li>Bosses use a radius of {@code 0}, which here means only the viewable-player list is trusted.</li>
     *     <li>{@link NpcAggressionPolicy#ALWAYS} doubles the default aggression radius.</li>
     *     <li>All other policies use {@link #DEFAULT_AGGRESSION_RADIUS}.</li>
     * </ul>
     * <p>
     * Each candidate must satisfy distance checks, multi-combat restrictions, tolerance rules, aggression policy
     * requirements, and basic alive/lock checks before an attack is initiated.
     *
     * @param world The world used to locate nearby viewable players.
     * @param policy The aggression policy currently governing this NPC.
     */
    private void scanPlayers(World world, NpcAggressionPolicy policy) {
        int radius = DEFAULT_AGGRESSION_RADIUS;
        if (attacker.isBoss()) {
            radius = 0; // 0 = Attack as soon as you're in viewing distance.
        } else if (policy == NpcAggressionPolicy.ALWAYS) {
            radius *= 2; // NPCs that are always aggressive have increased range.
        }
        for (Player victim : world.getLocator().findViewablePlayers(attacker)) {
            if (radius > 0 && !victim.isWithinDistance(attacker, radius)) {
                continue;
            }
            if (attacker.getCombat().checkMultiCombat(victim) &&
                    !isTolerantOf(victim) &&
                    checkPolicy(victim, policy) &&
                    victim.isAlive() &&
                    !victim.isLocked()) {
                attacker.getCombat().attack(victim);
                return;
            }
        }
    }

    /**
     * Evaluates whether a player satisfies the configured aggression policy.
     * <p>
     * Policy behavior:
     * <ul>
     *     <li>{@link NpcAggressionPolicy#COMBAT_LEVEL}: The player must be low enough combat level relative to the NPC.</li>
     *     <li>{@link NpcAggressionPolicy#WEARING_GUTHIX}: The player must not be wearing any Guthix-protective item.</li>
     *     <li>{@link NpcAggressionPolicy#WEARING_ZAMORAK}: The player must not be wearing any Zamorak-protective item.</li>
     *     <li>{@link NpcAggressionPolicy#WEARING_SARADOMIN}: The player must not be wearing any Saradomin-protective item.</li>
     *     <li>All other policies default to allowing aggression.</li>
     * </ul>
     *
     * @param player The candidate player.
     * @param policy The aggression policy to evaluate.
     * @return {@code true} if the player may be aggressed under {@code policy}, otherwise {@code false}.
     */
    private boolean checkPolicy(Player player, NpcAggressionPolicy policy) {
        switch (policy) {
            case COMBAT_LEVEL:
                return player.getCombatLevel() <= attacker.def().getCombatLevel() * 2;
            case WEARING_GUTHIX:
                return !player.getEquipment().containsAnyIds(GUTHIX_ITEMS);
            case WEARING_ZAMORAK:
                return !player.getEquipment().containsAnyIds(ZAMORAK_ITEMS);
            case WEARING_SARADOMIN:
                return !player.getEquipment().containsAnyIds(SARADOMIN_ITEMS);
            default:
                return true;
        }
    }

    /**
     * Determines whether this NPC should currently tolerate a player instead of auto-aggressing them.
     * <p>
     * Tolerance is based on the player's recorded aggression-tolerance duration and the attacker's configured
     * tolerance window in minutes.
     * <p>
     * Tolerance is ignored entirely when:
     * <ul>
     *     <li>The NPC's configured tolerance is {@code -1}.</li>
     *     <li>The attacker is a boss.</li>
     * </ul>
     *
     * @param player The player being evaluated.
     * @return {@code true} if the player has remained long enough to be tolerated, otherwise {@code false}.
     */
    private boolean isTolerantOf(Player player) {
        Duration duration = player.getTolerance().getDuration();
        int minutes = attacker.getAggressionProfile().getToleranceMinutes();
        if (minutes == -1 || attacker.isBoss()) {
            return false;
        }
        return duration.toMinutes() >= minutes;
    }
}