/*
 Plugin for running, supports:
   -> Clicking the walk and run buttons
*/

import io.luna.game.event.impl.ButtonClickEvent


/* If we clicked the walk button, stop running. */
>>@[ButtonClickEvent](152) { (msg, plr) => plr.getWalkingQueue.setRunning(false) }

/* If we clicked the run button, start running. */
>>@[ButtonClickEvent](153) { (msg, plr) => plr.getWalkingQueue.setRunning(true) }

