package api.bot.action

import api.bot.Suspendable.delay
import api.bot.script.BotScript.ActionSafety
import api.bot.webwalk.walk.crossing.NativeDialogueControl
import api.predef.ext.get
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.dialogue.NpcDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.dialogue.PlayerDialogue
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Opens shops through their native NPC interactions and remembers the resulting visit.
 *
 * @author lare96
 */
internal class BotShopVisits(private val bot: Bot, private val handler: BotActionHandler) {

    companion object {
        private const val SHOPKEEPER_WANDER_RADIUS = 5
        private const val MAX_OPEN_ATTEMPTS = 3
    }

    /**
     * Ties a purchase to the exact window opened at a live seller.
     *
     * @author lare96
     */
    private class Visit(val overlay: ShopInterface, val npc: Npc, val stand: Position, val seller: Shop.NpcSeller)

    private var visit: Visit? = null

    fun canTradeWith(shop: Shop): Boolean {
        val current = visit ?: return false
        return current.overlay.shop === shop && bot.overlays[ShopInterface::class] === current.overlay &&
            bot.position == current.stand && bot.walking.isEmpty && near(current.npc) &&
            current.seller.matches(current.npc) &&
            ActionSafety.NON_COMBAT.allows(bot)
    }

    fun distance(shop: Shop): Long? = findSellers(shop).firstOrNull()?.let { (npc, _) ->
        bot.position.computeLongestDistance(npc.position).toLong()
    }

    suspend fun open(shop: Shop, canContinue: () -> Boolean): ShopInterface? {
        fun eligible() = bot.state == EntityState.ACTIVE && ActionSafety.NON_COMBAT.allows(bot) && canContinue()
        if (!eligible()) return null
        if (canTradeWith(shop)) return visit?.overlay

        visit = null
        val candidates = findSellers(shop)
        if (candidates.isEmpty()) return null

        bot.overlays.closeWindows()
        return withTimeoutOrNull(120.seconds) {
            for ((npc, seller) in candidates) {
                for (attempt in 0 until MAX_OPEN_ATTEMPTS) {
                    if (!eligible()) return@withTimeoutOrNull null
                    if (!seller.matches(npc) || !npc.isAlive || npc.state != EntityState.ACTIVE) break
                    val travelTime = (bot.position.computeLongestDistance(npc.position).toLong() + 15)
                        .coerceIn(25L, 110L).seconds
                    if (!handler.interactions.approach(npc, timeout = travelTime, allowOutOfView = true,
                            canContinue = ::eligible)) break
                    val overlay = openAtNpc(shop, npc, seller, ::eligible) ?: continue
                    if (!eligible() || !near(npc)) {
                        if (bot.overlays[ShopInterface::class] === overlay) bot.overlays.closeWindows()
                        continue
                    }
                    visit = Visit(overlay, npc, bot.position, seller)
                    return@withTimeoutOrNull overlay
                }
            }
            null
        }
    }

    private fun findSellers(shop: Shop): List<Pair<Npc, Shop.NpcSeller>> {
        val (local, automatic) = shop.npcSellers.partition { it.area() != null }
        val world = bot.world
        val candidates = buildList {
            val byId = automatic.groupBy { it.id() }
            if (byId.isNotEmpty()) {
                for (npc in world.npcs.byCurrentNpcIds(byId.keys)) {
                    byId[npc.id].orEmpty().forEach { add(npc to it) }
                }
            }
            for ((area, sellers) in local.groupBy { it.area() }) {
                val localById = sellers.groupBy { it.id() }
                val center = area.centerPosition
                // The declared bounds describe spawn tiles. Shopkeepers can wander beyond their edges.
                val radius = area.tileRadius + SHOPKEEPER_WANDER_RADIUS
                val planes = if (area.plane < 0) Position.HEIGHT_LEVELS.lowerEndpoint() until
                    Position.HEIGHT_LEVELS.upperEndpoint() else area.plane..area.plane
                for (plane in planes) {
                    val nearby = world.locator.findNpcs(center.setZ(plane), radius) {
                        it.id in localById && area.contains(it.basePosition)
                    }
                    for (npc in nearby) localById[npc.id].orEmpty().forEach { add(npc to it) }
                }
            }
        }
        return candidates.filter { (npc) -> npc.state == EntityState.ACTIVE && npc.isAlive }
            .sortedWith(compareBy<Pair<Npc, Shop.NpcSeller>> { it.first.position.z != bot.position.z }
                .thenBy { bot.position.computeLongestDistance(it.first.position) })
    }

    private fun near(npc: Npc): Boolean =
        bot.state == EntityState.ACTIVE && npc.state == EntityState.ACTIVE && npc.isAlive &&
            bot.position.isWithinDistance(npc.position, 3)

    private suspend fun openAtNpc(shop: Shop, npc: Npc, seller: Shop.NpcSeller,
                                  eligible: () -> Boolean): ShopInterface? = NativeDialogueControl.own(bot) {
        val previousDialogue = bot.dialogues
        var handledPage: DialogueInterface? = null
        var selectedReply = false
        try {
            if (!handler.interactions.interact(seller.option(), npc) {
                    eligible() && seller.matches(npc)
                }) return@own null
            val timeout = if (seller.dialogueOption() != null) 10.seconds else 4.seconds
            withTimeoutOrNull(timeout) {
                while (eligible() && near(npc) && seller.matches(npc)) {
                    val shopWindow = bot.overlays[ShopInterface::class]
                    if (shopWindow != null) {
                        if (shopWindow.shop === shop) return@withTimeoutOrNull shopWindow
                        bot.overlays.closeWindows()
                        return@withTimeoutOrNull null
                    }
                    val page = bot.overlays[DialogueInterface::class]
                    if (page != null && bot.dialogues !== previousDialogue && page !== handledPage) {
                        val reply = seller.dialogueOption()
                        val accepted = when (page) {
                            is NpcDialogue -> page.npcId == npc.id && reply != null && bot.output.sendContinueDialogue()
                            is OptionDialogue -> !selectedReply && reply != null && handler.widgets.clickDialogueOption(reply)
                            is PlayerDialogue -> selectedReply && page.lines == listOf(reply) && bot.output.sendContinueDialogue()
                            else -> false
                        }
                        handledPage = page
                        if (!accepted) return@withTimeoutOrNull null
                        if (page is OptionDialogue) selectedReply = true
                    }
                    delay(300.milliseconds)
                }
                null
            }
        } finally {
            if (handledPage != null && bot.overlays[DialogueInterface::class] === handledPage) {
                bot.overlays.closeWindows()
            }
        }
    }
}
