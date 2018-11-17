import io.luna.game.action.{Action, HarvestingAction}
import io.luna.game.event.impl.NpcClickEvent
import io.luna.game.event.impl.NpcClickEvent.{NpcFirstClickEvent, NpcSecondClickEvent}
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Animation.CANCEL
import io.luna.util.Rational


/* Class representing fish that can be caught. */
case class Fish(id: Int, level: Int, exp: Double) {
  def toItem(action: FishAction) = new Item(id)

  def getMessage(action: FishAction) = ""
}

/* Class representing Karambwanji. */
class Karambwanji extends Fish(3150, 5, 5.0) {
  override def toItem(action: FishAction) = {
    val level = action.skill.getLevel
    new Item(id, (1 + Math.floor(level / 5)).toInt)
  }
}

/* Class representing Karambwan. */
class Karambwan extends Fish(3142, 65, 50.0) {
  override def toItem(action: FishAction) = {
    val eatBait = rand(3) == 0
    if (eatBait) {
      action.noCatchMessage = true
      action.getMob.sendMessage("A Karambwan deftly snatches the Karambwanji from your vessel!")
      null
    } else {
      new Item(id, 1)
    }
  }
}

/* Class representing tools that can be used to catch fish. */
private case class Tool(id: Int,
                        level: Int,
                        bait: Option[Int],
                        chance: Rational,
                        animation: Int,
                        fish: Fish*)


/* Constants representing fish that can be caught. */
private val SHRIMP =
  Fish(id = 317,
    level = 1,
    exp = 10.0)

private val KARAMBWANJI = new Karambwanji

private val SARDINE =
  Fish(id = 327,
    level = 5,
    exp = 20.0)

private val HERRING =
  Fish(id = 345,
    level = 10,
    exp = 30.0)

private val ANCHOVY =
  Fish(id = 321,
    level = 15,
    exp = 40.0)

private val MACKEREL =
  Fish(id = 353,
    level = 16,
    exp = 20.0)

private val CASKET =
  Fish(id = 405,
    level = 16,
    exp = 10.0)

private val OYSTER =
  Fish(id = 407,
    level = 16,
    exp = 10.0)

private val LEATHER_BOOTS =
  Fish(id = 1061,
    level = 16,
    exp = 1.0)

private val LEATHER_GLOVES =
  Fish(id = 1059,
    level = 16,
    exp = 1.0)

private val SEAWEED =
  Fish(id = 401,
    level = 16,
    exp = 1.0)

private val TROUT =
  Fish(id = 335,
    level = 20,
    exp = 50.0)

private val COD =
  Fish(id = 341,
    level = 23,
    exp = 45.0)

private val PIKE =
  Fish(id = 349,
    level = 25,
    exp = 60.0)

private val SALMON =
  Fish(id = 331,
    level = 30,
    exp = 70.0)

private val TUNA =
  Fish(id = 359,
    level = 35,
    exp = 80.0)

private val LOBSTER =
  Fish(id = 377,
    level = 40,
    exp = 90.0)

private val BASS =
  Fish(id = 363,
    level = 46,
    exp = 100.0)

private val SWORDFISH =
  Fish(id = 371,
    level = 50,
    exp = 100.0)

private val MONKFISH =
  Fish(id = 7944,
    level = 62,
    exp = 120.0)

private val KARAMBWAN = new Karambwan

private val SHARK =
  Fish(id = 383,
    level = 76,
    exp = 110.0)

/* Constants representing tools that can be used. */
private val SMALL_NET =
  Tool(id = 303,
    level = 1,
    bait = None,
    chance = CHANCE_COMMON,
    animation = 621,
    fish = SHRIMP, ANCHOVY)

private val KARAMBWANJI_SMALL_NET =
  Tool(id = 303,
    level = 5,
    bait = None,
    chance = CHANCE_COMMON,
    animation = 621,
    fish = SHRIMP, KARAMBWANJI)

private val FISHING_ROD =
  Tool(id = 307,
    level = 5,
    bait = Some(313),
    chance = CHANCE_COMMON,
    animation = 622,
    fish = SARDINE, HERRING, PIKE)

private val BIG_NET =
  Tool(id = 305,
    level = 16,
    bait = None,
    chance = CHANCE_UNCOMMON,
    animation = 620,
    fish = MACKEREL, OYSTER, COD, BASS, CASKET, LEATHER_BOOTS, LEATHER_GLOVES, SEAWEED)

private val FLY_FISHING_ROD =
  Tool(id = 309,
    level = 20,
    bait = Some(314),
    chance = CHANCE_VERY_COMMON,
    animation = 622,
    fish = TROUT, SALMON)

private val HARPOON =
  Tool(id = 311,
    level = 35,
    bait = None,
    chance = CHANCE_VERY_UNCOMMON,
    animation = 618,
    fish = TUNA, SWORDFISH)

private val LOBSTER_POT =
  Tool(id = 301,
    level = 40,
    bait = None,
    chance = CHANCE_UNCOMMON,
    animation = 619,
    fish = LOBSTER)

private val MONKFISH_NET =
  Tool(id = 303,
    level = 62,
    bait = None,
    chance = CHANCE_COMMON,
    animation = 621,
    fish = MONKFISH)

private val KARAMBWAN_VESSEL =
  Tool(id = 3157,
    level = 65,
    bait = Some(3150),
    chance = CHANCE_UNCOMMON,
    animation = 519,
    fish = KARAMBWAN)

private val SHARK_HARPOON =
  Tool(id = 311,
    level = 76,
    bait = None,
    chance = CHANCE_VERY_UNCOMMON,
    animation = 618,
    fish = SHARK)


/* An Action implementation that will manage the fish catching operation. */
private final class FishAction(val evt: NpcClickEvent, val tool: Tool) extends HarvestingAction(evt.plr) {

  var noCatchMessage = false
  var exp = 0.0
  val skill = mob.skill(SKILL_FISHING)

  private def canFish = {
    if (skill.getLevel < tool.level) {
      mob.sendMessage(s"You need a Fishing level of ${ tool.level } to fish here.")
      false
    } else if (!tool.bait.forall(mob.inventory.contains)) {
      mob.sendMessage(s"You do not have the bait required to fish here.")
      false
    } else if (!mob.inventory.contains(tool.id)) {
      mob.sendMessage(s"You need a ${ nameOfItem(tool.id) } to fish here.")
      false
    } else {
      mob.animation(new Animation(tool.animation))
      true
    }
  }

  override def canInit = {
    if (canFish) {
      mob.sendMessage("You begin to fish...")
      true
    } else {
      false
    }
  }

  override def onHarvest() = {
    if (!noCatchMessage) {
      currentAdd.foreach(it => mob.sendMessage(s"You catch some ${ nameOfItem(it.getId) }."))
    }

    skill.addExperience(exp)
    exp = 0.0
  }

  override def canHarvest = canFish && rand(500) != 0

  override def harvestChance = tool.chance

  override def add = {
    val toFish = for (fish <- tool.fish
                      if skill.getLevel >= fish.level) yield fish
    val amount = if (tool == BIG_NET) rand(1, 3) else 1
    val caught = new Array[Item](amount)

    caught.indices.foreach { index =>
      val toCatch = pick(toFish)
      exp += toCatch.exp
      caught(index) = toCatch.toItem(this)
    }
    caught
  }

  override def remove = tool.bait.map { it => Array(new Item(it)) }.getOrElse(Array.empty)

  override def onInterrupt() = mob.animation(CANCEL)

  override def isEqual(other: Action[_]) = {
    other match {
      case action: FishAction => evt.npc.equals(action.evt.npc)
      case _ => false
    }
  }
}


/* Intercepted first click events for fishing spots. */
on[NpcFirstClickEvent].
  args(233, 234, 235, 236).
  run { msg => msg.plr.submitAction(new FishAction(msg, FISHING_ROD)) }

on[NpcFirstClickEvent].
  args(309, 310, 311, 314, 315, 317, 318).
  run { msg => msg.plr.submitAction(new FishAction(msg, FLY_FISHING_ROD)) }

on[NpcFirstClickEvent].
  args { 312 }.
  run { msg => msg.plr.submitAction(new FishAction(msg, LOBSTER_POT)) }

on[NpcFirstClickEvent].
  args { 313 }.
  run { msg => msg.plr.submitAction(new FishAction(msg, BIG_NET)) }

on[NpcFirstClickEvent].
  args(316, 319).
  run { msg => msg.plr.submitAction(new FishAction(msg, SMALL_NET)) }

on[NpcFirstClickEvent].
  args { 1174 }.
  run { msg => msg.plr.submitAction(new FishAction(msg, MONKFISH_NET)) }

/* Intercepted second click events for fishing spots. */
on[NpcSecondClickEvent].
  args(309, 316, 319, 310, 311, 314, 315, 317, 318).
  run { msg => msg.plr.submitAction(new FishAction(msg, FISHING_ROD)) }

on[NpcSecondClickEvent].
  args { 312 }.
  run { msg => msg.plr.submitAction(new FishAction(msg, HARPOON)) }

on[NpcSecondClickEvent].
  args { 313 }.
  run { msg => msg.plr.submitAction(new FishAction(msg, SHARK_HARPOON)) }
