package game.skill.farming.patch

/**
 * The compost a farming patch has been treated with. It lasts until the patch is next emptied.
 *
 * The [experience] values are unchecked placeholders, they are not from the wiki.
 *
 * @author hydrozoa
 */
enum class CompostType(val itemId: Int, val diseaseReduction: Double, val extraLives: Int, val experience: Double) {

    /**
     * The patch has not been treated.
     */
    NONE(itemId = -1, diseaseReduction = 0.0, extraLives = 0, experience = 0.0),

    /**
     * Halves the disease chance and adds one harvest life (wiki).
     */
    COMPOST(itemId = 6032, diseaseReduction = 0.5, extraLives = 1, experience = 18.0),

    /**
     * Lowers the disease chance by 80% and adds two harvest lives (wiki).
     */
    SUPERCOMPOST(itemId = 6034, diseaseReduction = 0.8, extraLives = 2, experience = 26.0);

    companion object {
        private val types = values().filter { it != NONE }.associateBy(CompostType::itemId)
        fun lookup(itemId: Int): CompostType? = types[itemId]
    }
}
