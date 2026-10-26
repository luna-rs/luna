package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Farming guide. The allotment levels match `SeedType`.
 */
val farmingGuide = skillGuide("Farming") {
    category("Allotments") {
        row(1, 1942, "Potato")
        row(5, 1957, "Onion")
        row(7, 1965, "Cabbage")
        row(12, 1982, "Tomato")
        row(20, 5986, "Sweetcorn")
        row(31, 5504, "Strawberry")
        row(47, 5982, "Watermelon")
    }
    category("Hops") {
        row(3, 6006, "Barley")
        row(4, 5994, "Hammerstone hops")
        row(8, 5996, "Asgarnian hops")
        row(13, 5931, "Jute fibre")
        row(16, 5998, "Yanillian hops")
        row(21, 6000, "Krandorian hops")
        row(28, 6002, "Wildblood hops")
    }
    category("Trees") {
        row(15, 5370, "Oak tree")
        row(30, 5371, "Willow tree")
        row(45, 5372, "Maple tree")
        row(60, 5373, "Yew tree")
        row(75, 5374, "Magic tree")
    }
    category("Fruit trees") {
        row(27, 1955, "Apple tree")
        row(33, 1963, "Banana tree")
        row(39, 2108, "Orange tree")
        row(42, 5970, "Curry tree")
        row(51, 2114, "Pineapple plant")
        row(57, 5972, "Papaya tree")
        row(68, 5974, "Palm tree")
    }
    category("Herbs") {
        row(9, 249, "Guam leaf")
        row(14, 251, "Marrentill")
        row(19, 253, "Tarromin")
        row(26, 255, "Harralander")
        row(32, 257, "Ranarr weed")
        row(38, 2998, "Toadflax")
        row(44, 259, "Irit leaf")
        row(50, 261, "Avantoe")
        row(56, 263, "Kwuarm")
        row(62, 3000, "Snapdragon")
        row(67, 265, "Cadantine")
        row(73, 2481, "Lantadyme")
        row(79, 267, "Dwarf weed")
        row(85, 269, "Torstol")
    }
    category("Flowers") {
        row(2, 6010, "Marigolds")
        row(11, 6014, "Rosemary")
        row(24, 6012, "Nasturtiums")
        row(25, 1793, "Woad")
        row(26, 225, "Limpwurt")
    }
    category("Bushes") {
        row(10, 1951, "Redberry bush")
        row(22, 753, "Cadavaberry bush")
        row(36, 2126, "Dwellberry bush")
        row(48, 247, "Jangerberry bush")
        row(59, 239, "Whiteberry bush")
        row(70, 6018, "Poison ivy bush")
    }
    category("Special") {
        row(23, 6059, "Scarecrow")
        row(53, 6004, "Bittercap mushrooms")
        row(55, 6016, "Cactus")
        row(63, 2398, "Belladonna")
        row(72, 5980, "Calquat tree")
        row(83, 5317, "Spirit tree")
    }
}
