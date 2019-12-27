package world.player.skill.smithing

/*import io.luna.game.action.ProducingAction
import io.luna.game.model.item.Equipment.HANDS
import io.luna.game.model.item.{Equipment, Item}
import io.luna.game.model.mob.{Animation, Player}

import scala.collection.mutable

// TODO goldsmith gauntlets

private case class Bar(id: Int, level: Int, exp: Double, ingredients: Item*)


private val COAL = 453
private val ANIMATION = new Animation(899)
private val SMELT_TABLE = Map(
  'bronze_bar -> Bar(2349, 1, 6.25, new Item(438), new Item(436)),
  'iron_bar -> Bar(2351, 15, 12.5, new Item(440)),
  'silver_bar -> Bar(2355, 20, 13.67, new Item(442)),
  'steel_bar -> Bar(2353, 30, 17.5, new Item(440), new Item(COAL, 2)),
  'gold_bar -> Bar(2357, 40, 22.5, new Item(444)),
  'mithril_bar -> Bar(2359, 50, 30.0, new Item(447), new Item(COAL, 4)),
  'adamant_bar -> Bar(2361, 70, 37.5, new Item(449), new Item(COAL, 6)),
  'rune_bar -> Bar(2363, 85, 50.0, new Item(451), new Item(COAL, 8))
)

private val INGREDIENT_TO_BAR = {
  val newMap = mutable.HashMap[Int, Bar]()

  for ((symbol, bar) <- SMELT_TABLE) {
    for (ingredient <- bar.ingredients) {
      if (ingredient.getId != COAL) newMap += ingredient.getId -> bar
    }
  }

  newMap
}


private final class SmeltAction(plr: Player, amount: Int, bar: Bar) extends ProducingAction(plr, true, 5) {

  private val skill = plr.skill(SKILL_SMITHING)
  private val successful = if (bar.id == 2351) rand.nextBoolean else true

  override def canProduce = {
    if (skill.getLevel < bar.level) {
      plr.sendMessage(s"You need a Smithing level of ${ bar.level } to smelt this.")
      false
    } else if (amount == 0) {
      false
    } else {
      true
    }
  }

  override def onProduce() = {
    if (successful) {
      plr.sendMessage("") // TODO


      val handsId = plr.equipment.computeIdForIndex(HANDS)
      if (handsId == ???) {

      }
      skill.addExperience(bar.exp)


    } else {
      plr.sendMessage("") // TODO
    }
    amount -= 1
  }

  override def add = if (successful) Array(bar.id) else Array.empty
  override def remove = bar.ingredients.toArray
}


private def open = {
  // private final int[] SMELT_BARS =
  // {2349,2351,2355,2353,2357,2359,2361,2363};
  // private final int[] SMELT_FRAME =
  // {2405,2406,2407,2409,2410,2411,2412,2413};
  // for (int j = 0; j < SMELT_FRAME.length; j++) {
  // c.getPA().sendFrame246(SMELT_FRAME[j], 150, SMELT_BARS[j]);
  // }
  //player.getPacketBuilder().sendChatInterface(2400);
}