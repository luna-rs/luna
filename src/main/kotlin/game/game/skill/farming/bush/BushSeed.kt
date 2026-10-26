package game.skill.farming.bush

import game.skill.farming.patch.*
import io.luna.game.model.item.Item

/**
 * Data for bush seeds, planted in bush patches.
 *
 * A bush is drawn as its [configIndexOffset] plus its stage minus one, so the seedling is drawn as [configIndexOffset]
 * itself, and a fully grown bush whose health has been checked as that plus its berries. [checkHealthValue] is drawn
 * while the fully grown bush waits to have its health checked. [plantXp] is given when the seed is planted,
 * [harvestXp] for every berry picked, and [checkHealthXp] when the bush's health is checked.
 *
 * The [baseDiseaseChance] of 20 is an unchecked placeholder, the wiki gives none for bushes. Poison ivy is immune to
 * disease (wiki), so its chance is 0.
 *
 * @author hydrozoa
 */
enum class BushSeed(override val seedId: Int,
                    override val level: Int,
                    val configIndexOffset: Int,
                    override val stages: Int,
                    override val produce: Item,
                    override val plantXp: Double,
                    override val harvestXp: Double,
                    override val checkHealthXp: Double,
                    override val baseDiseaseChance: Int,
                    val checkHealthValue: Int) : Crop {

    REDBERRY(5101, 10, 5, 6, Item(1951), 11.5, 4.5, 64.0, 20, 250),
    CADAVABERRY(5102, 22, 15, 7, Item(753), 18.0, 7.0, 102.5, 20, 251),
    DWELLBERRY(5103, 36, 26, 8, Item(2126), 31.5, 12.0, 177.5, 20, 252),
    JANGERBERRY(5104, 48, 38, 9, Item(247), 50.5, 19.0, 284.5, 20, 253),
    WHITEBERRY(5105, 59, 51, 9, Item(239), 78.0, 29.0, 437.5, 20, 254),
    POISON_IVY(5106, 70, 197, 9, Item(6018), 120.0, 45.0, 675.0, 0, 255);

    companion object {
        private val seeds = values().associateBy(BushSeed::seedId)
        fun lookup(seedId: Int): BushSeed? = seeds[seedId]
    }
}
