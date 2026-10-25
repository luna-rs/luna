package engine.bot.coordinator.skill

import api.bot.zone.SubZone

/**
 * Resources that can be used together at the given locations.
 * Factories decide which activities to offer using their existing level and personality branches.
 * Empty zones mean the script supplies its own processing locations.
 *
 * @author lare96
 */
data class BotSkillingActivity<T>(val tokens: Set<T>, val zones: Set<SubZone> = emptySet())
