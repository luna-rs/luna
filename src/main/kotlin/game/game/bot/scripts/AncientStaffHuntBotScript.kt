package game.bot.scripts

import api.bot.action.BotPyramidTravel
import api.bot.script.EquipmentDropPlan
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import com.google.common.collect.ImmutableSetMultimap
import engine.bot.coordinator.EquipmentDropJobs
import engine.bot.coordinator.EquipmentDropSources
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Hunts pyramid mummies for one personal Ancient staff using the native equipment-drop lifecycle.
 *
 * @author lare96
 */
class AncientStaffHuntBotScript(bot: Bot, private val plan: EquipmentDropPlan) : NpcDropGatheringScript(
    bot, EquipmentDropSources.ANCIENT_STAFF, 1, EquipmentDropPlan.MAX_MILLIS.milliseconds,
    mutableListOf(SubZone.JALDRAOCHT_PYRAMID_MUMMIES),
    ImmutableSetMultimap.of(SubZone.JALDRAOCHT_PYRAMID_MUMMIES, "Mummy"),
    sellOnCompletion = false, equipmentPlan = plan
) {
    override val exclusiveNpcIds: Set<Int>?
        get() = EquipmentDropSources.find(plan.sourceKey)?.npcIds

    override suspend fun withdrawBoundedFood(): Boolean {
        if (!super.withdrawBoundedFood()) return false
        if (SubZone.JALDRAOCHT_PYRAMID_MUMMIES in bot.subZones) return true
        return BotPyramidTravel.withdrawPassOrFare(bot, handler) {
            !isPaused() && !isTerminated() && EquipmentDropJobs.valid(bot, plan)
        }
    }

    override suspend fun travelToWorkZone(zone: SubZone, bank: BankAccess): Boolean {
        val remaining = plan.deadline - System.currentTimeMillis()
        if (remaining <= 0L) return false
        return withTimeoutOrNull(remaining) {
            BotPyramidTravel.travel(bot, handler, zone.inside, BankAccess.NONE) {
                !isPaused() && !isTerminated() && EquipmentDropJobs.valid(bot, plan)
            }
        } == true
    }
}
