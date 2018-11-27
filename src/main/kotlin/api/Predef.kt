package api

import io.luna.game.event.EventListener
import io.luna.game.model.*
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.*
import io.luna.game.model.mob.attr.AttributeValue
import io.luna.game.model.mob.inter.*
import io.luna.game.plugin.*
import io.luna.game.task.Task
import io.luna.util.*
import java.util.concurrent.*


/* The Kotlin bindings. */
private val bindings: ScalaBindings = ReflectionUtils.getStaticField(
        PluginBootstrap::class.java,
        "bindings",
        ScalaBindings::class.java)

/* The injected state. */
val ctx = bindings.ctx!!
val logger = bindings.logger!!
val scriptListeners: MutableList<EventListener<*>> = bindings.listeners!!

/* Aliases for 'LunaContext'. */
val plugins = ctx.plugins!!
val world = ctx.world!!
val service = ctx.service!!

/* Aliases for 'PlayerRights'. */
val RIGHTS_PLAYER = PlayerRights.PLAYER
val RIGHTS_MOD = PlayerRights.MODERATOR
val RIGHTS_ADMIN = PlayerRights.ADMINISTRATOR
val RIGHTS_DEV = PlayerRights.DEVELOPER

/* Aliases for 'PlayerInteraction'. */
val INTERACTION_TRADE = PlayerInteraction.TRADE
val INTERACTION_CHALLENGE = PlayerInteraction.CHALLENGE
val INTERACTION_ATTACK = PlayerInteraction.ATTACK
val INTERACTION_FOLLOW = PlayerInteraction.FOLLOW

/* Aliases for 'EntityType'. */
val TYPE_PLAYER = EntityType.PLAYER
val TYPE_NPC = EntityType.NPC
val TYPE_OBJECT = EntityType.OBJECT
val TYPE_ITEM = EntityType.ITEM

/* Aliases for 'Skill'. */
const val SKILL_ATTACK = Skill.ATTACK
const val SKILL_DEFENCE = Skill.DEFENCE
const val SKILL_STRENGTH = Skill.STRENGTH
const val SKILL_HITPOINTS = Skill.HITPOINTS
const val SKILL_RANGED = Skill.RANGED
const val SKILL_PRAYER = Skill.PRAYER
const val SKILL_MAGIC = Skill.MAGIC
const val SKILL_COOKING = Skill.COOKING
const val SKILL_WOODCUTTING = Skill.WOODCUTTING
const val SKILL_FLETCHING = Skill.FLETCHING
const val SKILL_FISHING = Skill.FISHING
const val SKILL_FIREMAKING = Skill.FIREMAKING
const val SKILL_CRAFTING = Skill.CRAFTING
const val SKILL_SMITHING = Skill.SMITHING
const val SKILL_MINING = Skill.MINING
const val SKILL_HERBLORE = Skill.HERBLORE
const val SKILL_AGILITY = Skill.AGILITY
const val SKILL_THIEVING = Skill.THIEVING
const val SKILL_SLAYER = Skill.SLAYER
const val SKILL_FARMING = Skill.FARMING
const val SKILL_RUNECRAFTING = Skill.RUNECRAFTING

/* Aliases for 'Rational'. */
val CHANCE_ALWAYS = Rational.ALWAYS
val CHANCE_VERY_COMMON = Rational.VERY_COMMON
val CHANCE_COMMON = Rational.COMMON
val CHANCE_UNCOMMON = Rational.UNCOMMON
val CHANCE_VERY_UNCOMMON = Rational.VERY_UNCOMMON
val CHANCE_RARE = Rational.RARE
val CHANCE_VERY_RARE = Rational.VERY_RARE


fun currentTimeMillis() = TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS)


fun computeItemName(id: Int) = ItemDefinition.ALL.retrieve(id).name


/* Asynchronous block functions. */
fun async(func: () -> Unit) =
        service.submit {
            try {
                func.invoke()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

/* Random generation functions. */
fun rand() = ThreadLocalRandom.current()!!

fun rand(from: Int, to: Int): Int = rand().nextInt((to - from) + 1) + from
fun rand(to: Int): Int = rand().nextInt(to + 1)


fun Player.address() = client.ipAddress!!
fun Player.stopWalking() = walkingQueue.clear()
fun Player.lockMovement() {
    stopWalking()
    walkingQueue.isLocked = true
}

fun Player.unlockMovement() {
    walkingQueue.isLocked = false
}

// TODO new attribute DSL
fun <T> Mob.attr(key: String): T {
    val attr: AttributeValue<T> = attributes.get(key)
    return attr.get()
}

fun <T> Mob.attr(key: String, value: T) {
    val attr: AttributeValue<T> = attributes.get(key)
    attr.set(value)
}

fun <T> Mob.attr(key: String, value: (T) -> (T)) {
    val attr: AttributeValue<T> = attributes.get(key)
    val replaceWith = value.invoke(attr.get())
    attr.set(replaceWith)
}

fun Mob.isAttr(key: String) = attributes.contains(key)
fun <T> Mob.attrEquals(key: String, equals: T) = attr<T>(key) == equals
fun Mob.elapsedTime(key: String, ms: Long) {
    val value = attr<Long>(key)
    currentTimeMillis() - value >= ms
}

fun Mob.resetTime(key: String) {
    attr(key, currentTimeMillis())
}

fun <T : Mob> World.add(mob: T): T =
        when (mob.type) {// Remove return value
            TYPE_PLAYER -> {
                val plr = mob as Player
                players.add(plr)
            }
            TYPE_NPC -> {
                val npc = mob as Npc
                npcs.add(npc)
            }
            else -> throw IllegalArgumentException("invalid mob")
        }


fun World.schedule(delay: Int, instant: Boolean = false, action: (Task) -> Unit) {
    schedule(object : Task(instant, delay) {
        override fun execute() {
            action.invoke(this)
        }
    })
}

fun World.scheduleOnce(delay: Int, action: (Task) -> Unit) {
    schedule(delay) {
        action(it)
        it.cancel()
    }
}

fun World.scheduleUntil(delay: Int,
                        instant: Boolean = false,
                        action: (Task) -> Unit,
                        condition: (Task) -> Boolean) {
    schedule(delay, instant) {
        if (condition.invoke(it)) {
            it.cancel()
        } else {
            action.invoke(it)
        }
    }
}

fun World.scheduleForever(delay: Int, instant: Boolean = false, action: (Task) -> Unit) {
    schedule(delay, instant) {
        action(it)
    }
}

fun World.isPlayerOnline(name: Long) = getPlayer(name).isPresent
fun World.isPlayerOnline(name: String) = getPlayer(name).isPresent
fun World.getChunk(pos: Position) = chunks.getChunk(pos.chunkPosition)

fun <T : AbstractInterface> AbstractInterfaceSet.get(interClass: Class<T>): T? =
        currentStandard.filter { interClass.isInstance(it) }.map { interClass.cast(it) }.orElse(null)

fun <T> Array<T>.shuffle() {
    var i = size - 1
    while (i > 0) {
        val index = rand(i)
        val obj = this[index]
        this[index] = this[i]
        this[i] = obj
        i--
    }
}
