package io.luna.game.model.def;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import game.player.Sound;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.combat.CombatStyle;
import io.luna.game.model.mob.combat.damage.CombatDamageType;

import java.util.List;
import java.util.Map;

/**
 * Defines verified ordinary attack sounds shared by a group of weapon item IDs.
 * <p>
 * Sounds are selected by the offensive bonus of the selected style. Missing weapons and damage styles remain
 * silent; they never inherit another weapon family's sound. Special attacks supply their own launch sound.
 * Definitions are published together after parsing so readers cannot observe a partial reload.
 *
 * @author lare96
 */
public final class WeaponSoundDefinition {

    /**
     * The immutable weapon sound lookup, indexed by item ID.
     */
    public static volatile ImmutableMap<Integer, WeaponSoundDefinition> ALL = ImmutableMap.of();

    /**
     * The item IDs sharing these sounds.
     */
    private final ImmutableSet<Integer> ids;

    /**
     * The verified sounds indexed by stab, slash, crush, or ranged offensive bonus.
     */
    private final ImmutableMap<EquipmentBonus, Sound> sounds;

    /**
     * Creates an ordinary weapon sound definition.
     *
     * @param ids The weapon item IDs sharing the mapping.
     * @param sounds The offensive bonus to sound mappings; omitted bonuses remain silent.
     */
    public WeaponSoundDefinition(ImmutableSet<Integer> ids, Map<EquipmentBonus, Sound> sounds) {
        this.ids = ids;
        this.sounds = ImmutableMap.copyOf(sounds);
    }

    /**
     * Publishes a complete lookup. Duplicate IDs reject the load instead of silently replacing a definition.
     *
     * @param definitions The parsed sound definitions.
     * @throws IllegalArgumentException If more than one definition contains the same item ID.
     */
    public static void load(List<WeaponSoundDefinition> definitions) {
        ImmutableMap.Builder<Integer, WeaponSoundDefinition> builder = ImmutableMap.builder();
        for (WeaponSoundDefinition definition : definitions) {
            for (int id : definition.ids) {
                builder.put(id, definition);
            }
        }
        ALL = builder.buildOrThrow();
    }

    /**
     * Resolves the ordinary launch sound before equipment can be changed by ammunition consumption.
     * Unarmed punch and block share a sound; kick has its own. An equipped but unverified weapon remains silent.
     *
     * @param weaponId The equipped weapon item ID, or a negative value for an empty slot.
     * @param style The selected combat style definition.
     * @param type The attack's damage category; magic is handled by spell audio instead.
     * @return The verified sound, or {@code null} if no mapping applies.
     */
    public static Sound resolve(int weaponId, CombatStyleDefinition style, CombatDamageType type) {
        if (type == CombatDamageType.MAGIC) {
            return null;
        }
        if (weaponId < 0 && type == CombatDamageType.MELEE) {
            CombatStyle selected = style.getType();
            return switch (selected) {
                case UNARMED_PUNCH, UNARMED_BLOCK -> Sound.UNARMED_PUNCH;
                case UNARMED_KICK -> Sound.UNARMED_KICK;
                default -> null;
            };
        }
        WeaponSoundDefinition definition = ALL.get(weaponId);
        EquipmentBonus bonus = type == CombatDamageType.RANGED ? EquipmentBonus.RANGED_ATTACK : style.getBonus();
        return definition == null ? null : definition.sounds.get(bonus);
    }

    /**
     * @return The item IDs sharing this definition.
     */
    public ImmutableSet<Integer> getIds() {
        return ids;
    }

    /**
     * @return The immutable offensive bonus to sound mappings.
     */
    public ImmutableMap<EquipmentBonus, Sound> getSounds() {
        return sounds;
    }
}
