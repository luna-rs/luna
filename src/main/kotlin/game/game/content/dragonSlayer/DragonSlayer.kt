/*
 * Mechanics adapted from LostCityRS/Content scripts/quests/quest_dragon,
 * revision 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 */
package game.content.dragonSlayer

import api.predef.ext.playJingle
import api.quest.QuestProgress
import game.player.Jingles
import game.player.QuestJournalInterface
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import kotlin.random.Random

/**
 * Tracks the Dragon Slayer story and credits its rewards through the native Elvarg death hook.
 *
 * @author lare96
 */
object DragonSlayer {
    const val GUILD_MASTER = 198
    const val ELVARG = 742
    const val OZIACH = 747
    const val MAZE_MAP_PART = 1535
    const val WORMBRAIN_MAP_PART = 1536
    const val ORACLE_MAP_PART = 1537
    const val CRANDOR_MAP = 1538
    const val ANTI_DRAGON_SHIELD = 1540
    const val JOURNAL_BUTTON = 7383

    const val GUILD_MASTER_SPOKEN_TO = 1
    const val OZIACH_SPOKEN_TO = 2
    const val SHIP_BOUGHT = 3
    const val SHIP_REPAIRED = 7
    const val MAP_GIVEN_TO_NED = 8
    const val SAILED_TO_CRANDOR = 9
    const val COMPLETE = 10

    private const val REWARD_XP = 18_650
    private val progress = QuestProgress(PersistentVarp.DRAGON_SLAYER, COMPLETE, 2)
    private val elvargLairExit = Position(2846, 9636)
    val mapParts = listOf(MAZE_MAP_PART, WORMBRAIN_MAP_PART, ORACLE_MAP_PART)

    fun stage(player: Player): Int = progress.stage(player)

    fun collectingMap(player: Player): Boolean = stage(player) in OZIACH_SPOKEN_TO until MAP_GIVEN_TO_NED

    internal fun advance(player: Player, next: Int) {
        if (progress.advance(player, next)) refresh(player)
    }

    fun refresh(player: Player) {
        val colour = when (stage(player)) {
            0 -> 0xff0000
            COMPLETE -> 0x00ff00
            else -> 0xffff00
        }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    /**
     * Combines the three distinct carried pieces, including when the inventory is full at the start.
     */
    internal fun assembleMap(player: Player) {
        val inventory = player.inventory
        if (!collectingMap(player)) return
        if (!inventory.containsAll(*mapParts.toIntArray())) {
            player.sendMessage("You need all three pieces to assemble the Crandor map.")
            return
        }
        val pieces = mapParts.map { Item(it, inventory.computeAmountForId(it)) }
        inventory.updateAll(listOf(Item(CRANDOR_MAP)), pieces)
        player.newDialogue().text("You fit the three pieces together.", "The map shows a route through the reefs to Crandor.").open()
    }

    /**
     * Requires the native kill credit and the final hit. Public combat with Elvarg awards quest progress only
     * to a player who has reached the encounter in the story.
     */
    internal fun elvargDefeated(player: Player, elvarg: Npc) {
        if (elvarg.combat.lastDamageReceived?.attacker !== player || player.state != EntityState.ACTIVE || !player.isAlive ||
            !progress.complete(player, SAILED_TO_CRANDOR)) return

        player.varpManager.setAndSendValue(PersistentVarp.DRAGON_SLAYER_SHIP_REPAIRS, 0)
        player.varpManager.setAndSendValue(PersistentVarp.DRAGON_SLAYER_NED_HIRED, 0)
        for (id in listOf(Skill.STRENGTH, Skill.DEFENCE)) {
            val skill = player.skill(id)
            skill.experience += REWARD_XP
        }
        if (player.z == 0 && player.x in 2847..2863 && player.y in 9628..9646) player.move(elvargLairExit)
        refresh(player)
        player.playJingle(when (Random.nextInt(4)) {
            0 -> Jingles.QUEST_COMPLETE_3
            1 -> Jingles.QUEST_COMPLETE_2
            else -> Jingles.QUEST_COMPLETE_1
        })
        player.sendMessage("Congratulations! Quest complete!")
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed Dragon Slayer!", 12144)
                plr.queue(WidgetItemModelMessageWriter(12145, 250, 1127))
                plr.sendText("${progress.questPoints} Quest Points", 12150)
                plr.sendText("18,650 Strength XP", 12151)
                plr.sendText("18,650 Defence XP", 12152)
                for (id in 12153..12155) plr.sendText("", id)
            }
        })
    }

    internal fun nearby(player: Player, npc: Npc, distance: Int = 3): Boolean =
        player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked && npc.state == EntityState.ACTIVE &&
            player.z == npc.z && player.position.isWithinDistance(npc.position, distance)

    /**
     * Shows the player's next story objective on the native quest journal.
     */
    fun journal(player: Player) {
        val journal = QuestJournalInterface("Dragon Slayer")
        val lines = when (stage(player)) {
            0 -> listOf("@dbl@I can start by speaking to the Guildmaster in the", "@dbl@Champions' Guild south-west of Varrock.")
            GUILD_MASTER_SPOKEN_TO -> listOf("@dbl@The Guildmaster sent me to Oziach, who lives", "@dbl@in a hut west of Edgeville.")
            OZIACH_SPOKEN_TO -> listOf("@dbl@Oziach challenged me to defeat Elvarg on Crandor.", "@dbl@I should look for a ship in Port Sarim.")
            in SHIP_BOUGHT until SHIP_REPAIRED -> listOf("@str@I bought the Lady Lumbridge at Port Sarim.", "@dbl@I need a hammer, three planks and 90 steel nails", "@dbl@to repair the hole in her hull.")
            SHIP_REPAIRED -> listOf("@str@I repaired the Lady Lumbridge.", "@dbl@I should find Ned in Draynor Village and give him", "@dbl@the assembled chart of Crandor.")
            MAP_GIVEN_TO_NED -> listOf("@str@I gave Ned the chart of Crandor.", "@dbl@I should meet him aboard the Lady Lumbridge.")
            SAILED_TO_CRANDOR -> listOf("@str@I sailed to Crandor with Ned.", "@dbl@I need to descend into the dungeon and defeat Elvarg.")
            else -> listOf("@str@I defeated Elvarg on Crandor.", "", "@red@QUEST COMPLETE!", "${progress.questPoints} Quest Points", "18,650 Strength XP", "18,650 Defence XP")
        }
        lines.forEach(journal::addLine)
        if (collectingMap(player)) {
            journal.newLine()
            journal.addLine("@dbl@The chart has three pieces. I can find them in")
            journal.addLine("@dbl@Melzar's Maze, at the Dwarven Mine and with Wormbrain.")
            journal.addLine("@dbl@An anti-dragon shield will help against Elvarg.")
        }
        player.overlays.open(journal)
    }
}
