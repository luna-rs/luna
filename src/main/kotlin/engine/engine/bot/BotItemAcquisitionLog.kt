package engine.bot

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.isRealBot
import engine.trade.ConfirmTradeInterface
import game.content.treasureTrails.TrailRewards
import game.content.treasureTrails.TrailTier
import io.luna.game.model.EntityState
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.item.DeathGroundItem
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.item.ItemContainerListener
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.interact.InteractionAction
import io.luna.util.logging.LoggingSettings.FileOutputType
import org.apache.logging.log4j.LogManager

/**
 * Observes notable item gains without changing the functions that award or transfer items.
 * Equipment, rare clue rewards and items worth at least 10,000 intrinsic GP are included.
 * The synchronous callback captures the actual awarding call site; available interaction details
 * supplement it. A pickup without retained origin information is explicitly marked unknown.
 *
 * @author lare96
 */
class BotItemAcquisitionLog private constructor(private val bot: Bot) : ItemContainerListener {
    companion object {
        private const val TRAIL = 1
        private const val TRADE = 2
        private const val PICKUP = 3
        private const val SHOP = 4
        private var Bot.acquisitionLogAttached by Attr.boolean { false }
        private val walker = StackWalker.getInstance()
        private val logger = LogManager.getLogger(BotItemAcquisitionLog::class.java)
        private val acquisitionLogger = FileOutputType.ITEM_ACQUISITION.logger
        private val acquisitionLevel = FileOutputType.ITEM_ACQUISITION.level
        private val clueRares by lazy { TrailTier.entries.flatMap { TrailRewards.rare(it) }.map { it.id }.toSet() }
        private val containerClass = ItemContainer::class.java.name
        private val transfers = setOf("io.luna.game.model.item.Bank", "io.luna.game.model.item.Equipment")
        private val arrangements = setOf("load", "swap", "insert", "swapOperation", "clearSpaces")

        /**
         * Registers one observer after login while preserving every existing container listener.
         */
        fun attach(player: Player) {
            if (!player.isRealBot() || player.acquisitionLogAttached) return
            val observer = BotItemAcquisitionLog(player)
            player.inventory.addListener(observer)
            player.bank.addListener(observer)
            player.equipment.addListener(observer)
            player.acquisitionLogAttached = true
        }

        private fun field(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace('\r', ' ').replace('\n', ' ').replace('\t', ' ')
    }

    override fun onSingleUpdate(index: Int, items: ItemContainer, oldItem: Item?, newItem: Item?) {
        observe(items, oldItem, newItem)
    }

    override fun onBulkUpdate(index: Int, items: ItemContainer, oldItem: Item?, newItem: Item?) {
        observe(items, oldItem, newItem)
    }

    /**
     * Records positive slot deltas at their original call site. Ordinary container moves and refunds
     * are excluded using their native call paths, so they do not masquerade as new acquisitions.
     */
    private fun observe(items: ItemContainer, old: Item?, item: Item?) {
        if (item == null || bot.state != EntityState.ACTIVE || !bot.isBot) return
        val gained = item.amount - if (old?.id == item.id) old.amount else 0
        if (gained <= 0 || item.id == 995) return
        try {
            val base = item.itemDef.unnotedId.orElse(item.id)
            if (EquipmentDefinition.ALL.get(base).isEmpty && item.itemDef.value < 10_000 && base !in clueRares) return
            if (!acquisitionLogger.isEnabled(acquisitionLevel)) return
            val origin = origin() ?: return
            val source = when (origin.kind) {
                TRAIL -> "TREASURE_TRAIL"
                TRADE -> {
                    val partner = bot.overlays.getOverlay(ConfirmTradeInterface::class.java)?.offer?.other?.username
                    "TRADE partner=${partner ?: "unknown"}"
                }
                PICKUP -> pickupSource(item.id)
                SHOP -> {
                    val shop = bot.overlays.getOverlay(ShopInterface::class.java)?.shop?.name
                    "SHOP name=${shop ?: "unknown"}"
                }
                else -> "${origin.caller.className}.${origin.caller.methodName}"
            }
            val destination = when (items) { bot.inventory -> "inventory"; bot.bank -> "bank"; else -> "equipment" }
            val tile = bot.position
            acquisitionLogger.log(acquisitionLevel,
                "bot=\"{}\" item=\"{}\" id={} amount={} source=\"{}\" caller=\"{}\" destination={} position={},{},{}",
                field(bot.username), field(item.itemDef.name), item.id, gained, field(source), field(origin.caller.toString()),
                destination, tile.x, tile.y, tile.z)
        } catch (failure: RuntimeException) {
            logger.warn("Could not record an item acquisition for {}.", bot.username, failure)
        }
    }

    /**
     * Retains only the selected caller and source category from a bounded stack walk.
     *
     * @author lare96
     */
    private data class Origin(val caller: StackTraceElement, val kind: Int)

    /**
     * Stops immediately on excluded moves and reads each remaining frame once. Only the final caller
     * is converted to a stack-trace element; unused frames need no file or line-number materialization.
     */
    private fun origin(): Origin? = walker.walk { stream ->
        val frames = stream.iterator()
        // This new helper is outside the original observe() frame window.
        if (frames.hasNext()) frames.next()
        var caller: StackWalker.StackFrame? = null
        var fallback: StackWalker.StackFrame? = null
        var kind = 0
        var visited = 0
        while (visited++ < 32 && frames.hasNext()) {
            val frame = frames.next()
            val name = frame.className
            if (name in transfers ||
                name == containerClass && frame.methodName in arrangements ||
                name == "engine.trade.OfferTradeInterface" && frame.methodName == "onClose" ||
                name == "game.content.partyRoom.dropParty.DropPartyOption" && frame.methodName == "returnOffers") {
                return@walk null
            }
            if (caller == null && (name.startsWith("game.") || name.startsWith("engine.")) &&
                !name.startsWith(BotItemAcquisitionLog::class.java.name)) caller = frame
            if (fallback == null && name.startsWith("io.luna.game.") && name != containerClass) fallback = frame
            val category = when {
                name == "game.content.treasureTrails.TreasureTrails" -> TRAIL
                name == ConfirmTradeInterface::class.java.name -> TRADE
                name.startsWith("engine.widget.PickupItem") -> PICKUP
                name == Shop::class.java.name && frame.methodName == "buy" -> SHOP
                else -> 0
            }
            if (category != 0 && (kind == 0 || category < kind)) kind = category
        }
        (caller ?: fallback)?.let { Origin(it.toStackTraceElement(), kind) }
    }

    /**
     * Reads the active pickup target without modifying ground items or claiming unretained provenance.
     */
    private fun pickupSource(id: Int): String {
        val target = bot.actions.first(InteractionAction::class.java)?.target as? GroundItem
        val origin = (target?.takeIf { it.id == id } as? DeathGroundItem)?.origin
        return when (origin) {
            is Npc -> "GROUND_PICKUP npc=${origin.id} name=${origin.def().name}"
            is Player -> "GROUND_PICKUP player_death=${origin.username}"
            else -> "GROUND_PICKUP origin=unknown"
        }
    }
}
