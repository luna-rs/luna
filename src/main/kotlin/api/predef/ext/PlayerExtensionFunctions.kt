package api.predef.ext

import io.luna.game.model.def.MusicDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerMusicTab
import io.luna.net.msg.out.ColorChangeMessageWriter
import io.luna.net.msg.out.MusicMessageWriter
import world.player.Messages
import java.awt.Color

/**
 * Plays music for the player.
 */
fun Player.sendMusic(def: MusicDefinition) {
    val id = def.id
    if (musicTab.lastPlayed != id && id != -1) {
        musicTab.lastPlayed = id
        queue(MusicMessageWriter(id))
        queue(ColorChangeMessageWriter(def.lineId, Color.GREEN))
        sendText(def.name, 4439)
        musicTab.unlocked.add(PlayerMusicTab.UnlockedSong(id, def.lineId))
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
