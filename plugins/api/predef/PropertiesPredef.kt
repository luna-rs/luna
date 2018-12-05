package api.predef

import io.luna.game.event.EventListener
import io.luna.game.model.EntityType
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
val service = ctx.service!!


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


/**********************************
 *                                *
 *  [Rational] property aliases.  *
 *                                *
 *********************************/

val CHANCE_ALWAYS = Rational.ALWAYS
val CHANCE_VERY_COMMON = Rational.VERY_COMMON
val CHANCE_COMMON = Rational.COMMON
val CHANCE_UNCOMMON = Rational.UNCOMMON
val CHANCE_VERY_UNCOMMON = Rational.VERY_UNCOMMON
val CHANCE_RARE = Rational.RARE
val CHANCE_VERY_RARE = Rational.VERY_RARE