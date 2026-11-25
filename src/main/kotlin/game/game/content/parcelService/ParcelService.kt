package game.content.parcelService

import api.predef.*
import api.predef.ext.*
import game.content.quests.Quest
import game.content.quests.QuestGates
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.InventoryOverlayInterface
import io.luna.game.model.mob.varp.Varbit

/**
 * Rionasta's Tai Bwo Wannai Parcel Service, which sends inventory items to the bank for 10 trading sticks each.
 *
 * @author TheLining
 */
object ParcelService {

    /**
     * Rionasta's spawned form, which shows him only once his varbit is set.
     */
    const val RIONASTA = 2531

    /**
     * The visible Rionasta the root shows when [VISIBLE] is set, used for his chathead.
     */
    const val RIONASTA_SHOWN = 2533

    /**
     * Which Rionasta form the client shows.
     */
    const val VISIBILITY_VARBIT = 901

    /**
     * Shows Rionasta without the trading sticks he gives out in the Clean-Up.
     */
    const val VISIBLE = 2

    const val INTERFACE = 14187
    const val GRID = 14203

    /**
     * The overlay that hides the inventory while the service is open, as the deposit box does.
     */
    const val SIDEBAR = 197

    const val STICKS = 6306
    const val FEE = 10

    private val currency = setOf(995, 617, 6306, 6529, 1464, 2996, 4067, 4278, 6183, 619, 620, 7774, 7775, 7776)
    private val rum = setOf(431, 3164, 3165)

    /**
     * Whether Rionasta runs the service for [plr]. He moves to his hut after Jungle Potion.
     */
    fun available(plr: Player) = QuestGates.passes(plr, Quest.JUNGLE_POTION)

    /**
     * Shows Rionasta to [plr] when the service is open to them.
     */
    fun show(plr: Player) {
        if (available(plr)) {
            plr.sendVarbit(Varbit(VISIBILITY_VARBIT, VISIBLE))
        }
    }

    fun talk(plr: Player) {
        if (!available(plr)) return
        plr.newDialogue().options(
            "What do you do here?", { whatDoYouDo(it) },
            "Ok, thanks.", { it.newDialogue().player("Ok, thanks.").open() }).open()
    }

    private fun whatDoYouDo(plr: Player) {
        plr.newDialogue()
            .player("What do you do here?")
            .npc(RIONASTA_SHOWN, "Well, it's funny you should ask that actually, I run the local parcel service to the mainland.")
            .player("Parcel service?")
            .npc(RIONASTA_SHOWN, "Yes, we sometimes get asked to send items back to the mainland. We can send items to individuals' bank accounts, for a small fee of course.")
            .options("How much does it cost?", { cost(it) },
                     "Can I send a parcel please?", { sendParcel(it) },
                     "Ok, thanks.", { it.newDialogue().player("Ok, thanks.").open() }).open()
    }

    private fun cost(plr: Player) {
        plr.newDialogue()
            .player("How much does it cost?")
            .npc(RIONASTA_SHOWN, "It costs 10 trading sticks per item, unfortunately, even stackable items cost the same.")
            .options("What are trading sticks and how do I get some?", { tradingSticks(it) },
                     "Can I send a parcel please?", { sendParcel(it) },
                     "Ok, thanks.", { it.newDialogue().player("Ok, thanks.").open() }).open()
    }

    private fun tradingSticks(plr: Player) {
        plr.newDialogue()
            .player("What are trading sticks and how do I get some?")
            .npc(RIONASTA_SHOWN, "Trading sticks are our local currency, you could perhaps earn some trading sticks by doing some work around the village.")
            .npc(RIONASTA_SHOWN, "Have a chat with Murcaily, I think he was looking for someone to help him, he works near the hardwood grove to the east of Trufitus' hut.")
            .then { cost(it) }.open()
    }

    private fun sendParcel(plr: Player) {
        val builder = plr.newDialogue().player("Can I send a parcel please?")
        if (plr.inventory.computeAmountForId(STICKS) < FEE) {
            builder.npc(RIONASTA_SHOWN, "It costs at least 10 trading sticks to use this service. Sorry but you don't have enough.")
                .options("What are trading sticks and how do I get some?", { tradingSticks(it) },
                         "Ok, thanks.", { it.newDialogue().player("Ok, thanks.").open() }).open()
            return
        }
        builder.npc(RIONASTA_SHOWN, "Of course...which items would you like to send to your bank?")
            .then { it.overlays.open(ParcelInterface()) }.open()
    }

    /**
     * Sends up to [amount] of the item in inventory slot [index] to the bank, as many as [plr]'s sticks pay for.
     */
    fun pack(plr: Player, index: Int, amount: Int) {
        if (ParcelInterface::class !in plr.overlays || amount < 1) return
        val item = plr.inventory[index] ?: return
        val id = item.itemDef.unnotedId.orElse(item.id)
        when (id) {
            in currency -> {
                plr.sendMessage("You cannot send any form of currency through the parcel service.")
                return
            }
            in rum -> {
                plr.sendMessage("The villager refuses to send this through the parcel system.")
                return
            }
        }
        val affordable = plr.inventory.computeAmountForId(STICKS) / FEE
        if (affordable < 1) {
            plr.sendMessage("It costs at least 10 trading sticks to use this service.")
            return
        }
        val before = plr.inventory.computeAmountForId(item.id)
        if (!plr.bank.deposit(index, minOf(amount, affordable))) return
        val sent = before - plr.inventory.computeAmountForId(item.id)
        if (sent > 0) {
            plr.inventory.remove(Item(STICKS, sent * FEE))
        }
    }

    fun value(plr: Player, index: Int) {
        if (ParcelInterface::class !in plr.overlays || plr.inventory[index] == null) return
        plr.sendMessage("It costs 10 trading sticks per item to send items to your bank.")
    }

    /**
     * The parcel window, listing the inventory in its own grid.
     *
     * @author TheLining
     */
    class ParcelInterface : InventoryOverlayInterface(INTERFACE, SIDEBAR) {

        override fun onOpen(plr: Player) {
            plr.inventory.setSecondaryWidget(GRID)
            plr.inventory.updateSecondaryWidget(plr)
        }

        override fun onClose(plr: Player) {
            plr.inventory.clearSecondaryWidget()
        }
    }
}
