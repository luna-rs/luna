package game.skill.crafting.potteryCrafting

import api.predef.*
import game.obj.resource.fillable.WaterResource

// Use anything that can be filled with water (bucket, jug, vial, etc.) with clay to make soft clay.
WaterResource.FILLABLES.values.forEach {
    useItem(434).onItem(it) { plr.submitAction(MakeSoftClayActionItem(plr)) }
}
