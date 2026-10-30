package api.bot.action

import api.bot.script.InventoryProductionFixtures
import api.combat.npc.NpcCombatHandler
import engine.bot.gear.BotItemTracker
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.EntityState
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.def.NpcCombatDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.CombatStance
import io.luna.game.model.mob.combat.state.PlayerCombatWeapon
import io.luna.game.model.mob.combat.state.PlayerRangedCombat
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
 * Evaluates threat decisions against native equipment and controlled combat state without executing attacks.
 *
 * @author lare96
 */
class BotCombatThreatTest {
    companion object {
        private val nextNpcId = AtomicInteger(40_000)

        @JvmStatic @BeforeAll fun definitions() = InventoryProductionFixtures.initializeCombat()
    }

    private lateinit var luna: MockedStatic<Luna>
    private var tick = 0L

    @BeforeEach fun settings() {
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        luna = mockStatic(Luna::class.java)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
    }

    @AfterEach fun restoreSettings() { luna.close() }

    @Test fun equalCombatLevelsDoNotHideEquipmentAdvantages() {
        val bot = player(70)
        val opponent = player(70)
        val threat = BotCombatThreat(bot)
        val unarmed = threat.assess(opponent).score

        equip(opponent, Item(1333), Item(1127), Item(1079), Item(1163), Item(1185), Item(1712))
        assertEquals(bot.combatLevel, opponent.combatLevel)
        assertTrue(threat.assess(opponent).score > unarmed)
    }

    @Test fun aLowerLevelWellEquippedPlayerCanBeMoreDangerousThanAHigherLevelUnarmedPlayer() {
        val bot = player(70)
        val unarmed = player(99)
        val equipped = player(70)
        `when`(unarmed.combatLevel).thenReturn(126)
        `when`(equipped.combatLevel).thenReturn(86)
        equip(equipped, Item(1333), Item(1127), Item(1079), Item(1163), Item(1185), Item(1712))
        val threat = BotCombatThreat(bot)
        val higherLevel = threat.assess(unarmed)
        val lowerLevel = threat.assess(equipped)

        assertTrue(equipped.combatLevel < unarmed.combatLevel)
        assertTrue(unarmed.combatLevel < bot.combatLevel * 2)
        assertTrue(lowerLevel.incomingDamagePerTick > higherLevel.incomingDamagePerTick)
        assertTrue(lowerLevel.outgoingDamagePerTick < higherLevel.outgoingDamagePerTick)
        assertTrue(lowerLevel.score > higherLevel.score)
    }

    @Test fun anNpcBelowTwiceTheBotsCombatLevelCanStillBeOverwhelming() {
        val bot = player(50)
        val opponent = npc(level = 200, hitpoints = 500, maximumHit = 45, combatLevel = 150, boss = true)
        assertTrue(opponent.combatLevel < bot.combatLevel * 2)
        assertTrue(BotCombatThreat(bot).assess(opponent).threatened)
    }

    @Test fun bossStatusAloneDoesNotMakeAWeakNpcThreatening() {
        val bot = player(99)
        equip(bot, Item(1333), Item(1127), Item(1079), Item(1163), Item(1185))
        val opponent = npc(level = 1, hitpoints = 7, maximumHit = 1, boss = true)
        assertFalse(BotCombatThreat(bot).assess(opponent).threatened)
    }

    @Test fun lowHealthAndCarriedFoodAffectImmediateSurvival() {
        val bot = player(70)
        val opponent = npc(level = 80, hitpoints = 500, maximumHit = 8)
        val threat = BotCombatThreat(bot)
        val healthy = threat.assess(opponent).score
        health(bot, 35)
        val wounded = threat.assess(opponent).score
        assertTrue(wounded > healthy)

        InventoryProductionFixtures.inventory(bot, Item(333), Item(333), Item(333), Item(333))
        assertTrue(threat.assess(opponent).score < wounded)
        assertEquals(4, bot.inventory.computeAmountForId(333))
        assertEquals(35, bot.health)
    }

    @Test fun recoveryAndAchievementIgnoreTemporaryHealthFoodAndSkillDrains() {
        val bot = player(70)
        val opponent = npc(level = 80, hitpoints = 150, maximumHit = 12)
        val threat = BotCombatThreat(bot)
        val recovery = threat.assess(opponent, BotCombatThreat.Context.RECOVERY).score
        val achievement = threat.assess(opponent, BotCombatThreat.Context.ACHIEVEMENT).score

        health(bot, 1)
        for (skill in listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE)) {
            `when`(bot.skill(skill).level).thenReturn(1)
        }
        InventoryProductionFixtures.inventory(bot, Item(333), Item(333))
        assertEquals(recovery, threat.assess(opponent, BotCombatThreat.Context.RECOVERY).score)
        assertEquals(achievement, threat.assess(opponent, BotCombatThreat.Context.ACHIEVEMENT).score)
    }

    @Test fun fullAndPartialCakesCreditOnlyTheirRemainingBites() {
        val bot = player(70)
        val opponent = npc(level = 80, hitpoints = 150, maximumHit = 12)
        val threat = BotCombatThreat(bot)
        for ((id, healing) in listOf(1891 to 12.0, 1893 to 8.0, 1895 to 4.0)) {
            bot.inventory.set(0, Item(id))
            assertEquals(healing, threat.assess(opponent).availableHealing)
            assertEquals(id, bot.inventory.get(0).id)
        }
    }

    @Test fun carriedFoodIsCappedAndLargeSweetQuantitiesCannotOverflowHealing() {
        val bot = player(70)
        val opponent = npc(level = 80, hitpoints = 150, maximumHit = 12)
        bot.inventory.set(0, Item(4561, Int.MAX_VALUE))
        val assessment = BotCombatThreat(bot).assess(opponent)
        assertEquals(70.0, assessment.availableHealing)
        assertTrue(assessment.score.isFinite())
        assertEquals(Int.MAX_VALUE, bot.inventory.get(0).amount)
    }

    @Test fun carryingWeakFoodCannotIncreaseDangerDuringAShortFight() {
        val bot = player(99)
        equip(bot, Item(1333))
        val opponent = npc(level = 300, hitpoints = 1, maximumHit = 50)
        val threat = BotCombatThreat(bot)
        val withoutFood = threat.assess(opponent)
        assertTrue(opponent.health / withoutFood.outgoingDamagePerTick < 5.0)

        bot.inventory.set(0, Item(1963, Int.MAX_VALUE))
        val withWeakFood = threat.assess(opponent)
        assertTrue(withWeakFood.score <= withoutFood.score)
        assertEquals(Int.MAX_VALUE, bot.inventory.get(0).amount)
    }

    @Test fun earlierWeakFoodDoesNotDisplaceAnExistingSharkReserve() {
        val bot = player(70)
        val opponent = npc(level = 80, hitpoints = 150, maximumHit = 12)
        for (slot in 1..5) bot.inventory.set(slot, Item(385))
        val threat = BotCombatThreat(bot)
        val sharksOnly = threat.assess(opponent)

        bot.inventory.set(0, Item(1963, Int.MAX_VALUE))
        val withWeakFood = threat.assess(opponent)
        assertEquals(sharksOnly.availableHealing, withWeakFood.availableHealing)
        assertEquals(sharksOnly.score, withWeakFood.score)
        assertEquals(5, bot.inventory.computeAmountForId(385))
        assertEquals(Int.MAX_VALUE, bot.inventory.get(0).amount)
    }

    @Test fun recoveryIsMoreCautiousAndAchievementRequiresAnEvenMatch() {
        val bot = player(99)
        equip(bot, Item(1333), Item(1127), Item(1079), Item(1163), Item(1185))
        val opponent = npc(level = 1, hitpoints = 7, maximumHit = 45)
        val threat = BotCombatThreat(bot)
        val recovery = threat.assess(opponent, BotCombatThreat.Context.RECOVERY)
        val achievement = threat.assess(opponent, BotCombatThreat.Context.ACHIEVEMENT)
        assertEquals(0.90, BotCombatThreat.Context.COMBAT.threshold)
        assertEquals(threat.assess(opponent, BotCombatThreat.Context.COMBAT), threat.assess(opponent))
        assertTrue(BotCombatThreat.Context.RECOVERY.threshold < BotCombatThreat.Context.COMBAT.threshold)
        assertTrue(BotCombatThreat.Context.COMBAT.threshold < BotCombatThreat.Context.ACHIEVEMENT.threshold)
        assertEquals(recovery.score, achievement.score)
        assertTrue(recovery.threatened)
        assertFalse(achievement.threatened)
    }

    @Test fun aDeadNpcsCurrentHealthCannotMakeItsRecoveryOrAchievementAssessmentSafe() {
        val bot = player(70)
        val opponent = npc(level = 80, hitpoints = 150, maximumHit = 12)
        val threat = BotCombatThreat(bot)
        val recovery = threat.assess(opponent, BotCombatThreat.Context.RECOVERY).score
        val achievement = threat.assess(opponent, BotCombatThreat.Context.ACHIEVEMENT).score

        `when`(opponent.health).thenReturn(0)
        `when`(opponent.isAlive).thenReturn(false)
        assertEquals(recovery, threat.assess(opponent, BotCombatThreat.Context.RECOVERY).score)
        assertEquals(achievement, threat.assess(opponent, BotCombatThreat.Context.ACHIEVEMENT).score)
    }

    @Test fun gearChangesAndCurrentSkillDrainsAreReevaluatedWithoutWaitingForAnotherTick() {
        val bot = player(70)
        val opponent = player(70)
        val threat = BotCombatThreat(bot)
        val unarmed = threat.assess(opponent).score
        equip(bot, Item(1333), Item(1127), Item(1079), Item(1163), Item(1185), Item(1712))
        val equipped = threat.assess(opponent).score
        assertTrue(equipped < unarmed)

        for (skill in listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE)) {
            `when`(bot.skill(skill).level).thenReturn(1)
        }
        assertTrue(threat.assess(opponent).score > equipped)
    }

    @Test fun missingNpcDefinitionsProduceABoundedAssessment() {
        val bot = player(70)
        val opponent = mock(Npc::class.java, RETURNS_DEEP_STUBS)
        `when`(opponent.id).thenReturn(Int.MAX_VALUE)
        `when`(opponent.combatLevel).thenReturn(100)
        `when`(opponent.totalHealth).thenReturn(100)
        `when`(opponent.health).thenReturn(100)
        `when`(opponent.asNpc()).thenReturn(opponent)

        for (context in BotCombatThreat.Context.entries) {
            val assessment = BotCombatThreat(bot).assess(opponent, context)
            assertTrue(assessment.score.isFinite())
            assertTrue(assessment.score >= 0.0)
        }
    }

    @Test fun unknownNpcAttackHooksAreNeverExecutedToEstimateDanger() {
        val bot = player(70)
        val opponent = npc(level = 80, hitpoints = 150, maximumHit = 12)
        NpcCombatHandler.combat(opponent.id) {
            attack { error("Threat assessment must not execute a combat hook") }
        }
        try {
            for (context in BotCombatThreat.Context.entries) {
                assertTrue(BotCombatThreat(bot).assess(opponent, context).score.isFinite())
            }
        } finally {
            NpcCombatHandler.idMap.remove(opponent.id)
        }
    }

    @Test fun anEmptyQuiverIsReevaluatedWithoutConsumingOrCreatingAmmunition() {
        val bot = player(70)
        equip(bot, Item(861))
        val opponent = npc(level = 40, hitpoints = 200, maximumHit = 5)
        val threat = BotCombatThreat(bot)
        val empty = threat.assess(opponent).score
        assertTrue(empty.isFinite())
        assertNull(bot.equipment.get(Equipment.AMMUNITION))
        assertTrue(threat.assess(opponent, BotCombatThreat.Context.RECOVERY).outgoingDamagePerTick > 0.0)

        equip(bot, Item(892, 100))
        val equipped = threat.assess(opponent).score
        assertTrue(equipped < empty)
        assertEquals(100, bot.equipment.computeAmountForId(892))
        assertEquals(0, bot.inventory.computeAmountForId(892))
    }

    @Test fun absentRunesDoNotExecuteCastingOrMutateTheChosenSpell() {
        val bot = player(70)
        equip(bot, Item(1381))
        val spell = CombatSpell.WIND_STRIKE.def
        `when`(bot.combat.magic.selectedSpell).thenReturn(spell)
        `when`(bot.combat.magic.autocastSpell).thenReturn(spell)
        `when`(bot.combat.magic.isAutocasting).thenReturn(true)
        `when`(bot.combat.magic.getMaxHit(spell)).thenReturn(spell.maxHit)
        val opponent = npc(level = 10, hitpoints = 100, maximumHit = 2)
        val threat = BotCombatThreat(bot)
        val missing = threat.assess(opponent).score
        assertTrue(threat.assess(opponent, BotCombatThreat.Context.RECOVERY).outgoingDamagePerTick > 0.0)
        InventoryProductionFixtures.inventory(bot, Item(558, 100))
        assertTrue(threat.assess(opponent).score < missing)
        assertEquals(100, bot.inventory.computeAmountForId(558))
        assertEquals(spell, bot.combat.magic.selectedSpell)
        assertTrue(bot.combat.magic.isAutocasting)
    }

    @Test fun repeatedAssessmentsAreDeterministicAndLeaveSuppliesIntact() {
        val bot = player(70)
        equip(bot, Item(1333), Item(1127), Item(1079))
        InventoryProductionFixtures.inventory(bot, Item(333), Item(333))
        val opponent = npc(level = 80, hitpoints = 150, maximumHit = 12)
        val threat = BotCombatThreat(bot)
        val locator = bot.world.locator
        val ownCombat = bot.combat
        val opposingCombat = opponent.combat
        clearInvocations(bot, opponent, ownCombat, opposingCombat, locator)
        val first = threat.assess(opponent)
        repeat(100) { assertEquals(first, threat.assess(opponent)) }
        assertEquals(2, bot.inventory.computeAmountForId(333))
        assertEquals(70, bot.health)
        assertNull(bot.combat.target)
        assertNull(opponent.combat.target)
        verify(ownCombat, never()).attack(any(Mob::class.java))
        verify(ownCombat, never()).getNextAttack(any(Mob::class.java))
        verify(opposingCombat, never()).attack(any(Mob::class.java))
        verify(opposingCombat, never()).getNextAttack(any(Mob::class.java))
        verify(bot, never()).sendMessage(any())
        verify(bot, never()).bank
        verifyNoInteractions(locator)
    }

    @Test fun combatDangerEntersImmediatelyButRequiresSustainedImprovementToClear() {
        val bot = player(99)
        equip(bot, Item(1333), Item(1127), Item(1079), Item(1163), Item(1185))
        val opponent = npc(level = 1, hitpoints = 7, maximumHit = 1)
        val threat = BotCombatThreat(bot)
        assertFalse(threat.isThreat(opponent))

        health(bot, 1)
        assertTrue(threat.isThreat(opponent))
        health(bot, 99)
        tick = 1
        assertFalse(threat.assess(opponent).threatened)
        assertFalse(threat.isThreat(opponent, BotCombatThreat.Context.RECOVERY))
        assertTrue(threat.isThreat(opponent))
        repeat(20) { assertTrue(threat.isThreat(opponent)) }
        tick = 5
        assertTrue(threat.isThreat(opponent))
        tick = 6
        assertFalse(threat.isThreat(opponent))
    }

    @Test fun aDifferentOpponentDoesNotInheritThePreviousThreatWarning() {
        val bot = player(99)
        val first = npc(level = 1, hitpoints = 7, maximumHit = 1)
        val second = npc(level = 1, hitpoints = 7, maximumHit = 1)
        val threat = BotCombatThreat(bot)
        health(bot, 1)
        assertTrue(threat.isThreat(first))
        health(bot, 99)
        assertFalse(threat.isThreat(second))
    }

    @Test fun respawningAnNpcResetsItsPreviousThreatWarning() {
        val bot = player(99)
        val opponent = npc(level = 1, hitpoints = 7, maximumHit = 1)
        val threat = BotCombatThreat(bot)
        health(bot, 1)
        assertTrue(threat.isThreat(opponent))
        health(bot, 99)
        `when`(opponent.spawnCount).thenReturn(1)
        assertFalse(threat.isThreat(opponent))
    }

    @Test fun twentyTicksWithoutAssessmentStartsAFreshDecision() {
        val bot = player(99)
        val opponent = npc(level = 1, hitpoints = 7, maximumHit = 1)
        val threat = BotCombatThreat(bot)
        health(bot, 1)
        assertTrue(threat.isThreat(opponent))
        health(bot, 99)
        tick = 20
        assertFalse(threat.isThreat(opponent))
    }

    @Test fun losingProtectiveGearMakesTheLiveDecisionDangerousImmediately() {
        val bot = player(70)
        val opponent = player(70)
        equip(bot, Item(1333), Item(1127), Item(1079), Item(1163), Item(1185), Item(1712))
        val threat = BotCombatThreat(bot)
        assertFalse(threat.isThreat(opponent))
        bot.equipment.clear()
        equip(bot)
        assertTrue(threat.isThreat(opponent))
    }

    private fun player(level: Int): Bot = InventoryProductionFixtures.bot().also { player ->
        val fixtureWorld = player.world
        val combat = player.combat
        `when`(player.asPlr()).thenReturn(player)
        `when`(player.totalHealth).thenReturn(level)
        `when`(player.combatLevel).thenReturn(100)
        `when`(player.spellbook).thenReturn(Spellbook.REGULAR)
        doAnswer { tick }.`when`(fixtureWorld).currentTick
        for (id in 0..6) {
            val skill = mock(Skill::class.java)
            `when`(skill.staticLevel).thenReturn(level)
            `when`(skill.level).thenReturn(level)
            `when`(player.skill(id)).thenReturn(skill)
            `when`(player.skills.getSkill(id)).thenReturn(skill)
        }
        `when`(player.health).thenReturn(level)
        val equipment = Equipment(player).also { it.setListeners(BotItemTracker(player)) }
        `when`(player.equipment).thenReturn(equipment)
        val weapon = PlayerCombatWeapon(player)
        `when`(player.combat.weapon).thenReturn(weapon)
        doAnswer { weapon.styleDef }.`when`(combat).styleDef
        doAnswer { weapon.styleDef.bonus }.`when`(combat).attackStyleBonus
        val ranged = PlayerRangedCombat(player)
        `when`(player.combat.ranged).thenReturn(ranged)
        for (type in CombatDamageType.entries) {
            `when`(player.combat.getDefaultMaxHit(type)).thenAnswer {
                CombatFormula.calculatePhysicalMaxHit(player, if (type == CombatDamageType.RANGED)
                    CombatFormula.PhysicalType.RANGED else CombatFormula.PhysicalType.MELEE)
            }
        }
        `when`(player.combat.magic.selectedSpell).thenReturn(CombatSpellDefinition.NONE)
        `when`(player.combat.magic.autocastSpell).thenReturn(CombatSpellDefinition.NONE)
        `when`(player.combat.prayers.active).thenReturn(emptySet())
        `when`(player.combat.target).thenReturn(null)
    }

    private fun equip(player: Bot, vararg items: Item) {
        for (item in items) player.equipment.set(item.equipDef.index, item)
        player.equipment.loadBonuses()
        player.combat.weapon.refreshWeapon(CombatStance.ACCURATE)
        player.combat.ranged.refreshAmmo(Equipment.WEAPON)
    }

    private fun health(player: Bot, value: Int) {
        `when`(player.health).thenReturn(value)
        `when`(player.skill(Skill.HITPOINTS).level).thenReturn(value)
    }

    private fun npc(level: Int, hitpoints: Int, maximumHit: Int, combatLevel: Int = 100, boss: Boolean = false): Npc {
        val id = nextNpcId.getAndIncrement()
        val definition = NpcCombatDefinition(id, 10, null, false, false, hitpoints, maximumHit, 4,
            0, 0, 0, 0, 0, 0, IntArray(5) { level }, IntArray(6))
        NpcCombatDefinition.ALL.storeDefinition(definition)
        return mock(Npc::class.java, RETURNS_DEEP_STUBS).also { npc ->
            `when`(npc.id).thenReturn(id)
            `when`(npc.asNpc()).thenReturn(npc)
            `when`(npc.combatDef()).thenReturn(definition)
            `when`(npc.combatLevel).thenReturn(combatLevel)
            `when`(npc.totalHealth).thenReturn(hitpoints)
            `when`(npc.health).thenReturn(hitpoints)
            `when`(npc.isAlive).thenReturn(true)
            `when`(npc.state).thenReturn(EntityState.ACTIVE)
            `when`(npc.isBoss).thenReturn(boss)
            `when`(npc.combat.attackStyleBonus).thenReturn(EquipmentBonus.SLASH_ATTACK)
            `when`(npc.combat.maxHit).thenReturn(-1)
            `when`(npc.combat.target).thenReturn(null)
            for (type in CombatDamageType.entries) {
                `when`(npc.combat.getDefaultMaxHit(type)).thenReturn(maximumHit)
            }
            for (id in 0..6) {
                val skill = mock(Skill::class.java)
                val value = if (id == Skill.HITPOINTS) hitpoints else level
                `when`(skill.staticLevel).thenReturn(value)
                `when`(skill.level).thenReturn(value)
                `when`(npc.skill(id)).thenReturn(skill)
                `when`(npc.skills.getSkill(id)).thenReturn(skill)
            }
        }
    }
}
