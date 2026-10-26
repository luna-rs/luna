package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Crafting guide. The levels match the crafting enums.
 */
val craftingGuide = skillGuide("Crafting") {
    category("Leather") {
        row(1, 1059, "Leather gloves")
        row(7, 1061, "Leather boots")
        row(9, 1167, "Leather cowl")
        row(11, 1063, "Leather vambraces")
        row(14, 1129, "Leather body")
        row(18, 1095, "Leather chaps")
        row(28, 1131, "Hardleather body")
        row(38, 1169, "Coif")
        row(41, 1133, "Studded body")
        row(44, 1097, "Studded chaps")
        row(45, 6328, "Snakeskin boots")
        row(47, 6330, "Snakeskin v'brace")
        row(48, 6326, "Snakeskin bandana")
        row(51, 6324, "Snakeskin chaps")
        row(53, 6322, "Snakeskin body")
    }
    category("Dragonhide") {
        row(57, 1065, "Green d'hide vamb")
        row(60, 1099, "Green d'hide chaps")
        row(63, 1135, "Green d'hide body")
        row(66, 2487, "Blue d'hide vamb")
        row(68, 2493, "Blue d'hide chaps")
        row(71, 2499, "Blue d'hide body")
        row(73, 2489, "Red d'hide vamb")
        row(75, 2495, "Red d'hide chaps")
        row(77, 2501, "Red d'hide body")
        row(79, 2491, "Black d'hide vamb")
        row(82, 2497, "Black d'hide chaps")
        row(84, 2503, "Black d'hide body")
    }
    category("Spinning") {
        row(1, 1759, "Ball of wool")
        row(10, 1777, "Bow string")
        row(19, 6038, "Magic string")
    }
    category("Weaving") {
        row(10, 3224, "Cloth")
        row(21, 5418, "Empty sack")
        row(36, 5376, "Basket")
    }
    category("Pottery") {
        row(1, 1931, "Pot")
        row(7, 2313, "Pie dish")
        row(8, 1923, "Bowl")
        row(19, 5354, "Plant pot")
        row(25, 4440, "Pot lid")
    }
    category("Glass") {
        row(1, 1919, "Beer glass")
        row(4, 4527, "Candle lantern")
        row(12, 4522, "Oil lamp")
        row(33, 229, "Vial")
        row(42, 6667, "Fishbowl")
        row(46, 567, "Unpowered orb")
        row(49, 4542, "Lantern lens")
    }
    category("Gems") {
        row(1, 1609, "Opal")
        row(13, 1611, "Jade")
        row(16, 1613, "Red topaz")
        row(20, 1607, "Sapphire")
        row(27, 1605, "Emerald")
        row(34, 1603, "Ruby")
        row(43, 1601, "Diamond")
        row(55, 1615, "Dragonstone")
        row(67, 6573, "Onyx")
    }
    category("Jewellery") {
        row(5, 1635, "Gold ring")
        row(6, 1654, "Gold necklace")
        row(8, 1673, "Gold amulet")
        row(16, 1714, "Holy symbol")
        row(17, 1720, "Unholy symbol")
        row(18, 2961, "Silver sickle")
        row(20, 1637, "Sapphire ring")
        row(20, 4201, "Lightning rod")
        row(22, 1656, "Sapphire necklace")
        row(23, 5525, "Tiara")
        row(24, 1675, "Sapphire amulet")
        row(25, 7620, "Silvthrill rod")
        row(27, 1639, "Emerald ring")
        row(29, 1658, "Emerald necklace")
        row(30, 6748, "Demonic sigil")
        row(31, 1677, "Emerald amulet")
        row(34, 1641, "Ruby ring")
        row(40, 1660, "Ruby necklace")
        row(43, 1643, "Diamond ring")
        row(50, 1679, "Ruby amulet")
        row(55, 1645, "Dragonstone ring")
        row(56, 1662, "Diamond necklace")
        row(67, 6575, "Onyx ring")
        row(70, 1681, "Diamond amulet")
        row(72, 1664, "Dragon necklace")
        row(80, 1683, "Dragonstone amulet")
        row(90, 6579, "Onyx amulet")
    }
    category("Battlestaves") {
        row(54, 1395, "Water battlestaff")
        row(58, 1399, "Earth battlestaff")
        row(62, 1393, "Fire battlestaff")
        row(66, 1397, "Air battlestaff")
    }
}
