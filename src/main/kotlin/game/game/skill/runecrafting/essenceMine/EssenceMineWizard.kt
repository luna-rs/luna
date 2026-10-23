package game.skill.runecrafting.essenceMine

import io.luna.game.model.Position

/**
 * The wizards who teleport players to the Rune Essence mine.
 *
 * @property npcId The wizard's npc id.
 * @property exit Where the mine's portals return players this wizard sent.
 * @property animates Whether the wizard plays the casting animation. Brimstail is a gnome, so he doesn't.
 * @author TheLining
 */
enum class EssenceMineWizard(val npcId: Int, val exit: Position, val animates: Boolean = true) {
    AUBURY(npcId = 553,
           exit = Position(3253, 3401)),
    SEDRIDOR(npcId = 300,
             exit = Position(3106, 9572)),
    WIZARD_DISTENTOR(npcId = 462,
                     exit = Position(2591, 3086)),
    WIZARD_CROMPERTY(npcId = 844,
                     exit = Position(2684, 3322)),
    BRIMSTAIL(npcId = 171,
              exit = Position(2390, 9810),
              animates = false);

    companion object {

        /**
         * The wizards, keyed by npc id.
         */
        private val NPC_TO_WIZARD = values().associateBy { it.npcId }

        /**
         * The wizard with [npcId], or `null` if there's none.
         */
        fun forNpc(npcId: Int): EssenceMineWizard? = NPC_TO_WIZARD[npcId]
    }
}
