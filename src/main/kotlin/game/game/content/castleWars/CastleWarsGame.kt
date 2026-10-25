package game.content.castleWars

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.combat.death.DeathHookHandler
import api.predef.*
import api.predef.ext.*
import game.content.castleWars.CastleWarsMatch.Team
import game.player.item.consume.potion.Potion
import game.skill.magic.CombinationRune
import game.skill.magic.Rune
import game.player.Animations
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.block.Animation.AnimationPriority

/**
 * Applies Castle Wars rules to native inventories, equipment and player controllers.
 * The match and all calls to this adapter belong to the game thread.
 *
 * @author lare96
 */
class CastleWarsGame(val match: CastleWarsMatch<Player> = CastleWarsMatch()) {
    companion object {
        val LOBBY = Position(2441, 3090)
        val TAKE_FROM = PlayerContextMenuOption(5, "Take-from", false)
        val TOOLS = setOf(4037, 4039, 4041, 4042, 4043, 4045, 4047, 4049, 4051, 4053)
        val TABLES = mapOf(4458 to 4049, 4459 to 4051, 4460 to 4043, 4461 to 4053,
            4462 to 954, 4463 to 4045, 4464 to 1265)
        private val allowedConsumables = Potion.entries.flatMap {
            listOf(it.fourDose, it.threeDose, it.twoDose, it.oneDose)
        }.toSet() + Rune.entries.map { it.id } + CombinationRune.entries.map { it.id } + 4067
        var Player.castleWarsTickets by Attr.int().persist("castle_wars_unclaimed_tickets")

        fun inWaitingRoom(pos: Position): Boolean = pos.z == 0 &&
            (pos.x in 2368..2393 && pos.y in 9472..9497 || pos.x in 2408..2431 && pos.y in 9512..9535)

        fun inArena(pos: Position): Boolean = pos.x in 2368..2431 &&
            (pos.y in 3072..3135 && pos.z in 0..3 ||
                pos.y in 9472..9535 && pos.z == 0 && !inWaitingRoom(pos))

        fun inSpawnRoom(pos: Position): Boolean = pos.z == 1 &&
            (pos.x in 2423..2430 && pos.y in 3072..3080 || pos.x in 2368..2376 && pos.y in 3127..3135)

        fun allowed(item: Item): Boolean = item.equipDef != null || item.id in allowedConsumables

        fun registerDeathHook() {
            DeathHookHandler.addPlayerHook(CastleWarsController::class.java) {
                val controller = victim.controllers.primary as CastleWarsController
                preDeath {
                    controller.game.dropFlag(victim)
                    victim.animation(Animations.DEATH, AnimationPriority.IMMUTABLE)
                }
                death { /* Castle Wars never removes ordinary inventory or equipment. */ }
                postDeath { controller.game.respawn(victim) }
            }
        }
    }

    fun enter(player: Player, preferred: Team? = null): Boolean {
        if (!player.position.isWithinDistance(LOBBY, 16) ||
            player.controllers.primary.javaClass != PlayerController::class.java) return false
        if (player.equipment.occupied(Equipment.HEAD) || player.equipment.occupied(Equipment.CAPE)) {
            player.sendMessage("You can't wear hats, capes, or helms in the arena.")
            return false
        }
        if (player.inventory.filterNotNull().any { !allowed(it) }) {
            player.sendMessage("You can't take non-combat items into the arena.")
            return false
        }
        if (!match.join(player, preferred)) {
            player.sendMessage("There isn't enough space on this team!")
            return false
        }
        val team = match.team(player)!!
        player.equipment.set(Equipment.CAPE, Item(team.cloak))
        player.move(team.waiting)
        player.controllers.register(CastleWarsController(player, this))
        player.sendMessage("Waiting for the next Castle Wars match.")
        return true
    }

    fun tick(): CastleWarsMatch.Update<Player> {
        val update = match.tick()
        update.started.forEach { (player, team) -> start(player, team) }
        update.rewards.forEach { (player, amount) ->
            player.castleWarsTickets = Math.addExact(player.castleWarsTickets, amount)
            exit(player)
            claimTickets(player)
        }
        return update
    }

    private fun start(player: Player, team: Team) {
        player.overlays.closeWindows()
        player.move(team.spawn)
        player.contextMenu.show(PlayerContextMenuOption.ATTACK)
        player.contextMenu.show(TAKE_FROM)
    }

    fun joinInProgress(player: Player): Boolean {
        if (!inWaitingRoom(player.position) || !match.joinInProgress(player)) return false
        start(player, match.team(player)!!)
        return true
    }

    fun exit(player: Player) {
        detach(player)
        // Keep the safe-death hook until POST_DEATH even if the match ends during a death animation.
        if (!player.isAlive) return
        if (player.controllers.primary is CastleWarsController) player.controllers.unregister()
        player.move(LOBBY)
    }

    fun quit(player: Player, owner: Team) {
        val team = match.team(player) ?: return
        if (team != owner || !match.isPlaying(player)) { exit(player); return }
        detach(player)
        match.requeue(player, team)
        player.equipment.set(Equipment.CAPE, Item(team.cloak))
        player.move(team.waiting)
    }

    /**
     * Removes membership before controller removal, making cleanup safe to call repeatedly.
     */
    fun detach(player: Player) {
        dropFlag(player)
        match.leave(player, player.position)
        player.actions.getAll(io.luna.game.model.mob.combat.damage.CombatDamageAction::class.java)
            .forEach { it.interrupt() }
        removeTools(player)
        player.contextMenu.hide(PlayerContextMenuOption.ATTACK)
        player.contextMenu.hide(TAKE_FROM)
        player.overlays.closeWalkable()
        player.combat.target = null
        player.combat.autoRetaliateTarget = null
    }

    fun login(player: Player) {
        // In-memory matches cannot survive a server restart. Never restore a carried standard from a save.
        removeTools(player)
        if (inArena(player.position) || inWaitingRoom(player.position)) player.move(LOBBY)
        player.castleWarsTickets
    }

    fun claimTickets(player: Player) {
        val amount = player.castleWarsTickets
        if (amount <= 0 || !player.position.isWithinDistance(LOBBY, 16)) return
        val previous = player.inventory.computeAmountForId(4067)
        if (player.inventory.add(Item(4067, amount))) {
            val delivered = player.inventory.computeAmountForId(4067) - previous
            player.castleWarsTickets -= delivered
            player.sendMessage("You receive $delivered Castle Wars tickets.")
        } else player.sendMessage("Make room and speak to Lanthus to collect your tickets.")
    }

    private fun removeTools(player: Player) {
        for (container in listOf(player.inventory, player.equipment)) {
            for (item in container.filterNotNull().filter { it.id in TOOLS }) container.remove(item)
        }
    }

    fun canFight(player: Player, other: Player): Boolean = match.isPlaying(player) && match.isPlaying(other) &&
        match.team(player) != match.team(other) && inArena(player.position) && inArena(other.position) &&
        !inSpawnRoom(player.position) && !inSpawnRoom(other.position)

    fun dropFlag(player: Player) {
        val flag = match.carried(player) ?: return
        // A tunnel collapse can kill the carrier under a blocking object. Do not replace that object with a flag.
        val candidates = listOf(player.position) + io.luna.game.model.Direction.NESW.map { player.position.translate(1, it) }
        val position = candidates.firstOrNull { tile ->
            inArena(tile) && !world.collisionManager.isBlocked(tile, false) &&
                world.locator.findObjectsOnTile(tile) { it.objectType == io.luna.game.model.`object`.ObjectType.DEFAULT }.isEmpty()
        } ?: flag.home
        match.drop(player, position)
        player.equipment.remove(Item(flag.banner))
        player.inventory.remove(flag.banner)
    }

    fun respawn(player: Player) {
        dropFlag(player)
        val team = match.team(player)
        player.runEnergy = 100.0
        if (team != null && match.isPlaying(player)) player.move(team.spawn) else {
            exit(player)
            claimTickets(player)
        }
    }

    /**
     * Reserves space for both displaced hands before moving a standard into equipment.
     */
    private fun equipFlag(player: Player, team: Team): Boolean {
        val displaced = listOf(Equipment.WEAPON, Equipment.SHIELD).mapNotNull { player.equipment.get(it) }
        if (player.inventory.computeRemainingSize() < displaced.size) {
            player.sendMessage("You need room to put away your weapon and shield.")
            return false
        }
        displaced.forEach { check(player.inventory.add(it)) }
        player.equipment.set(Equipment.SHIELD, null)
        player.equipment.set(Equipment.WEAPON, Item(team.banner))
        return true
    }

    fun pickup(player: Player, team: Team): Boolean {
        val flag = match.flag(team)
        val position = flag.position ?: return false
        if (!match.isPlaying(player) || match.carried(player) != null ||
            !player.position.isWithinDistance(position, 1) || position == team.home && match.team(player) == team) return false
        if (!equipFlag(player, team)) return false
        check(match.pickup(player, team, false))
        return true
    }

    fun capture(player: Player): Boolean {
        val team = match.team(player) ?: return false
        val flag = match.carried(player) ?: return false
        if (!player.position.isWithinDistance(team.home, 1) || !match.capture(player)) return false
        player.equipment.remove(Item(flag.banner))
        player.sendMessage(if (flag == team) "You returned your team's flag." else "You captured the enemy flag!")
        return true
    }

    fun takeFrom(player: Player, other: Player): Boolean {
        if (!player.position.isWithinDistance(other.position, 1) || !match.canTakeFrom(player, other)) return false
        val flag = match.carried(other) ?: return false
        if (!equipFlag(player, flag)) return false
        check(match.takeFrom(player, other))
        other.equipment.remove(Item(flag.banner))
        return true
    }

    fun supply(player: Player, objectId: Int, position: Position): Boolean {
        val item = TABLES[objectId] ?: return false
        if (!match.isPlaying(player) || !inArena(position) || !player.position.isWithinDistance(position, 2)) return false
        return player.inventory.add(Item(item))
    }

    fun bandage(player: Player, recipient: Player = player): Boolean {
        if (!match.isPlaying(player) || !match.isPlaying(recipient) || match.team(player) != match.team(recipient) ||
            !player.position.isWithinDistance(recipient.position, 1) || !player.inventory.remove(4049)) return false
        recipient.hitpoints.adjustLevel(6)
        recipient.increaseRunEnergy(30.0)
        return true
    }
}
