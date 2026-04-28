package io.luna.game.model.mob.combat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.HashMap;
import java.util.Map;

/**
 * Enumerates special attack types and the item IDs that can trigger them.
 *
 * @author lare96
 */
public enum SpecialAttackType {

    /**
     * Name: Lumber Up
     *
     * <p>Energy Used: 100%
     *
     * <p>Description: Temporarily increases the user's Woodcutting level by 3.
     */
    DRAGON_AXE(6739),

    /**
     * Name: Sanctuary
     *
     * <p>Energy Used: 100%
     *
     * <p>Description: Temporarily increases the user's Defence level by 8.
     */
    EXCALIBUR(35),

    /**
     * Name: Rampage
     *
     * <p>Energy Used: 100%
     *
     * <p>Description: Lowers the user's Attack, Defence, Ranged, and Magic levels by 10%
     * each, then increases Strength by 10 plus 25% of the drained levels.
     */
    DRAGON_BATTLE_AXE(1377),

    /**
     * Name: Sweep
     *
     * <p>Energy Used: 30%
     *
     * <p>Description: Hits targets in front of the user for 10% extra damage. Large targets can be hit twice, with
     * the second hit using 25% reduced accuracy.
     */
    DRAGON_HALBERD(3204),

    /**
     * Name: Powerstab
     *
     * <p>Energy Used: 60%
     *
     * <p>Description: Hits up to 14 enemies surrounding the user.
     */
    DRAGON_2H_SWORD(7158),

    /**
     * Name: Chainhit
     *
     * <p>Energy Used: 10% per hit, up to 50%
     *
     * <p>Description: Ricochets from the target to hit up to 5 opponents. Each chained hit consumes an additional
     * 10% special attack energy.
     */
    RUNE_THROWN_AXE(805),

    /**
     * Name: Energy Drain
     *
     * <p>Energy Used: 50%
     *
     * <p>Description: Attacks with 25% increased accuracy and transfers 10% of the target's run energy to the user
     * in PvP.
     */
    ABYSSAL_WHIP(4151),

    /**
     * Name: Weaken
     *
     * <p>Energy Used: 50%
     *
     * <p>Description: On a successful hit, reduces the target's Attack, Strength, and Defence by 5% of their maximum
     * levels, or 10% against demons.
     */
    DARKLIGHT(6746),

    /**
     * Name: Sever
     *
     * <p>Energy Used: 55%
     *
     * <p>Description: On a successful hit, prevents the target from using protection prayers for 5 seconds.
     */
    DRAGON_SCIMITAR(4587),

    /**
     * Name: Cleave
     *
     * <p>Energy Used: 25%
     *
     * <p>Description: Deals 15% more damage for one hit.
     */
    DRAGON_LONGSWORD(1305),

    /**
     * Name: Shatter
     *
     * <p>Energy Used: 25%
     *
     * <p>Description: Increases damage by 50% and accuracy by 25% for one hit.
     */
    DRAGON_MACE(1434),

    /**
     * Name: Powershot
     *
     * <p>Energy Used: 35%
     *
     * <p>Description: Fires a shot that is guaranteed to hit.
     */
    MAGIC_LONGBOW(859),

    /**
     * Name: Impale
     *
     * <p>Energy Used: 25%
     *
     * <p>Description: Attacks with 10% more Attack and Strength, but with a slower swing.
     */
    RUNE_CLAWS(3101),

    /**
     * Name: Puncture
     *
     * <p>Energy Used: 25%
     *
     * <p>Description: Performs two quick slashes with 25% increased accuracy and 15% increased damage.
     */
    DRAGON_DAGGER(1215, 1231, 5680, 5698),

    /**
     * Name: Quick Smash
     *
     * <p>Energy Used: 50%
     *
     * <p>Description: Instantly performs an additional attack with no extra modifiers.
     */
    GRANITE_MAUL(4153),

    /**
     * Name: Snapshot
     *
     * <p>Energy Used: 55%
     *
     * <p>Description: Fires two arrows in rapid succession with reduced accuracy. The special ignores prayer-based
     * and Void-style damage boosts.
     */
    MAGIC_SHORTBOW(861),

    /**
     * Name: Shove
     *
     * <p>Energy Used: 25%
     *
     * <p>Description: Pushes the target back and stuns them for 3 seconds. This attack deals no damage.
     */
    DRAGON_SPEAR(1249, 1263, 3176, 5716, 5730);

    /**
     * A map of all weapon IDs to their special attack types.
     */
    public static final ImmutableMap<Integer, SpecialAttackType> IDS;

    static {
        Map<Integer, SpecialAttackType> ids = new HashMap<>();
        for (SpecialAttackType type : values()) {
            type.getIds().forEach(id -> ids.put(id, type));
        }
        IDS = ImmutableMap.copyOf(ids);
    }

    /**
     * All supported wieldable item IDs that map to this special attack type.
     */
    private final ImmutableSet<Integer> ids;

    /**
     * Creates a new {@link SpecialAttackType}.
     *
     * @param ids The wieldable item IDs that resolve to this special attack type.
     */
    SpecialAttackType(Integer... ids) {
        this.ids = ImmutableSet.copyOf(ids);
    }

    /**
     * @return The immutable set of item IDs.
     */
    public ImmutableSet<Integer> getIds() {
        return ids;
    }
}