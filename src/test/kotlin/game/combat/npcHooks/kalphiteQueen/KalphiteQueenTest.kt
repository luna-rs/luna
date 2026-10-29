package game.combat.npcHooks.kalphiteQueen

import api.combat.death.DeathHookHandler
import engine.combat.prayer.CombatPrayer
import engine.controllers.MultiCombatAreaListener
import io.luna.LunaContext
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.def.NpcCombatDefinition
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.mob.MobDeathAction.DeathStage
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.combat.CombatImpactQueue
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.task.Task
import io.luna.game.task.TaskManager
import io.luna.util.RandomUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.ArgumentCaptor

/**
 * Exercises phase hooks with real NPC skills and checks prayer rolls and multi-target eligibility.
 *
 * @author lare96
 */
class KalphiteQueenTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun definitions() {
            for (id in listOf(1158, 1159, 1160)) {
                if (NpcDefinition.ALL.get(id).isEmpty) {
                    val definition = mock(NpcDefinition::class.java)
                    `when`(definition.id()).thenReturn(id)
                    `when`(definition.size).thenReturn(5)
                    NpcDefinition.ALL.storeDefinition(definition)
                }
                if (id != 1159 && NpcCombatDefinition.ALL.get(id).isEmpty) {
                    val combat = mock(NpcCombatDefinition::class.java)
                    `when`(combat.id()).thenReturn(id)
                    `when`(combat.hitpoints).thenReturn(255)
                    `when`(combat.attackLevel).thenReturn(300)
                    `when`(combat.defenceLevel).thenReturn(300)
                    `when`(combat.strengthLevel).thenReturn(300)
                    `when`(combat.magicLevel).thenReturn(150)
                    `when`(combat.rangedLevel).thenReturn(1)
                    NpcCombatDefinition.ALL.storeDefinition(combat)
                }
            }
            Class.forName("game.combat.npcHooks.kalphiteQueen.KalphiteQueenHooks")
                .getConstructor(Array<String>::class.java).newInstance(emptyArray<String>() as Any)
        }
    }

    private fun queen(): Npc {
        val npc = Npc(mock(LunaContext::class.java, RETURNS_DEEP_STUBS), 1158, Position(3485, 9500))
        npc.state = EntityState.ACTIVE
        return npc
    }

    private fun player(npc: Npc, x: Int = 3485, y: Int = 9500, z: Int = 0): Player {
        val player = mock(Player::class.java, RETURNS_DEEP_STUBS)
        `when`(player.asPlr()).thenReturn(player)
        `when`(player.position).thenReturn(Position(x, y, z))
        `when`(player.isAlive).thenReturn(true)
        `when`(player.state).thenReturn(EntityState.ACTIVE)
        `when`(player.world).thenReturn(npc.world)
        `when`(player.combat.impacts).thenReturn(CombatImpactQueue(player))
        `when`(player.controllers.contains(MultiCombatAreaListener)).thenReturn(true)
        `when`(player.controllers.checkCombat(npc)).thenReturn(true)
        return player
    }

    @Test
    fun firstDeathKeepsNpcUntilPostDeathAndRestoresOnlyHitpoints() {
        val npc = queen()
        npc.skill(Skill.ATTACK).level = 120
        npc.skill(Skill.DEFENCE).level = 70
        npc.skill(Skill.HITPOINTS).level = 0
        DeathHookHandler.onDeath(npc, null, DeathStage.PRE_DEATH)
        DeathHookHandler.onDeath(npc, null, DeathStage.DEATH)
        assertEquals(1158, npc.id)
        assertEquals(0, npc.health)
        assertTrue(npc.isLocked)
        verify(npc.world.npcs, never()).remove(npc)
        DeathHookHandler.onDeath(npc, null, DeathStage.POST_DEATH)
        assertEquals(1160, npc.id)
        assertEquals(1158, npc.baseId)
        assertEquals(255, npc.health)
        assertEquals(120, npc.skill(Skill.ATTACK).level)
        assertEquals(70, npc.skill(Skill.DEFENCE).level)
        assertFalse(npc.isLocked)
        assertNull(npc.combat.damageStack.highestDamage)
        verify(npc.world.npcs, never()).remove(npc)
    }

    @Test
    fun flyingTimeoutCannotReplaceAnAlreadyRemovedQueen() {
        val npc = queen()
        val tasks = TaskManager()
        doAnswer { tasks.schedule(it.getArgument<Task>(0)); null }.`when`(npc.world).schedule(any(Task::class.java))
        KalphiteQueen.rise(npc)
        npc.state = EntityState.INACTIVE
        repeat(2001) { tasks.runTaskIteration() }
        verify(npc.world.npcs, never()).remove(npc)
    }

    @Test
    fun livingFlyingQueenExpiresAfterTwoThousandTicks() {
        val npc = queen()
        val tasks = TaskManager()
        doAnswer { tasks.schedule(it.getArgument<Task>(0)); null }.`when`(npc.world).schedule(any(Task::class.java))
        KalphiteQueen.rise(npc)
        repeat(1999) { tasks.runTaskIteration() }
        verify(npc.world.npcs, never()).remove(npc)
        repeat(2) { tasks.runTaskIteration() }
        verify(npc.world.npcs).remove(npc)
    }

    @Test
    fun matchingPrayerBlocksBothAccurateStyles() {
        val npc = queen()
        val target = player(npc)
        for ((magic, prayer) in listOf(true to CombatPrayer.PROTECT_FROM_MAGIC,
            false to CombatPrayer.PROTECT_FROM_MISSILES)) {
            `when`(target.combat.prayers.isActive(prayer)).thenReturn(true)
            repeat(100) {
                val damage = KalphiteQueenAttack.rollDamage(npc, target, magic)
                assertTrue(damage.amount.isPresent)
                assertTrue(damage.isAccurate)
                assertEquals(0, damage.rawAmount)
            }
            `when`(target.combat.prayers.isActive(prayer)).thenReturn(false)
            repeat(100) {
                val damage = KalphiteQueenAttack.rollDamage(npc, target, magic)
                assertTrue(damage.amount.isPresent)
                assertTrue(damage.isAccurate)
                assertTrue(damage.rawAmount in 0..31)
            }
        }
    }

    @Test
    fun spinesCapAtFiveAndLightningReachesTenTilesAroundPrimary() {
        val npc = queen()
        val primary = player(npc)
        val nearby = (1..7).map { player(npc, 3485 + it) }
        val ranged = KalphiteQueenAttack.selectTargets(npc, primary, nearby, false)
        assertEquals(listOf(primary) + nearby.take(4), ranged)
        assertEquals(listOf(primary) + nearby,
            KalphiteQueenAttack.selectTargets(npc, primary, nearby, true))
    }

    @Test
    fun secondaryTargetsExcludeOtherFloorsDeadPlayersAndSingleCombat() {
        val npc = queen()
        val primary = player(npc)
        val otherFloor = player(npc, z = 2)
        val dead = player(npc).also { `when`(it.isAlive).thenReturn(false) }
        val single = player(npc).also { `when`(it.controllers.contains(MultiCombatAreaListener)).thenReturn(false) }
        val blocked = player(npc).also { `when`(it.controllers.checkCombat(npc)).thenReturn(false) }
        val tooFar = player(npc, x = 3496)
        assertEquals(listOf(primary), KalphiteQueenAttack.selectTargets(npc, primary,
            listOf(primary, otherFloor, dead, single, blocked, tooFar), true))
        `when`(primary.controllers.contains(MultiCombatAreaListener)).thenReturn(false)
        assertEquals(listOf(primary), KalphiteQueenAttack.selectTargets(npc, primary, listOf(player(npc)), true))
    }

    @Test
    fun lightningLaunchQueuesFirstTwoHitsTogetherThenAccumulatesDistance() {
        val npc = queen()
        val primary = player(npc, x = 3495)
        val second = player(npc, x = 3504)
        val third = player(npc, x = 3495, y = 9505)
        val targets = listOf(primary, second, third)
        targets.forEachIndexed { index, player -> `when`(player.index).thenReturn(index + 1) }
        val locator = npc.world.locator
        doReturn(targets.toSet()).`when`(locator)
            .findViewable<Player>(eq(EntityType.PLAYER), same(primary), any())
        KalphiteQueenAttack(npc, primary, true).apply()
        targets.forEach { verify(it, never()).submitAction(any()) }
        for (tick in 0L..3L) {
            `when`(npc.world.currentTick).thenReturn(tick)
            targets.forEach { it.combat.impacts.process() }
            for ((index, target) in targets.withIndex()) {
                val hitTick = if (index < 2) 1 else 3
                verify(target, times(if (tick >= hitTick) 1 else 0)).submitAction(any())
            }
        }
        targets.forEach { target ->
            val action = ArgumentCaptor.forClass(CombatDamageAction::class.java)
            verify(target).submitAction(action.capture())
            assertSame(target, action.value.damage.victim)
            verify(target.skill(Skill.PRAYER), never()).level = anyInt()
            assertTrue(action.value.isInstant)
        }
        assertEquals(4, npc.combat.attackDelay)
    }

    @Test
    fun spineLaunchDrainsOnePrayerOnlyForPositiveUnprotectedHits() {
        val npc = queen()
        val primary = player(npc)
        val protected = player(npc, x = 3486)
        `when`(primary.skill(Skill.PRAYER).level).thenReturn(12)
        `when`(protected.combat.prayers.isActive(CombatPrayer.PROTECT_FROM_MISSILES)).thenReturn(true)
        val locator = npc.world.locator
        doReturn(linkedSetOf(primary, protected)).`when`(locator)
            .findViewable<Player>(eq(EntityType.PLAYER), same(primary), any())
        mockStatic(RandomUtils::class.java).use { random ->
            random.`when`<Boolean> { RandomUtils.roll(2.0) }.thenReturn(true)
            random.`when`<Int> { RandomUtils.inclusive(31) }.thenReturn(7)
            KalphiteQueenAttack(npc, primary, false).apply()
        }
        verify(primary.skill(Skill.PRAYER)).level = 11
        verify(protected.skill(Skill.PRAYER), never()).level = anyInt()
        for (target in listOf(primary, protected)) {
            verify(target, never()).submitAction(any())
            target.combat.impacts.process()
        }
        val action = ArgumentCaptor.forClass(CombatDamageAction::class.java)
        verify(primary).submitAction(action.capture())
        assertEquals(7, action.value.damage.rawAmount)
        verify(protected).submitAction(action.capture())
        assertEquals(0, action.value.damage.rawAmount)
    }
}
