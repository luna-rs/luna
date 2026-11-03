package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.waitFor
import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import game.content.partyRoom.DancingDrunksOption
import game.content.partyRoom.FightingWomenOption
import game.content.partyRoom.PartyRoom
import game.content.partyRoom.PartyRoomOption
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Occasional paid entertainment before social or minigame activities. Enthusiastic, outgoing bots may spend a
 * small confidence/intelligence-based fraction of their owned coins on the native dancing or fighting show.
 * The shared cooldown survives restarts; an attempted purchase is never repeated after an interrupted script.
 *
 * @author lare96
 */
object PartyRoomEntertainment {
    private var Bot.nextPartyEntertainment by Attr.long().persist("next_party_entertainment")

    /**
     * Real spendable coin stock and a one-to-five-percent discretionary cap, without valuing equipment as cash.
     */
    internal fun affordable(bot: Bot): List<PartyRoomOption> {
        val coins = bot.bank.computeAmountForId(995).toLong() + bot.inventory.computeAmountForId(995)
        val share = 0.01 + 0.04 * bot.personality.confidence.coerceIn(0.0, 1.0) *
            (1.0 - bot.personality.intelligence.coerceIn(0.0, 1.0) / 2)
        return listOf(DancingDrunksOption, FightingWomenOption).filter { it.cost <= coins * share }
    }

    /**
     * Reserves one consideration for two hours. Only genuinely owned coins fund the selected native service;
     * unrelated party hosts and locked/busy rooms retain priority and are never displaced.
     */
    fun select(bot: Bot): BotScript? {
        val now = System.currentTimeMillis()
        if (now < bot.nextPartyEntertainment || PartyRoom.locked || PartyRoom.option != null || bot.rights > RIGHTS_MOD ||
            !(bot.preferences.likesActivity(BotActivity.MINIGAMES) || bot.preferences.likesActivity(BotActivity.SOCIALIZING)) ||
            !(bot.personality.isKind || bot.personality.isDumb || bot.personality.confidence >= 0.7)) return null
        val options = affordable(bot)
        if (options.isEmpty()) return null
        bot.nextPartyEntertainment = now + 2 * 60 * 60_000L
        if (!rand(0.10 + bot.personality.confidence.coerceIn(0.0, 1.0) * 0.25)) return null
        val selected = options.random()
        return object : DynamicBotScript(bot) {
            private var attempted = false
            private var ownedOptions: OptionDialogue? = null
            override suspend fun run(): Boolean {
                if (attempted) return true
                withTimeoutOrNull(120.seconds) {
                    if (!isActionSafe() || PartyRoom.locked || PartyRoom.option != null || selected !in affordable(bot) ||
                        !handler.banking.travelToBankDepositAll() || !isActionSafe() || selected !in affordable(bot) ||
                        !handler.banking.withdraw(Item(995, selected.cost)) || !handler.widgets.clickCloseInterface() ||
                        !handler.webWalkTo(SubZone.PARTY_ROOM)) return@withTimeoutOrNull
                    if (!isActionSafe() || PartyRoom.locked || PartyRoom.option != null || selected !in affordable(bot))
                        return@withTimeoutOrNull
                    val lever = world.locator.findObjects(SubZone.PARTY_ROOM.inside, 20) { it.id == 2416 }
                        .firstOrNull() ?: return@withTimeoutOrNull
                    if (!handler.interactions.interact(1, lever) { isActionSafe() && PartyRoom.option == null } ||
                        !waitFor(5.seconds) { bot.overlays.getOverlay(OptionDialogue::class.java) != null })
                        return@withTimeoutOrNull
                    val page = bot.overlays.getOverlay(OptionDialogue::class.java) ?: return@withTimeoutOrNull
                    if (!isActionSafe() || selected.description !in page.options || PartyRoom.option != null ||
                        !bot.inventory.contains(Item(995, selected.cost))) return@withTimeoutOrNull
                    ownedOptions = page
                    attempted = true
                    val before = bot.inventory.computeAmountForId(995)
                    if (!handler.widgets.clickDialogueOption(selected.description) || !waitFor(5.seconds) {
                            PartyRoom.option === selected && bot.inventory.computeAmountForId(995) == before - selected.cost
                        }) return@withTimeoutOrNull
                    waitFor(45.seconds) { PartyRoom.option !== selected || !isActionSafe() }
                }
                return true
            }

            override suspend fun finish() {
                if (ownedOptions != null && bot.overlays.getOverlay(OptionDialogue::class.java) === ownedOptions) {
                    bot.overlays.closeWindows()
                    bot.resetDialogues()
                }
            }
        }
    }
}
