package game.obj.entrances

import api.predef.*

for (id in HangarExit.IDS) {
    object1(id) { HangarExit.use(plr, gameObject) }
}
