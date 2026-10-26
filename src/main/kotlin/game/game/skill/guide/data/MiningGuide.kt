package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Mining guide. The levels match `Ore` and `Pickaxe`.
 */
val miningGuide = skillGuide("Mining") {
    category("Ores") {
        row(1, 1436, "Rune essence")
        row(1, 434, "Clay")
        row(1, 438, "Tin ore")
        row(1, 436, "Copper ore")
        row(10, 668, "Blurite ore")
        row(10, 3211, "Limestone")
        row(15, 440, "Iron ore")
        row(20, 442, "Silver ore")
        row(30, 7936, "Pure essence")
        row(30, 453, "Coal")
        row(35, 6971, "Sandstone")
        row(40, 444, "Gold ore")
        row(40, 1623, "Gem rocks")
        row(45, 6979, "Granite")
        row(55, 447, "Mithril ore")
        row(70, 449, "Adamantite ore")
        row(85, 451, "Runite ore")
    }
    category("Pickaxes") {
        row(1, 1265, "Bronze pickaxe")
        row(1, 1267, "Iron pickaxe")
        row(6, 1269, "Steel pickaxe")
        row(21, 1273, "Mithril pickaxe")
        row(31, 1271, "Adamant pickaxe")
        row(41, 1275, "Rune pickaxe")
    }
}
