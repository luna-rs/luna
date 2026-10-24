package game.skill.farming

import game.skill.farming.Farming.allotmentPatches
import game.skill.farming.Farming.herbPatches
import game.skill.farming.patch.*
import io.luna.game.model.mob.*
import io.luna.game.task.*
import java.time.*
import kotlin.random.*

/**
 * Task responsible for updating players farms. The task runs indefinitely every 60 seconds.
 * It will handle weed growing, plant growing, death and disease.
 * // todo finish
 * 
 * @author hydrozoa
 */
class UpdateFarmsTask(private val players: MobList<Player>) : Task(false, 100) {

    companion object {
        /**
         * Progresses weed growth on a patch.
         * @return true if any weeds were grown
         */
        fun progressWeeds(patch: FarmingPatch): Boolean {
            if (!patch.hasPlant() && patch.weeds < 3) {
                patch.weeds++
                return true
            }
            return false
        }

        /**
         * Progresses plant growth on a patch.
         * @return true if any plants were changed
         */
        fun progressPlants(patch: FarmingPatch): Boolean {
            if (patch.hasPlant() && patch.growthStage < patch.maxGrowth()!! && !patch.isDead) {
                if (Random.nextInt(10) == 1) {
                    patch.isDiseased = true
                    return true
                }
                if (patch.isDiseased) {
                    patch.isDiseased = false
                    patch.isDead = true
                    return true
                }

                patch.growthStage++
                if (patch.harvestReady()) { // finished growing
                    patch.produceAvailable = 5 // todo calc amount
                }
                return true
            }
            return false
        }
    }

    override fun execute() {
        players.filterNotNull().forEach({ plr ->
            var changedHerbs = false
            var changedAllotments = false

            var herbGrowthTick = false
            var allotmentGrowthTick = false
            val minute = LocalTime.now().minute
            if (minute == 0 || minute == 20 || minute == 40) {
                herbGrowthTick = true
            }
            if (minute % 10 == 0) {
                allotmentGrowthTick = true
            }

            plr.herbPatches.forEach({ (location, patch) ->
                var weedsProgressed = progressWeeds(patch)
                var plantsChanged = false
                if (herbGrowthTick) {
                    plantsChanged = progressPlants(patch)
                }
                if (weedsProgressed || plantsChanged) {
                    changedHerbs = true
                }
            })

            plr.allotmentPatches.forEach({ (location, patch) ->
                var weedsProgressed = progressWeeds(patch)
                var plantsChanged = false
                if (allotmentGrowthTick) {
                    plantsChanged = progressPlants(patch)
                }
                if (weedsProgressed || plantsChanged) {
                    changedAllotments = true
                }
            })

            if (changedHerbs) {
                Farming.sendHerbState(plr)
            }
            if (changedAllotments) {
                Farming.sendAllotmentState(plr)
            }
        })
    }
}