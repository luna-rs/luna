package game.obj.doors

import api.predef.*

for (id in ArdougneWallDoors.IDS) {
    object1(id, { _, _ -> ArdougneWallDoors.POLICY }) { ArdougneWallDoors.use(plr, gameObject) }
}
