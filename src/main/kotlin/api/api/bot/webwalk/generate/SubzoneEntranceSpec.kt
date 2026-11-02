package api.bot.webwalk.generate

import api.bot.webwalk.model.ItemRequirement
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.SubzoneEntranceCrossing
import game.obj.entrances.SwampCaveEntrance
import game.obj.entrances.WaterfallApproach
import io.luna.game.model.Position

/**
 * Native access facts for personal Swamp ropes and the full Waterfall approach.
 * Ordinary Swamp rope and Waterfall door exits remain generated from Entrance.BY_ID.
 *
 * @author lare96
 */
object SubzoneEntranceSpec {
    val SWAMP = CrossingSpec(SubzoneEntranceCrossing.ID, setOf(SwampCaveEntrance.DARK_HOLE),
        Requirements(flags = setOf(SubzoneEntranceCrossing.SWAMP_READY)), bidirectional = false, cost = 4,
        placements = listOf(CrossingPlacement(SwampCaveEntrance.DARK_HOLE, Position(3169, 3172),
            Position(3169, 3173), Position(3169, 9573))))

    val RAFT = CrossingSpec(SubzoneEntranceCrossing.ID, setOf(WaterfallApproach.RAFT), bidirectional = false, cost = 6,
        placements = listOf(CrossingPlacement(WaterfallApproach.RAFT, WaterfallApproach.RAFT_OBJECT,
            WaterfallApproach.RAFT_FROM, WaterfallApproach.ISLAND)))

    val ROCK = CrossingSpec(SubzoneEntranceCrossing.ID, setOf(WaterfallApproach.ROCK),
        Requirements(items = listOf(ItemRequirement(WaterfallApproach.ROPE))), bidirectional = false, cost = 7,
        placements = listOf(CrossingPlacement(WaterfallApproach.ROCK, WaterfallApproach.ROCK_OBJECT,
            WaterfallApproach.ROCK_FROM, WaterfallApproach.ROCK_LANDING)))

    val TREE = CrossingSpec(SubzoneEntranceCrossing.ID, setOf(WaterfallApproach.TREE),
        Requirements(items = listOf(ItemRequirement(WaterfallApproach.ROPE))), bidirectional = false, cost = 4,
        placements = listOf(CrossingPlacement(WaterfallApproach.TREE, WaterfallApproach.TREE_OBJECT,
            WaterfallApproach.TREE_FROM, WaterfallApproach.LEDGE)))

    val WATERFALL = CrossingSpec(SubzoneEntranceCrossing.ID, setOf(2010),
        Requirements(flags = setOf(SubzoneEntranceCrossing.WATERFALL_AMULET)), bidirectional = false, cost = 2,
        placements = listOf(CrossingPlacement(2010, Position(2511, 3464),
            WaterfallApproach.LEDGE, WaterfallApproach.DUNGEON)))

    val BARREL = CrossingSpec(SubzoneEntranceCrossing.ID, setOf(WaterfallApproach.BARREL), bidirectional = false, cost = 6,
        placements = listOf(CrossingPlacement(WaterfallApproach.BARREL, WaterfallApproach.BARREL_OBJECT,
            WaterfallApproach.LEDGE, WaterfallApproach.RIVERSIDE)))

    val ALL = listOf(SWAMP, RAFT, ROCK, TREE, WATERFALL, BARREL)
}
