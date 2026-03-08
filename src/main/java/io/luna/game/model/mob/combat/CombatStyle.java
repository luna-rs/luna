package io.luna.game.model.mob.combat;

/**
 * The selectable combat styles available across all supported weapon interfaces.
 * <p>
 * Each combat style represents a specific attack option exposed on a weapon combat tab and is used to determine
 * attack behavior, experience distribution, stance, and client-side combat settings.
 *
 * @author lare96
 */
public enum CombatStyle {

    /**
     * Staff bash attack style.
     */
    STAFF_BASH,

    /**
     * Staff pound attack style.
     */
    STAFF_POUND,

    /**
     * Staff focus attack style.
     */
    STAFF_FOCUS,

    /**
     * Staff autocast attack style.
     */
    STAFF_AUTOCAST,

    /**
     * Warhammer pound attack style.
     */
    WARHAMMER_POUND,

    /**
     * Warhammer pummel attack style.
     */
    WARHAMMER_PUMMEL,

    /**
     * Warhammer block attack style.
     */
    WARHAMMER_BLOCK,

    /**
     * Scythe reap attack style.
     */
    SCYTHE_REAP,

    /**
     * Scythe chop attack style.
     */
    SCYTHE_CHOP,

    /**
     * Scythe jab attack style.
     */
    SCYTHE_JAB,

    /**
     * Scythe block attack style.
     */
    SCYTHE_BLOCK,

    /**
     * Battleaxe chop attack style.
     */
    BATTLEAXE_CHOP,

    /**
     * Battleaxe hack attack style.
     */
    BATTLEAXE_HACK,

    /**
     * Battleaxe smash attack style.
     */
    BATTLEAXE_SMASH,

    /**
     * Battleaxe block attack style.
     */
    BATTLEAXE_BLOCK,

    /**
     * Crossbow accurate attack style.
     */
    CROSSBOW_ACCURATE,

    /**
     * Crossbow rapid attack style.
     */
    CROSSBOW_RAPID,

    /**
     * Crossbow longrange attack style.
     */
    CROSSBOW_LONGRANGE,

    /**
     * Shortbow accurate attack style.
     */
    SHORTBOW_ACCURATE,

    /**
     * Shortbow rapid attack style.
     */
    SHORTBOW_RAPID,

    /**
     * Shortbow longrange attack style.
     */
    SHORTBOW_LONGRANGE,

    /**
     * Longbow accurate attack style.
     */
    LONGBOW_ACCURATE,

    /**
     * Longbow rapid attack style.
     */
    LONGBOW_RAPID,

    /**
     * Longbow longrange attack style.
     */
    LONGBOW_LONGRANGE,

    /**
     * Dagger stab attack style.
     */
    DAGGER_STAB,

    /**
     * Dagger lunge attack style.
     */
    DAGGER_LUNGE,

    /**
     * Dagger slash attack style.
     */
    DAGGER_SLASH,

    /**
     * Dagger block attack style.
     */
    DAGGER_BLOCK,

    /**
     * Sword stab attack style.
     */
    SWORD_STAB,

    /**
     * Sword lunge attack style.
     */
    SWORD_LUNGE,

    /**
     * Sword slash attack style.
     */
    SWORD_SLASH,

    /**
     * Sword block attack style.
     */
    SWORD_BLOCK,

    /**
     * Scimitar chop attack style.
     */
    SCIMITAR_CHOP,

    /**
     * Scimitar slash attack style.
     */
    SCIMITAR_SLASH,

    /**
     * Scimitar lunge attack style.
     */
    SCIMITAR_LUNGE,

    /**
     * Scimitar block attack style.
     */
    SCIMITAR_BLOCK,

    /**
     * Sickle chop attack style.
     */
    SICKLE_CHOP,

    /**
     * Sickle slash attack style.
     */
    SICKLE_SLASH,

    /**
     * Sickle lunge attack style.
     */
    SICKLE_LUNGE,

    /**
     * Sickle block attack style.
     */
    SICKLE_BLOCK,

    /**
     * Longsword chop attack style.
     */
    LONGSWORD_CHOP,

    /**
     * Longsword slash attack style.
     */
    LONGSWORD_SLASH,

    /**
     * Longsword lunge attack style.
     */
    LONGSWORD_LUNGE,

    /**
     * Longsword block attack style.
     */
    LONGSWORD_BLOCK,

    /**
     * Mace pound attack style.
     */
    MACE_POUND,

    /**
     * Mace pummel attack style.
     */
    MACE_PUMMEL,

    /**
     * Mace spike attack style.
     */
    MACE_SPIKE,

    /**
     * Mace block attack style.
     */
    MACE_BLOCK,

    /**
     * Knife accurate attack style.
     */
    KNIFE_ACCURATE,

    /**
     * Knife rapid attack style.
     */
    KNIFE_RAPID,

    /**
     * Knife longrange attack style.
     */
    KNIFE_LONGRANGE,

    /**
     * Spear lunge attack style.
     */
    SPEAR_LUNGE,

    /**
     * Spear swipe attack style.
     */
    SPEAR_SWIPE,

    /**
     * Spear pound attack style.
     */
    SPEAR_POUND,

    /**
     * Spear block attack style.
     */
    SPEAR_BLOCK,

    /**
     * Two-handed sword chop attack style.
     */
    TWO_HANDED_SWORD_CHOP,

    /**
     * Two-handed sword slash attack style.
     */
    TWO_HANDED_SWORD_SLASH,

    /**
     * Two-handed sword smash attack style.
     */
    TWO_HANDED_SWORD_SMASH,

    /**
     * Two-handed sword block attack style.
     */
    TWO_HANDED_SWORD_BLOCK,

    /**
     * Pickaxe spike attack style.
     */
    PICKAXE_SPIKE,

    /**
     * Pickaxe impale attack style.
     */
    PICKAXE_IMPALE,

    /**
     * Pickaxe smash attack style.
     */
    PICKAXE_SMASH,

    /**
     * Pickaxe block attack style.
     */
    PICKAXE_BLOCK,

    /**
     * Claws chop attack style.
     */
    CLAWS_CHOP,

    /**
     * Claws slash attack style.
     */
    CLAWS_SLASH,

    /**
     * Claws lunge attack style.
     */
    CLAWS_LUNGE,

    /**
     * Claws block attack style.
     */
    CLAWS_BLOCK,

    /**
     * Halberd jab attack style.
     */
    HALBERD_JAB,

    /**
     * Halberd swipe attack style.
     */
    HALBERD_SWIPE,

    /**
     * Halberd fend attack style.
     */
    HALBERD_FEND,

    /**
     * Unarmed punch attack style.
     */
    UNARMED_PUNCH,

    /**
     * Unarmed kick attack style.
     */
    UNARMED_KICK,

    /**
     * Unarmed block attack style.
     */
    UNARMED_BLOCK,

    /**
     * Whip flick attack style.
     */
    WHIP_FLICK,

    /**
     * Whip lash attack style.
     */
    WHIP_LASH,

    /**
     * Whip deflect attack style.
     */
    WHIP_DEFLECT,

    /**
     * Thrown axe accurate attack style.
     */
    THROWN_AXE_ACCURATE,

    /**
     * Thrown axe rapid attack style.
     */
    THROWN_AXE_RAPID,

    /**
     * Thrown axe longrange attack style.
     */
    THROWN_AXE_LONGRANGE,

    /**
     * Dart accurate attack style.
     */
    DART_ACCURATE,

    /**
     * Dart rapid attack style.
     */
    DART_RAPID,

    /**
     * Dart longrange attack style.
     */
    DART_LONGRANGE,

    /**
     * Javelin accurate attack style.
     */
    JAVELIN_ACCURATE,

    /**
     * Javelin rapid attack style.
     */
    JAVELIN_RAPID,

    /**
     * Javelin longrange attack style.
     */
    JAVELIN_LONGRANGE
}