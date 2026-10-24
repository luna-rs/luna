package game.bot.scripts

import api.bot.action.CombatLoadout

/**
 * Preserves the original NPC script loadout name while combat consumers migrate to the shared API.
 * Both names refer to the same implementation and owned equipment selection rules.
 *
 * @author lare96
 */
@Deprecated("Use the shared combat loadout", ReplaceWith("CombatLoadout", "api.bot.action.CombatLoadout"))
internal typealias NpcCombatLoadout = CombatLoadout
