package api.predef.ext

import io.luna.game.model.def.MusicDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.MusicTab
import io.luna.net.msg.out.MusicMessageWriter
import io.luna.net.msg.out.WidgetColorMessageWriter
import java.awt.Color

/**
 * Plays music for the player.
 */
fun Player.sendMusic(def: MusicDefinition) {
    val id = def.id
    if (musicTab.lastPlayed != id && id != -1) {
        musicTab.lastPlayed = id
        queue(MusicMessageWriter(id))
        queue(WidgetColorMessageWriter(def.lineId, Color.GREEN))
        sendText(def.name, 4439)
        musicTab.unlocked.add(MusicTab.UnlockedSong(id, def.lineId))
    }
}

/**
 * Stops music for the player.
 */
fun Player.stopMusic() {
    if (musicTab.lastPlayed != -1) {
        musicTab.lastPlayed = -1
        sendText(if (musicTab.isAutomaticMode) "AUTO" else "MAN", 4439)
        queue(MusicMessageWriter(-1))
    }
}