package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Defence guide.
 */
val defenceGuide = skillGuide("Defence") {
    category("Melee") {
        row(1, 1155, "Bronze armour")
        row(1, 1153, "Iron armour")
        row(5, 1157, "Steel armour")
        row(10, 1165, "Black armour")
        row(10, 6623, "White armour")
        row(20, 1159, "Mithril armour")
        row(30, 1161, "Adamant armour")
        row(40, 1163, "Rune armour")
        row(40, 1127, "Rune platebody, after Dragon Slayer")
        row(45, 3751, "Fremennik helms")
        row(50, 6809, "Granite armour")
        row(60, 1149, "Dragon armour")
        row(60, 6524, "Obsidian shield")
        row(70, 4720, "Barrows armour")
    }
    category("Ranged") {
        row(1, 1129, "Leather armour")
        row(10, 1131, "Hardleather body")
        row(20, 1133, "Studded leather, with 20 Ranged")
        row(30, 6322, "Snakeskin armour, with 30 Ranged")
        row(40, 1135, "Dragonhide bodies, with Ranged")
        row(70, 4736, "Karil's armour, with 70 Ranged")
    }
    category("Magic") {
        row(20, 4091, "Mystic robes, with 40 Magic")
        row(40, 3387, "Splitbark armour, with 40 Magic")
        row(70, 4712, "Ahrim's armour, with 70 Magic")
    }
}
