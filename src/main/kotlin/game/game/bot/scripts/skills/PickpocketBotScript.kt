package game.bot.scripts.skills

import api.bot.BotScriptData
import api.bot.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.skill.SkillingBotScript
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import game.skill.thieving.pickpocketNpc.ThievingNpcType
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Trains Thieving by pickpocketing configured NPC types inside selected zones.
 *
 * The script searches for nearby visible NPCs whose definitions expose a `Pickpocket` action and whose names match
 * one of the configured [ThievingNpcType] aliases. When a valid target is found, the bot repeatedly uses the
 * pickpocket interaction option on that NPC.
 *
 * The script also performs basic food management. If the bot becomes nervous about its current hitpoints, it will
 * attempt to eat food from its inventory. If no food is available, the script forces a banking trip and withdraws
 * more food before continuing.
 *
 * @property bot The bot running this script.
 * @property npcs The thieving NPC types this script is allowed to pickpocket.
 * @property duration The maximum amount of time the bot should spend running this script.
 * @property zones The subzones where the bot may train.
 *
 * @author lare96
 */
class PickpocketBotScript(
    bot: Bot,
    val npcs: Set<ThievingNpcType>,
    duration: Duration,
    zones: MutableList<SubZone>
) : SkillingBotScript<Npc>(bot, duration, zones, bot.thieving) {

    companion object {

        /**
         * Persistent script data for [PickpocketBotScript].
         *
         * This stores the normal zoned-script settings inherited from [ZonedBotScriptData], along with the set of
         * [ThievingNpcType] values that the bot is allowed to pickpocket.
         */
        class PickpocketData : ZonedBotScriptData() {

            /**
             * The NPC types that should be restored when this script is reconstructed from saved data.
             */
            var npcs = emptySet<ThievingNpcType>()

            override fun load(data: JsonObject) {
                super.load(data)
                loadEnumSet("npcs", data) { ThievingNpcType.valueOf(it) }
            }

            override fun save(data: JsonObject) {
                super.save(data)
                saveEnumSet("npcs", data, npcs)
            }
        }
    }

    /**
     * Creates a [PickpocketBotScript] from persistent script data.
     *
     * @param bot The bot running this script.
     * @param data The saved pickpocket script settings.
     */
    constructor(bot: Bot, data: PickpocketData) : this(bot, data.npcs, data.duration, data.zones)

    /**
     * The normalized set of NPC names accepted as valid pickpocket targets.
     *
     * Each [ThievingNpcType] may expose multiple possible NPC names. This set flattens those aliases into one lookup
     * table so target filtering can quickly check whether a visible NPC belongs to the configured target pool.
     */
    private var npcNames = run {
        val names = HashSet<String>()
        for (type in npcs) {
            names.addAll(type.names)
        }
        names
    }

    /**
     * Executes one pickpocketing cycle while the bot is inside a valid training zone.
     *
     * If the bot is nervous about its hitpoints, it tries to eat any available food. If no food is available, the
     * script requests a banking trip. Otherwise, if a target NPC is already focused, the bot continues pickpocketing
     * that same NPC.
     *
     * @param searching Whether the parent script is currently searching for a new target.
     * @param focus The currently selected NPC target, or `null` if no target is focused.
     */
    override suspend fun onExecuteInZone(searching: Boolean, focus: Npc?) {
        if (bot.emotions.isNervousAboutHp && !handler.inventory.eatAnyFood()) {
            bot.log("No food in inventory, banking for more.")
            forceBanking = true
        } else if (focus != null) {
            handler.interactions.interact(2, focus)
        }
    }

    /**
     * Handles banking before or during a pickpocketing session.
     *
     * The bot attempts to fill roughly 25% of its remaining inventory space with any available food. If no suitable
     * food can be withdrawn, the script ends because pickpocketing without food may become unsafe for low-health bots.
     *
     * @param initial Whether this is the first banking pass for the script.
     */
    override suspend fun onBankItems(initial: Boolean) {
        val remainingSpace = bot.inventory.computeRemainingSize()
        if (!handler.banking.withdrawAnyFood((remainingSpace * 0.25).toInt())) {
            // TODO@0.5.0 Trigger the bot needing food? Maybe have something called an ItemRequestBotScript that will
            //  make a bot try and retrieve an item by any means. Shops, buying, skilling, etc. Need a universal system for this.
            bot.log("No food left, ending script.")
            terminate()
        }
    }

    override fun interactionOption(): Int = 2

    override fun snapshot(): BotScriptData {
        val data = PickpocketData()
        data.npcs = npcs
        data.duration = duration
        data.zones = zones
        return data
    }

    override fun find(searchRadius: Int): MutableCollection<Npc> {
        return world.locator.findNpcs(bot, searchRadius, true) {
            val def = it.def()
            def.actions.elementAtOrNull(2)?.equals("Pickpocket") == true && def.name in npcNames
        }
    }

    override fun levelRequired(): Int = npcs.maxOfOrNull { it.level } ?: 0
}