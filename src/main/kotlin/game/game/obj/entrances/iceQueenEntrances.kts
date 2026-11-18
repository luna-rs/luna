package game.obj.entrances

import api.predef.*
import game.skill.mining.Pickaxe

object1(IceQueenRockSlide.ID) {
    if (gameObject.position == IceQueenRockSlide.POSITION) {
        plr.sendMessage("These rocks contain nothing interesting. They are just in the way.")
    }
}

object2(IceQueenRockSlide.ID, { _, _ -> IceQueenRockSlide.POLICY }) {
    IceQueenRockSlide.mine(plr, gameObject)
}

for (pickaxe in Pickaxe.entries) {
    useItem(pickaxe.id).onObject(IceQueenRockSlide.ID, { _, _ -> IceQueenRockSlide.POLICY }) {
        IceQueenRockSlide.mine(plr, gameObject)
    }
}
