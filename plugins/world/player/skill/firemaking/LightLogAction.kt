package world.player.skill.firemaking

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ConditionalAction
import io.luna.game.action.InventoryAction
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] implementation that will light a log on fire.
 */
class LightLogAction(plr: Player, var delayTicks: Int, val log: Log) : ConditionalAction<Player>(plr, false, 1) {

    companion object {

        val TINDERBOX_ID = 590
        val FIRE_OBJ_ID = 2732
        val ASHES_ID = 592
        val LIGHT_ANIMATION = Animation(733)

        /**
         * How fast all logs will be lit. Higher value = slower. Must be greater than or equal to {@code 2}.
         */
        val BASE_LIGHT_RATE = 13

        /**
         * Jagex have stated that log type does not have an effect on burn times, so we use a random time between 45s
         * and 2m.
         */
        val BURN_TIME = 75..200
    }

    /**
     * The animation delay.
     */
    private var animationDelay: Int = 0

    override fun start() =
        when {
            occupied(0, 0) -> {
                mob.sendMessage("You cannot light a fire here.")
                false
            }
            mob.fishing.level < log.level -> {
                mob.sendMessage("You need a Firemaking level of ${log.level} to light this.")
                false
            }
            !mob.inventory.contains(TINDERBOX_ID) -> {
                mob.sendMessage("You need a tinderbox to light this.")
                false
            }
            else -> {
                if (mob.inventory.remove(log.id)) {
                    world.addItem(log.id, 1, mob.position, mob)
                    mob.sendMessage("You light the ${itemName(log.id).toLowerCase()}...")
                    true
                } else {
                    false
                }
            }
        }

    override fun condition(): Boolean {
        if (occupied(0, 0)) {
            return false
        } else if (--delayTicks <= 0) {
            lightFire()
            return false
        }
        doLightAnim()
        return true
    }

    override fun stop() = mob.animation(Animation.CANCEL)

    override fun ignoreIf(other: Action<*>?) =
        when (other) {
            is LightLogAction -> log.id == other.log.id
            else -> false
        }

    /**
     * Ensure the light animation only plays once every 1.8s, otherwise it stutters.
     */
    private fun doLightAnim() {
        if (--animationDelay <= 0) {
            mob.animation(LIGHT_ANIMATION)
            animationDelay = 3
        }
    }

    private fun lightFire() {
        if (world.items.removeFromPosition(mob.position) { it.id == log.id }) {
            val firePosition = mob.position
            mob.firemaking.addExperience(log.exp)
            if (!occupied(-1, 0)) {
                mob.walking.walk(-1, 0)
            } else if (!occupied(1, 0)) {
                mob.walking.walk(1, 0)
            } else if (!occupied(0, -1)) {
                mob.walking.walk(0, -1)
            } else if (!occupied(0, 1)) {
                mob.walking.walk(0, 1)
            }
            val fireObject = world.addObject(FIRE_OBJ_ID, firePosition)

            world.scheduleOnce(1) {
                world.scheduleOnce(rand(BURN_TIME)) {
                    world.removeObject(fireObject)
                    world.addItem(ASHES_ID, 1, fireObject.position)
                }
            }
        }
    }

    /* TODO This should be handled by collision detection once map loading is added. Right now, only checks
     for other objects. */
    private fun occupied(offsetX: Int, offsetY: Int): Boolean {
        val offsetPos = mob.position.translate(offsetX, offsetY)
        return world.objects.findAll(offsetPos).count() > 0L
    }
}

