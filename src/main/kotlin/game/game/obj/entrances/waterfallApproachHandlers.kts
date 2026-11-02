package game.obj.entrances

import api.predef.*
import io.luna.game.model.mob.interact.InteractionPolicy

object1(WaterfallApproach.RAFT) { WaterfallApproach.board(plr, gameObject) }
useItem(WaterfallApproach.ROPE).onObject(WaterfallApproach.ROCK, InteractionPolicy.UNSPECIFIED_BIF) {
    WaterfallApproach.ropeRock(plr, gameObject)
}
useItem(WaterfallApproach.ROPE).onObject(WaterfallApproach.TREE) { WaterfallApproach.ropeTree(plr, gameObject) }
object1(WaterfallApproach.BARREL) { WaterfallApproach.barrel(plr, gameObject) }
