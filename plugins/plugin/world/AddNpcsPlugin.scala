package plugin.world

import io.luna.game.model.Position
import plugin.{AddNpcsEvent, Plugin}

class AddNpcsPlugin extends Plugin[AddNpcsEvent] {

  world.addNpc(1, new Position(3222, 3222))
}
