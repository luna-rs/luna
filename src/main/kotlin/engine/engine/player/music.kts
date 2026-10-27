package engine.player

import api.predef.button
import api.predef.on
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.def.MusicDefinition
import io.luna.game.model.mob.PlayerMusic

// Run after saved volume and unlock varps have been restored.
on(LoginEvent::class, EventPriority.LOW) {
    plr.music.initialize()
}

button(PlayerMusic.AUTO_BUTTON) { plr.music.setAutomatic(true) }
button(PlayerMusic.MANUAL_BUTTON) { plr.music.setAutomatic(false) }

for (track in MusicDefinition.ALL) {
    button(track.buttonId) { plr.music.playManually(track.id()) }
}
