package game.obj.doors

import api.predef.*

for (leaf in GrandTreeDoors.Leaf.entries) {
    object1(leaf.closed) { GrandTreeDoors.use(plr, gameObject) }
}
