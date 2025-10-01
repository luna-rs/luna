package api.bot.zone;

import api.bot.action.BotMovementActionHandler
import com.google.common.collect.ImmutableList
import io.luna.Luna
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import world.player.item.jewelleryTeleport.TeleportJewellery
import world.player.skill.magic.teleportSpells.TeleportSpell

/**
 * An enum representing different zones within the RS2 world that a [Bot] can be located. Zones can be seamlessly
 * travelled to by bots using the [BotMovementActionHandler] and [TravelStrategy] types.
 *
 * **Note: Travel strategies ([travel]) will be attempted in order.**
 *
 * @author lare96
 */
enum class Zone(val anchor: Position,
                val regions: Set<Int>,
                val travel: List<TravelStrategy>,
                val safe: Boolean = true) {
    HOME(anchor = Luna.settings().game().startingPosition(),
         regions = setOf(12338, 12339),
         travel = listOf(HomeTravelStrategy,
                         WalkingTravelStrategy)),
    EDGEVILLE(anchor = Position(3087, 3496),
              regions = setOf(12342),
              travel = listOf(JewelleryTravelStrategy(TeleportJewellery.AMULET_OF_GLORY, 1),
                              HomeTravelStrategy,
                              WalkingTravelStrategy)),
    VARROCK(anchor = Position(3212, 3423),
            regions = setOf(12598, 12854, 13110, 12597, 12853, 13109, 13108, 12852),
            travel = listOf(TeleportTravelStrategy(TeleportSpell.VARROCK),
                            JewelleryTravelStrategy(TeleportJewellery.AMULET_OF_GLORY, 1),
                            HomeTravelStrategy,
                            WalkingTravelStrategy)),
    FALADOR(anchor = Position(2964, 3377),
            regions = setOf(11829, 12085, 12084, 12083, 11827, 11571),
            travel = listOf(TeleportTravelStrategy(TeleportSpell.FALADOR),
                            JewelleryTravelStrategy(TeleportJewellery.AMULET_OF_GLORY, 3),
                            HomeTravelStrategy,
                            WalkingTravelStrategy)),
    LUMBRIDGE(anchor = Position(3222, 3219),
              regions = setOf(12594, 12850, 12849, 12593),
              travel = listOf(TeleportTravelStrategy(TeleportSpell.LUMBRIDGE),
                              JewelleryTravelStrategy(TeleportJewellery.AMULET_OF_GLORY, 3),
                              HomeTravelStrategy,
                              WalkingTravelStrategy)),
    CAMELOT(anchor = Position(2757, 3479),
            regions = setOf(10808, 11062),
            travel = listOf(TeleportTravelStrategy(TeleportSpell.CAMELOT),
                            JewelleryTravelStrategy(TeleportJewellery.GAMES_NECKLACE, 1),
                            JewelleryTravelStrategy(TeleportJewellery.AMULET_OF_GLORY, 1),
                            HomeTravelStrategy,
                            WalkingTravelStrategy)),
    WILDERNESS(anchor = Position(3088, 3544),
               regions = setOf(11831, 11832, 11833, 11834, 11835, 11836, 11837, 12087, 12088, 12089, 12090, 12091,
                               12092, 12093, 12343, 12344, 12345, 12346, 12347, 12348, 12349, 12599, 12600, 12601,
                               12602, 12603, 12604, 12605, 12855, 12856, 12857, 12858, 12859, 12860, 12861, 13111,
                               13112, 13113, 13114, 13115, 13116, 13117, 13367, 13368, 13369, 13370, 13371, 13372,
                               13373),
               travel = listOf(JewelleryTravelStrategy(TeleportJewellery.AMULET_OF_GLORY, 1),
                               TeleportTravelStrategy(TeleportSpell.VARROCK),
                               TeleportTravelStrategy(TeleportSpell.DAREEYAK),
                               TeleportTravelStrategy(TeleportSpell.CARRALLANGER),
                               TeleportTravelStrategy(TeleportSpell.ANNAKARL),
                               TeleportTravelStrategy(TeleportSpell.GHORROCK),
                               HomeTravelStrategy,
                               WalkingTravelStrategy),
               safe = false);

    companion object {

        /**
         * A list of all safe zones.
         */
        val SAFE_ZONES = ImmutableList.copyOf(values().filter { it.safe })
    }

    /**
     * Determines if [locatable] is inside this zone.
     */
    fun inside(locatable: Locatable) = regions.contains(locatable.location().region.id)
}