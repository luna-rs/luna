package api.predef.ext

import api.attr.Attr
import game.player.Jingles
import io.luna.game.model.def.MusicDefinition
import io.luna.game.model.mob.MusicTab
import io.luna.game.model.mob.Player
import io.luna.net.msg.out.JingleMessageWriter
import io.luna.net.msg.out.MusicMessageWriter
import io.luna.net.msg.out.WidgetColorMessageWriter
import java.awt.Color

/**
 * Attribute for jingle throttle.
 */
private val Player.jingleThrottle by Attr.timer()

/**
 * Plays [jingle] for the player.
 */
fun Player.playJingle(jingle: Jingles) {
    if (jingleThrottle.durationTicks >= 8) {
        queue(JingleMessageWriter(jingle))
        jingleThrottle.reset()
    }
}