package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Fletching guide. The levels match `Bow`, `Arrow`, `Dart`, `BoltTip` and `Bolt`.
 */
val fletchingGuide = skillGuide("Fletching") {
    category("Bows") {
        row(5, 841, "Shortbow")
        row(10, 839, "Longbow")
        row(20, 843, "Oak shortbow")
        row(25, 845, "Oak longbow")
        row(35, 849, "Willow shortbow")
        row(40, 847, "Willow longbow")
        row(50, 853, "Maple shortbow")
        row(55, 851, "Maple longbow")
        row(65, 857, "Yew shortbow")
        row(70, 855, "Yew longbow")
        row(80, 861, "Magic shortbow")
        row(85, 859, "Magic longbow")
    }
    category("Arrows") {
        row(1, 52, "15 arrow shafts per log")
        row(1, 53, "Headless arrows")
        row(1, 882, "Bronze arrows")
        row(15, 884, "Iron arrows")
        row(30, 886, "Steel arrows")
        row(45, 888, "Mithril arrows")
        row(60, 890, "Adamant arrows")
        row(75, 892, "Rune arrows")
    }
    category("Darts") {
        row(1, 806, "Bronze darts")
        row(22, 807, "Iron darts")
        row(37, 808, "Steel darts")
        row(52, 809, "Mithril darts")
        row(67, 810, "Adamant darts")
        row(81, 811, "Rune darts")
    }
    category("Bolts") {
        row(17, 45, "Opal bolttips")
        row(17, 879, "Opal bolts")
        row(34, 46, "Pearl bolttips")
        row(34, 880, "Pearl bolts")
        row(51, 881, "Barbed bolts")
    }
}
