package game.content.dragonSlayer

import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.PersistentVarp.DRAGON_SLAYER_ORACLE
import io.luna.game.model.mob.varp.PersistentVarp.DRAGON_SLAYER_SHIELD
import io.luna.game.model.mob.varp.PersistentVarp.DRAGON_SLAYER_WORMBRAIN

/**
 * Offers the quest through native NPC conversations while preserving the normal armour shop.
 *
 * @author lare96
 */
object DragonSlayerDialogue {
    const val DUKE = 741
    const val ORACLE = 746
    const val MAZE_KEY = 1542
    const val START = "I'm looking for a rune platebody."
    const val CHALLENGE = "Tell me about the dragon."
    const val MAZE = "How do I find Melzar's map piece?"
    const val ORACLE_HINT = "Who has the piece in the Dwarven Mine?"
    const val WORMBRAIN_HINT = "Who stole the last piece?"
    const val SHIELD_HINT = "Where can I get a shield?"
    const val ORACLE_MAP = "I'm looking for the Crandor map piece."
    const val SHIELD = "May I have an anti-dragon shield?"

    fun guildMaster(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(npc.id, "Welcome to the Champions' Guild.")
        if (DragonSlayer.stage(player) == 0) {
            dialogue.options(START, { p ->
                if (DragonSlayer.nearby(p, npc) && DragonSlayer.stage(p) == 0) {
                    DragonSlayer.advance(p, DragonSlayer.GUILD_MASTER_SPOKEN_TO)
                    p.newDialogue().npc(npc.id, "Oziach can help you. Look for his hut west of Edgeville.").open()
                }
            }, "Good day.", { })
        }
        dialogue.open()
    }

    fun oziach(player: Player, npc: Npc, shop: (Player) -> Unit) {
        when {
            DragonSlayer.stage(player) == DragonSlayer.GUILD_MASTER_SPOKEN_TO ->
                player.newDialogue().npc(npc.id, "The Guildmaster sent you? I have a challenge for you.")
                    .options(CHALLENGE, {
                        if (DragonSlayer.nearby(it, npc) && DragonSlayer.stage(it) == DragonSlayer.GUILD_MASTER_SPOKEN_TO) {
                            DragonSlayer.advance(it, DragonSlayer.OZIACH_SPOKEN_TO)
                            it.newDialogue().npc(npc.id, "Defeat Elvarg on Crandor if you want to prove yourself.",
                                "Her fire is deadly. Bring an anti-dragon shield.")
                                .npc(npc.id, "The map to Crandor was torn into three pieces.",
                                    "You'll need a ship and someone to captain it.").open()
                        }
                    }, "Show me your armour stock.", shop).open()
            DragonSlayer.collectingMap(player) -> player.newDialogue().npc(npc.id, "What are you looking for?")
                .options(MAZE, { p ->
                    if (DragonSlayer.nearby(p, npc) && DragonSlayer.collectingMap(p)) {
                        val hasKey = p.inventory.contains(MAZE_KEY) || p.bank.contains(MAZE_KEY)
                        val given = !hasKey && p.inventory.add(Item(MAZE_KEY))
                        p.newDialogue().npc(npc.id, "You'll find Melzar's Maze north-west of Rimmington.", when {
                            given -> "Take this key for the entrance."
                            hasKey -> "You already have the entrance key."
                            else -> "Clear some space for the entrance key first."
                        }).open()
                    }
                }, ORACLE_HINT, { hint(it, npc, DRAGON_SLAYER_ORACLE, "Ask the Oracle on Ice Mountain about the mine.") },
                    WORMBRAIN_HINT, { hint(it, npc, DRAGON_SLAYER_WORMBRAIN, "Try Wormbrain in the Port Sarim jail.") },
                    SHIELD_HINT, { hint(it, npc, DRAGON_SLAYER_SHIELD, "The Duke of Lumbridge may have a spare shield.") },
                    "Show me your armour stock.", shop).open()
            else -> shop(player)
        }
    }

    private fun hint(player: Player, npc: Npc, flag: PersistentVarp, text: String) {
        if (!DragonSlayer.nearby(player, npc) || !DragonSlayer.collectingMap(player)) return
        if (player.varpManager.getValue(flag) == 0) player.varpManager.setAndSendValue(flag, 1)
        player.newDialogue().npc(npc.id, text).open()
    }

    fun oracle(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(npc.id, "What knowledge do you seek?")
        if (DragonSlayer.collectingMap(player) && player.varpManager.getValue(DRAGON_SLAYER_ORACLE) >= 1) {
            dialogue.options(ORACLE_MAP, { p ->
                if (DragonSlayer.nearby(p, npc) && DragonSlayer.collectingMap(p)) {
                    if (p.varpManager.getValue(DRAGON_SLAYER_ORACLE) == 1) {
                        p.varpManager.setAndSendValue(DRAGON_SLAYER_ORACLE, 2)
                    }
                    p.newDialogue().npc(npc.id, "A chest in the Dwarven Mine holds the piece.",
                        "The door needs four offerings from you first.")
                        .npc(npc.id, "A Wizard's mind bomb, silk, a lobster pot and", "a bowl that has yet to see the kiln.").open()
                }
            }, "I'll be on my way.", { })
        }
        dialogue.open()
    }

    fun duke(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(npc.id, "Welcome to Lumbridge Castle.")
        if (player.varpManager.getValue(DRAGON_SLAYER_SHIELD) == 1 || DragonSlayer.stage(player) == DragonSlayer.COMPLETE) {
            dialogue.options(SHIELD, { p ->
                if (DragonSlayer.nearby(p, npc)) {
                    val shield = DragonSlayer.ANTI_DRAGON_SHIELD
                    val text = when {
                        p.inventory.contains(shield) || p.equipment.contains(shield) || p.bank.contains(shield) ->
                            "You already have a shield."
                        p.inventory.add(Item(shield)) -> "Here is a spare shield from our armoury."
                        else -> "You'll need room to carry it."
                    }
                    p.newDialogue().npc(npc.id, text).open()
                }
            }, "Good day.", { })
        }
        dialogue.open()
    }
}
