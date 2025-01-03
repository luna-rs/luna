package world.player.inter.makeItem

/**
 * A model representing an item on a [MakeItemDialogueInterface] interface.
 */
class ButtonIndex(val make1: Int,
                  val make5: Int,
                  val make10: Int,
                  val makeX: Int,
                  val index: Int) {

    constructor(make1: Int, index: Int) :
            this(make1, make1 - 1, make1 - 2, make1 - 3, index)

    /**
     * Computes a list of [MakeItemOption] instances from this item.
     */
    fun options(): List<Pair<Int, MakeItemOption>> {
        val make1Action = Pair(make1, MakeItemOption(1, index))
        val make5Action = Pair(make5, MakeItemOption(5, index))
        val make10Action = Pair(make10, MakeItemOption(10, index))
        val makeXAction = Pair(makeX, MakeItemOption(-1, index))
        return listOf(make1Action,
                      make5Action,
                      make10Action,
                      makeXAction)
    }
}