package api.predef.ext

import api.attr.Attr
import game.player.Jingles
import io.luna.game.model.mob.Player

/**
 * Attribute for jingle throttle.
 */
private val Player.jingleThrottle by Attr.timer()

/**
 * Plays [jingle] for the player.
 */
fun Player.playJingle(jingle: Jingles) {
    if (!jingleThrottle.isRunning || jingleThrottle.durationTicks >= 8) {
        music.playJingle(jingle)
        jingleThrottle.reset().start()
    }
}
