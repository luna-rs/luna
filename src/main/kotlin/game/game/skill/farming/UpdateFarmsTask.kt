package game.skill.farming

import game.skill.farming.Farming.herbPatches
import game.skill.farming.patch.*
import io.luna.game.model.mob.*
import io.luna.game.task.*
import java.time.*

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
         * @return true if any plants were grown
         */
        fun progressPlants(patch: FarmingPatch): Boolean {
            if (patch.hasPlant() && patch.growthStage < patch.maxGrowth()!!) {
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
            // update herb patches
            var changedState = false

            var herbGrowthTick = false
            val minute = LocalTime.now().minute
            if (minute == 0 || minute == 20 || minute == 40) {
                herbGrowthTick = true
            }

            plr.herbPatches.forEach({ (location, patch) ->
                var weedsProgressed = progressWeeds(patch)
                var plantsProgressed = false
                if (herbGrowthTick) {
                    plantsProgressed = progressPlants(patch)
                }
                if (weedsProgressed || plantsProgressed) {
                    changedState = true
                }
            })

            // send new herb patch state
            if (changedState) {
                Farming.sendHerbState(plr)
            }
        })
    }
}