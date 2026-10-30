package game.content.blastFurnace

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.skill.smithing.BarType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.varp.Varp
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import java.util.WeakHashMap

/**
 * Manual Blast Furnace with personal saved stock and shared player-operated machinery.
 * Cache identifiers and geometry come from Lost City's pinned 377 unpack and maps/m30_77.jm2.
 * No surviving gameplay script is present there: heat, fuel, wear, operating XP, and access timing below
 * are explicit reconstruction parameters. There are no later workers, coffer or basement bank chest.
 * The later OSRS Wiki manual-operation section corroborates level 30 Strength, Agility, Crafting and Firemaking,
 * and unboosted level 60 Smithing for free access; these checks are not established by cache assets alone.
 * All mutations, inventory transactions and machine steps belong to the game thread.
 *
 * @author lare96
 */
object BlastFurnace {
    const val FEE = 2500
    const val ACCESS_MILLIS = 600_000L
    const val SPADE = 952
    const val COKE_SPADE = 6448
    const val HAMMER = 2347
    const val BUCKET = 1925
    const val WATER = 1929
    const val ICE_GLOVES = 1580
    val ENTRY = Position(1940, 4958)
    val FOREMAN = Position(1944, 4958)
    val BELT = Position(1943, 4967)
    val PEDALS = Position(1947, 4966)
    val PUMP = Position(1950, 4961)
    val STOVE = Position(1948, 4963)
    val COKE = Position(1950, 4964)
    val DISPENSER = Position(1940, 4963)
    val SINK = Position(1942, 4956)

    private val oreFields = linkedMapOf(453 to BlastFurnaceVarbit.COAL, 438 to BlastFurnaceVarbit.TIN_ORE,
        440 to BlastFurnaceVarbit.IRON_ORE, 447 to BlastFurnaceVarbit.MITHRIL_ORE,
        449 to BlastFurnaceVarbit.ADAMANT_ORE, 451 to BlastFurnaceVarbit.RUNE_ORE,
        444 to BlastFurnaceVarbit.GOLD_ORE, 442 to BlastFurnaceVarbit.SILVER_ORE, 436 to BlastFurnaceVarbit.COPPER_ORE)
    private val barFields = linkedMapOf(BarType.BRONZE to BlastFurnaceVarbit.BRONZE,
        BarType.IRON to BlastFurnaceVarbit.IRON, BarType.STEEL to BlastFurnaceVarbit.STEEL,
        BarType.MITHRIL to BlastFurnaceVarbit.MITHRIL, BarType.ADAMANT to BlastFurnaceVarbit.ADAMANT,
        BarType.RUNE to BlastFurnaceVarbit.RUNE, BarType.GOLD to BlastFurnaceVarbit.GOLD,
        BarType.SILVER to BlastFurnaceVarbit.SILVER)
    private val oreIds = oreFields.keys.toList()
    private var Player.furnaceStock by Attr.obj { Stock() }.persist("blast_furnace_stock")
    private val machines = WeakHashMap<World, Machine>()

    /**
     * Deposited ores include the subset still on the belt. Moving the belt never creates a second copy.
     * Counts are bounded by the eight-bit client fields; payment and hot bars survive logout.
     *
     * @author lare96
     */
    class Stock {
        var ores = IntArray(9)
        var belt = IntArray(9)
        var bars = IntArray(BarType.entries.size)
        var hot = false
        var paidUntil = 0L
    }

    /**
     * Shared damage locations read from the 377 map. Replacements retain normal world collision and views.
     *
     * @author lare96
     */
    enum class Damage(val intact: Int, val broken: Int, val position: Position, val pipe: Boolean) {
        DRIVE(9102, 9103, Position(1944, 4967), false), COG(9104, 9105, Position(1945, 4967), false),
        PIPE_WEST(9116, 9117, Position(1943, 4961), true), PIPE_EAST(9120, 9121, Position(1947, 4961), true)
    }

    /**
     * World machinery is transient; ore and earned bars are personal saved assets instead.
     *
     * @author lare96
     */
    internal class Machine {
        var fuel = 0
        var temperature = 0
        var ticks = 0
        var wear = 0
        var nextDamage = 0
        var damage: Damage? = null
        var pump: Player? = null
        var pedals: Player? = null
        val players = linkedSetOf<Player>()
        var started = false
        var stove = 9085
    }

    internal fun machine(world: World): Machine = machines.getOrPut(world) { Machine() }
    internal fun stock(player: Player): Stock = player.furnaceStock
    fun inside(position: Position): Boolean = position.z == 0 && position.x in 1920..1983 && position.y in 4928..4991
    fun present(player: Player): Boolean = player.state == EntityState.ACTIVE && player.isAlive && inside(player.position)
    fun hasAccess(player: Player, now: Long = System.currentTimeMillis()): Boolean =
        player.smithing.staticLevel >= 60 || stock(player).paidUntil > now
    fun oreCount(player: Player, id: Int): Int = oreIds.indexOf(id).takeIf { it >= 0 }?.let { stock(player).ores[it] } ?: 0
    fun barCount(player: Player, bar: BarType): Int = stock(player).bars[bar.ordinal]
    fun beltCount(player: Player): Int = stock(player).belt.sum()
    fun totalOre(player: Player): Int = stock(player).ores.sum()
    fun totalBars(player: Player): Int = stock(player).bars.sum()
    fun hot(player: Player): Boolean = stock(player).hot && totalBars(player) > 0
    fun temperature(player: Player): Int = machine(player.world).temperature
    fun fuel(player: Player): Int = machine(player.world).fuel
    fun damage(player: Player): Damage? = machine(player.world).damage

    /**
     * Reuses Smithing recipes, halving only coal. Iron is guaranteed; no normal-furnace failure roll is used.
     */
    fun ingredients(bar: BarType): List<Item> = bar.oreList.map {
        if (it.id == 453) Item(it.id, it.amount / 2) else it
    }

    fun pay(player: Player, now: Long = System.currentTimeMillis()): Boolean {
        if (!present(player) || !player.position.isWithinDistance(FOREMAN, 4)) return false
        if (hasAccess(player, now)) return true
        if (!player.inventory.remove(Item(995, FEE))) {
            player.sendMessage("You need $FEE coins to use the furnace below level 60 Smithing.")
            return false
        }
        stock(player).paidUntil = now + ACCESS_MILLIS
        player.sendMessage("You may use the furnace for ten minutes.")
        return true
    }

    private fun allowed(player: Player, position: Position): Boolean {
        if (!present(player) || !player.position.isWithinDistance(position, 4)) return false
        if (!hasAccess(player)) {
            player.sendMessage("Speak to the Blast Furnace Foreman before using the furnace.")
            return false
        }
        restore(player)
        return true
    }

    /**
     * Activates saved stock on every login, including surface logins that may save again before returning.
     */
    fun restore(player: Player) {
        stock(player)
        if (!present(player)) return
        val machine = machine(player.world)
        machine.players += player
        if (!machine.started) {
            machine.started = true
            player.world.schedule(1) { step(player.world) }
        }
        publish(player)
    }

    fun deposit(player: Player): Int {
        if (!allowed(player, BELT)) return 0
        player.actions.interruptWeak()
        val stock = stock(player)
        var deposited = 0
        for ((index, id) in oreIds.withIndex()) {
            if (id != 453 && BarType.entries.none { it.level <= player.smithing.level &&
                    it.oreList.any { ore -> ore.id == id } }) continue
            val count = minOf(255 - stock.ores[index], player.inventory.computeAmountForId(id))
            if (count > 0 && player.inventory.remove(Item(id, count))) {
                stock.ores[index] += count
                stock.belt[index] += count
                deposited += count
            }
        }
        player.sendMessage(if (deposited == 0) "You have no suitable ore, or the furnace stock is full."
            else "You put your ore on the conveyor belt.")
        publish(player)
        return deposited
    }

    fun collectCoke(player: Player): Boolean {
        if (!allowed(player, COKE)) return false
        player.actions.interruptWeak()
        if (!player.inventory.replace(SPADE, COKE_SPADE)) {
            player.sendMessage("You need a spade to collect some coke.")
            return false
        }
        player.animation(Animation(830))
        return true
    }

    fun refuel(player: Player): Boolean {
        if (!allowed(player, STOVE)) return false
        player.actions.interruptWeak()
        if (player.firemaking.level < 30) {
            player.sendMessage("You need level 30 Firemaking to refuel the stove.")
            return false
        }
        val machine = machine(player.world)
        if (machine.fuel >= 200) return false
        if (!player.inventory.replace(COKE_SPADE, SPADE)) {
            player.sendMessage("Collect some coke on a spade first.")
            return false
        }
        machine.fuel = minOf(200, machine.fuel + 60)
        updateStove(player.world)
        player.firemaking.addExperience(5.0)
        publishAll(machine)
        return true
    }

    fun fillBucket(player: Player): Boolean {
        if (!allowed(player, SINK)) return false
        player.actions.interruptWeak()
        return player.inventory.replace(BUCKET, WATER)
    }

    fun cool(player: Player): Boolean {
        if (!allowed(player, DISPENSER) || !hot(player)) return false
        player.actions.interruptWeak()
        if (!player.inventory.replace(WATER, BUCKET)) {
            player.sendMessage("You need a bucket of water to cool the bars.")
            return false
        }
        stock(player).hot = false
        publish(player)
        return true
    }

    fun repair(player: Player, damage: Damage): Boolean {
        if (!allowed(player, damage.position)) return false
        player.actions.interruptWeak()
        val machine = machine(player.world)
        if (machine.damage != damage) return false
        if (player.crafting.level < 30) {
            player.sendMessage("You need level 30 Crafting to repair the machinery.")
            return false
        }
        if (!player.inventory.contains(HAMMER)) {
            player.sendMessage("You need a hammer to repair the machinery.")
            return false
        }
        player.animation(Animation(897))
        player.lock(3)
        replace(player.world, damage, false)
        machine.damage = null
        player.crafting.addExperience(50.0)
        publishAll(machine)
        return true
    }

    private fun replace(world: World, damage: Damage, broken: Boolean) {
        val old = world.locator.findObjects(damage.position, 0) { it.id == damage.intact || it.id == damage.broken }
            .firstOrNull() ?: return
        if (!world.objects.unregister(old)) return
        world.objects.register(GameObject.createDynamic(world.context, if (broken) damage.broken else damage.intact,
            damage.position, old.objectType, old.direction, ChunkUpdatableView.globalView()))
    }

    private fun updateStove(world: World) {
        val machine = machine(world)
        val id = if (machine.fuel > 120) 9087 else if (machine.fuel > 20) 9086 else 9085
        if (id == machine.stove) return
        val old = world.locator.findObjects(STOVE, 0) { it.id in 9085..9087 }.firstOrNull()
        if (old != null && world.objects.unregister(old)) {
            world.objects.register(GameObject.createDynamic(world.context, id, old.position,
                old.objectType, old.direction, ChunkUpdatableView.globalView()))
        }
        machine.stove = id
    }

    /**
     * Starts one exclusive native operator action. Walking, logout, another interaction or loss of access stops it.
     */
    fun operate(player: Player, role: BlastFurnaceWorkAction.Role): Boolean {
        if (!allowed(player, role.position)) return false
        if (!operatorLevel(player, role)) {
            player.sendMessage("You need level 30 ${if (role == BlastFurnaceWorkAction.Role.PUMP) "Strength" else "Agility"} to operate that.")
            return false
        }
        val machine = machine(player.world)
        val operator = if (role == BlastFurnaceWorkAction.Role.PUMP) machine.pump else machine.pedals
        if (operator != null && operator !== player && present(operator)) {
            player.sendMessage("Someone is already operating that.")
            return false
        }
        player.actions.interruptWeak()
        if (role == BlastFurnaceWorkAction.Role.PUMP) machine.pump = player else machine.pedals = player
        player.submitAction(BlastFurnaceWorkAction(player, role))
        return true
    }

    internal fun operatorLevel(player: Player, role: BlastFurnaceWorkAction.Role): Boolean =
        (if (role == BlastFurnaceWorkAction.Role.PUMP) player.strength else player.agility).level >= 30

    internal fun work(player: Player, role: BlastFurnaceWorkAction.Role): Boolean {
        val machine = machine(player.world)
        if (role == BlastFurnaceWorkAction.Role.PUMP) {
            machine.pump = player
            if (machine.fuel == 0 || machine.damage?.pipe == true) return false
            if (machine.temperature >= 100) {
                player.damage(5)
                player.sendMessage("The furnace is dangerously hot!")
                return false
            }
            machine.temperature = minOf(100, machine.temperature + 4)
            player.strength.addExperience(2.0)
        } else {
            machine.pedals = player
            if (machine.damage?.pipe == false) return false
            var transferred = 0
            for (participant in machine.players.toList()) {
                if (!present(participant)) continue
                val stock = stock(participant)
                for (index in stock.belt.indices) {
                    val count = minOf(stock.belt[index], 4)
                    stock.belt[index] -= count
                    transferred += count
                }
            }
            if (transferred == 0) return false
            player.agility.addExperience(1.0)
        }
        if (++machine.wear % 120 == 0 && machine.damage == null) {
            val damage = Damage.entries[machine.nextDamage++ % Damage.entries.size]
            machine.damage = damage
            replace(player.world, damage, true)
        }
        publishAll(machine)
        return true
    }

    internal fun release(player: Player, role: BlastFurnaceWorkAction.Role) {
        val machine = machine(player.world)
        if (role == BlastFurnaceWorkAction.Role.PUMP && machine.pump === player) machine.pump = null
        if (role == BlastFurnaceWorkAction.Role.PEDALS && machine.pedals === player) machine.pedals = null
    }

    /**
     * Fuel and heat are shared; saved ore and earned bars remain private to their depositor.
     * One bar per player per two ticks is tuning, not a recovered historical production rate.
     */
    fun step(world: World) {
        val machine = machine(world)
        machine.players.removeIf { !present(it) }
        machine.ticks++
        if (machine.ticks % 5 == 0 && machine.temperature > 0) machine.temperature--
        if (machine.ticks % 2 == 0 && machine.fuel > 0 && machine.temperature in 60..90 && machine.damage?.pipe != true) {
            for (player in machine.players.toList()) {
                if (hasAccess(player) && smelt(player)) {
                    machine.fuel--
                    if (machine.fuel == 0) break
                }
            }
        }
        if (machine.ticks % 5 == 0) publishAll(machine)
        updateStove(world)
    }

    internal fun smelt(player: Player): Boolean {
        val stock = stock(player)
        val candidates = listOf(BarType.RUNE, BarType.ADAMANT, BarType.MITHRIL, BarType.STEEL,
            BarType.IRON, BarType.GOLD, BarType.SILVER, BarType.BRONZE)
        for (bar in candidates) {
            if (player.smithing.level < bar.level || stock.bars[bar.ordinal] >= 255) continue
            val recipe = ingredients(bar)
            if (recipe.any { ore -> val index = oreIds.indexOf(ore.id)
                    stock.ores[index] - stock.belt[index] < ore.amount }) continue
            for (ore in recipe) stock.ores[oreIds.indexOf(ore.id)] -= ore.amount
            stock.bars[bar.ordinal]++
            stock.hot = true
            val goldsmith = bar == BarType.GOLD && player.equipment.computeIdForIndex(Equipment.HANDS) == 776
            player.smithing.addExperience(bar.xp * if (goldsmith) 2.5 else 1.0)
            publish(player)
            return true
        }
        return false
    }

    fun openStock(player: Player) {
        if (!allowed(player, DISPENSER)) return
        if (hot(player) && player.equipment.computeIdForIndex(Equipment.HANDS) != ICE_GLOVES) {
            player.sendMessage("The bars are too hot. Cool them with water or wear ice gloves.")
            return
        }
        player.overlays.open(BlastFurnaceInterface(player.position))
        publish(player)
    }

    /**
     * Transfers only the bars that actually fit. Full inventories retain every unclaimed bar for another trip.
     */
    fun take(player: Player, bar: BarType, amount: Int): Int {
        if (amount <= 0 || !allowed(player, DISPENSER) ||
            hot(player) && player.equipment.computeIdForIndex(Equipment.HANDS) != ICE_GLOVES) return 0
        val stock = stock(player)
        val count = minOf(amount, stock.bars[bar.ordinal], player.inventory.computeRemainingSize())
        if (count == 0) {
            player.sendMessage("You need more inventory space to take the bars.")
            return 0
        }
        if (!player.inventory.add(Item(bar.id, count))) return 0
        stock.bars[bar.ordinal] -= count
        if (totalBars(player) == 0) stock.hot = false
        publish(player)
        return count
    }

    /**
     * Updates only verified fields, including preserving perfect-gold quest fields and unused high bits.
     */
    fun publish(player: Player) {
        val stock = stock(player)
        val machine = machine(player.world)
        val parents = linkedMapOf<Int, Int>()
        fun set(field: BlastFurnaceVarbit, value: Int) {
            parents[field.parent] = field.write(parents[field.parent] ?: player.cachedVarps[field.parent] ?: 0, value)
        }
        for ((index, entry) in oreFields.entries.withIndex()) set(entry.value, stock.ores[index])
        for ((bar, field) in barFields) set(field, stock.bars[bar.ordinal])
        val gloves = player.equipment.computeIdForIndex(Equipment.HANDS) == ICE_GLOVES
        set(BlastFurnaceVarbit.HOT, if (totalBars(player) == 0) 0 else if (stock.hot && !gloves) 2 else 3)
        set(BlastFurnaceVarbit.TEMPERATURE, machine.temperature)
        set(BlastFurnaceVarbit.PIPE, if (machine.damage?.pipe == true) 1 else 0)
        set(BlastFurnaceVarbit.FUEL, if (machine.fuel < 20) 1 else 0)
        val coalNeeded = listOf(BarType.STEEL, BarType.MITHRIL, BarType.ADAMANT, BarType.RUNE)
            .sumOf { oreCount(player, it.oreRequired.first.id) * (ingredients(it).firstOrNull { ore -> ore.id == 453 }?.amount ?: 0) }
        set(BlastFurnaceVarbit.COAL_REQUIRED, maxOf(0, coalNeeded - oreCount(player, 453)))
        parents.forEach { (id, value) -> player.sendVarp(Varp(id, value)) }
    }

    private fun publishAll(machine: Machine) = machine.players.filter(::present).forEach(::publish)
}
