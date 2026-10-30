package api.bot.action

import api.bot.action.prayer.MonsterStyles
import api.bot.script.InventoryProductionFixtures
import api.combat.npc.NpcCombatHandler
import engine.combat.prayer.CombatPrayer
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.def.NpcCombatDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.damage.CombatDamageType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Exercises conservative threat envelopes around protection exhaustion and native encounter-specific damage.
 *
 * @author lare96
 */
class CombatThreatEstimateTest {
    companion object {
        private val nextNpcId = AtomicInteger(42_000)

        @JvmStatic @BeforeAll fun definitions() = InventoryProductionFixtures.initializeCombat()
    }

    private lateinit var luna: MockedStatic<Luna>

    @BeforeEach fun settings() {
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        luna = mockStatic(Luna::class.java)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
    }

    @AfterEach fun restoreSettings() { luna.close() }

    @Test fun aLearnedCustomAttackStyleCannotPromisePrayerImmunity() {
        val bot = player(mock(Bot::class.java, RETURNS_DEEP_STUBS))
        val npc = npc()
        for (prayer in listOf(CombatPrayer.PROTECT_FROM_MELEE, CombatPrayer.PROTECT_FROM_MAGIC,
                CombatPrayer.PROTECT_FROM_MISSILES)) {
            `when`(bot.combat.prayers.isActive(prayer)).thenReturn(true)
        }
        NpcCombatHandler.combat(npc.id) { attack { error("An estimator must never execute attack hooks") } }
        repeat(MonsterStyles.KNOWN_AFTER) { MonsterStyles.record(npc, CombatDamageType.MELEE) }
        try {
            val estimate = estimateCombatThreat(npc, bot, false)
            assertTrue(estimate.damagePerTick > 0)
            assertTrue(estimate.maximumHit > 0)
            assertTrue(estimate.uncertain)
        } finally {
            NpcCombatHandler.idMap.remove(npc.id)
        }
    }

    @Test fun standardProtectionNeedsTenPointsBeforeReceivingFullCredit() {
        val bot = player(mock(Bot::class.java, RETURNS_DEEP_STUBS))
        val npc = npc(maximumHit = 11)
        `when`(bot.combat.prayers.isActive(CombatPrayer.PROTECT_FROM_MELEE)).thenReturn(true)
        `when`(bot.skill(Skill.PRAYER).level).thenReturn(1)
        val nearlyEmpty = estimateCombatThreat(npc, bot, false)
        assertEquals(6, nearlyEmpty.maximumHit)
        assertTrue(nearlyEmpty.damagePerTick > 0)
        assertTrue(nearlyEmpty.uncertain)

        `when`(bot.skill(Skill.PRAYER).level).thenReturn(10)
        val reserve = estimateCombatThreat(npc, bot, false)
        assertEquals(0, reserve.maximumHit)
        assertEquals(0.0, reserve.damagePerTick)
        assertFalse(reserve.uncertain)
    }

    @Test fun dharokUsesLowHealthDamageWithoutLettingDeathChangeCapability() {
        val bot = player(mock(Bot::class.java, RETURNS_DEEP_STUBS))
        val dharok = npc(id = 2026, maximumHit = 29)
        val healthy = estimateCombatThreat(dharok, bot, false)
        val capability = estimateCombatThreat(dharok, bot, true)
        `when`(dharok.health).thenReturn(1)
        assertTrue(estimateCombatThreat(dharok, bot, false).maximumHit > healthy.maximumHit)
        assertEquals(capability, estimateCombatThreat(dharok, bot, true))
        `when`(dharok.health).thenReturn(0)
        assertEquals(capability, estimateCombatThreat(dharok, bot, true))
    }

    @Test fun anActuallyWornShieldReducesKingBlackDragonBreathDanger() {
        val bot = player(mock(Bot::class.java, RETURNS_DEEP_STUBS))
        val dragon = npc(id = 50, maximumHit = 25)
        val exposed = estimateCombatThreat(dragon, bot, false)
        `when`(bot.equipment.computeIdForIndex(Equipment.SHIELD)).thenReturn(1540)
        val shielded = estimateCombatThreat(dragon, bot, false)
        assertEquals(65, exposed.maximumHit)
        assertTrue(shielded.maximumHit < exposed.maximumHit)
        assertTrue(shielded.damagePerTick < exposed.damagePerTick)
    }

    @Test fun aHumanOpponentsPrivateRuneAndFoodInventoryIsNeverRead() {
        val bot = player(mock(Bot::class.java, RETURNS_DEEP_STUBS))
        val human = player(mock(Player::class.java, RETURNS_DEEP_STUBS))
        val spell = mock(CombatSpellDefinition::class.java)
        `when`(spell.spell).thenReturn(CombatSpell.FIRE_WAVE)
        `when`(spell.maxHit).thenReturn(20)
        `when`(human.combat.magic.selectedSpell).thenReturn(spell)
        `when`(human.combat.magic.getMaxHit(spell)).thenReturn(20)
        clearInvocations(human)

        assertTrue(estimateCombatThreat(human, bot, false).damagePerTick > 0)
        assertTrue(estimateCombatThreat(human, bot, true).damagePerTick > 0)
        verify(human, never()).inventory
        verify(human, never()).bank
    }

    @Test fun aBotOpponentsPrivateRuneAndFoodInventoryIsNeverRead() {
        val bot = player(mock(Bot::class.java, RETURNS_DEEP_STUBS))
        val opponent = player(mock(Bot::class.java, RETURNS_DEEP_STUBS))
        val spell = mock(CombatSpellDefinition::class.java)
        `when`(spell.spell).thenReturn(CombatSpell.FIRE_WAVE)
        `when`(spell.maxHit).thenReturn(20)
        `when`(opponent.combat.magic.selectedSpell).thenReturn(spell)
        `when`(opponent.combat.magic.getMaxHit(spell)).thenReturn(20)
        clearInvocations(opponent)

        assertTrue(estimateCombatThreat(opponent, bot, false).damagePerTick > 0)
        assertTrue(estimateCombatThreat(opponent, bot, true).damagePerTick > 0)
        verify(opponent, never()).inventory
        verify(opponent, never()).bank
    }

    private fun <T : Player> player(player: T): T = player.also {
        `when`(it.asPlr()).thenReturn(it)
        `when`(it.attributes()).thenReturn(AttributeMap())
        `when`(it.totalHealth).thenReturn(100)
        `when`(it.health).thenReturn(100)
        `when`(it.combat.attackStyleBonus).thenReturn(EquipmentBonus.SLASH_ATTACK)
        `when`(it.combat.magic.selectedSpell).thenReturn(CombatSpellDefinition.NONE)
        `when`(it.combat.magic.autocastSpell).thenReturn(CombatSpellDefinition.NONE)
        `when`(it.combat.prayers.active).thenReturn(emptySet())
        skills(it, 70)
    }

    private fun npc(id: Int = nextNpcId.getAndIncrement(), maximumHit: Int = 20): Npc {
        val definition = NpcCombatDefinition.ALL.get(id).orElseGet {
            NpcCombatDefinition(id, 10, null, false, false, 100, maximumHit, 4,
                0, 0, 0, 0, 0, 0, IntArray(5) { 70 }, IntArray(6)).also {
                NpcCombatDefinition.ALL.storeDefinition(it)
            }
        }
        return mock(Npc::class.java, RETURNS_DEEP_STUBS).also {
            `when`(it.id).thenReturn(id)
            `when`(it.asNpc()).thenReturn(it)
            `when`(it.combatDef()).thenReturn(definition)
            `when`(it.totalHealth).thenReturn(definition.hitpoints)
            `when`(it.health).thenReturn(definition.hitpoints)
            `when`(it.combat.attackStyleBonus).thenReturn(EquipmentBonus.SLASH_ATTACK)
            for (type in CombatDamageType.entries) {
                `when`(it.combat.getDefaultMaxHit(type)).thenReturn(definition.maximumHit)
            }
            skills(it, 70)
        }
    }

    private fun skills(mob: Mob, level: Int) {
        for (id in 0..6) {
            val skill = mock(Skill::class.java)
            `when`(skill.staticLevel).thenReturn(level)
            `when`(skill.level).thenReturn(level)
            `when`(mob.skill(id)).thenReturn(skill)
            `when`(mob.skills.getSkill(id)).thenReturn(skill)
        }
    }
}
