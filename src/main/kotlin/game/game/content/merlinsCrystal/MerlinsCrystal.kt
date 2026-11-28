/* Mechanics adapted from LostCityRS/Content quest_arthur at 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50. */
package game.content.merlinsCrystal

import api.predef.ext.playJingle
import api.predef.ext.scheduleOnce
import api.quest.QuestProgress
import game.content.questRewards.QuestRewards
import game.player.Jingles
import game.player.QuestJournalInterface
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * King Arthur's request, Mordred's surrender, the summoning and Merlin's release.
 *
 * @author lare96
 */
object MerlinsCrystal {
    const val ARTHUR = 251
    const val GAWAIN = 240
    const val LANCELOT = 239
    const val MORDRED = 247
    const val MORGAN = 248
    const val MERLIN = 249
    const val THRANTAX = 238
    const val LADY = 250
    const val BEGGAR = 252
    const val CRYSTAL = 62
    const val ALTAR = 61
    const val BAT_BONES = 530
    const val LIT_CANDLE = 32
    const val EXCALIBUR = 35
    const val BREAD = 2309
    const val JOURNAL_BUTTON = 7368
    const val STARTED = 1
    const val SPOKE_GAWAIN = 2
    const val SPOKE_LANCELOT = 3
    const val SPOKE_MORGAN = 4
    const val SPIRIT_BOUND = 5
    const val FREED_MERLIN = 6
    const val COMPLETE = 7
    const val HELP = "I'll help free Merlin."
    const val ASK_GAWAIN = "How did Merlin get trapped?"
    const val ASK_LANCELOT = "How can I get into Keep Le Faye?"
    const val ASK_MORGAN = "Tell me how to free Merlin."
    const val TAKE_TRIAL = "How can I prove myself worthy of Excalibur?"
    const val GIVE_BREAD = "You can have some bread."
    const val HIDE_IN_CRATE = "Hide in the crate."
    const val WORDS = "Snarthon Candtrick Termanto"

    val symbol = Position(2780, 3515)
    val crystalTile = Position(2767, 3493, 2)
    val altarTile = Position(3259, 3381)
    val morganTile = Position(2770, 3403, 2)
    val beggarTile = Position(3016, 3247)
    private const val WORDS_KNOWN = 1
    private const val TRIAL_OFFERED = 1 shl 2
    private const val TRIAL_DONE = 1 shl 4
    private val progress = QuestProgress(PersistentVarp.MERLINS_CRYSTAL, COMPLETE, 6)

    fun stage(player: Player): Int = progress.stage(player)

    fun refresh(player: Player) {
        val colour = when (stage(player)) {
            0 -> 0xff0000
            COMPLETE -> 0x00ff00
            else -> 0xffff00
        }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    private fun advance(player: Player, from: Int, to: Int): Boolean {
        if (stage(player) != from || !progress.advance(player, to)) return false
        refresh(player)
        return true
    }

    private fun nearby(player: Player, npc: Npc): Boolean =
        player.isAlive && !player.isLocked && npc.state == EntityState.ACTIVE &&
            npc.isAlive && player.z == npc.z && player.position.isWithinDistance(npc.position, 3)

    private fun setFlag(player: Player, flag: Int) {
        val varps = player.varpManager
        val setting = PersistentVarp.MERLINS_CRYSTAL_COMPONENTS
        varps.setValue(setting, varps.getValue(setting) or flag)
    }

    private fun hasFlag(player: Player, flag: Int): Boolean =
        player.varpManager.getValue(PersistentVarp.MERLINS_CRYSTAL_COMPONENTS) and flag != 0

    fun knowsWords(player: Player): Boolean = hasFlag(player, WORDS_KNOWN)

    fun trialOffered(player: Player): Boolean = hasFlag(player, TRIAL_OFFERED)

    fun arthur(player: Player, npc: Npc) {
        val dialogue = player.newDialogue()
        when (stage(player)) {
            0 -> dialogue.npc(ARTHUR, "Welcome to Camelot. Our wizard Merlin has been imprisoned in a crystal.")
                .options(HELP, {
                    it.newDialogue().npc(ARTHUR, "You have my thanks. Start by asking my knights what happened.")
                        .then { volunteer -> if (nearby(volunteer, npc)) advance(volunteer, 0, STARTED) }.open()
                }, "I must be on my way.", {})
            FREED_MERLIN -> dialogue.npc(ARTHUR, "Merlin is free! You have done a great service for Camelot.")
                .then { finish(it, npc) }
            COMPLETE -> dialogue.npc(ARTHUR, "Camelot is grateful for your help.")
            else -> dialogue.npc(ARTHUR, "If you can free Merlin, let me know as soon as he's safe.")
        }
        dialogue.open()
    }

    fun gawain(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(GAWAIN, "Good day to you.")
        if (stage(player) == STARTED) {
            dialogue.options(ASK_GAWAIN, {
                it.newDialogue().npc(GAWAIN, "Morgan Le Faye imprisoned him. She and her son are at Keep Le Faye.")
                    .npc(GAWAIN, "Lancelot may know how to reach them.")
                    .then { listener -> if (nearby(listener, npc)) advance(listener, STARTED, SPOKE_GAWAIN) }.open()
            }, "Good day.", {})
        }
        dialogue.open()
    }

    fun lancelot(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(LANCELOT, "Do you need my help?")
        if (stage(player) == SPOKE_GAWAIN) {
            dialogue.options(ASK_LANCELOT, {
                it.newDialogue().npc(LANCELOT, "Arhein delivers supplies to the keep from Catherby.",
                    "There are crates beside the ship. One might be big enough to hide in.")
                    .then { listener ->
                        if (nearby(listener, npc)) advance(listener, SPOKE_GAWAIN, SPOKE_LANCELOT)
                    }.open()
            }, "I can manage.", {})
        }
        dialogue.open()
    }

    fun mordredDefence(npc: Npc, hit: CombatDamage?): CombatDamage? {
        val player = hit?.attacker as? Player ?: return hit
        if (stage(player) != SPOKE_LANCELOT || hit.amount.orElse(-1) < npc.health) return hit
        npc.world.scheduleOnce(1) {
            if (nearby(player, npc) && stage(player) == SPOKE_LANCELOT && npc.health <= 1) {
                npc.combat.reset()
                player.combat.reset()
                npc.health = npc.totalHealth
                spawnVisitor(player, MORGAN, morganTile, 65)?.let { morgan(player, it) }
            }
        }
        return hit.cappedAt(npc.health - 1)
    }

    fun morgan(player: Player, npc: Npc) {
        if (!ownsVisitor(player, npc) || stage(player) != SPOKE_LANCELOT) return
        player.newDialogue().npc(MORGAN, "Stop! What would it take for you to spare my son?")
            .options(ASK_MORGAN, {
                it.newDialogue().npc(MORGAN, "Bring a lit black candle and drop bat bones on the symbol at Camelot.",
                    "The words to bind the spirit are on the Chaos altar in Varrock.")
                    .npc(MORGAN, "Once it lifts the spell, use Excalibur to break the crystal.",
                        "The Lady of the Lake has the sword.")
                    .then { listener ->
                        if (ownsVisitor(listener, npc) && nearby(listener, npc) &&
                            advance(listener, SPOKE_LANCELOT, SPOKE_MORGAN)) listener.world.npcs.remove(npc)
                    }.open()
            }, "I'll leave him be.", {}).open()
    }

    fun checkAltar(player: Player) {
        if (stage(player) == SPOKE_MORGAN) setFlag(player, WORDS_KNOWN)
        player.newDialogue().text("An inscription at the foot of the altar reads:", WORDS).open()
    }

    fun offerTrial(player: Player) {
        player.newDialogue().npc(LADY, "Go to the jewellery shop in Port Sarim. I'll judge what you do there.")
            .then { if (stage(it) == SPOKE_MORGAN) setFlag(it, TRIAL_OFFERED) }.open()
    }

    fun visitJeweller(player: Player) {
        if (stage(player) == SPOKE_MORGAN && hasFlag(player, TRIAL_OFFERED) && !hasFlag(player, TRIAL_DONE)) {
            spawnVisitor(player, BEGGAR, beggarTile, 100)
        }
    }

    fun beggar(player: Player, npc: Npc) {
        if (!ownsVisitor(player, npc) || !hasFlag(player, TRIAL_OFFERED) || hasFlag(player, TRIAL_DONE)) return
        player.newDialogue().npc(BEGGAR, "Could you spare me a little bread?")
            .options(GIVE_BREAD, {
                if (!ownsVisitor(it, npc) || !nearby(it, npc) || stage(it) != SPOKE_MORGAN ||
                    hasFlag(it, TRIAL_DONE)) return@options
                if (!it.inventory.remove(BREAD)) {
                    it.newDialogue().npc(BEGGAR, "Come back if you find any.").open()
                } else {
                    setFlag(it, TRIAL_DONE)
                    it.world.npcs.remove(npc)
                    if (!QuestRewards.owns(it, EXCALIBUR)) it.inventory.add(EXCALIBUR)
                    it.newDialogue().npc(LADY, "You helped someone in need. You've passed my test.").open()
                }
            }, "I can't help you.", {}).open()
    }

    fun dropBones(player: Player, slot: Int): Boolean {
        if (stage(player) != SPOKE_MORGAN || player.z != symbol.z ||
            !player.position.isWithinDistance(symbol, 1) || !player.inventory.contains(LIT_CANDLE) ||
            player.inventory[slot]?.id != BAT_BONES) return false
        if (!knowsWords(player)) {
            player.sendMessage("I should check the Chaos altar for the words first.")
            return true
        }
        if (visitor(player, THRANTAX, symbol) != null) return true
        val spirit = spawnVisitor(player, THRANTAX, symbol, 75) ?: return true
        player.inventory[slot] = null
        val choices = listOf(WORDS, "Snarthtrick Candanto Termon", "Snarthanto Candon Termtrick").shuffled()
        player.newDialogue().text("A spirit rises from the symbol. What are the words of binding?")
            .options(choices.map { words ->
                io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption(words) {
                    if (!ownsVisitor(it, spirit) || !nearby(it, spirit) || stage(it) != SPOKE_MORGAN) return@DialogueOption
                    if (words == WORDS && advance(it, SPOKE_MORGAN, SPIRIT_BOUND)) {
                        it.world.npcs.remove(spirit)
                        it.newDialogue().npc(THRANTAX, "The crystal may now be broken with Excalibur.").open()
                    } else if (it.inventory.remove(LIT_CANDLE)) spirit.combat.attack(it)
                }
            }).open()
        return true
    }

    fun smash(player: Player, useItem: Boolean) {
        when {
            stage(player) >= FREED_MERLIN -> player.sendMessage("Merlin is already free.")
            !useItem -> player.sendMessage("The crystal is too tough to smash by hand.")
            stage(player) != SPIRIT_BOUND -> player.sendMessage("A spell protects the crystal.")
            player.inventory.contains(EXCALIBUR) && advance(player, SPIRIT_BOUND, FREED_MERLIN) -> {
                spawnVisitor(player, MERLIN, crystalTile.translate(1, 0), 50)
                player.newDialogue().npc(MERLIN, "You broke the spell! Tell King Arthur that I'm free.").open()
            }
        }
    }

    private fun finish(player: Player, npc: Npc) {
        if (!nearby(player, npc) || !progress.complete(player, FREED_MERLIN)) return
        refresh(player)
        player.playJingle(Jingles.QUEST_COMPLETE_1)
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed\\nMerlin's Crystal!", 12144)
                plr.queue(WidgetItemModelMessageWriter(12145, 250, EXCALIBUR))
                plr.sendText("6 Quest Points", 12150)
                for (id in 12151..12155) plr.sendText("", id)
            }
        })
    }

    fun journal(player: Player) {
        val journal = QuestJournalInterface("Merlin's Crystal")
        val instruction = when (stage(player)) {
            0 -> "I can start by speaking to King Arthur at Camelot."
            STARTED -> "I should ask Sir Gawain what happened to Merlin."
            SPOKE_GAWAIN -> "Sir Lancelot may know how to reach Keep Le Faye."
            SPOKE_LANCELOT -> "Arhein's shipment leaves from Catherby. I need to confront Mordred."
            SPOKE_MORGAN -> "I need bat bones, a lit black candle and the binding words."
            SPIRIT_BOUND -> "I can shatter Merlin's crystal with Excalibur."
            FREED_MERLIN -> "Merlin is free. I should tell King Arthur."
            else -> "@red@QUEST COMPLETE!"
        }
        journal.addLine(instruction)
        if (stage(player) == SPOKE_MORGAN) {
            journal.addLine(if (knowsWords(player)) "The words are $WORDS."
                            else "The Chaos altar in Varrock has the binding words.")
            journal.addLine("The Lady of the Lake can help me obtain Excalibur.")
        } else if (stage(player) == COMPLETE) journal.addLine("6 Quest Points")
        player.overlays.open(journal)
    }

    private fun spawnVisitor(player: Player, id: Int, at: Position, ticks: Int): Npc? {
        visitor(player, id, at)?.let { return it }
        val world = player.world
        val npc = MerlinVisitor(player, id, at)
        if (!world.npcs.add(npc)) return null
        world.scheduleOnce(ticks) { if (npc.state == EntityState.ACTIVE) world.npcs.remove(npc) }
        return npc
    }

    private fun visitor(player: Player, id: Int, at: Position): Npc? = player.world.locator.findNpcs(at, 4) {
        it.id == id && ownsVisitor(player, it)
    }.firstOrNull()

    private fun ownsVisitor(player: Player, npc: Npc): Boolean =
        npc is MerlinVisitor && npc.owner === player && npc.state == EntityState.ACTIVE
}
