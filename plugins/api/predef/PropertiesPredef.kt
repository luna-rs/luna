package api.predef

import io.luna.game.event.EventListener
import io.luna.game.event.EventMatcherListener
import io.luna.game.model.EntityType
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerInteraction
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Skill
import io.luna.game.plugin.KotlinBindings
import io.luna.game.plugin.PluginBootstrap
import io.luna.util.Rational
import io.luna.util.ReflectionUtils

/**
 * The Kotlin bindings. Not accessible to scripts.
 */
private val bindings: KotlinBindings = ReflectionUtils.getStaticField(
        PluginBootstrap::class.java,
        "bindings",
        KotlinBindings::class.java)

/**
 * The [LunaContext] instance.
 */
val ctx = bindings.ctx!!

/**
 * The [Logger] instance.
 */
val logger = bindings.logger!!

/**
 * The script event listeners.
 */
val scriptListeners: MutableList<EventListener<*>> = bindings.listeners!!

/**
 * The script event listeners.
 */
val scriptMatchers: MutableList<EventMatcherListener<*>> = bindings.matchers!!

/**
 * The [EventListenerPipelineSet] instance.
 */
val pipelines = bindings.pipelines!!

/**
 * The [PluginManger] instance.
 */
val plugins = ctx.plugins!!

/**
 * The [World] instance.
 */
val world = ctx.world!!

/**
 * The [GameService] instance.
 */
val game = ctx.game!!


/*******************
 *                 *
 *  Type aliases.  *
 *                 *
 ******************/

typealias TableEntry<R, C, V> = Pair<Pair<R, C>, V>


/**************************************
 *                                    *
 *  [PlayerRights] property aliases.  *
 *                                    *
 *************************************/

val RIGHTS_PLAYER = PlayerRights.PLAYER
val RIGHTS_MOD = PlayerRights.MODERATOR
val RIGHTS_ADMIN = PlayerRights.ADMINISTRATOR
val RIGHTS_DEV = PlayerRights.DEVELOPER


/*******************************************
 *                                         *
 *  [PlayerInteraction] property aliases.  *
 *                                         *
 ******************************************/

val INTERACTION_TRADE = PlayerInteraction.TRADE!!
val INTERACTION_CHALLENGE = PlayerInteraction.CHALLENGE!!
val INTERACTION_ATTACK = PlayerInteraction.ATTACK!!
val INTERACTION_FOLLOW = PlayerInteraction.FOLLOW!!


/************************************
 *                                  *
 *  [EntityType] property aliases.  *
 *                                  *
 ***********************************/

/* Aliases for 'EntityType'. */
val TYPE_PLAYER = EntityType.PLAYER
val TYPE_NPC = EntityType.NPC
val TYPE_OBJECT = EntityType.OBJECT
val TYPE_ITEM = EntityType.ITEM


/******************************************
 *                                        *
 *  [Skill] identifier property aliases.  *
 *                                        *
 *****************************************/

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


/***********************************
 *                                 *
 *  [Skill] extension properties.  *
 *                                 *
 **********************************/

val Player.attack: Skill
    get() = skill(SKILL_ATTACK)

val Player.strength: Skill
    get() = skill(SKILL_STRENGTH)

val Player.defence: Skill
    get() = skill(SKILL_DEFENCE)

val Player.hitpoints: Skill
    get() = skill(SKILL_HITPOINTS)

val Player.ranged: Skill
    get() = skill(SKILL_RANGED)

val Player.prayer: Skill
    get() = skill(SKILL_PRAYER)

val Player.magic: Skill
    get() = skill(SKILL_MAGIC)

val Player.cooking: Skill
    get() = skill(SKILL_COOKING)

val Player.woodcutting: Skill
    get() = skill(SKILL_WOODCUTTING)

val Player.fletching: Skill
    get() = skill(SKILL_FLETCHING)

val Player.fishing: Skill
    get() = skill(SKILL_FISHING)

val Player.firemaking: Skill
    get() = skill(SKILL_FIREMAKING)

val Player.crafting: Skill
    get() = skill(SKILL_CRAFTING)

val Player.smithing: Skill
    get() = skill(SKILL_SMITHING)

val Player.mining: Skill
    get() = skill(SKILL_MINING)

val Player.herblore: Skill
    get() = skill(SKILL_HERBLORE)

val Player.agility: Skill
    get() = skill(SKILL_AGILITY)

val Player.thieving: Skill
    get() = skill(SKILL_THIEVING)

val Player.slayer: Skill
    get() = skill(SKILL_SLAYER)

val Player.farming: Skill
    get() = skill(SKILL_FARMING)

val Player.runecrafting: Skill
    get() = skill(SKILL_RUNECRAFTING)

/***************************************
 *                                     *
 *  [Equipment] extension properties.  *
 *                                     *
 ***************************************/

val Equipment.head: Item?
    get() = this[Equipment.HEAD]

val Equipment.cape: Item?
    get() = this[Equipment.CAPE]

val Equipment.amulet: Item?
    get() = this[Equipment.AMULET]

val Equipment.weapon: Item?
    get() = this[Equipment.WEAPON]

val Equipment.chest: Item?
    get() = this[Equipment.CHEST]

val Equipment.shield: Item?
    get() = this[Equipment.SHIELD]

val Equipment.legs: Item?
    get() = this[Equipment.LEGS]

val Equipment.hands: Item?
    get() = this[Equipment.HANDS]

val Equipment.feet: Item?
    get() = this[Equipment.FEET]

val Equipment.ring: Item?
    get() = this[Equipment.RING]

val Equipment.ammo: Item?
    get() = this[Equipment.AMMUNITION]


/**********************************
 *                                *
 *  [Rational] property aliases.  *
 *                                *
 *********************************/
val ALWAYS = Rational.ALWAYS
val VERY_COMMON = Rational.VERY_COMMON
val COMMON = Rational.COMMON
val UNCOMMON = Rational.UNCOMMON
val VERY_UNCOMMON = Rational.VERY_UNCOMMON
val RARE = Rational.RARE
val VERY_RARE = Rational.VERY_RARE
val NEVER = Rational(0, 1)