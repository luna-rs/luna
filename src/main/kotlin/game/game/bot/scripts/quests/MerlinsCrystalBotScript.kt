package game.bot.scripts.quests

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.script.BotScript
import api.bot.zone.Zone
import game.content.merlinsCrystal.MerlinVoyage
import game.content.merlinsCrystal.MerlinsCrystal as Quest
import game.npc.spawn.catherby.BlackCandle
import game.npc.spawn.catherby.BlackCandle.wantsBlackCandle
import game.skill.magic.RuneRequirement
import game.skill.magic.teleportSpells.TeleportSpell
import io.luna.game.model.Position
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Frees Merlin with regular ship, dialogue, combat and inventory interactions.
 *
 * @author lare96
 */
class MerlinsCrystalBotScript(bot: Bot, private val data: QuestAttemptData = QuestAttemptData()) : BotScript(bot) {
    companion object {
        private val arthur = Position(2764, 3515)
        private val gawain = Position(2763, 3506)
        private val lancelot = Position(2759, 3515, 1)
        private val mordred = Position(2769, 3403, 2)
        private val lady = Position(2925, 3406)
        private val candleMaker = Position(2800, 3439)
        private val candleShopDoor = Position(2799, 3437)
        private val jewellerDoor = Position(3016, 3246)
        private const val TINDERBOX = 590
        private var Bot.merlinRetryAt by Attr.long().persist("merlins_crystal_bot_retry_at")

        fun eligible(bot: Bot): Boolean = Quest.stage(bot) < Quest.COMPLETE &&
            ActionSafety.NON_COMBAT.allows(bot) && !bot.overlays.hasWindow() &&
            bot.skill(Skill.HITPOINTS).staticLevel >= 30 && System.currentTimeMillis() >= bot.merlinRetryAt &&
            (Quest.stage(bot) >= Quest.SPIRIT_BOUND || bot.inventory.contains(Quest.BAT_BONES) &&
                (bot.inventory.contains(Quest.LIT_CANDLE) || bot.inventory.contains(TINDERBOX) &&
                    (bot.inventory.contains(BlackCandle.BLACK_CANDLE) || bot.inventory.contains(BlackCandle.BUCKET_OF_WAX)))) &&
            (Quest.stage(bot) > 0 || bot.position.isWithinDistance(arthur, 35))
    }

    private val actions = QuestBotActions(bot)
    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse = false
    var failure: String? = null
        private set

    override fun snapshot() = data

    override suspend fun run(): Boolean {
        if (Quest.stage(bot) == Quest.COMPLETE) return true
        val finished = withTimeoutOrNull(data.deadline - System.currentTimeMillis()) {
            while (actions.live() && Quest.stage(bot) < Quest.COMPLETE) {
                val stage = Quest.stage(bot)
                bot.log("Merlin's Crystal: stage $stage.")
                if (!step(stage)) {
                    delay(600)
                    if (Quest.stage(bot) == stage && !step(stage)) return@withTimeoutOrNull false
                }
            }
            Quest.stage(bot) == Quest.COMPLETE
        } == true
        if (!finished && Quest.stage(bot) != Quest.COMPLETE) {
            failure = "Could not finish stage ${Quest.stage(bot)} at ${bot.position}."
            bot.log("Merlin's Crystal: $failure")
            bot.merlinRetryAt = System.currentTimeMillis() + 30 * 60_000L
        }
        return true
    }

    private suspend fun step(stage: Int): Boolean = when (stage) {
        0 -> actions.talk(Quest.ARTHUR, arthur, Quest.HELP) { Quest.stage(bot) > stage }
        Quest.STARTED -> actions.talk(Quest.GAWAIN, gawain, Quest.ASK_GAWAIN) { Quest.stage(bot) > stage }
        Quest.SPOKE_GAWAIN -> actions.talk(Quest.LANCELOT, lancelot, Quest.ASK_LANCELOT) { Quest.stage(bot) > stage }
        Quest.SPOKE_LANCELOT -> persuadeMorgan()
        Quest.SPOKE_MORGAN -> bindThrantax()
        Quest.SPIRIT_BOUND -> {
            val crystal = actions.objectAt(Quest.CRYSTAL, Quest.crystalTile)
            actions.has(Quest.EXCALIBUR) && crystal != null &&
                handler.inventory.useItem(Quest.EXCALIBUR).onObject(crystal) && actions.await { Quest.stage(bot) > stage }
        }
        Quest.FREED_MERLIN -> actions.talk(Quest.ARTHUR, arthur) { Quest.stage(bot) > stage }
        else -> false
    }

    private fun atKeep() = bot.x in 2754..2785 && bot.y in 3384..3413

    private suspend fun voyage(toKeep: Boolean): Boolean {
        if (!toKeep && !actions.walk(MerlinVoyage.keepArrival)) return false
        val travel = if (toKeep) actions.click(MerlinVoyage.CATHERBY_CRATE, MerlinVoyage.catherbyCrate, 2)
                     else actions.click(MerlinVoyage.KEEP_CRATE, MerlinVoyage.keepCrate, 2)
        if (!travel || !actions.await { optionAvailable(Quest.HIDE_IN_CRATE) } ||
            !handler.widgets.clickDialogueOption(Quest.HIDE_IN_CRATE)) return false
        return actions.await(20_000) { atKeep() == toKeep }
    }

    private suspend fun persuadeMorgan(): Boolean {
        if (!atKeep() && !voyage(toKeep = true)) return false
        if (actions.npc(Quest.MORGAN, Quest.morganTile) == null &&
            !actions.fight(Quest.MORDRED, mordred) { actions.npc(Quest.MORGAN, Quest.morganTile) != null }) return false
        if (actions.await(4_000) { optionAvailable(Quest.ASK_MORGAN) } &&
            handler.widgets.clickDialogueOption(Quest.ASK_MORGAN) &&
            actions.await { Quest.stage(bot) >= Quest.SPOKE_MORGAN }) {
            return true
        }
        return actions.talk(Quest.MORGAN, Quest.morganTile, Quest.ASK_MORGAN) { Quest.stage(bot) >= Quest.SPOKE_MORGAN }
    }

    private suspend fun bindThrantax(): Boolean {
        if (atKeep() && !voyage(toKeep = false)) return false
        if (!prepareCandle()) return false
        if (bot.z == 0 && bot.x in 2797..2802 && bot.y in 3437..3441 &&
            !actions.walk(candleShopDoor)) return false
        if (!prepareSword()) return false
        if (!Quest.knowsWords(bot)) {
            if (!actions.click(Quest.ALTAR, Quest.altarTile, 2) || !actions.await { Quest.knowsWords(bot) }) return false
            handler.widgets.clickCloseInterface()
        }
        if (!reachSymbol() || !handler.inventory.dropItem(Quest.BAT_BONES)) return false
        return actions.await { optionAvailable(Quest.WORDS) } && handler.widgets.clickDialogueOption(Quest.WORDS) &&
            actions.await { Quest.stage(bot) >= Quest.SPIRIT_BOUND }
    }

    private suspend fun reachSymbol(): Boolean {
        val spell = TeleportSpell.CAMELOT
        if (!bot.position.isWithinDistance(Quest.symbol, 50) && bot.skill(Skill.MAGIC).level >= spell.level &&
            spell.requirements.filterIsInstance<RuneRequirement>().all { actions.has(it.rune.id, it.amount) } &&
            bot.output.clickButton(spell.button) &&
            actions.await { bot.position.isWithinDistance(spell.destination, 4) }) return actions.walk(Quest.symbol)
        if (bot.z == 0 && bot.x > Zone.FALADOR.anchor.x + 20 && !actions.walk(Zone.FALADOR.anchor, 6)) return false
        if (bot.z == 0 && bot.x > Zone.TAVERLY.anchor.x + 20 && !actions.walk(Zone.TAVERLY.anchor, 6)) return false
        return actions.walk(Quest.symbol)
    }

    private suspend fun prepareCandle(): Boolean {
        if (actions.has(Quest.LIT_CANDLE)) return true
        if (!actions.has(BlackCandle.BLACK_CANDLE)) {
            if (!bot.wantsBlackCandle && !actions.talk(BlackCandle.CANDLE_MAKER, candleMaker,
                    "Have you got any black candles?") { bot.wantsBlackCandle }) return false
            val maker = actions.npc(BlackCandle.CANDLE_MAKER, candleMaker) ?: return false
            if (!handler.interactions.interact(1, maker, actions::live) ||
                !actions.await(20_000) { actions.has(BlackCandle.BLACK_CANDLE) }) return false
            handler.widgets.clickCloseInterface()
        }
        return handler.inventory.useItem(TINDERBOX).onItem(BlackCandle.BLACK_CANDLE) &&
            actions.await { actions.has(Quest.LIT_CANDLE) }
    }

    private suspend fun prepareSword(): Boolean {
        if (actions.has(Quest.EXCALIBUR)) return true
        if (!actions.has(Quest.BREAD)) {
            return actions.talk(Quest.LADY, lady, "I seek the sword Excalibur.") { actions.has(Quest.EXCALIBUR) }
        }
        if (!Quest.trialOffered(bot) && !actions.talk(Quest.LADY, lady, Quest.TAKE_TRIAL) {
                Quest.trialOffered(bot)
            }) return false
        if (actions.npc(Quest.BEGGAR, Quest.beggarTile) == null &&
            (!actions.click(59, jewellerDoor) || !actions.await { actions.npc(Quest.BEGGAR, Quest.beggarTile) != null })) {
            return false
        }
        return actions.talk(Quest.BEGGAR, Quest.beggarTile, Quest.GIVE_BREAD) { actions.has(Quest.EXCALIBUR) }
    }

    private fun optionAvailable(option: String) =
        bot.overlays.getOverlay(OptionDialogue::class.java)?.options?.contains(option) == true
}
