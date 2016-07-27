import io.luna.game.action.HarvestingSkillAction
import io.luna.game.event.impl.NpcClickEvent.{NpcFirstClickEvent, NpcSecondClickEvent}
import io.luna.game.model.mobile.Player
import io.luna.game.model.mobile.Skill.FISHING


// TODO documentation, major cleanup
// TODO exp rates, fishing action

private case class Fish(id: Int, level: Int, exp: Double)

private case class Tool
(
  id: Int,
  level: Int,
  bait: Option[Int],
  chance: Double,
  animation: Int,
  fish: Fish*
)


private val SHRIMP = Fish(317, 1, -1)
private val SARDINE = Fish(327, 5, -1)
private val HERRING = Fish(345, 10, -1)
private val ANCHOVY = Fish(321, 15, -1)
private val MACKEREL = Fish(353, 16, -1)
private val CASKET = Fish(405, 16, -1)
private val OYSTER = Fish(407, 16, -1)
private val TROUT = Fish(335, 20, -1)
private val COD = Fish(341, 23, -1)
private val PIKE = Fish(349, 25, -1)
private val SALMON = Fish(331, 30, -1)
private val TUNA = Fish(359, 35, -1)
private val LOBSTER = Fish(377, 40, -1)
private val BASS = Fish(363, 46, -1)
private val SWORDFISH = Fish(371, 50, -1)
private val SHARK = Fish(383, 76, -1)


private val SMALL_NET = Tool(303, 1, None, 0.3, 621, SHRIMP, ANCHOVY)
private val FISHING_ROD = Tool(307, 5, Some(313), 0.4, 622, SARDINE, HERRING, PIKE)
private val BIG_NET = Tool(305, 16, None, 0.25, 620, MACKEREL, OYSTER, COD, BASS, CASKET)
private val FLY_FISHING_ROD = Tool(309, 20, Some(314), 0.55, 622, TROUT, SALMON)
private val HARPOON = Tool(311, 35, None, 0.15, 618, TUNA, SWORDFISH)
private val LOBSTER_POT = Tool(301, 40, None, 0.2, 619, LOBSTER)
private val SHARK_HARPOON = Tool(311, 76, None, 0.05, 618, SHARK)


private final class FishAction(plr: Player, tool: Tool) extends HarvestingSkillAction(plr, FISHING) {
  // level reqs, compute random fish, give experience, you catch some <x>
  override def remove = ???
  override def add = ???
  override def harvestChance = ???
}


intercept_@[NpcFirstClickEvent](233, 234, 235, 236) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, FISHING_ROD))
}
intercept_@[NpcFirstClickEvent](309, 310, 311, 314, 315, 317, 318) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, FLY_FISHING_ROD))
}
intercept_@[NpcFirstClickEvent](312) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, LOBSTER_POT))
}
intercept_@[NpcFirstClickEvent](313) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, BIG_NET))
}
intercept_@[NpcFirstClickEvent](316, 319) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, SMALL_NET))
}

intercept_@[NpcSecondClickEvent](309, 316, 319, 310, 311, 314, 315, 317, 318) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, FISHING_ROD))
}
intercept_@[NpcSecondClickEvent](312) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, HARPOON))
}
intercept_@[NpcSecondClickEvent](313) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, SHARK_HARPOON))
}