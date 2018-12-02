import MakePotion.MakePotionAction
import api.*
import io.luna.game.action.Action
import io.luna.game.action.ProducingAction
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import world.player.skill.herblore.Potion

/**
 * A [ProducingAction] that will make potions.
 */
class MakePotionAction(plr: Player,
                       private val potion: Potion,
                       private var makeTimes: Int) : ProducingAction(plr, true, 2) {

    companion object {

        /**
         * Potion making animation.
         */
        private val ANIMATION = Animation(363)
    }

    /**
     * The herblore skill.
     */
    private val skill = plr.skill(SKILL_HERBLORE)

    override fun canInit() =
        when {
            skill.level < potion.level -> {
                mob.sendMessage("You need a Herblore level of ${potion.level} to make this potion.")
                false
            }
            else -> true
        }

    override fun canProduce() = canInit()

    override fun onProduce() {
        mob.sendMessage("You mix the ${itemDef(potion.secondary)?.name} into your potion.")
        mob.animation(ANIMATION)
        skill.addExperience(potion.exp)
    }

    override fun add() = arrayOf(potion.idItem)

    override fun remove() = arrayOf(potion.unfItem, potion.secondaryItem)

    override fun isEqual(other: Action<*>) =
        when (other) {
            is MakePotionAction -> potion == other.potion
            else -> false
        }
}

/**
 * Start a [MakePotionAction] if the intercepted event contains the required items.
 */
on(ItemOnItemEvent::class).run {
    val potion = Potion.getPotion(it.usedId, it.targetId)
    if (potion != null) {
        val plr = it.plr
        plr.interfaces.open(object : MakeItemDialogueInterface(potion.id) {
            override fun makeItem(player: Player, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(MakePotionAction(plr, potion, forAmount))
        })
        it.terminate()
    }
}
