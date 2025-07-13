package world.player.skill.fishing

import com.google.common.collect.ImmutableList
import world.player.skill.fishing.Fish.*

/**
 * An enum representing tools used to catch [Fish].
 */
enum class Tool(
    val id: Int,
    val level: Int,
    val bait: Int? = null,
    val catchAmount: IntRange = 1..1,
    val catchRate: Int,
    val animation: Int,
    val fish: List<Fish>
) {

    SMALL_NET(
        id = 303,
        level = 1,
        catchRate = 15,
        animation = 621,
        fish = listOf(SHRIMP, ANCHOVY)
    ), // You cast out your net
    KARAMBWANJI_SMALL_NET(
        id = 303,
        level = 5,
        catchRate = 15,
        animation = 621,
        fish = listOf(SHRIMP, KARAMBWANJI)
    ),
    FISHING_ROD(
        id = 307, // You cast our your line...
        level = 5,
        bait = 313,
        catchRate = 10,
        animation = 622,
        fish = listOf(SARDINE, HERRING, PIKE)
    ), // TODO send dialogue message on no tool

    // "You need a Fishing Rod to Bait these fish.
    BIG_NET(
        id = 305,
        level = 16,
        catchAmount = 1..3,
        catchRate = 17,
        animation = 620,
        fish = listOf(MACKEREL, OYSTER, COD, BASS, CASKET, LEATHER_BOOTS, LEATHER_GLOVES, SEAWEED)
    ), // You cast out your net
    FLY_FISHING_ROD(
        id = 309,
        level = 20,
        bait = 314,
        catchRate = 10,
        animation = 622,
        fish = listOf(TROUT, SALMON)
    ), // You cast out your line
    HARPOON(
        id = 311,
        level = 35,
        catchRate = 18,
        animation = 618,
        fish = listOf(TUNA, SWORDFISH)
    ),
    LOBSTER_POT(
        id = 301,
        level = 40,
        catchRate = 16,
        animation = 619,
        fish = listOf(LOBSTER)
    ),
    MONKFISH_NET(
        id = 303,
        level = 62,
        catchRate = 13,
        animation = 621,
        fish = listOf(MONKFISH)
    ),
    KARAMBWAN_VESSEL(
        id = 3157,
        level = 65,
        bait = 3150,
        catchRate = 16,
        animation = 519,
        fish = listOf(KARAMBWAN)
    ),
    SHARK_HARPOON(
        id = 311,
        level = 76,
        catchRate = 20,
        animation = 618,
        fish = listOf(SHARK)
    );

    companion object {
        val ALL: ImmutableList<Tool> = ImmutableList.copyOf(values())
    }
}