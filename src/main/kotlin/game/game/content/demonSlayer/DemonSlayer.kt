/*
 * Mechanics adapted from LostCityRS/Content revision 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 */
package game.content.demonSlayer

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.ext.addItem
import api.predef.ext.playJingle
import api.predef.ext.schedule
import api.predef.ext.scheduleOnce
import api.quest.QuestProgress
import game.content.questRewards.QuestRewards
import game.player.Jingles
import game.player.QuestJournalInterface
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * Owns the Demon Slayer story, key transactions and the player's journal.
 *
 * @author lare96
 */
object DemonSlayer {
    const val ARIS = 882
    const val PRYSIN = 883
    const val ROVIN = 884
    const val TRAIBORN = 881
    const val JOURNAL_BUTTON = 7334
    const val DRAIN = 2843
    const val EMPTY_BUCKET = 1925
    const val WATER_BUCKET = 1929
    const val BONES = 526
    const val COINS = 995
    const val TRAIBORN_KEY = 2399
    const val ROVIN_KEY = 2400
    const val DRAIN_KEY = 2401
    const val SILVERLIGHT = 2402

    const val ARIS_SPOKEN_TO = 1
    const val KEY_HUNT = 2
    const val FIND_BONES = 3
    const val TRAIBORN_RITUAL = 28
    const val SILVERLIGHT_RECLAIMED = 29
    const val COMPLETE = 30
    const val BONES_REQUIRED = TRAIBORN_RITUAL - FIND_BONES

    val keys = listOf(DRAIN_KEY, ROVIN_KEY, TRAIBORN_KEY)
    val sewerKeyTile = Position(3225, 9897)

    private val progress = QuestProgress(PersistentVarp.DEMON_SLAYER, COMPLETE, 3)
    private var Player.paidAris by Attr.boolean().persist("demon_slayer_paid_aris")
    private var Player.claimedTraibornKey by Attr.boolean().persist("demon_slayer_claimed_traiborn_key")
    private var Player.replacementBones by Attr.int().persist("demon_slayer_replacement_bones")
    private var Player.washedKey by Attr.nullableObj(GroundItem::class)
    private var Player.donatingBones by Attr.boolean()

    fun stage(player: Player): Int = progress.stage(player)

    fun lookingForKeys(player: Player): Boolean = stage(player) in KEY_HUNT until SILVERLIGHT_RECLAIMED

    internal fun advance(player: Player, next: Int) {
        if (progress.advance(player, next) && next == ARIS_SPOKEN_TO) refresh(player)
    }

    internal fun paidFortune(player: Player): Boolean =
        player.paidAris || player.inventory.remove(Item(COINS)).also { if (it) player.paidAris = true }

    internal fun needsFortunePayment(player: Player): Boolean = stage(player) == 0 && !player.paidAris

    internal fun bonesRemaining(player: Player): Int = when (val stage = stage(player)) {
        in FIND_BONES until TRAIBORN_RITUAL -> TRAIBORN_RITUAL - stage
        TRAIBORN_RITUAL -> if (player.claimedTraibornKey && !QuestRewards.owns(player, TRAIBORN_KEY))
            BONES_REQUIRED - player.replacementBones else 0
        else -> if (stage < FIND_BONES) BONES_REQUIRED else 0
    }

    internal fun donatingBones(player: Player): Boolean = player.donatingBones

    internal fun offerBones(player: Player, finished: (Player) -> Unit) {
        if (player.donatingBones || stage(player) !in FIND_BONES..TRAIBORN_RITUAL ||
            bonesRemaining(player) == 0 || !player.inventory.contains(BONES)) return
        player.overlays.closeWindows()
        val origin = player.position
        player.donatingBones = true
        player.world.schedule(1) { task ->
            val stage = stage(player)
            if (player.state != EntityState.ACTIVE || !player.isAlive || player.position != origin ||
                player.overlays.hasWindow() || stage !in FIND_BONES..TRAIBORN_RITUAL ||
                QuestRewards.owns(player, TRAIBORN_KEY) || !player.inventory.remove(Item(BONES))) {
                task.cancel()
                player.donatingBones = false
                return@schedule
            }
            if (stage < TRAIBORN_RITUAL) advance(player, stage + 1)
            else player.replacementBones++
            player.sendMessage("You give Traiborn a set of bones.")
            if (bonesRemaining(player) == 0 || !player.inventory.contains(BONES)) {
                task.cancel()
                player.donatingBones = false
                finished(player)
            }
        }
    }

    internal fun claimTraibornKey(player: Player): Boolean {
        if (stage(player) != TRAIBORN_RITUAL || bonesRemaining(player) > 0 ||
            QuestRewards.owns(player, TRAIBORN_KEY) || !QuestRewards.give(player, TRAIBORN_KEY)) return false
        player.claimedTraibornKey = true
        player.replacementBones = 0
        return true
    }

    internal fun exchangeKeys(player: Player): Boolean {
        if (stage(player) != TRAIBORN_RITUAL || !player.inventory.containsAll(*keys.toIntArray())) return false
        val sword = if (QuestRewards.owns(player, SILVERLIGHT)) emptyList() else listOf(Item(SILVERLIGHT))
        if (!player.inventory.updateAll(sword, keys.map { Item(it) })) return false
        advance(player, SILVERLIGHT_RECLAIMED)
        return true
    }

    internal fun searchDrain(player: Player) {
        val dialogue = player.newDialogue().text("This drainpipe leads from the palace kitchen to the sewers.")
        when {
            stage(player) >= SILVERLIGHT_RECLAIMED || QuestRewards.owns(player, DRAIN_KEY) ||
                washedKey(player) != null -> dialogue.text("There is nothing in the drain.")
            stage(player) < KEY_HUNT -> dialogue.text("You can see a key just out of reach.")
            else -> dialogue.text("Prysin's key is stuck inside the drain.", "Perhaps some water could wash it down into the sewers.")
        }
        dialogue.open()
    }

    internal fun washedKey(player: Player): GroundItem? = player.washedKey?.takeIf { it.state == EntityState.ACTIVE }

    private fun hasWashedKey(player: Player): Boolean =
        player.washedKey?.let { it.state != EntityState.INACTIVE } == true

    internal fun pourWater(player: Player) {
        if (!player.inventory.updateAll(listOf(Item(EMPTY_BUCKET)), listOf(Item(WATER_BUCKET)))) return
        player.sendMessage("You pour the water down the drain.")
        if (!lookingForKeys(player) || QuestRewards.owns(player, DRAIN_KEY) || hasWashedKey(player)) return
        val key = player.world.addItem(DRAIN_KEY, position = sewerKeyTile, plr = player)
        key.setExpire(false)
        player.washedKey = key
        player.world.scheduleOnce(300) {
            player.world.items.expire(key)
            if (player.washedKey === key) player.washedKey = null
        }
        player.sendMessage("You hear the key wash down into the sewer.")
    }

    internal fun complete(player: Player): Boolean {
        if (!progress.complete(player, SILVERLIGHT_RECLAIMED)) return false
        refresh(player)
        player.playJingle(Jingles.QUEST_COMPLETE_1)
        player.sendMessage("Congratulations! Quest complete!")
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed Demon Slayer!", 12144)
                plr.queue(WidgetItemModelMessageWriter(12145, 250, SILVERLIGHT))
                plr.sendText("${progress.questPoints} Quest Points", 12150)
                plr.sendText("Silverlight", 12151)
                for (id in 12152..12155) plr.sendText("", id)
            }
        })
        return true
    }

    fun refresh(player: Player) {
        val colour = when (stage(player)) {
            0 -> 0xff0000
            COMPLETE -> 0x00ff00
            else -> 0xffff00
        }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    fun journal(player: Player) {
        val journal = QuestJournalInterface("Demon Slayer")
        val stage = stage(player)
        if (stage == 0) {
            journal.addLine("@dbl@I can start this quest by speaking to the Gypsy")
            journal.addLine("@dbl@in the tent in Varrock Square.")
        } else {
            journal.addLine("@str@Gypsy Aris foresaw the return of the demon Delrith.")
            journal.addLine("@str@I must use Silverlight to stop him as Wally did.")
            journal.newLine()
            when (stage) {
                ARIS_SPOKEN_TO -> journal.addLine("@dbl@Sir Prysin in Varrock Palace has Silverlight.")
                in KEY_HUNT..TRAIBORN_RITUAL -> {
                    journal.addLine("@dbl@Sir Prysin needs three keys to open the sword's box.")
                    journal.addLine(keyLine(player, DRAIN_KEY, "Prysin's key", "Look in the palace kitchen drain for Prysin's key."))
                    journal.addLine(keyLine(player, ROVIN_KEY, "Rovin's key", "Rovin is upstairs in the palace guards' quarters."))
                    journal.addLine(keyLine(player, TRAIBORN_KEY, "Traiborn's key", "Traiborn is upstairs in the Wizards' Tower."))
                    if (!QuestRewards.owns(player, TRAIBORN_KEY) && stage >= FIND_BONES && bonesRemaining(player) > 0) {
                        journal.addLine("@dbl@Traiborn needs ${bonesRemaining(player)} more sets of bones.")
                    }
                    if (player.inventory.containsAll(*keys.toIntArray())) {
                        journal.addLine("@dbl@I should bring all three keys to Sir Prysin.")
                    }
                }
                SILVERLIGHT_RECLAIMED -> {
                    journal.addLine("@str@I unlocked Silverlight with the three keys.")
                    journal.addLine("@dbl@I must wield Silverlight at the stone circle")
                    journal.addLine("@dbl@south of Varrock and use Aris's incantation.")
                    if (!QuestRewards.owns(player, SILVERLIGHT)) {
                        journal.addLine("@dbl@Sir Prysin can replace my lost Silverlight.")
                    }
                }
                COMPLETE -> {
                    journal.addLine("@str@I weakened Delrith with Silverlight and banished him")
                    journal.addLine("@str@by speaking the correct incantation.")
                    journal.newLine()
                    journal.addLine("@red@QUEST COMPLETE!")
                    journal.addLine("${progress.questPoints} Quest Points")
                }
            }
        }
        player.overlays.open(journal)
    }

    private fun keyLine(player: Player, id: Int, name: String, hint: String): String = when {
        player.inventory.contains(id) -> "@str@I am carrying $name."
        player.bank.contains(id) -> "@dbl@$name is in my bank."
        else -> "@dbl@$hint"
    }
}
