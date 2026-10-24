package game.bot.scripts

import api.bot.action.BotInteractionActionHandler
import api.bot.action.CombatLoadout
import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.bot.zone.Zone
import api.predef.*
import com.google.common.collect.ImmutableList
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import engine.bot.coordinator.skill.SlayerScriptFactory
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.NpcCombatScript.Companion.NpcCombatData
import game.bot.scripts.skills.SlayerBotScript
import game.bot.scripts.skills.SlayerBotScript.SlayerData
import game.skill.slayer.ActiveSlayerTask
import game.skill.slayer.Slayer
import game.skill.slayer.Slayer.activeSlayerTask
import game.skill.slayer.Slayer.completedSlayerTasks
import game.skill.slayer.SlayerMaster
import game.skill.slayer.SlayerTaskType
import io.luna.game.model.Position
import io.luna.game.model.EntityState
import io.luna.game.model.Locatable
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.interact.InteractionPolicy
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.time.Duration.Companion.minutes

/**
 * Exercises assignment restrictions, native kill credit, preservation, persistence and approach cancellation.
 * Fixtures provide game-thread state without starting a live server or exercising real zone travel.
 *
 * @author lare96
 */
class SlayerBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun initialize() {
            InventoryProductionFixtures.initializeCombat()
            Files.newBufferedReader(Path.of("data/dumps/npcs.json")).use { reader ->
                for (element in JsonParser.parseReader(reader).asJsonArray) {
                    if (element.isJsonNull) continue
                    val npc = element.asJsonObject
                    val id = npc.get("id").asInt
                    if (NpcDefinition.ALL.get(id).isPresent) continue
                    NpcDefinition.ALL.storeDefinition(NpcDefinition(id, npc.get("name").asString,
                        "", 1, -1, -1, -1, -1, -1, 0, ImmutableList.of(), false, 1, null))
                }
            }
        }
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.combatLevel).thenReturn(126)
        `when`(it.preferences.pk).thenReturn(null)
        `when`(it.attack.level).thenReturn(99)
        `when`(it.strength.level).thenReturn(99)
        `when`(it.magic.level).thenReturn(99)
        `when`(it.ranged.level).thenReturn(99)
        `when`(it.slayer.level).thenReturn(99)
        for (id in 0..20) {
            `when`(it.skill(id).level).thenReturn(99)
            `when`(it.skill(id).staticLevel).thenReturn(99)
        }
        it.activeSlayerTask = ActiveSlayerTask(SlayerTaskType.COW, SlayerMaster.TURAEL, 2)
    }

    private fun combat(bot: Bot) = NpcCombatScript(bot, 10.minutes,
        mutableListOf(SubZone.LUMBRIDGE_COW_PEN), slayerTask = SlayerTaskType.COW,
        slayerMaster = SlayerMaster.TURAEL)

    private fun npc(id: Int): Npc = mock(Npc::class.java).also { `when`(it.id).thenReturn(id) }

    @Test fun turaelUsesSuppliedArrivalAndInclusiveFootprint() {
        val bot = bot()
        assertEquals(Position(2931, 3536), SubZone.TURAEL.inside)
        assertEquals(Zone.BURTHORPE, SubZone.TURAEL.parent(bot))
        assertTrue(Position(2927, 3533) in SubZone.TURAEL.area)
        assertTrue(Position(2935, 3540) in SubZone.TURAEL.area)
        assertFalse(Position(2936, 3541) in SubZone.TURAEL.area)
    }

    @Test fun everyMasterRespectsItsNativeCombatAndSlayerRequirements() {
        assertEquals(listOf(SlayerMaster.TURAEL), SlayerScriptFactory.eligibleMasters(3, 1))
        assertFalse(SlayerMaster.MAZCHNA in SlayerScriptFactory.eligibleMasters(19, 99))
        assertTrue(SlayerMaster.MAZCHNA in SlayerScriptFactory.eligibleMasters(20, 1))
        assertFalse(SlayerMaster.VANNAKA in SlayerScriptFactory.eligibleMasters(39, 99))
        assertTrue(SlayerMaster.VANNAKA in SlayerScriptFactory.eligibleMasters(40, 1))
        assertFalse(SlayerMaster.CHAELDAR in SlayerScriptFactory.eligibleMasters(69, 99))
        assertTrue(SlayerMaster.CHAELDAR in SlayerScriptFactory.eligibleMasters(70, 1))
        assertFalse(SlayerMaster.DURADEL in SlayerScriptFactory.eligibleMasters(99, 99))
        assertFalse(SlayerMaster.DURADEL in SlayerScriptFactory.eligibleMasters(126, 49))
        assertEquals(SlayerMaster.entries, SlayerScriptFactory.eligibleMasters(100, 50))
    }

    @Test fun selectionUsesTheStrongestEligibleMasterAndSkillersHaveNone() {
        val bot = bot()
        `when`(bot.personality.isIntelligent).thenReturn(true)
        assertEquals(SlayerMaster.DURADEL, SlayerScriptFactory.selectMaster(bot))
        `when`(bot.slayer.level).thenReturn(49)
        assertEquals(SlayerMaster.CHAELDAR, SlayerScriptFactory.selectMaster(bot))
        `when`(bot.slayer.level).thenReturn(99)
        `when`(bot.combatLevel).thenReturn(99)
        assertEquals(SlayerMaster.CHAELDAR, SlayerScriptFactory.selectMaster(bot))
        `when`(bot.combatLevel).thenReturn(69)
        assertEquals(SlayerMaster.VANNAKA, SlayerScriptFactory.selectMaster(bot))
        `when`(bot.combatLevel).thenReturn(39)
        assertEquals(SlayerMaster.MAZCHNA, SlayerScriptFactory.selectMaster(bot))
        `when`(bot.combatLevel).thenReturn(19)
        assertEquals(SlayerMaster.TURAEL, SlayerScriptFactory.selectMaster(bot))
        `when`(bot.preferences.isSkiller).thenReturn(true)
        assertNull(SlayerScriptFactory.selectMaster(bot))
    }

    @Test fun suppliedMasterAreasContainSpawnsAndKeepTheirArrivalAnchors() {
        val bot = bot()
        assertEquals(Position(3494, 3473), SubZone.CANIFIS_MAIN.inside)
        assertEquals(Zone.CANIFIS, SubZone.CANIFIS_MAIN.parent(bot))
        assertTrue(Position(3473, 3461) in SubZone.CANIFIS_MAIN.area)
        assertTrue(Position(3519, 3517) in SubZone.CANIFIS_MAIN.area)
        assertTrue(Position(3511, 3509) in SubZone.CANIFIS_MAIN.area)
        assertTrue(Zone.CANIFIS.regions.containsAll(SubZone.CANIFIS_MAIN.area.touchedRegions))
        assertEquals(Zone.CANIFIS, Zone.REGIONS[Position(3511, 3509).regionId])
        assertEquals(Position(3147, 9914), SubZone.EDGEVILLE_DUNGEON.inside)
        assertEquals(Zone.EDGEVILLE, SubZone.EDGEVILLE_DUNGEON.parent(bot))
        assertTrue(Position(3075, 9816) in SubZone.EDGEVILLE_DUNGEON.area)
        assertTrue(Position(3151, 10000) in SubZone.EDGEVILLE_DUNGEON.area)
        assertEquals(Position(2387, 4455), SubZone.ZANARIS_MAIN.inside)
        assertEquals(Zone.ZANARIS, SubZone.ZANARIS_MAIN.parent(bot))
        assertTrue(Position(2314, 4351) in SubZone.ZANARIS_MAIN.area)
        assertTrue(Position(2510, 4485) in SubZone.ZANARIS_MAIN.area)
        assertTrue(Position(2444, 4431) in SubZone.ZANARIS_MAIN.area)
        assertEquals(Position(2852, 2958), SubZone.SHILO_VILLAGE.inside)
        assertEquals(Zone.KARAMJA, SubZone.SHILO_VILLAGE.parent(bot))
        assertTrue(Position(2816, 2944) in SubZone.SHILO_VILLAGE.area)
        assertTrue(Position(2879, 3007) in SubZone.SHILO_VILLAGE.area)
        assertFalse(Position(2880, 3008) in SubZone.SHILO_VILLAGE.area)
        assertTrue(Position(2869, 2982, 1) in SubZone.SHILO_VILLAGE.area)
    }

    @Test fun masterSearchCoversTheTownInsteadOfOnlyTheArrivalTile() {
        val bot = bot()
        val master = npc(SlayerMaster.CHAELDAR.id)
        val spawn = Position(2444, 4431)
        `when`(master.position).thenReturn(spawn)
        val zone = SubZone.ZANARIS_MAIN
        assertFalse(zone.inside.isWithinDistance(spawn, 16))
        val locator = world.locator
        `when`(locator.findNpcs(any(Locatable::class.java), anyInt(), eq(true), any())).thenAnswer { call ->
            val center = (call.getArgument<Locatable>(0)).abs()
            val radius = call.getArgument<Int>(1)
            val filter = call.getArgument<Predicate<Npc>>(3)
            if (center.isWithinDistance(spawn, radius) && filter.test(master)) setOf(master) else emptySet<Npc>()
        }
        try {
            assertSame(master, SlayerBotScript(bot, 20.minutes).findMaster(SlayerMaster.CHAELDAR, zone))
        } finally {
            reset(locator)
        }
    }

    @Test fun duradelCanBeFoundUpstairsFromTheVillageArrival() {
        val bot = bot()
        val master = npc(SlayerMaster.DURADEL.id)
        val spawn = Position(2869, 2982, 1)
        `when`(master.position).thenReturn(spawn)
        val zone = SlayerScriptFactory.masterZones.getValue(SlayerMaster.DURADEL)
        assertEquals(0, zone.inside.z)
        val locator = world.locator
        `when`(locator.findNpcs(any(Locatable::class.java), anyInt(), eq(true), any())).thenAnswer { call ->
            val center = call.getArgument<Locatable>(0).abs()
            val radius = call.getArgument<Int>(1)
            val filter = call.getArgument<Predicate<Npc>>(3)
            if (center.z == spawn.z && center.isWithinDistance(spawn, radius) && filter.test(master)) {
                setOf(master)
            } else emptySet<Npc>()
        }
        try {
            assertSame(master, SlayerBotScript(bot, 20.minutes).findMaster(SlayerMaster.DURADEL, zone))
        } finally {
            reset(locator)
        }
    }

    @Test fun highLevelBotStillReceivesItsLowLevelAssignedCamp() {
        val bot = bot()
        val camps = SlayerScriptFactory.camps(bot, BotGearPurpose.MELEE)
        assertEquals(setOf(SubZone.LUMBRIDGE_COW_PEN), camps.keys)
        assertTrue("Cow" in camps.values.flatten())
        assertFalse(SubZone.KBD_LAIR in camps)
    }

    @Test fun exactTaskIdsRejectUnrelatedNpcsEvenInTheSameCamp() {
        val bot = bot()
        val script = combat(bot)
        assertTrue(script.acceptsAssignment(npc(81)))
        assertFalse(script.acceptsAssignment(npc(41)))
        val original = bot.activeSlayerTask!!
        bot.activeSlayerTask = ActiveSlayerTask(original.task, original.assignee, original.remaining)
        assertFalse(script.assignmentActive())
        assertFalse(script.acceptsAssignment(npc(81)))
    }

    @Test fun onlyNativeMatchingKillCreditCompletesTheTaskAndStopsCombat() {
        val bot = bot()
        bot.completedSlayerTasks = 7
        val script = InventoryProductionFixtures.active(combat(bot))
        Slayer.record(bot, 41)
        assertEquals(2, bot.activeSlayerTask!!.remaining)
        Slayer.record(bot, 81)
        assertTrue(script.assignmentActive())
        assertEquals(1, bot.activeSlayerTask!!.remaining)
        Slayer.record(bot, 81)
        assertNull(bot.activeSlayerTask)
        assertEquals(8, bot.completedSlayerTasks)
        assertFalse(script.prepareCombat())
        assertTrue(script.isTerminated())
        verify(bot.combat).setTarget(null)
        verify(bot.reflex).setDisableCombatReflex(false)
    }

    @Test fun unsupportedAssignmentKeepsItsNativeObjectCountAndStreak() = runBlocking<Unit> {
        val bot = bot()
        val assignment = ActiveSlayerTask(SlayerTaskType.ABYSSAL_DEMON, SlayerMaster.DURADEL, 75)
        bot.activeSlayerTask = assignment
        bot.completedSlayerTasks = 24
        assertTrue(SlayerBotScript(bot, 20.minutes).run())
        assertSame(assignment, bot.activeSlayerTask)
        assertEquals(75, assignment.remaining)
        assertEquals(24, bot.completedSlayerTasks)
        verify(bot.actionHandler, never()).travelTo(SubZone.TURAEL)
        verifyNoInteractions(bot.scriptStack)
    }

    @Test fun skillersNeverRequestOrFightTasks() = runBlocking<Unit> {
        val bot = bot()
        `when`(bot.preferences.isSkiller).thenReturn(true)
        assertTrue(SlayerBotScript(bot, 20.minutes).run())
        assertTrue(SlayerScriptFactory.camps(bot, BotGearPurpose.MELEE).isEmpty())
        verifyNoInteractions(bot.actionHandler, bot.scriptStack)
    }

    @Test fun assignmentRestrictionsSurviveSnapshotsWhileLegacyCombatRemainsUnrestricted() {
        val bot = bot()
        val json = JsonObject().also { combat(bot).snapshot().save(it) }
        val data = NpcCombatData().also { it.load(json) }
        assertEquals(SlayerTaskType.COW, data.slayerTask)
        assertEquals(SlayerMaster.TURAEL, data.slayerMaster)
        assertFalse(NpcCombatScript(bot, data).acceptsAssignment(npc(41)))
        json.remove("slayerTask")
        json.remove("slayerMaster")
        val legacy = NpcCombatData().also { it.load(json) }
        assertTrue(NpcCombatScript(bot, legacy).acceptsAssignment(npc(41)))
    }

    @Test fun restoredDispatchedSessionCannotQueueCombatTwice() = runBlocking<Unit> {
        val bot = bot()
        val json = JsonObject().also { SlayerBotScript(bot, 20.minutes).snapshot().save(it) }
        json.addProperty("dispatched", true)
        val data = SlayerData().also { it.load(json) }
        assertTrue(SlayerBotScript(bot, data).run())
        verifyNoInteractions(bot.scriptStack)
    }

    @Test fun unchangedCombatCheckpointEndsWithoutRetryingOrCancelling() = runBlocking<Unit> {
        val bot = bot()
        val task = bot.activeSlayerTask!!
        val data = SlayerBotScript(bot, 20.minutes).snapshot().also {
            it.previousTask = "TURAEL:COW"
            it.previousRemaining = 2
        }
        val script = SlayerBotScript(bot, data)
        assertTrue(script.noProgress(task))
        assertTrue(script.run())
        assertSame(task, bot.activeSlayerTask)
        verifyNoInteractions(bot.scriptStack)
        task.remaining = 1
        assertFalse(script.noProgress(task))
    }

    @Test fun ownedLoadoutDispatchQueuesCombatBeforeItsPersistedContinuation() = runBlocking<Unit> {
        val bot = bot()
        bot.itemTracker.add(1277)
        assertNotNull(CombatLoadout.find(bot, BotGearPurpose.MELEE))
        val script = SlayerBotScript(bot, 20.minutes)
        assertTrue(script.run())
        val order = inOrder(bot.scriptStack)
        order.verify(bot.scriptStack).softPushHead(any(SlayerBotScript::class.java))
        order.verify(bot.scriptStack).softPushHead(any(NpcCombatScript::class.java))
        assertTrue(script.snapshot().dispatched)
    }

    @Test fun cancelledPermissionAfterApproachNeverSendsTheAttack() = runBlocking<Unit> {
        val bot = bot()
        val target = npc(81)
        var permitted = true
        `when`(bot.isAlive).thenReturn(true)
        `when`(target.isAlive).thenReturn(true)
        `when`(target.state).thenReturn(EntityState.ACTIVE)
        `when`(target.position).thenReturn(Position(3200, 3200))
        `when`(target.isViewableFrom(bot)).thenReturn(true)
        val collision = world.collisionManager
        `when`(collision.reached(any(Position::class.java), eq(target), eq(InteractionPolicy.STANDARD_SIZE)))
            .thenReturn(true)
        `when`(bot.actionHandler.webWalkToAny(anyCollection())).thenAnswer {
            permitted = false
            true
        }
        val handler = BotInteractionActionHandler(bot, bot.actionHandler)
        try {
            assertFalse(handler.interact(3, target) { permitted })
            assertFalse(permitted)
            verify(bot.actionHandler).webWalkToAny(anyCollection())
            verifyNoInteractions(bot.botClient.output)
        } finally {
            reset(collision)
        }
    }

    @Test fun unrelatedAggressionReturnsControlToDefensiveReflexWithoutCancellingTheTask() {
        val bot = bot()
        val assignment = bot.activeSlayerTask
        val attacker = npc(41)
        `when`(bot.combat.inCombat()).thenReturn(true)
        `when`(bot.combat.lastCombatWith).thenReturn(attacker)
        val script = InventoryProductionFixtures.active(combat(bot))
        assertFalse(script.prepareCombat())
        assertFalse(script.isTerminated())
        assertSame(assignment, bot.activeSlayerTask)
        verify(bot.reflex).setDisableCombatReflex(false)
    }

    @Test fun expiredSessionsPreserveTheAssignmentAndQueueNothing() = runBlocking<Unit> {
        val bot = bot()
        val task = bot.activeSlayerTask
        val data = SlayerBotScript(bot, 20.minutes).snapshot().also { it.deadline = 0 }
        assertTrue(SlayerBotScript(bot, data).run())
        assertSame(task, bot.activeSlayerTask)
        verifyNoInteractions(bot.actionHandler, bot.scriptStack)
    }
}
