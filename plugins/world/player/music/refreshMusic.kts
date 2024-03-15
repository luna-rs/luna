package world.player.music

import api.predef.*
import io.luna.Luna
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.event.impl.RegionIdChangedEvent
import io.luna.game.model.def.MusicDefinition
import io.luna.game.model.mob.Player
import io.luna.net.msg.out.ColorChangeMessageWriter
import java.awt.Color

/**
 * Updates the players music based on the region they're in.
 */
fun updateMusicRegion(plr: Player, regionId: Int) { // TODO For some reason it updates twice when teleporting
    MusicDefinition.getMusicForRegion(regionId).ifPresentOrElse({ plr.sendMusic(it) }, {
        if (Luna.settings().betaMode()) {
            plr.sendMessage("No music for region[$regionId].")
        }
        plr.stopMusic()
    })
}

/**
 * Updates the players music interface with previously unlocked songs, and determines the first song to play.
 */
fun updateMusicLogin(plr: Player) {
    // TODO Test when logging in different accounts with same client
    // TODO  Or check client side for something that resets the music tab back to red if needed
    plr.musicTab.unlocked.forEach { plr.queue(ColorChangeMessageWriter(it.lineId, Color.GREEN)) }
    //    plr.queue(ColorChangeMessageWriter(next.lineId, Color.RED))

    // Start playing the previous song if needed.
    // TODO Modify button presses based on data, maybe should be done in Java like player settings.
    if (plr.musicTab.isLoopMode && plr.musicTab.lastPlayed != -1) { // TODO confirm functionality
        MusicDefinition.ALL.get(plr.musicTab.lastPlayed).ifPresent { plr.sendMusic(it) }
    } else if (plr.musicTab.isAutomaticMode) {
        val regionId = plr.position.regionPosition.id
        MusicDefinition.getMusicForRegion(regionId).ifPresent { plr.sendMusic(it) }
    }
}

// TODO Looping music and manual mode testing and configuration
//TODO Weird client connection bug after playing a lot of tracks (luna-client old side)
// TODO Remember auto/loop setting on login
// https://rune-server.org/runescape-development/rs2-server/help/672574-317-music-system.html <-- varbits
// TODO More research into fading and proper transitions between regions/tracks (feels clunky)
// TODO Incorrect music being played, verify if client or database issue

on(RegionIdChangedEvent::class) {
   // updateMusicRegion(plr, newId)
}

on(LoginEvent::class) {
   // updateMusicLogin(plr)
}

on(LogoutEvent::class) {
  //   plr.stopMusic()
}