package engine.combat.drops

/**
 * Represents a single static NPC drop entry loaded from JSON.
 *
 * Each entry belongs to a logical sub-table identified by [table], which is later grouped and emitted into the
 * drop DSL.
 *
 * @property id The item ID for this drop.
 * @property name The display name of the dropped item.
 * @property quantity The serialized amount, either fixed or ranged.
 * @property noted Whether this drop should be emitted inside a noted block.
 * @property rarity The drop chance represented as a decimal probability.
 * @property table The identifier of the logical sub-table this drop belongs to.
 *
 * @author lare96
 */
class StaticNpcDrop(
    val id: Int,
    val name: String,
    val quantity: String,
    val noted: Boolean,
    val rarity: Double,
    val table: Int
)