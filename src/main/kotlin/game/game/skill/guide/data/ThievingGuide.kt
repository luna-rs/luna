package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Thieving guide. The levels match the thieving enums.
 */
val thievingGuide = skillGuide("Thieving") {
    category("Pickpocket") {
        row(1, null, "Men and women")
        row(10, null, "Farmers")
        row(25, null, "Warrior women")
        row(32, null, "Rogues")
        row(38, null, "Master Farmers")
        row(40, null, "Guards")
        row(55, null, "Knights of Ardougne")
        row(65, null, "Watchmen")
        row(70, null, "Paladins")
        row(75, null, "Gnomes")
        row(80, null, "Heroes")
    }
    category("Stalls") {
        row(2, 1965, "Vegetable stall")
        row(5, 1891, "Baker's stall")
        row(5, 712, "Tea stall")
        row(5, 1755, "Crafting stall")
        row(20, 950, "Silk stall")
        row(22, 1993, "Wine stall")
        row(27, null, "Seed stall")
        row(35, 958, "Fur stall")
        row(42, 331, "Fish stall")
        row(50, 442, "Silver stall")
        row(65, 2007, "Spice stall")
        row(65, null, "Magic stall")
        row(65, 1323, "Scimitar stall")
        row(75, 1623, "Gem stall")
    }
    category("Chests") {
        row(13, null, "Chest (10 coins)")
        row(28, null, "Chest (nature rune)")
        row(43, null, "Chest (50 coins)")
        row(47, null, "Chest (steel arrowtips)")
        row(59, null, "Chest (blood runes)")
        row(72, null, "Ardougne castle chest")
    }
}
