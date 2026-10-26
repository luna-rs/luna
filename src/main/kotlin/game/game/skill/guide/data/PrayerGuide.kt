package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Prayer guide. The levels match `CombatPrayer`.
 */
val prayerGuide = skillGuide("Prayer") {
    category("Prayers") {
        row(1, null, "Thick Skin")
        row(4, null, "Burst of Strength")
        row(7, null, "Clarity of Thought")
        row(10, null, "Rock Skin")
        row(13, null, "Superhuman Strength")
        row(16, null, "Improved Reflexes")
        row(19, null, "Rapid Restore")
        row(22, null, "Rapid Heal")
        row(25, null, "Protect Item")
        row(28, null, "Steel Skin")
        row(31, null, "Ultimate Strength")
        row(34, null, "Incredible Reflexes")
        row(37, null, "Protect from Magic")
        row(40, null, "Protect from Missiles")
        row(43, null, "Protect from Melee")
        row(46, null, "Retribution")
        row(49, null, "Redemption")
        row(52, null, "Smite")
    }
}
