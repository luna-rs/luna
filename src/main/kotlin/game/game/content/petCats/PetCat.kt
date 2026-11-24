package game.content.petCats

/**
 * One pet cat: how grown it is and which of the six coats it has. The coats keep the same order in every stage, so the
 * same coat maps to one item and one NPC in each stage.
 *
 * @author TheLining
 */
data class PetCat(val stage: Stage, val coat: Int) {

    /**
     * How grown a pet cat is.
     */
    enum class Stage(val firstItem: Int, val firstNpc: Int) {
        KITTEN(1555, 761),
        CAT(1561, 768),
        OVERGROWN(1567, 774)
    }

    /**
     * The inventory item for this cat.
     */
    val item get() = stage.firstItem + coat

    /**
     * The following NPC for this cat.
     */
    val npc get() = stage.firstNpc + coat

    /**
     * The same cat one stage older, or `null` if it is fully grown.
     */
    fun grown(): PetCat? = when (stage) {
        Stage.KITTEN -> PetCat(Stage.CAT, coat)
        Stage.CAT -> PetCat(Stage.OVERGROWN, coat)
        Stage.OVERGROWN -> null
    }

    companion object {

        /**
         * How many coats a pet cat can have.
         */
        const val COATS = 6

        /**
         * Every pet cat item.
         */
        val ITEMS = Stage.entries.flatMap { stage -> (0 until COATS).map { stage.firstItem + it } }.toSet()

        /**
         * Every pet cat NPC.
         */
        val NPCS = Stage.entries.flatMap { stage -> (0 until COATS).map { stage.firstNpc + it } }.toSet()

        /**
         * The pet cat for an inventory item, or `null` if [id] isn't one.
         */
        fun forItem(id: Int): PetCat? = Stage.entries.firstNotNullOfOrNull {
            val coat = id - it.firstItem
            if (coat in 0 until COATS) PetCat(it, coat) else null
        }

        /**
         * The pet cat for a following NPC, or `null` if [id] isn't one.
         */
        fun forNpc(id: Int): PetCat? = Stage.entries.firstNotNullOfOrNull {
            val coat = id - it.firstNpc
            if (coat in 0 until COATS) PetCat(it, coat) else null
        }
    }
}
