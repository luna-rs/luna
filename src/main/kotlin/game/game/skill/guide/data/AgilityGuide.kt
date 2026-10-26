package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Agility guide. The levels of the courses Luna has match `AgilityCourse`.
 */
val agilityGuide = skillGuide("Agility") {
    category("Courses") {
        row(1, null, "Gnome Stronghold course")
        row(1, 2996, "Brimhaven Agility Arena")
        row(30, null, "Agility Pyramid")
        row(35, null, "Barbarian Outpost course")
        row(48, null, "Ape Atoll course")
        row(52, null, "Wilderness course")
        row(60, null, "Werewolf course")
    }
}
