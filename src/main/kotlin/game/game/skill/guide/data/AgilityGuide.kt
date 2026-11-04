package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Agility guide. The levels of the courses Luna has match `AgilityCourse`; the shortcuts and areas are the
 * special areas the 2006 guide listed, in Jagex's words, at the levels Luna checks.
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
    category("Shortcuts") {
        row(5, null, "Falador Agility Shortcut")
        row(16, null, "Yanille Agility Shortcut")
        row(20, null, "Coal Truck log balance")
        row(21, null, "Varrock Agility Shortcut")
        row(26, null, "Falador Agility Shortcut")
        row(29, null, "Lumbridge fence squeeze to swamp")
        row(31, null, "Draynor Manor stones to Champions' Guild")
        row(33, null, "Ardougne log balance shortcut")
        row(37, null, "Gnome Stronghold shortcut")
        row(38, null, "Al Kharid mining pit cliff side scramble")
        row(41, null, "Trollheim - easy cliffside scramble")
        row(42, null, "Dwarven mine - narrow crevice")
        row(43, null, "Trollheim - medium cliffside scramble")
        row(44, null, "Trollheim - advanced cliffside scramble")
        row(46, null, "Cosmic Temple - medium narrow walkway")
        row(47, null, "Trollheim - hard cliffside scramble")
        row(48, null, "Log balance to Fremennik Province")
        row(51, null, "Pipe from Edgeville dungeon to Varrock sewers")
        row(58, null, "Port Phasmatys - Ectopool shortcut")
        row(59, null, "Elven Overpass - easy cliffside scramble")
        row(61, null, "Slayer tower - medium spiked chain climb")
        row(62, null, "Slayer Dungeon - narrow crevice")
        row(64, null, "Trollheim Wilderness Route")
        row(65, null, "Temple on the Salve to Morytania shortcut")
        row(66, null, "Cosmic Temple - advanced narrow walkway")
        row(68, null, "Elven Overpass - medium cliffside scramble")
        row(70, null, "Taverley Dungeon shortcuts to blue dragons")
        row(71, null, "Slayer tower - advanced spiked chain climb")
        row(80, null, "Taverly Dungeon spiked blades jump")
        row(81, null, "Slayer Dungeon spiked blades jump")
        row(85, null, "Elven Overpass - advanced cliffside scramble")
    }
    category("Areas") {
        row(10, null, "Rope swing to Moss Giant Island")
        row(12, null, "Stepping stones in Karamja Dungeon")
        row(15, null, "Monkey bars under Edgeville")
        row(18, null, "Watchtower wall climb")
        row(22, null, "Pipe contortion in Karamja Dungeon")
        row(30, null, "Stepping stones in south eastern Karamja")
        row(34, null, "Pipe contortion in Karamja Dungeon")
        row(45, null, "Elf area log balance")
        row(49, null, "Contortion in Yanille Dungeon small room")
        row(67, null, "Yanille Dungeon's rubble climb")
    }
}
