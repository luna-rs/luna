/*
 A plugin that adds functionality for clicking the "walk" and "run" buttons.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.ButtonClickEvent


/* If we clicked the walk button, stop running. */
intercept_@[ButtonClickEvent](152) { (msg, plr) => plr.getWalkingQueue.setRunning(false) }

/* If we clicked the run button, start running. */
intercept_@[ButtonClickEvent](153) { (msg, plr) => plr.getWalkingQueue.setRunning(true) }

