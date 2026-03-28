package io.luna.game.model.mob.combat;

enum SpecialAttackType {

    /**
     *Name	Lumber Up
     * Energy used	100%
     * Description	Temporarily increases the user's Woodcutting level by 3.
     * Tooltip	Increase your Woodcutting level by 3.
     */
    DRAGON_AXE,

    /**
     Name	Sanctuary
     Energy used	100%
     Description	Increases the user's Defence by 8 levels.
     Tooltip	Temporarily increase your Defence level by 8.
     Attack roll modifier	None
     Damage roll modifier	None
     Attack Level	20
     */
    EXCALIBUR,

    /**
     Name	Rampage
     Energy used	100%
     Description	Lowers the user's Attack, Defence, Ranged, and Magic levels by 10%.
     Increases the user's Strength by 10 + (levels drained / 4).

     Tooltip	Drain your Attack, Defence, Ranged & Magic levels by 10%, while increasing your Strength by 10 levels, plus 25% of the drained levels.
     Attack roll modifier	None
     Damage roll modifier	None
     Attack Level	60     */
    DRAGON_BATTLE_AXE,

    /**
     Name	Sweep
     Energy used	30%
     Description	Hits up to 10 NPC's in front of the user at once.
     For monsters that occupy two or more tiles, the halberd will hit twice.

     Tooltip	If your target is small, adjacent targets may be hit too. Otherwise, your target may be hit a second time, with 25% decreased accuracy. Damage in all cases is increased by 10% of your max hit.
     Defence roll	Slash
     Attack roll modifier	None; separate roll for each hit.
     Damage roll modifier	10%
     Attack Level	60	70
     Strength Level	30	35
     Agility Level	56
     */
    DRAGON_HALBERD,

    /**
     Name	Powerstab
     Energy used	60%
     Description	Hits up to 14 enemies within one square of the user.
     Tooltip	Hit up to fourteen enemies surrounding you.
     Defence roll	Slash
     Attack roll modifier	None
     Damage roll modifier	None
     Attack Level	60
     */
    DRAGON_2H_SWORD,

    /**
     *Name	Chainhit
     * Energy used	10% for each hit; up to 50%
     * Description	Bounces off target to hit up to five other opponents in a 3 tile radius.
     * Tooltip	Throw an axe which hits your target and then chains to hit further nearby targets, costing 10% additional special attack per extra target.
     * Attack roll modifier	None, but an accuracy roll is made after each target hit; a failure ends the sequence.
     * Damage roll modifier	The user's prayers are not taken into account for the damage roll.
     * Ranged Level	40
     */
    RUNE_THROWN_AXE,

    /**
     * Name	Energy Drain
     * Energy used	50%
     * Description	Transfers 10% of the target's run energy to the user in PvP.
     * Tooltip	Deal an attack with 25% increased accuracy that siphons 10% of your target's run energy.
     * Defence roll	Slash
     * Attack roll modifier	25%
     * Damage roll modifier	None
     * Attack Level	70
     */
    ABYSSAL_WHIP,

    /**
     * Name	Weaken
     * Energy used	50%
     * Description	Drains the target's Attack, Strength, and Defence by 5% of their level + 1.
     * Twice as effective on demons (reduces each stat by 10%).
     *
     * Special is only activated on successful hits.
     *
     * Tooltip	Temporarily drain your target's Attack, Strength and Defence by 5%. Is twice as effective against demons.
     * Defence roll	Stab
     * Attack roll modifier	None
     * Damage roll modifier	None
     * Attack Level	1
     */
    DARKLIGHT,

    /**
     Name	Sever
     Energy used	55%
     Description	In PvP, disables the target's active protection prayers and prevents the target from using protection prayers for 8 ticks (4.8 seconds).
     Only activates if the user lands a successful hit.

     Tooltip	Deal a slash with increased accuracy that prevents your target from using protection prayers for 5 seconds if it successfully hits.
     Defence roll	Slash
     Attack roll modifier	25%
     Damage roll modifier	None
     Attack Level	60
     */
    DRAGON_SCIMITAR,

    /*
    Name	Cleave
Energy used	25%
Description	Deals 25% more damage.
Tooltip	Deal a powerful attack that inflicts 25% more damage.
Defence roll	Slash
Attack roll modifier	None
Damage roll modifier	25%
Attack Level	60
Quest points Quest	Lost City
     */
    DRAGON_LONGSWORD,

    /*
    Name	Shatter
Energy used	25%
Description	Increases damage and accuracy for one hit.
Tooltip	Increase damage by 50% and accuracy by 25% for one hit.
Defence roll	Crush
Attack roll modifier	25%
Damage roll modifier	50%
Attack Level	60
     */
    DRAGON_MACE,

    /*
    Name	Powershot
Energy used	35%
Description	Launches a shot that is guaranteed to hit your target.
Tooltip	Deal an attack that is guaranteed to hit your target.
Attack roll modifier	True; guaranteed hit
Damage roll modifier	The effects of the Void set and prayers are ignored when rolling.
Ranged Level	50
     */
    MAGIC_LONGBOW,

    /**
     * Name	Impale
     * Energy used	25%
     * Description	Attack with 10% more Attack and Strength, but with a slower speed.
     * Tooltip	Deal an attack with 10% increased Attack and Strength, but with a slower speed.
     * Attack roll modifier	None
     * Damage roll modifier	None
     * Attack Level	40
     */
    RUNE_CLAWS,

    /**
     * Name	Puncture
     * Energy used	25%
     * Description	Performs two quick slashes.
     * Tooltip	Deal two quick slashes with 15% increased accuracy and 15% increased damage.
     * Defence roll	Slash
     * Attack roll modifier	15%
     * Damage roll modifier	15%
     * Attack Level	60
     */
    DRAGON_DAGGER,

    /**
     * Information	Granite maul	Granite maul (ornate handle)
     * 	Name	Quick Smash
     * Energy used	60%	50%
     * Description	Instantly performs a second attack.
     * Tooltip	Deal an extra attack instantly.
     * Attack roll modifier	None
     * Damage roll modifier	None
     * Attack Level	50
     * Strength Level	50
     */
    GRANITE_MAUL,

    /**
     * Name	Snapshot
     * Energy used	55%	50%
     * Description	Fires two arrows very rapidly.
     * Tooltip	Fire two arrows within quick succession, but with reduced accuracy.
     * Attack roll modifier	43%; Tooltip falsely says it reduces accuracy.
     * Damage roll modifier	Ignores void set and prayer effects.
     * Ranged Level	50
     */
    MAGIC_SHORTBOW,

    /*
    Name	Shove
Energy used	25%
Description	Pushes the target back and stuns them for 3 seconds.
Tooltip	Push your target back and stun them for 3 seconds. This attack deals no damage.
Attack roll modifier	Guaranteed false; the stun always applies, but never deals damage.
Damage roll modifier	None
Attack Level	60
     */
    DRAGON_SPEAR,


}