package game.content.championsChallenge

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.combat.specialAttack.SpecialAttackHandler.specialBarClicks
import api.predef.*
import api.predef.ext.*
import engine.combat.prayer.CombatPrayer
import game.skill.magic.teleportSpells.TeleportAction
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.EquipItemEvent
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.attack.MeleeCombatAttack
import io.luna.game.model.mob.combat.attack.RangedCombatAttack
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.Varbit
import io.luna.game.model.mob.varp.Varp

/**
 * The Champions' Challenge beneath the Champions' Guild, as described in the Jagex Knowledge Base (11 May 2006).
 * Champion scrolls are handed to Larxus, who explains the champion's rule. The fight is in the arena below the
 * Champion Statue and lasts until the champion dies or the player climbs back up the arena ladder.
 */
object ChampionsChallenge {
    const val LARXUS = 3050
    const val STATUE = 10556
    const val STATUE_OPEN = 10557
    const val PORTCULLIS = 10553

    /**
     * The handwritten scroll a champion's challenge is read on.
     */
    private const val SCROLL_INTERFACE = 8119
    private val SCROLL_LINES = 8121..8128

    /**
     * The reward screen and the varps its Hitpoints and Slayer lines read.
     */
    private const val REWARD_INTERFACE = 15831
    private const val HITPOINTS_XP_VARP = 620
    private const val SLAYER_XP_VARP = 621

    private val ANTECHAMBER = Position(3182, 9758, 0)
    private val ARENA_ENTRY = Position(3180, 9758, 0)
    private val CHAMPION_SPAWN = Position(3171, 9758, 0)
    private val SPECIAL_BUTTONS = setOf(7562, 7587, 7687, 7512, 7537, 7637, 7612, 7487, 8481, 7662, 7462, 7788, 12311)

    var Player.championsDefeated by Attr.int().persist("champions_defeated")

    /**
     * The accepted challenge, as the champion's ordinal plus one.
     */
    var Player.championChallenge by Attr.int().persist("champion_challenge")

    private val owners = mutableMapOf<Npc, Player>()

    fun defeated(player: Player, champion: Champion) = player.championsDefeated and champion.bit != 0

    fun challenge(player: Player): Champion? = Champion.entries.getOrNull(player.championChallenge - 1)

    private fun leonWaiting(player: Player) =
        player.championsDefeated and Champion.ALL_LOWER == Champion.ALL_LOWER && !defeated(player, Champion.LEON)

    fun arena(position: Position) = position.z == 0 && position.x in 3150..3181 && position.y in 9745..9772

    private fun lowerRooms(position: Position) = arena(position) || position.z == 0 && position.x in 3182..3183 &&
        position.y in 9757..9759

    fun login(player: Player) {
        for (champion in Champion.LOWER) {
            if (defeated(player, champion)) player.sendVarbit(Varbit(champion.banner, 1))
        }
    }

    fun read(player: Player, champion: Champion) {
        if (!player.inventory.contains(champion.scroll)) return
        player.overlays.open(StandardInterface(SCROLL_INTERFACE))
        val lines = wrap(champion.challenge) + "" + champion.signature
        SCROLL_LINES.forEachIndexed { index, child -> player.sendText(lines.getOrNull(index) ?: "", child) }
    }

    private fun wrap(text: String, width: Int = 36): List<String> {
        val lines = ArrayList<String>()
        var line = ""
        for (word in text.split(' ')) {
            if (line.isNotEmpty() && line.length + 1 + word.length > width) {
                lines += line
                line = word
            } else line = if (line.isEmpty()) word else "$line $word"
        }
        lines += line
        return lines
    }

    fun talk(player: Player) {
        if (leonWaiting(player) && challenge(player) == null) {
            player.newDialogue().npc(LARXUS, "Leon d'Cour has issued you a challenge. ${ChampionRule.EMPTY_INVENTORY.text} " +
                "Do you want to accept the challenge?")
                .options("Yes, let me at him!", { accept(it, Champion.LEON) },
                         "No thanks, I'll pass.", { it.newDialogue().player("No thanks, I'll pass.").open() }).open()
            return
        }
        val hasScroll = Champion.LOWER.any { player.inventory.contains(it.scroll) }
        val place = "What is this place?"
        val nothing = "Nothing thanks."
        val explain: (Player) -> Unit = {
            it.newDialogue().player(place).npc(LARXUS, "This is the champions' arena. The champions of various races use " +
                "it to duel those they deem worthy of the honour. If you find a challenge scroll in your travels, bring " +
                "it here to face your challenger.").open()
        }
        val leave: (Player) -> Unit = { it.newDialogue().player(nothing).open() }
        val dialogue = player.newDialogue().npc(LARXUS, "Is there something I can help you with?")
        if (hasScroll) {
            val given = "I was given a challenge, what now?"
            dialogue.options(given, { it.newDialogue().player(given).npc(LARXUS, "Hand it to me, and we'll get you started.").open() },
                             place, explain, nothing, leave).open()
        } else {
            dialogue.options(place, explain, nothing, leave).open()
        }
    }

    fun handIn(player: Player, champion: Champion) {
        if (!player.inventory.contains(champion.scroll)) return
        when {
            defeated(player, champion) -> {
                player.inventory.remove(champion.scroll)
                player.newDialogue().npc(LARXUS, "You've already defeated this Champion. The challenge is void.").open()
            }
            challenge(player) != null ->
                player.newDialogue().npc(LARXUS, "You've already got a champion waiting for you.").open()
            else -> player.newDialogue().npc(LARXUS, "So you want to accept the challenge huh? Well there are some " +
                "specific rules for these Champion fights. ${champion.rule.text} Do you still want to proceed?")
                .options("Yes, let me at him!", { accept(it, champion) },
                         "No thanks, I'll pass.", { it.newDialogue().player("No thanks, I'll pass.").open() }).open()
        }
    }

    private fun accept(player: Player, champion: Champion) {
        if (challenge(player) != null) return
        if (champion != Champion.LEON && !player.inventory.contains(champion.scroll)) return
        player.championChallenge = champion.ordinal + 1
        player.newDialogue().npc(LARXUS, "Your challenger is ready! Please go down through the trapdoor when you're ready.").open()
    }

    /**
     * Whether the player's items keep to the champion's rule. Ghouls allow only a wielded weapon, the lesser demon
     * nothing worn and Leon d'Cour nothing carried.
     */
    private fun allowed(player: Player, rule: ChampionRule): Boolean = when (rule) {
        ChampionRule.WEAPON_ONLY -> player.inventory.size() == 0 &&
            (0 until player.equipment.capacity()).all { it == Equipment.WEAPON || player.equipment[it] == null }
        ChampionRule.UNARMED -> player.equipment.size() == 0
        ChampionRule.EMPTY_INVENTORY -> player.inventory.size() == 0
        else -> true
    }

    fun portcullis(player: Player) {
        if (arena(player.position)) {
            player.move(ANTECHAMBER)
            return
        }
        val champion = challenge(player)
        if (champion == null) {
            player.sendMessage("You need to accept a challenge from Larxus first.")
            return
        }
        if (!allowed(player, champion.rule)) {
            player.sendMessage(champion.rule.text)
            return
        }
        player.move(ARENA_ENTRY)
        if (player.controllers.primary !is FightController) player.controllers.register(FightController(player, champion))
        if (owners.none { it.value === player && it.key.state == EntityState.ACTIVE }) {
            val npc = Npc(ctx, champion.npc, CHAMPION_SPAWN)
            npc.respawnTicks = -1
            npc.combat.retreatPolicy = RetreatPolicy.NONE
            world.addNpc(npc)
            owners[npc] = player
            npc.combat.attack(player)
        }
    }

    fun leave(player: Player) {
        val npcs = owners.filterValues { it === player }.keys.toList()
        npcs.forEach { owners.remove(it); if (it.state == EntityState.ACTIVE) world.removeNpc(it) }
    }

    /**
     * Blocks the attack styles the champion's rule forbids, and anyone fighting another player's champion.
     */
    fun stopAttack(player: Player, attack: CombatAttack<*>): Boolean {
        val victim = attack.victim as? Npc ?: return false
        val owner = owners[victim] ?: return false
        if (owner !== player) return true
        val rule = challenge(player)?.rule ?: return false
        return when (attack) {
            is MeleeCombatAttack<*> -> rule in setOf(ChampionRule.MAGIC_ONLY, ChampionRule.RANGED_ONLY, ChampionRule.NO_MELEE)
            is RangedCombatAttack<*> -> rule in setOf(ChampionRule.MAGIC_ONLY, ChampionRule.MELEE_ONLY, ChampionRule.NO_RANGED)
            is MagicCombatAttack<*> -> rule in setOf(ChampionRule.RANGED_ONLY, ChampionRule.MELEE_ONLY, ChampionRule.NO_MAGIC)
            else -> false
        }
    }

    fun defeated(npc: Npc, source: Mob?) {
        val player = owners.remove(npc) ?: return
        val champion = Champion.NPCS[npc.id] ?: return
        if (source !== player || challenge(player) != champion || player.state != EntityState.ACTIVE) return
        if (champion != Champion.LEON && !player.inventory.remove(champion.scroll)) return
        player.championChallenge = 0
        player.championsDefeated = player.championsDefeated or champion.bit
        // The reward screen shows what was actually gained, after the server's experience rate.
        val hitpoints = award(player, Skill.HITPOINTS, champion.xp)
        val slayer = award(player, Skill.SLAYER, champion.xp)
        if (champion.banner != -1) player.sendVarbit(Varbit(champion.banner, 1))
        player.sendVarp(Varp(HITPOINTS_XP_VARP, hitpoints))
        player.sendVarp(Varp(SLAYER_XP_VARP, slayer))
        player.overlays.open(StandardInterface(REWARD_INTERFACE))
        if (champion != Champion.LEON && leonWaiting(player)) {
            player.newDialogue().npc(Champion.LEON.npc, "You have done well brave adventurer, but I would test your " +
                "mettle now. You may arrange the fight with Larxus at your leisure.").open()
        }
    }

    private fun award(player: Player, skill: Int, xp: Int): Int {
        val before = player.skill(skill).experience
        player.skill(skill).addExperience(xp.toDouble())
        return (player.skill(skill).experience - before).toInt()
    }

    /**
     * Keeps a challenger to the champion's rule while they are below the gallery. Climbing the arena ladder ends
     * the fight; the scroll and the accepted challenge stay until the champion is beaten.
     */
    class FightController(player: Player, private val champion: Champion) : PlayerController(player) {
        private val rule = champion.rule

        override fun process() {
            if (rule == ChampionRule.NO_PRAYER) player.combat.prayers.deactivateAll()
            if (rule == ChampionRule.NO_SPECIAL) {
                player.combat.specialBar.forceOff()
                player.combat.specialBar.isForced = false
                player.specialBarClicks = 0
            }
        }

        override fun move() {
            if (!lowerRooms(player.position)) player.controllers.unregister()
        }

        override fun teleport(action: TeleportAction): Boolean {
            player.newDialogue().npc(LARXUS, "There's a ladder for a reason.").open()
            return false
        }

        override fun logout(): Boolean = true

        override fun unregister() = leave(player)

        override fun event(event: ControllableEvent): Boolean {
            if (event is ButtonClickEvent) {
                if (rule == ChampionRule.NO_PRAYER && CombatPrayer.entries.any { it.button == event.id }) return false
                if (rule == ChampionRule.NO_SPECIAL && event.id in SPECIAL_BUTTONS) return false
            }
            if (event is EquipItemEvent) {
                if (rule == ChampionRule.UNARMED) return false
                if (rule == ChampionRule.WEAPON_ONLY && Item(event.itemId).equipDef?.index != Equipment.WEAPON) return false
            }
            return true
        }
    }
}
