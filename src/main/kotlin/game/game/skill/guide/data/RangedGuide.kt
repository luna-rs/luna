package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Ranged guide.
 */
val rangedGuide = skillGuide("Ranged") {
    category("Bows") {
        row(1, 841, "Shortbow and longbow")
        row(1, 837, "Crossbow")
        row(1, 882, "Bronze arrows")
        row(1, 884, "Iron arrows")
        row(5, 843, "Oak bows")
        row(5, 886, "Steel arrows")
        row(20, 849, "Willow bows")
        row(20, 888, "Mithril arrows")
        row(30, 853, "Maple bows")
        row(30, 2883, "Ogre bow")
        row(30, 890, "Adamant arrows")
        row(40, 857, "Yew bows")
        row(40, 892, "Rune arrows")
        row(50, 861, "Magic bows")
    }
    category("Thrown") {
        row(1, 806, "Bronze darts")
        row(1, 864, "Bronze knives")
        row(1, 800, "Bronze throwing axes")
        row(1, 807, "Iron darts")
        row(1, 863, "Iron knives")
        row(1, 801, "Iron throwing axes")
        row(5, 808, "Steel darts")
        row(5, 865, "Steel knives")
        row(5, 802, "Steel throwing axes")
        row(10, 3093, "Black darts")
        row(10, 869, "Black knives")
        row(20, 809, "Mithril darts")
        row(20, 866, "Mithril knives")
        row(20, 803, "Mithril throwing axes")
        row(30, 810, "Adamant darts")
        row(30, 867, "Adamant knives")
        row(30, 804, "Adamant throwing axes")
        row(40, 811, "Rune darts")
        row(40, 868, "Rune knives")
        row(40, 805, "Rune throwing axes")
        row(60, 6522, "Obsidian throwing rings")
    }
    category("Armour") {
        row(1, 1129, "Leather armour")
        row(1, 1169, "Coif")
        row(20, 1133, "Studded leather, with 20 Defence")
        row(30, 6322, "Snakeskin armour")
        row(40, 1135, "Green dragonhide")
        row(50, 2499, "Blue dragonhide")
        row(60, 2501, "Red dragonhide")
        row(70, 2503, "Black dragonhide")
        row(70, 4736, "Karil's armour")
    }
}
