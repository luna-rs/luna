package engine.combat.drops

/**
 * Represents all static drop data for a single NPC.
 *
 * This contains the NPC identity, any generic rare drop table linkage, and the concrete list of explicit drop entries
 * loaded from JSON.
 *
 * @property id The NPC ID this drop table belongs to.
 * @property name The NPC display name from the source data.
 * @property rare The optional generic rare drop table key.
 * @property drops The explicit drop entries for this NPC.
 *
 * @author lare96
 */
class StaticNpcDropTable(
    val id: Int,
    val name: String,
    val rare: String?,
    val drops: List<StaticNpcDrop>
)