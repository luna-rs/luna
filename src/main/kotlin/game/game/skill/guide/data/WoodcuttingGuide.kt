package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Woodcutting guide. The levels match `Tree` and `Axe`.
 */
val woodcuttingGuide = skillGuide("Woodcutting") {
    category("Trees") {
        row(1, 1511, "Normal and dead trees")
        row(1, 2862, "Achey trees")
        row(15, 1521, "Oak trees")
        row(30, 1519, "Willow trees")
        row(35, 6333, "Teak trees")
        row(45, 1517, "Maple trees")
        row(45, 3239, "Hollow trees")
        row(50, 6332, "Mahogany trees")
        row(60, 1515, "Yew trees")
        row(75, 1513, "Magic trees")
    }
    category("Axes") {
        row(1, 1351, "Bronze axe")
        row(1, 1349, "Iron axe")
        row(6, 1353, "Steel axe")
        row(6, 1361, "Black axe")
        row(21, 1355, "Mithril axe")
        row(31, 1357, "Adamant axe")
        row(41, 1359, "Rune axe")
        row(41, 6739, "Dragon axe")
    }
}
