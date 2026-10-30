package game.content.lumbridgeGuide

import game.bot.scripts.combat.CombatBotScript
import game.bot.scripts.combat.PkBotScript
import game.player.QuestJournalInterface
import io.luna.game.model.EntityState
import io.luna.game.model.World
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import java.text.NumberFormat
import java.util.Locale

/**
 * A game-thread snapshot of the online bot population for the Lumbridge Guide's ledger.
 *
 * Reports retain values rather than live bot references and never load offline saves or change bot behaviour.
 *
 * @author lare96
 */
class BotStatistics private constructor(
    private val records: List<BotRecord>,
    val humansOnline: Int,
    registeredScripts: Set<String>
) {

    companion object {

        /**
         * The number of script entries on a journal page, leaving room for its explanation.
         */
        private const val SCRIPTS_PER_PAGE = 40

        /**
         * Copies online game state. Call from an NPC interaction or another game-thread callback.
         */
        fun capture(world: World): BotStatistics = capture(world.players,
            world.botManager.scriptManager.scriptMap.keys)

        /**
         * Copies a population and script registry without requiring persistence or network services.
         */
        internal fun capture(players: Iterable<Player>, registeredScripts: Set<String>): BotStatistics {
            val records = mutableListOf<BotRecord>()
            var humans = 0
            for (player in players) {
                if (player.state != EntityState.ACTIVE) {
                    continue
                }
                if (player !is Bot) {
                    humans++
                    continue
                }
                val scripts = player.scriptStack.scripts.map { it.javaClass.name }
                val levels = player.skills.map { it.staticLevel }
                records += BotRecord(
                    scripts = scripts.toSet(),
                    currentScript = scripts.firstOrNull(),
                    template = player.personality.type,
                    interests = BotActivity.values().filter { player.preferences.likesActivity(it) }.toSet(),
                    combatLevel = player.combatLevel,
                    totalLevel = levels.sum(),
                    masteredSkills = levels.count { it == 99 },
                    coins = player.inventory.computeAmountForId(995).toLong() +
                        player.bank.computeAmountForId(995).toLong(),
                    temporary = player.isTemporary
                )
            }
            return BotStatistics(records, humans, registeredScripts.toSet())
        }

        /**
         * Converts script class identifiers into short names suitable for the game interface.
         */
        private fun scriptName(type: String): String {
            val simple = type.substringAfterLast('.').substringBefore('$')
            return when (simple) {
                "NpcCombatScript" -> "NPC combat"
                "PkBotScript" -> "Player killing"
                "CombatBotScript" -> "Fighting or fleeing"
                "BuyFromStoreBotScript" -> "Buying from shops"
                "SellToGeneralStoreBotScript" -> "Selling to shops"
                "SaleBotScript" -> "Trading items"
                "HarvestBotScript" -> "Gathering ground supplies"
                "CutTreeBotScript" -> "Woodcutting"
                "MineBotScript" -> "Mining"
                "FishBotScript" -> "Fishing"
                "CraftRuneBotScript" -> "Runecrafting"
                "CraftArmorBotScript" -> "Crafting armour"
                "CutLogBotScript" -> "Fletching bows"
                "MakeArrowBotScript" -> "Making arrows"
                "SmithBarBotScript" -> "Smithing"
                "SmeltOreBotScript" -> "Smelting"
                "CookFoodBotScript" -> "Cooking"
                "FiremakingBotScript" -> "Firemaking"
                "IdentifyHerbBotScript" -> "Identifying herbs"
                "GrindIngredientBotScript" -> "Grinding ingredients"
                "MakeUnfPotionBotScript" -> "Making unfinished potions"
                "MakeBattlestaffBotScript" -> "Making battlestaves"
                "MakeSoftClayBotScript" -> "Softening clay"
                "MakeCrystalKeyBotScript" -> "Making crystal keys"
                "SpinFlaxBotScript" -> "Spinning flax"
                "StringBowBotScript" -> "Stringing bows"
                "StringJewelleryBotScript" -> "Stringing jewellery"
                "TanHideBotScript" -> "Tanning hides"
                "CutGemBotScript" -> "Cutting gems"
                "StealBotScript" -> "Stealing from stalls"
                "PickpocketBotScript" -> "Pickpocketing"
                "SearchBotScript" -> "Searching for supplies"
                "SplashBotScript" -> "Training magic by splashing"
                "AlchemyBotScript" -> "Alchemy"
                "LogoutBotScript" -> "Logging out"
                else -> if ('$' in type || simple.isBlank()) "Temporary action" else simple
                    .removeSuffix("BotScript").removeSuffix("Script")
                    .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").take(38)
            }
        }

        /**
         * Formats a count using the familiar English thousands separator.
         */
        private fun number(value: Long): String = NumberFormat.getIntegerInstance(Locale.ENGLISH).format(value)
    }

    /**
     * The copied values used by the reports.
     *
     * @author lare96
     */
    private data class BotRecord(
        val scripts: Set<String>,
        val currentScript: String?,
        val template: PersonalityTemplateType,
        val interests: Set<BotActivity>,
        val combatLevel: Int,
        val totalLevel: Int,
        val masteredSkills: Int,
        val coins: Long,
        val temporary: Boolean
    )

    /**
     * Registered scripts and observed runtime scripts, including common non-persistent combat tasks at zero.
     */
    private val scriptTypes = (registeredScripts + records.flatMap { it.scripts } +
        setOf(PkBotScript::class.java.name, CombatBotScript::class.java.name))
        .sortedWith(compareBy({ scriptName(it) }, { it }))

    /**
     * The online bot population used as the denominator for every percentage.
     */
    val onlineBots: Int = records.size

    /**
     * The number of pages in either script report.
     */
    val scriptPageCount: Int = ((scriptTypes.size + SCRIPTS_PER_PAGE - 1) / SCRIPTS_PER_PAGE).coerceAtLeast(1)

    /**
     * Formats a population count and its percentage, including an empty population.
     */
    private fun population(count: Int): String {
        val percentage = if (onlineBots == 0) 0.0 else count * 100.0 / onlineBots
        return "${number(count.toLong())} (${String.format(Locale.ENGLISH, "%.1f", percentage)}%)"
    }

    /**
     * Builds the general population and progression report.
     */
    fun overviewLines(): List<String> = buildList {
        add("@dbl@The Guide's bot census")
        add("A snapshot of adventurers online when opened.")
        add("Bots online: ${number(onlineBots.toLong())}")
        add("Human players online: ${number(humansOnline.toLong())}")
        add("Persistent bots: ${number(records.count { !it.temporary }.toLong())}")
        add("Temporary bots: ${number(records.count { it.temporary }.toLong())}")
        add("Bots with a current or queued task: ${population(records.count { it.scripts.isNotEmpty() })}")
        add("Bots awaiting another task: ${population(records.count { it.scripts.isEmpty() })}")
        if (onlineBots == 0) {
            add("")
            add("No bots are online just now. Try again later.")
            return@buildList
        }
        add("")
        add("@dbl@Levels and coin purses")
        add("Average combat level: ${String.format(Locale.ENGLISH, "%.1f", records.map { it.combatLevel }.average())}")
        add("Highest combat level: ${records.maxOf { it.combatLevel }}")
        add("Average total level: ${String.format(Locale.ENGLISH, "%.1f", records.map { it.totalLevel }.average())}")
        add("Highest total level: ${records.maxOf { it.totalLevel }}")
        add("Level 99 skills held: ${number(records.sumOf { it.masteredSkills.toLong() })}")
        add("Coins in inventories and banks: ${number(records.sumOf { it.coins })}")
        add("Coins only; equipment and other items are not valued.")
        add("")
        add("@dbl@Personality backgrounds")
        val names = mapOf(
            PersonalityTemplateType.NONE to "Individual",
            PersonalityTemplateType.SKILLER to "Skiller",
            PersonalityTemplateType.PKER to "PKer",
            PersonalityTemplateType.MERCHANT to "Merchant",
            PersonalityTemplateType.NO_LIFE to "Dedicated adventurer",
            PersonalityTemplateType.KID to "Young adventurer",
            PersonalityTemplateType.HERO to "Hero",
            PersonalityTemplateType.JACK_OF_ALL_TRADES to "Jack of all trades",
            PersonalityTemplateType.SCAMMER to "Scammer"
        )
        for ((type, name) in names) {
            add("$name: ${population(records.count { it.template == type })}")
        }
        add("")
        add("@dbl@Favourite pursuits")
        add("Strong interests, not current jobs; these may overlap.")
        val pursuits = mapOf(
            BotActivity.TRAINING_SKILLS to "Training skills",
            BotActivity.PROFIT_SKILLS to "Skilling for profit",
            BotActivity.TRAINING_COMBAT to "Training combat",
            BotActivity.PROFIT_COMBAT to "Hunting for profit",
            BotActivity.PKING to "Player killing",
            BotActivity.MERCHANTING to "Merchanting"
        )
        for ((activity, name) in pursuits) {
            add("$name: ${population(records.count { activity in it.interests })}")
        }
    }

    /**
     * Builds one page of script ownership or current-task counts.
     *
     * A bot counts once per script type even if that type occurs several times in its stack.
     */
    fun scriptLines(currentOnly: Boolean = false, page: Int = 0): List<String> = buildList {
        require(page in 0 until scriptPageCount) { "Invalid script report page: $page" }
        add("@dbl@${if (currentOnly) "Current or next tasks" else "Current and queued tasks"}")
        add("Online bots counted: ${number(onlineBots.toLong())}")
        add("Page ${page + 1} of $scriptPageCount")
        if (currentOnly) {
            add("The first task in each bot's queue; it may be paused.")
        } else {
            add("Each bot counts once per kind of task in its queue.")
            add("One bot may have several kinds; totals may exceed 100%.")
        }
        add("This shows present tasks, not everything a bot can do.")
        add("Registered tasks with no bots are included at 0%.")
        add("")
        for (type in scriptTypes.drop(page * SCRIPTS_PER_PAGE).take(SCRIPTS_PER_PAGE)) {
            val count = records.count { if (currentOnly) it.currentScript == type else type in it.scripts }
            add("${scriptName(type)}: ${population(count)}")
        }
        add("")
        add("No task queued: ${population(records.count { it.scripts.isEmpty() })}")
    }

    /**
     * Displays the population report using the quest journal.
     */
    fun openOverview(plr: Player) {
        openJournal(plr, "@dbl@Luna ~ Bot census", overviewLines())
    }

    /**
     * Displays a page of the selected script report using the quest journal.
     */
    fun openScripts(plr: Player, currentOnly: Boolean = false, page: Int = 0) {
        openJournal(plr, "@dbl@Luna ~ Bot tasks", scriptLines(currentOnly, page))
    }

    /**
     * Opens a bounded journal containing this snapshot's text.
     */
    private fun openJournal(plr: Player, title: String, lines: List<String>) {
        val journal = QuestJournalInterface(title)
        lines.forEach(journal::addLine)
        plr.overlays.open(journal)
    }
}
