package plugin

import io.luna.game.plugin.PluginPipeline

sealed class PluginEvent {
  var pipeline: PluginPipeline[_] = _
  def terminate = pipeline.terminate
}

class LoginEvent extends PluginEvent
class ChatEvent(val effects: Int, val color: Int, val textLength: Int, val text: Array[Byte]) extends PluginEvent
class PickupItemEvent(val x: Int, val y: Int, val id: Int) extends PluginEvent


