package plugin.events

import io.luna.game.model.mobile.Player

/**
 * Player-related plugin events should extend.
 */
class PlayerPluginEvent(_player: Player) extends GamePluginEvent {
  def player = _player
}