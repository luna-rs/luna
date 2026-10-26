package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Slayer guide. The monster levels match `SlayerTaskType`.
 */
val slayerGuide = skillGuide("Slayer") {
    category("Monsters") {
        row(5, null, "Crawling hands")
        row(7, null, "Cave bugs")
        row(10, null, "Cave crawlers")
        row(15, null, "Banshees")
        row(17, null, "Cave slimes")
        row(20, null, "Rockslugs")
        row(22, null, "Desert lizards")
        row(25, null, "Cockatrices")
        row(30, null, "Pyrefiends")
        row(32, null, "Mogres")
        row(33, null, "Harpie bug swarms")
        row(35, null, "Wall beasts")
        row(37, null, "Killerwatts")
        row(40, null, "Basilisks")
        row(42, null, "Fever spiders")
        row(45, null, "Infernal mages")
        row(50, null, "Bloodvelds")
        row(52, null, "Jellies")
        row(55, null, "Turoth")
        row(57, null, "Zygomites")
        row(60, null, "Aberrant spectres")
        row(65, null, "Dust devils")
        row(70, null, "Kurask")
        row(72, null, "Skeletal wyverns")
        row(75, null, "Gargoyles")
        row(80, null, "Nechryael")
        row(85, null, "Abyssal demons")
        row(90, null, "Dark beasts")
    }
    category("Equipment") {
        row(1, 4155, "Enchanted gem")
        row(15, 4166, "Earmuffs, for banshees")
        row(20, 4161, "Bag of salt, for rockslugs")
        row(22, 6696, "Ice cooler, for desert lizards")
        row(25, 4156, "Mirror shield, for cockatrice")
        row(32, 6664, "Fishing explosive, for mogres")
        row(33, 7051, "Bug lantern, for harpie bugs")
        row(35, 4551, "Spiny helmet, for wall beasts")
        row(42, 6720, "Slayer gloves, for fever spiders")
        row(55, 4158, "Leaf-bladed spear")
        row(55, 4170, "Slayer's staff")
        row(57, 7421, "Fungicide spray, for zygomites")
        row(60, 4168, "Nose peg, for aberrant spectres")
        row(65, 4164, "Facemask, for dust devils")
        row(75, 4162, "Rock hammer, for gargoyles")
    }
}
