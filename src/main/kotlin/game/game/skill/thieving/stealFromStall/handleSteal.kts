package game.skill.thieving.stealFromStall

import api.predef.*
import game.content.randomEvents.RandomEvents

/**
 * Tea, silk, bakery, gem, fur, spice and silver stalls sharing the source stealing dispatcher.
 * Other stall scripts retain the general timer until their own source hooks are confirmed.
 */
val randomEventStalls = setOf(635, 2560, 2561, 2562, 2563, 2564, 2565)

/* Add object interactions for all full stalls. */
for (stall in ThievingStallType.FULL_STALLS.entries) {
    object2(stall.key) {
        if (stall.key !in randomEventStalls || !RandomEvents.offerActivity(plr, RandomEvents.Activity.THIEVING)) {
            plr.submitAction(StealFromAction(plr, gameObject, stall.value))
        }
    }
}
