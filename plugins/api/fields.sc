/*
 A collection of variables accessible to every plugin. Variables with '$' symbols in their names have been injected
 into this script from the Java bootstrap.
*/
import io.luna.game.model.EntityType
import io.luna.game.model.mob.{PlayerInteraction, PlayerRights, Skill}
import io.luna.game.plugin.{PluginBootstrap, ScalaBindings}
import io.luna.util.{Rational, ReflectionUtils}


/* The scala bindings. */
val BINDINGS = ReflectionUtils.getStaticField(classOf[PluginBootstrap], "bindings", classOf[ScalaBindings])

/* The injected state. */
val ctx = BINDINGS.getCtx
val logger = BINDINGS.getLogger
val scriptListeners = BINDINGS.getListeners

/* Aliases for 'LunaContext'. */
val pluginManager = ctx.getPlugins
val world = ctx.getWorld
val service = ctx.getService


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
val SKILL_ATTACK = Skill.ATTACK
val SKILL_DEFENCE = Skill.DEFENCE
val SKILL_STRENGTH = Skill.STRENGTH
val SKILL_HITPOINTS = Skill.HITPOINTS
val SKILL_RANGED = Skill.RANGED
val SKILL_PRAYER = Skill.PRAYER
val SKILL_MAGIC = Skill.MAGIC
val SKILL_COOKING = Skill.COOKING
val SKILL_WOODCUTTING = Skill.WOODCUTTING
val SKILL_FLETCHING = Skill.FLETCHING
val SKILL_FISHING = Skill.FISHING
val SKILL_FIREMAKING = Skill.FIREMAKING
val SKILL_CRAFTING = Skill.CRAFTING
val SKILL_SMITHING = Skill.SMITHING
val SKILL_MINING = Skill.MINING
val SKILL_HERBLORE = Skill.HERBLORE
val SKILL_AGILITY = Skill.AGILITY
val SKILL_THIEVING = Skill.THIEVING
val SKILL_SLAYER = Skill.SLAYER
val SKILL_FARMING = Skill.FARMING
val SKILL_RUNECRAFTING = Skill.RUNECRAFTING


/* Aliases for 'Rational'. */
val CHANCE_ALWAYS = Rational.ALWAYS
val CHANCE_VERY_COMMON = Rational.VERY_COMMON
val CHANCE_COMMON = Rational.COMMON
val CHANCE_UNCOMMON = Rational.UNCOMMON
val CHANCE_VERY_UNCOMMON = Rational.VERY_UNCOMMON
val CHANCE_RARE = Rational.RARE
val CHANCE_VERY_RARE = Rational.VERY_RARE
