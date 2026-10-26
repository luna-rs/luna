package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Cooking guide. The levels match `Food` and `IncompleteFood` where they exist.
 */
val cookingGuide = skillGuide("Cooking") {
    category("Meat & fish") {
        row(1, 2142, "Cooked meat")
        row(1, 2140, "Cooked chicken")
        row(1, 315, "Shrimps")
        row(1, 319, "Anchovies")
        row(1, 325, "Sardine")
        row(10, 347, "Herring")
        row(10, 355, "Mackerel")
        row(15, 333, "Trout")
        row(18, 339, "Cod")
        row(20, 351, "Pike")
        row(25, 329, "Salmon")
        row(30, 361, "Tuna")
        row(38, 379, "Lobster")
        row(43, 365, "Bass")
        row(45, 373, "Swordfish")
        row(62, 7946, "Monkfish")
        row(80, 385, "Shark")
        row(91, 391, "Manta ray")
    }
    category("Bread") {
        row(1, 2309, "Bread")
        row(58, 1865, "Pitta bread")
    }
    category("Pies") {
        row(10, 2325, "Redberry pie")
        row(20, 2327, "Meat pie")
        row(29, 7170, "Mud pie")
        row(30, 2323, "Apple pie")
        row(34, 7178, "Garden pie")
        row(47, 7188, "Fish pie")
        row(70, 7198, "Admiral pie")
        row(95, 7218, "Summer pie")
    }
    category("Pizzas") {
        row(35, 2289, "Plain pizza")
        row(45, 2293, "Meat pizza")
        row(55, 2297, "Anchovy pizza")
        row(65, 2301, "Pineapple pizza")
    }
    category("Cakes") {
        row(40, 1891, "Cake")
        row(50, 1897, "Chocolate cake")
    }
    category("Stews") {
        row(25, 2003, "Stew")
        row(60, 2011, "Curry")
    }
    category("Wine") {
        row(35, 1993, "Jug of wine")
    }
    category("Hot drinks") {
        row(20, 4239, "Nettle tea")
    }
}
