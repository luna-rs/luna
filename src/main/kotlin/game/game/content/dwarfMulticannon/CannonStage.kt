package game.content.dwarfMulticannon

/**
 * The stages of building a Dwarf multicannon, in order. Each stage adds one part.
 *
 * @author TheLining
 */
enum class CannonStage(val objectId: Int, val partId: Int, val partName: String, val freeSpace: String) {
    BASE(objectId = 7, partId = 6, partName = "base", freeSpace = "one free inventory spaces"),
    STAND(objectId = 8, partId = 8, partName = "stand", freeSpace = "two free inventory spaces"),
    BARRELS(objectId = 9, partId = 10, partName = "barrels", freeSpace = "three free inventory spaces"),
    FURNACE(objectId = 6, partId = 12, partName = "furnace", freeSpace = "4 free inventory spaces");

    companion object {

        /**
         * The item IDs of every cannon part.
         */
        val PARTS = entries.map { it.partId }.toSet()

        /**
         * The object IDs of every stage.
         */
        val OBJECTS = entries.map { it.objectId }.toSet()
    }

    /**
     * The stage after this one, or `null` if the cannon is complete.
     */
    val next: CannonStage?
        get() = entries.getOrNull(ordinal + 1)

    /**
     * The item IDs of the parts a cannon at this stage is built from.
     */
    val parts: List<Int>
        get() = entries.take(ordinal + 1).map { it.partId }
}
