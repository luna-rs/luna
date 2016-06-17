import io.luna.game.event.impl.ButtonClickEvent

>>@[ButtonClickEvent](152) { (msg, plr) => plr.getWalkingQueue.setRunning(false) }

>>@[ButtonClickEvent](153) { (msg, plr) => plr.getWalkingQueue.setRunning(true) }

