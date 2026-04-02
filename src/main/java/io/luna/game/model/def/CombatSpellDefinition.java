package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import game.player.Sound;
import game.skill.magic.SpellRequirement;
import io.luna.game.model.LocalProjectile;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Spellbook;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.block.Graphic;
import io.luna.game.model.mob.combat.CombatSpell;
import io.luna.game.model.mob.combat.CombatSpellType;

import java.util.List;
import java.util.function.BiFunction;

/**
 * A {@link Definition} containing the static data for a combat spell.
 * <p>
 * Combat spell definitions describe the immutable properties of a spell.
 *
 * @author lare96
 */
public final class CombatSpellDefinition implements Definition {

    /**
     * The repository containing all loaded {@link CombatSpellDefinition} instances, keyed by spell id.
     */
    public static final MapDefinitionRepository<CombatSpellDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * A map containing all loaded {@link CombatSpellDefinition} instances, keyed by {@link CombatSpell}.
     */
    public static volatile ImmutableMap<CombatSpell, CombatSpellDefinition> SPELLS = ImmutableMap.of();

    /**
     * A dummy {@link CombatSpellDefinition} representing no selected spell.
     */
    public static final CombatSpellDefinition NONE = new CombatSpellDefinition(-1, null, null, 0, 0, 0.0, null, null,
            null, null, null, ImmutableList.of(), null, null);

    /**
     * Loads all combat spell definitions and rebuilds the immutable spell lookup table.
     * <p>
     * The supplied definitions are first stored in {@link #ALL}, then indexed by their {@link CombatSpell} key into
     * {@link #SPELLS} for fast lookup at runtime.
     *
     * @param definitions The combat spell definitions to load.
     * @throws IllegalArgumentException If multiple definitions use the same {@link CombatSpell} key.
     */
    public static void loadAll(List<CombatSpellDefinition> definitions) {
        ALL.storeAndLock(definitions);

        ImmutableMap.Builder<CombatSpell, CombatSpellDefinition> spells = ImmutableMap.builder();
        for (CombatSpellDefinition def : definitions) {
            spells.put(def.spell, def);
        }
        SPELLS = spells.build();
    }

    /**
     * The id for this spell.
     */
    private final int id;

    /**
     * The combat spell constant.
     */
    private final CombatSpell spell;

    /**
     * The combat spell type classification.
     */
    private final CombatSpellType spellType;

    /**
     * The Magic level required to cast this spell.
     */
    private final int level;

    /**
     * The maximum base hit dealt by this spell.
     */
    private final int maxHit;

    /**
     * The Magic experience awarded for casting this spell.
     */
    private final double exp;

    /**
     * The spellbook this spell belongs to.
     */
    private final Spellbook spellbook;

    /**
     * The animation played when this spell is cast.
     */
    private final Animation castAnimation;

    /**
     * The graphic displayed when this spell begins casting.
     */
    private final Graphic startGraphic;

    /**
     * The function used to generate the projectile shown while this spell travels to the target.
     */
    private final BiFunction<Mob, Mob, LocalProjectile> projectile;

    /**
     * The graphic displayed when this spell reaches the target.
     */
    private final Graphic endGraphic;

    /**
     * The requirements needed to cast this spell.
     */
    private final ImmutableList<SpellRequirement> required;

    /**
     * The sound played when this spell begins casting.
     */
    private final Sound startSound;

    /**
     * The sound played when this spell reaches the target.
     */
    private final Sound endSound;

    /**
     * Creates a new {@link CombatSpellDefinition}.
     *
     * @param id The button id for this spell.
     * @param spell The combat spell constant.
     * @param spellType The combat spell type classification.
     * @param level The Magic level required to cast this spell.
     * @param maxHit The maximum base hit dealt by this spell.
     * @param exp The Magic experience awarded for casting this spell.
     * @param spellbook The spellbook this spell belongs to.
     * @param castAnimation The animation played when this spell is cast.
     * @param startGraphic The graphic displayed when this spell begins casting.
     * @param projectile The function used to generate the projectile shown while this spell travels to the target.
     * @param endGraphic The graphic displayed when this spell reaches the target.
     * @param required The requirements needed to cast this spell.
     * @param startSound The sound played when this spell begins casting.
     * @param endSound The sound played when this spell reaches the target.
     */
    public CombatSpellDefinition(int id, CombatSpell spell, CombatSpellType spellType, int level, int maxHit,
                                 double exp, Spellbook spellbook, Animation castAnimation, Graphic startGraphic,
                                 BiFunction<Mob, Mob, LocalProjectile> projectile, Graphic endGraphic,
                                 ImmutableList<SpellRequirement> required, Sound startSound, Sound endSound) {
        this.id = id;
        this.spell = spell;
        this.spellType = spellType;
        this.level = level;
        this.maxHit = maxHit;
        this.exp = exp;
        this.spellbook = spellbook;
        this.castAnimation = castAnimation;
        this.startGraphic = startGraphic;
        this.projectile = projectile;
        this.endGraphic = endGraphic;
        this.required = required;
        this.startSound = startSound;
        this.endSound = endSound;
    }

    @Override
    public int getId() {
        return id;
    }

    /**
     * @return The combat spell constant.
     */
    public CombatSpell getSpell() {
        return spell;
    }

    /**
     * @return The combat spell type.
     */
    public CombatSpellType getSpellType() {
        return spellType;
    }

    /**
     * @return The required Magic level.
     */
    public int getLevel() {
        return level;
    }

    /**
     * @return The maximum base hit.
     */
    public int getMaxHit() {
        return maxHit;
    }

    /**
     * @return The Magic experience reward.
     */
    public double getExp() {
        return exp;
    }

    /**
     * @return The spellbook.
     */
    public Spellbook getSpellbook() {
        return spellbook;
    }

    /**
     * @return The cast animation id.
     */
    public Animation getCastAnimation() {
        return castAnimation;
    }

    /**
     * @return The starting cast graphic, or {@code null} if none is defined.
     */
    public Graphic getStartGraphic() {
        return startGraphic;
    }

    /**
     * @return The function used to generate the projectile shown while this spell travels to the target.
     */
    public BiFunction<Mob, Mob, LocalProjectile> getProjectile() {
        return projectile;
    }

    /**
     * @return The ending impact graphic, or {@code null} if none is defined.
     */
    public Graphic getEndGraphic() {
        return endGraphic;
    }

    /**
     * @return The immutable requirement list.
     */
    public ImmutableList<SpellRequirement> getRequired() {
        return required;
    }

    /**
     * @return The starting sound, or {@code null} if none is defined.
     */
    public Sound getStartSound() {
        return startSound;
    }

    /**
     * @return The ending sound, or {@code null} if none is defined.
     */
    public Sound getEndSound() {
        return endSound;
    }
}