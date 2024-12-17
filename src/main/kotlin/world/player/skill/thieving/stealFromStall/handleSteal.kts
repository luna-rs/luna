package world.player.skill.thieving.stealFromStall

import api.predef.*

// TODO Replace unused stalls at home with other ones, add some new stalls if space permits it

/* Add object interactions for all full stalls. */
for (stall in ThievableStall.FULL_STALLS.entries) {
    object2(stall.key) { plr.submitAction(StealFromAction(plr, gameObject, stall.value)) }
}