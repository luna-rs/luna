package game.obj.entrances

import api.predef.*
import io.luna.game.model.Position

// Edgeville Dungeon's odd looking walls swing open like a double door (see double_doors.json) and push the player
// through to the other side.
for (wall in listOf(3209, 3211)) {
    object1(wall) {
        plr.move(Position(if (plr.position.x >= 3094) 3093 else 3094, gameObject.position.y))
    }
}
