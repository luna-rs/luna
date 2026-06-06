package api.bot.action

import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.naturalMicroDelay
import api.bot.SuspendableCondition
import api.predef.*
import io.luna.game.model.Entity
import io.luna.game.model.LocalEntity
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.game.model.mob.movement.NavigationRequest
import io.luna.game.model.mob.movement.PathfinderType
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.`in`.GroundItemClickMessageReader
import io.luna.net.msg.`in`.NpcClickMessageReader
import io.luna.net.msg.`in`.ObjectClickMessageReader
import io.luna.net.msg.`in`.PlayerClickMessageReader
import kotlinx.coroutines.future.await

/**
 * Handles bot-driven entity interaction actions.
 *
 * This action handler is responsible for making a [Bot] interact with players, NPCs, game objects, and ground items
 * using the same packet path that a real client click would normally use.
 *
 * If the target is not currently reachable, the handler first asks the bot navigator to move within interaction
 * distance. Once movement has completed, the correct interaction packet is sent based on the runtime type of the target.
 *
 * @param bot The bot that will perform the interactions.
 * @param handler The parent bot action handler that owns this interaction handler.
 * @author lare96
 */
class BotInteractionActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * Attempts to make this bot interact with [target] using [option].
     *
     * The bot will first attempt to navigate close enough to the target for a standard interaction. After navigation
     * completes, a short natural micro delay is applied before the appropriate interaction packet is sent.
     *
     * The packet sent depends on the concrete type of [target]:
     * - [Player] targets use [PlayerClickMessageReader] handling.
     * - [Npc] targets use [NpcClickMessageReader] handling.
     * - [GameObject] targets use [ObjectClickMessageReader] handling.
     * - [GroundItem] targets use [GroundItemClickMessageReader] handling.
     *
     * This method returns `true` once the bot is no longer walking and has reached the target according to
     * [InteractionPolicy.STANDARD_SIZE]. It does not currently verify that the target accepted the action, opened an
     * interface, started combat, picked up an item, or triggered any other post-interaction result.
     *
     * @param option The interaction option index to send.
     * @param target The entity to interact with, or `null` if no target is available.
     * @return `true` if the bot reached the target after sending the interaction; `false` if the target was `null` or
     * the reach condition timed out.
     * @throws IllegalStateException If [target] is an entity type that this handler does not know how to interact with.
     */
    suspend fun interact(option: Int, target: Entity?): Boolean {
        if (target == null) {
            return false
        }

        bot.log("Attempting interaction w/ ${target.type}=${
            when (target) {
                is LocalEntity -> target.id
                is GroundItem -> target.id
                is GameObject -> target.id
                is Npc -> target.id
                is Player -> target.username
                else -> ""
            }
        }")

        // Wait for current non-continuous pending navigation to complete first.
        if(!bot.navigator.isCurrentContinuous) {
            bot.navigator.currentPending?.await()
        }
        val request = NavigationRequest.builder(bot)
            .async(true)
            .continuous(false)
            .policy(InteractionPolicy(InteractionType.SIZE, 1))
            .target(target)
            .pathfinder(PathfinderType.PLAYER)
            bot.navigator.submit(request.build()).await()
        val cond = SuspendableCondition {
            bot.walking.isEmpty && world.collisionManager.reached(bot, target, InteractionPolicy.STANDARD_SIZE)
        }
        when (target) {
            is Player -> bot.output.sendPlayerInteraction(option, target)
            is Npc -> bot.output.sendNpcInteraction(option, target)
            is GameObject -> bot.output.sendObjectInteraction(option, target)
            is GroundItem -> bot.output.sendGroundItemInteraction(option, target)
            else -> throw IllegalStateException("This entity cannot be interacted with.")
        }

        if (cond.submit(10).await()) {
            bot.log("Interaction successful.")
            return true
        }

        bot.log("Interaction failed.")
        return false
    }
}