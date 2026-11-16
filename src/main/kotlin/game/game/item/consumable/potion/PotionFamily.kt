package game.player.item.consume.potion

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import io.luna.game.model.def.ItemDefinition

/**
 * The ordinary glass-bottle variants of one potion, indexed by their current number of doses.
 * Native drinking definitions supply the catalogue, with the ordinary restore recipe reviewed explicitly.
 * Notes count as their underlying bottles for ownership; native combining still requires unnoted inputs.
 * Oils, quest liquids and other vessels are not inferred from names or finished-potion recipes.
 *
 * @property name The stable native family name.
 * @property potion The native drinking effect, when that family has one registered.
 * @author lare96
 */
class PotionFamily private constructor(
    val name: String,
    val potion: Potion?,
    val oneDose: Int,
    val twoDose: Int,
    val threeDose: Int,
    val fourDose: Int
) {

    companion object {

        /**
         * Explicit ordinary bottle families. Four-dose identifiers remain stable family keys.
         */
        val ALL: List<PotionFamily> by lazy {
            val native = Potion.entries.map {
                PotionFamily(it.name, it, it.oneDose, it.twoDose, it.threeDose, it.fourDose)
            }
            ImmutableList.copyOf(native + if (native.any { it.fourDose == 2430 }) emptyList() else
                listOf(PotionFamily("RESTORE_POTION", null, 131, 129, 127, 2430)))
        }

        private val byId: Map<Int, PotionFamily> by lazy {
            val entries = ALL.flatMap { family -> family.ids.map { it to family } }
            check(entries.map { it.first }.toSet().size == entries.size) { "Potion families overlap." }
            ImmutableMap.copyOf(entries.toMap())
        }

        /**
         * Looks up a reviewed family, accepting bank notes only as an ownership representation.
         */
        fun forId(id: Int): PotionFamily? = if (!ItemDefinition.isIdValid(id)) null else byId[id] ?: byId[unnoted(id)]

        /**
         * Returns the same catalogue instance used by item-id lookups for a native drinking definition.
         */
        fun forPotion(potion: Potion): PotionFamily = checkNotNull(byId[potion.fourDose])

        private fun unnoted(id: Int): Int = if (!ItemDefinition.isIdValid(id)) id else ItemDefinition.ALL.get(id)
            .map { it.unnotedId.orElse(id) }.orElse(id)
    }

    /**
     * Concrete unnoted identifiers, ordered from one to four current doses.
     */
    val ids: List<Int> = ImmutableList.of(oneDose, twoDose, threeDose, fourDose)

    /**
     * Unnoted identifiers keyed by their current dose counts, rather than post-drink remainders.
     */
    val idsByDose: Map<Int, Int> = ImmutableMap.of(1, oneDose, 2, twoDose, 3, threeDose, 4, fourDose)

    private val dosesById: Map<Int, Int> = idsByDose.entries.associate { it.value to it.key }

    /**
     * Counts the doses currently inside a family bottle or its note; unrelated items contain zero family doses.
     */
    fun doses(id: Int): Int = dosesById[id] ?: dosesById[unnoted(id)] ?: 0

    /**
     * Returns the concrete bottle for one through four doses. Empty vials belong to no potion family.
     */
    fun idForDoses(doses: Int): Int = requireNotNull(idsByDose[doses]) { "Invalid potion dose count: $doses" }
}
