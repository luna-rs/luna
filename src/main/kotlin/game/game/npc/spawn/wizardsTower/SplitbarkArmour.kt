package game.npc.spawn.wizardsTower

import api.predef.itemName
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption

/**
 * The armoured wizard's splitbark recipes in the Wizards' Tower.
 *
 * @author lare96
 */
enum class SplitbarkArmour(val product: Int, val pieces: Int, val coins: Int) {
    HELM(3385, 2, 6_000),
    BODY(3387, 4, 37_000),
    LEGS(3389, 3, 32_000),
    GAUNTLETS(3391, 1, 1_000),
    GREAVES(3393, 1, 1_000);

    fun optionText(): String = "${itemName(product)} (${"%,d".format(coins)} coins)"

    fun materials(): List<Item> = listOf(Item(BARK, pieces), Item(FINE_CLOTH, pieces))

    companion object {
        const val WIZARD = 1263
        const val BARK = 3239
        const val FINE_CLOTH = 3470
        private const val COINS = 995

        fun openMenu(player: Player) {
            player.newDialogue()
                .npc(WIZARD, "Bring me bark, fine cloth and some coins,",
                    "and I can make you a piece of splitbark armour.")
                .options(entries.map { piece -> DialogueOption(piece.optionText()) { piece.make(it) } })
                .open()
        }
    }

    private fun make(player: Player) {
        val inputs = materials() + Item(COINS, coins)
        if (!player.inventory.containsAll(inputs)) {
            player.newDialogue()
                .npc(WIZARD, "For that piece, I need $pieces bark, $pieces fine cloth",
                    "and ${"%,d".format(coins)} coins.")
                .open()
            return
        }
        // Even the smallest recipe frees two inventory slots before adding its single piece of armour.
        player.inventory.removeAll(inputs)
        player.inventory.add(Item(product))
        player.newDialogue().npc(WIZARD, "Here you go.").open()
    }
}
