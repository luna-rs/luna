package game.bot.scripts

import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import com.google.gson.JsonObject
import game.skill.fletching.openOyster.Oyster
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Opens owned reward containers through their native first inventory option.
 *
 * Owned oysters require an owned chisel. The native handler replaces one container with one reward,
 * so balanced bank batches need only reserve the reusable tool slot.
 * The shared inventory lifecycle handles banking, travel, interaction failures and saved sessions.
 * Containers and tools are never acquired for this activity; native handlers own all rewards.
 * Treasure Trail caskets and bird nests retain their existing scripts and reward rules.
 *
 * @param bot The bot opening its owned containers.
 * @property container The native container processed throughout this session.
 * @param duration Session length managed by the inherited lifecycle.
 * @param zones Existing stationary processing areas with banking support.
 * @author lare96
 */
class OpenContainerBotScript(
    bot: Bot,
    val container: Container,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    /**
     * Native one-slot containers and their reusable opening tools.
     *
     * @property id The unopened inventory item.
     * @property tools Tools retained by the player handler rather than consumed.
     * @author lare96
     */
    enum class Container(val id: Int, val tools: Set<Int> = emptySet()) {
        OYSTER(Oyster.ID, setOf(Oyster.CHISEL))
    }

    companion object {
        /**
         * Container identity and inherited session and retry state.
         *
         * @author lare96
         */
        class ContainerData : InventoryScriptData() {
            /**
             * Stable enum name identifying the native container handler.
             */
            var container = ""

            override fun load(data: JsonObject) {
                super.load(data)
                container = data.get("container")?.asString ?: ""
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("container", container)
            }
        }
    }

    /**
     * One owned container is consumed per successful native opening.
     */
    private val materials = listOf(Item(container.id))

    /**
     * Restores the selected container and inherited session state without resetting retry budgets.
     */
    constructor(bot: Bot, data: ContainerData) :
        this(bot, Container.valueOf(data.container), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /**
     * Requires a container and every native tool across the inventory and bank.
     */
    fun isEligible(): Boolean = bot.ownsProductionSupplies(materials, container.tools)

    /**
     * Withdraws owned containers up to capacity while reserving each reusable tool's slot.
     */
    fun bankBatch(): List<Item> = bot.productionBatch(materials, container.tools)

    override fun withdraw(): List<Item> = productionWithdraw(materials, container.tools, requestMissing = false)

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /**
     * Both handlers reuse the consumed container's slot, including when the inventory is otherwise full.
     */
    private fun hasMaterials(): Boolean = bot.inventory.containsAll(materials) &&
        container.tools.all { bot.inventory.contains(it) }

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials())) return true
        attemptProduction(container.id) { handler.inventory.clickItem(1, container.id) }
        return true
    }

    override fun snapshot(): ContainerData = ContainerData().also {
        it.container = container.name
        saveInventoryState(it)
    }
}
