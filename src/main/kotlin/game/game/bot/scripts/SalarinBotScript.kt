package game.bot.scripts

import api.bot.action.CombatLoadout
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import api.predef.*
import com.google.common.collect.ImmutableSetMultimap
import engine.bot.gear.BotGearPurpose
import game.bot.scripts.NpcCombatScript.Companion.NpcCombatData
import io.luna.game.model.Position
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatSpell
import kotlin.time.Duration

/**
 * Fights Salarin with an owned Strike spell and banks his native sinister-key drops.
 *
 * The ordinary combat script owns equipment, rune withdrawal, defensive behaviour, looting and bank trips.
 * Salarin's native hook rejects every other damage spell, so this encounter deliberately retains its selected
 * Strike instead of allowing the ordinary tactical spell upgrades.
 *
 * @author lare96
 */
class SalarinBotScript(bot: Bot, duration: Duration, val strike: CombatSpell,
    zones: MutableList<SubZone> = mutableListOf(SubZone.YANILLE_DUNGEON_SALARIN)) :
    NpcCombatScript(bot, duration, zones, targets, BotGearPurpose.MAGIC, strike) {

    companion object {

        /**
         * Native Salarin and his key, independent of names or drop-value estimates.
         */
        const val SALARIN = 205
        const val KEY = 993

        /**
         * Highest fixed native damage first: fire 12, earth 11, water 10, wind 9.
         */
        val STRIKES = listOf(CombatSpell.FIRE_STRIKE, CombatSpell.EARTH_STRIKE,
            CombatSpell.WATER_STRIKE, CombatSpell.WIND_STRIKE)

        private val targets = ImmutableSetMultimap.of(SubZone.YANILLE_DUNGEON_SALARIN, "Salarin the twisted")

        /**
         * Persistent combat session whose spell and destination remain restricted when restored.
         *
         * @author lare96
         */
        class SalarinData : NpcCombatData()

        /**
         * Chooses a supplied owned spell without changing the live spellbook during planning.
         */
        fun selectStrike(bot: Bot): CombatSpell? = STRIKES.firstOrNull {
            bot.magic.staticLevel >= it.def.level &&
                CombatLoadout.find(bot, BotGearPurpose.MAGIC, it, spellbook = Spellbook.REGULAR,
                    minimumCasts = 28) != null
        }

        /**
         * Conservative dungeon access and owned supplies for an ordinary combat session.
         */
        fun ready(bot: Bot): Boolean = !bot.preferences.isSkiller &&
            bot.agility.staticLevel >= 67 && bot.agility.level >= 67 &&
            bot.hitpoints.staticLevel >= 40 && YanilleDungeonBotSupplies.owned(bot) && selectStrike(bot) != null
    }

    constructor(bot: Bot, data: SalarinData) : this(bot, data.duration,
        data.spell ?: CombatSpell.WIND_STRIKE, data.zones)

    override suspend fun onInit(resumed: Boolean): Boolean = strike in STRIKES &&
        originalZones.all { it == SubZone.YANILLE_DUNGEON_SALARIN } && bot.agility.level >= 67 &&
        bot.agility.staticLevel >= 67 && bot.magic.staticLevel >= strike.def.level &&
        bot.hitpoints.staticLevel >= 40 && YanilleDungeonBotSupplies.owned(bot) && super.onInit(resumed)

    override suspend fun onBankOpen(initial: Boolean) {
        handler.banking.clickBankingMode(false)
        if (!YanilleDungeonBotSupplies.withdrawAntipoison(bot)) {
            stop()
            return
        }
        if (!handler.banking.withdrawAnyFood(amount = 4, minimumHeal = 10, retry = false) ||
            !handler.inventory.hasAnyFood(minimumHeal = 10)) {
            stop()
            return
        }
        super.onBankOpen(initial)
    }

    override suspend fun travelToWorkZone(zone: SubZone, bank: BankAccess): Boolean =
        handler.webWalkTo(zone = zone, bank = bank, attempts = 4, fallback = false)

    override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<Npc> =
        super.find(searchBase, searchRadius).filterTo(mutableListOf()) { it.id == SALARIN }

    override suspend fun onAssignFocus(newFocus: Npc): Boolean =
        newFocus.id == SALARIN && super.onAssignFocus(newFocus)

    override suspend fun onPrepareTargeting(): Boolean {
        bot.combat.magic.selectedSpell = CombatSpellDefinition.NONE
        if (!YanilleDungeonBotSupplies.eatIfHurt(bot) || !YanilleDungeonBotSupplies.protect(bot)) {
            bot.combat.target = null
            forceBanking = true
            return false
        }
        return super.onPrepareTargeting()
    }

    override suspend fun interactionOption(target: Npc): Int? {
        if (target.id != SALARIN || forceBanking || !prepareCombat()) return null
        // A paused combat child can leave a manual utility selected; only the saved Strike may reach Salarin.
        bot.combat.magic.selectedSpell = CombatSpellDefinition.NONE
        return 3
    }

    override fun isLoot(item: GroundItem): Boolean = item.id == KEY || super.isLoot(item)

    override fun snapshot(): SalarinData = SalarinData().also {
        it.duration = remainingDuration().coerceAtLeast(Duration.ZERO)
        it.zones = originalZones.toMutableList()
        it.names = targets
        it.purpose = BotGearPurpose.MAGIC
        it.spell = strike
    }
}
