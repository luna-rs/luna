package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Firemaking guide. The levels match `Log`.
 */
val firemakingGuide = skillGuide("Firemaking") {
    category("Logs") {
        row(1, 1511, "Logs")
        row(15, 1521, "Oak logs")
        row(30, 1519, "Willow logs")
        row(35, 6333, "Teak logs")
        row(45, 1517, "Maple logs")
        row(50, 6332, "Mahogany logs")
        row(60, 1515, "Yew logs")
        row(75, 1513, "Magic logs")
    }
}
