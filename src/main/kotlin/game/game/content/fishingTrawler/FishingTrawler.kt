package game.content.fishingTrawler

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.skill.Skills
import game.player.Jingles
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.PlayerModelAnimation
import io.luna.game.model.mob.overlay.WalkableInterface
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.Varp
import io.luna.game.model.`object`.GameObject
import io.luna.game.task.Task
import io.luna.net.msg.out.WidgetItemsMessageWriter
import io.luna.net.msg.out.CameraShakeMessageWriter

/**
 * Port Khazard's shared Fishing Trawler, including supplies, flooding and persistent unclaimed catches.
 * Active trips are abandoned on logout; earned catches remain claimable after reconnecting.
 *
 * @author lare96
 */
object FishingTrawler {
    val DOCK = Position(2668, 3162)
    val LOBBY = Position(2671, 3174, 1)
    val START = Position(1889, 4825)
    val ESCAPE = Position(3026, 3156)
    val WRECK = Position(1953, 4825)
    private val waiting = linkedSetOf<Player>()
    private val crew = linkedSetOf<Player>()
    private val cooldowns = mutableMapOf<Player, Int>()
    private val originals = mutableListOf<GameObject>()
    private val swimmers = mutableMapOf<Player, PlayerModelAnimation>()
    private val journeys = mutableMapOf<Player, Task>()
    private val swimming = PlayerModelAnimation.Builder().setStandingId(773).setStandingTurnId(773)
        .setWalkingId(772).setRunningId(772).setTurning180DegreesId(772)
        .setTurning90DegreesCwId(772).setTurning90DegreesCcwId(772).build()
    private var departure = false
    private var voyage: TrawlerVoyage? = null
    private var ticks = 0
    var Player.catchRolls by Attr.int().persist("trawler_catch_rolls")
    val Player.unclaimedCatch by Attr.list<Int>().persist("trawler_unclaimed_catch")

    fun atSea(position: Position): Boolean = position.y in 4800..4863 &&
        (position.x in 1856..1919 || position.x in 1984..2047) && position.z in 0..1
    fun inWreck(position: Position): Boolean = position.x in 1920..1983 && position.y in 4800..4863 && position.z == 0
    private fun inLobby(position: Position): Boolean = position.x in 2669..2673 && position.y in 3166..3182 && position.z in 0..1

    fun board(player: Player) {
        if (voyage != null) { player.sendMessage("There is already someone on a fishing trip!"); return }
        if (player.catchRolls > 0 || player.unclaimedCatch.isNotEmpty()) {
            player.sendMessage("Collect your previous catch from the reward net before sailing again."); return
        }
        if (!player.position.isWithinDistance(DOCK, 20)) return
        waiting.add(player)
        player.move(LOBBY)
        player.sendMessage("The boat leaves in one minute. Bring swamp paste, rope and a bailing bucket.")
        if (departure) return
        departure = true
        world.schedule(object : Task(100) {
            override fun execute() {
                departure = false
                waiting.removeIf { it.state != EntityState.ACTIVE || !inLobby(it.position) }
                if (waiting.isNotEmpty()) start()
                cancel()
            }
        })
    }

    private fun start() {
        crew.addAll(waiting)
        waiting.clear()
        ticks = -33
        voyage = TrawlerVoyage { bound -> rand().nextInt(bound) }
        originals.clear()
        for (x in listOf(1889, 2017)) for (plane in 0..1) {
            originals += world.locator.findObjects(Position(x, 4825, plane), 12) {
                it.id in setOf(2164, 2165, 2167, 2168, 2172, 2173, 2177)
            }
        }
        crew.forEach {
            it.overlays.closeWindows()
            it.move(START)
            it.overlays.open(WalkableInterface(11908))
            it.sendMessage("The trawler sets sail!")
            journey(it, false)
        }
        render()
        world.schedule(object : Task(1) {
            override fun execute() {
                val boat = voyage
                if (boat == null) { cancel(); return }
                ticks++
                crew.filter { it.state != EntityState.ACTIVE || !atSea(it.position) }.forEach(::leave)
                if (crew.isEmpty()) { finish(false); cancel(); return }
                if (ticks <= 0 || ticks % 5 != 0) return
                val wasFlooded = boat.flooded
                boat.tick(crew.size)
                if (boat.finished) { finish(!boat.sunk); cancel(); return }
                if (wasFlooded != boat.flooded) {
                    val offset = if (boat.flooded) 128 else -128
                    crew.forEach { it.move(Position(it.position.x + offset, it.position.y, it.position.z)) }
                }
                render()
                if (ticks % 100 == 0) crew.forEach {
                    it.sendMessage("${10 - ticks / 100} minutes left. Water: ${boat.water}. Fish caught: ${boat.catch}.")
                }
            }
        })
    }

    private fun render() {
        val boat = voyage ?: return
        crew.forEach {
            it.sendVarp(Varp(391, boat.water.coerceIn(0, 100)))
            it.sendText(if (boat.netRipped) "" else "Okay", 11935)
            it.sendText(if (boat.netRipped) "Ripped!" else "", 11936)
            it.sendText(if (boat.catch == 0) "Nothing" else "${boat.catch} fish", 11937)
            val minutes = (1000 - ticks.coerceAtLeast(0)) / 100
            it.sendText(if (minutes == 0) "Under 1 min" else "$minutes mins", 11938)
        }
        originals.forEach { original ->
            val hull = hullIndex(original.position)
            val id = when (original.id) {
                2177, 2167, 2168 -> when {
                    boat.leaking(hull) -> 2167
                    boat.repaired(hull) -> 2168
                    else -> 2177
                }
                2164, 2172 -> if (boat.netRipped) 2172 else 2164
                2165, 2173 -> if (boat.netRipped) 2173 else 2165
                else -> original.id
            }
            val current = world.locator.findObjectsOnTile(original.position) { it.objectType == original.objectType }.firstOrNull()
            if (current?.id != id) world.addObject(id, original.position, original.objectType, original.direction)
        }
    }

    private fun hullIndex(position: Position): Int {
        val x = position.x - if (position.x >= 1984) 1984 else 1856
        val row = when (position.y) { 4826 -> 0; 4823 -> 8; else -> return -1 }
        return if (x in 29..36 && position.z == 0) row + x - 29 else -1
    }

    private fun ready(player: Player): Boolean {
        if (player !in crew || !player.isAlive || player.isLocked ||
            !atSea(player.position) || ticks < (cooldowns[player] ?: 0)) return false
        cooldowns[player] = ticks + 2
        return true
    }

    fun repair(player: Player, obj: GameObject) {
        val boat = voyage ?: return
        if (obj.id !in setOf(2167, 2172, 2173)) return
        if (!player.position.isWithinDistance(obj.position, 2) || !ready(player)) return
        if (obj.id == 2167) {
            val index = hullIndex(obj.position)
            if (!boat.leaking(index)) return
            if (!player.inventory.remove(Item(1941))) { player.sendMessage("You'll need some swamp paste to fill that."); return }
            boat.repairHull(index)
        } else {
            if (!boat.netRipped) return
            if (!player.inventory.contains(954)) { player.sendMessage("You'll need some rope to fix it."); return }
            if (!Skills.success(30, 300, player.crafting.level)) {
                player.sendMessage("You fail to repair the net in the harsh conditions."); return
            }
            if (!player.inventory.remove(Item(954))) return
            boat.repairNet()
            player.crafting.addExperience(5.0)
            player.sendMessage("You manage to fix the net.")
        }
        player.animation(Animation(832))
        render()
    }

    fun bail(player: Player, full: Boolean) {
        if (full) {
            if (player.inventory.replace(585, 583)) player.sendMessage("You tip the water out of the bucket.")
            return
        }
        val boat = voyage ?: return
        if (!ready(player) || player.position.z != 0 || !boat.flooded || boat.water <= 0) return
        if (player.inventory.replace(583, 585)) {
            boat.bail()
            player.animation(Animation(832))
        }
    }

    fun ladder(player: Player, obj: GameObject, up: Boolean) {
        if (obj.id != (if (up) 2174 else 2175) ||
            !player.position.isWithinDistance(obj.position, 2)) return
        if (!inLobby(player.position) && !atSea(player.position)) return
        val offsets = if (up) listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)
            else listOf(0 to -1, -1 to 0, 0 to 1, 1 to 0)
        val offset = offsets[obj.direction.id]
        val plane = obj.position.z + if (up) 1 else -1
        if (plane in 0..1) player.move(Position(obj.position.x + offset.first, obj.position.y + offset.second, plane))
    }

    fun leave(player: Player) {
        journeys[player]?.cancel()
        waiting.remove(player)
        if (crew.remove(player)) {
            player.overlays.closeWalkable()
            stopCamera(player)
        }
        cooldowns.remove(player)
    }

    private fun stopCamera(player: Player) {
        player.queue(CameraShakeMessageWriter(4, 0, 0, 0))
        player.queue(CameraShakeMessageWriter(1, 0, 0, 0))
    }

    private fun journey(player: Player, returning: Boolean) {
        journeys[player]?.cancel()
        player.lock()
        player.sendVarp(Varp(75, if (returning) 12 else 11))
        player.overlays.open(StandardInterface(3281))
        player.playJingle(Jingles.SAILING_JOURNEY)
        val task = object : Task(20) {
            override fun execute() {
                if (player.state == EntityState.ACTIVE && atSea(player.position)) {
                    player.overlays.closeWindows()
                    if (returning) player.move(DOCK)
                    else if (player in crew) {
                        player.overlays.open(StandardInterface(5596))
                        player.queue(CameraShakeMessageWriter(4, 0, 20, 4))
                        player.queue(CameraShakeMessageWriter(1, 0, 20, 5))
                    }
                }
                cancel()
            }

            override fun onCancel() {
                if (journeys[player] === this) {
                    journeys.remove(player)
                    player.unlock()
                }
            }
        }
        journeys[player] = task
        world.schedule(task)
    }

    fun shipwreck(player: Player) {
        if (player !in crew) return
        val position = player.position
        leave(player)
        player.overlays.closeWindows()
        player.move(if (position.z == 0) Position(position.x + if (position.x < 1920) 64 else -64, position.y) else WRECK)
        swimmers[player] = player.model
        player.model = swimming
        world.schedule(object : Task(1) {
            override fun execute() {
                if (player.state != EntityState.ACTIVE || !inWreck(player.position)) {
                    restoreSwimming(player)
                    cancel()
                }
            }
        })
        player.sendMessage("The ship goes down! Climb onto a floating barrel to escape.")
    }

    private fun restoreSwimming(player: Player) {
        val previous = swimmers.remove(player) ?: return
        if (player.model == swimming) player.model = previous
    }

    fun escape(player: Player) {
        if (!inWreck(player.position)) return
        restoreSwimming(player)
        player.move(ESCAPE)
        player.damage(2)
        player.sendMessage("You make it to the shore tired and weary.")
    }

    fun abandon(player: Player) {
        leave(player)
        restoreSwimming(player)
        if (atSea(player.position) || inWreck(player.position) || inLobby(player.position)) {
            player.overlays.closeWindows()
            stopCamera(player)
            player.move(if (inLobby(player.position) || player.catchRolls > 0) DOCK else ESCAPE)
        }
    }

    fun waterLevel(player: Player): Int = if (player in crew) voyage?.water ?: 0 else 0
    fun flooded(player: Player): Boolean = player in crew && voyage?.flooded == true

    fun login(player: Player) {
        player.catchRolls
        player.unclaimedCatch
        restoreSwimming(player)
        if (atSea(player.position) || inWreck(player.position)) player.move(ESCAPE)
        else if (inLobby(player.position)) player.move(DOCK)
    }

    private fun finish(success: Boolean) {
        val boat = voyage ?: return
        voyage = null
        val share = if (success && crew.isNotEmpty()) boat.catch / crew.size else 0
        crew.toList().forEach {
            if (!success) { shipwreck(it); return@forEach }
            it.catchRolls = share
            it.overlays.closeWalkable()
            stopCamera(it)
            journey(it, true)
            it.sendMessage("Murphy returns to shore. Collect your catch from the net.")
        }
        crew.clear()
        cooldowns.clear()
        originals.forEach { world.addObject(it.id, it.position, it.objectType, it.direction) }
        originals.clear()
    }

    fun claim(player: Player) {
        if (!player.position.isWithinDistance(DOCK, 25)) return
        if (player.fishing.level < 15) { player.sendMessage("You need a Fishing level of 15 to find fish here."); return }
        val rolls = player.catchRolls.coerceAtLeast(0)
        player.catchRolls = 0
        repeat(rolls) {
            val (item, experience) = reward(player.fishing.level)
            player.unclaimedCatch.add(item)
            player.fishing.addExperience(experience)
        }
        if (player.unclaimedCatch.isEmpty()) {
            player.sendMessage("There are no more fish waiting for you.")
            return
        }
        player.overlays.open(TrawlerCatchInterface())
        updateCatch(player)
    }

    fun catchItems(player: Player): List<Item> = player.unclaimedCatch.groupingBy { it }.eachCount()
        .toSortedMap().map { (id, amount) -> Item(id, amount) }

    private fun updateCatch(player: Player) {
        player.queue(WidgetItemsMessageWriter(4640, catchItems(player)))
    }

    fun takeCatch(player: Player, index: Int, itemId: Int, all: Boolean) {
        if (TrawlerCatchInterface::class !in player.overlays || !player.position.isWithinDistance(DOCK, 25)) return
        val selected = catchItems(player).getOrNull(index) ?: return
        if (selected.id != itemId) return
        var remaining = if (all) selected.amount else 1
        val iterator = player.unclaimedCatch.iterator()
        while (iterator.hasNext()) {
            if (iterator.next() != itemId) continue
            if (!player.inventory.add(Item(itemId))) break
            iterator.remove()
            if (--remaining == 0) break
        }
        updateCatch(player)
        if (player.unclaimedCatch.isEmpty()) player.overlays.closeWindows()
        else if (remaining > 0) player.sendMessage("Make more inventory space and collect the rest from the net.")
    }

    private fun reward(level: Int): Pair<Int, Double> {
        fun roll(low: Int, high: Int) = Skills.success(low, high, level)
        if (roll(150, 200)) {
            val fish = listOf(
                intArrayOf(81, 3, 40, 389, 115), intArrayOf(79, 3, 40, 395, 105),
                intArrayOf(76, 3, 40, 383, 110), intArrayOf(60, 4, 48, 371, 100),
                intArrayOf(40, 6, 95, 377, 90), intArrayOf(35, 8, 64, 359, 80))
            for (entry in fish) if (level >= entry[0] && roll(entry[1], entry[2])) return entry[3] to entry[4].toDouble()
            if (level >= 16 && roll(10, 80)) return when (rand().nextInt(3)) {
                0 -> 407 to 10.0
                1 -> 401 to 1.2
                else -> 403 to 1.2
            }
            if (roll(24, 128)) return 321 to 40.0
            if (roll(32, 192)) return 327 to 20.0
            if (roll(48, 200)) return 317 to 10.0
        }
        return listOf(687, 690, 689, 688, 697, 685, 1931, 686).random() to 1.2
    }
}
