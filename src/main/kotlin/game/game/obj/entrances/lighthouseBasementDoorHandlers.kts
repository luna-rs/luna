package game.obj.entrances

import api.predef.*

for (wall in LighthouseBasementDoor.Wall.entries) {
    object1(wall.id) { LighthouseBasementDoor.use(plr, gameObject) }
}
