package api.combat.death

import api.combat.death.dsl.DeathHookReceiver
import io.luna.game.model.EntityState
import io.luna.game.model.LocalSound
import io.luna.game.model.Position
import io.luna.game.model.mob.MobDeathAction.DeathStage
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.net.msg.out.SoundMessageWriter
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.MockedStatic
import org.mockito.Mockito.*

/**
 * Covers private NPC death cries and the distance and lifecycle rules selecting their combat recipient.
 *
 * @author lare96
 */
class NpcDeathSoundTest {
    private val previousDefault = DeathHookHandler.defaultNpcHook
    private val position = Position(3200, 3200)
    private val npc = mock(Npc::class.java, RETURNS_DEEP_STUBS)
    private val player = mock(Player::class.java)
    private lateinit var sounds: MockedStatic<LocalSound>
    private var defaultCalls = 0

    @Suppress("UNCHECKED_CAST")
    private val hooks = DeathHookHandler::class.java.getDeclaredField("npcHooks").let {
        it.isAccessible = true
        it.get(DeathHookHandler) as MutableMap<Int, DeathHookReceiver<Npc>.() -> Unit>
    }
    private val previousHooks = hooks.toMap()

    @BeforeEach
    fun setup() {
        `when`(npc.id).thenReturn(999999)
        `when`(npc.position).thenReturn(position)
        `when`(npc.combatDef().deathSound).thenReturn(0)
        `when`(npc.combat.lastAttackingPlayer).thenReturn(player)
        `when`(player.position).thenReturn(position)
        `when`(player.state).thenReturn(EntityState.ACTIVE)
        DeathHookHandler.defaultNpcHook = { defaultCalls++ }
        sounds = mockStatic(LocalSound::class.java)
    }

    @AfterEach
    fun restoreHooks() {
        hooks.clear()
        hooks.putAll(previousHooks)
        DeathHookHandler.defaultNpcHook = previousDefault
        try {
            sounds.verifyNoInteractions()
        } finally {
            sounds.close()
        }
    }

    @Test
    fun defaultDeathPlaysSoundZeroOnceToTheLastAttackingPlayer() {
        DeathStage.entries.forEach { DeathHookHandler.onDeath(npc, null, it) }

        verifyPrivateSound(0)
        assertEquals(3, defaultCalls)
    }

    @Test
    fun customHookKeepsThePrivateDeathCryWithoutAKillerOrDefaultHook() {
        var customCalls = 0
        DeathHookHandler.addNpcHook(999999) {
            assertSame(npc, victim)
            assertNull(source)
            customCalls++
        }

        DeathStage.entries.forEach { DeathHookHandler.onDeath(npc, null, it) }

        verifyPrivateSound(0)
        assertEquals(3, customCalls)
        assertEquals(0, defaultCalls)
    }

    @Test
    fun twelveTileDiagonalBoundaryIsAudible() {
        `when`(player.position).thenReturn(Position(3212, 3212))

        DeathHookHandler.onDeath(npc, null, DeathStage.PRE_DEATH)

        verifyPrivateSound(0)
    }

    @Test
    fun distanceCheckDoesNotRequireTheSamePlane() {
        `when`(player.position).thenReturn(Position(3212, 3212, 1))

        DeathHookHandler.onDeath(npc, null, DeathStage.PRE_DEATH)

        verifyPrivateSound(0)
    }

    @Test
    fun beyondTwelveTilesDoesNotPlayToTheAttackerOrNearbyKiller() {
        `when`(player.position).thenReturn(Position(3213, 3200))
        val killer = mock(Player::class.java)

        DeathHookHandler.onDeath(npc, killer, DeathStage.PRE_DEATH)

        verify(player, never()).queue(any())
        verify(killer, never()).queue(any())
    }

    @Test
    fun noPlayerAttackerDoesNotFallBackToTheHighestDamageKiller() {
        `when`(npc.combat.lastAttackingPlayer).thenReturn(null)
        val killer = mock(Player::class.java)

        DeathHookHandler.onDeath(npc, killer, DeathStage.PRE_DEATH)

        verify(player, never()).queue(any())
        verify(killer, never()).queue(any())
    }

    @Test
    fun npcAttackerDoesNotProduceAPublicDeathCry() {
        `when`(npc.combat.lastAttackingPlayer).thenReturn(null)
        val attacker = mock(Npc::class.java)
        `when`(npc.combat.lastCombatWith).thenReturn(attacker)

        DeathHookHandler.onDeath(npc, attacker, DeathStage.PRE_DEATH)

        verify(player, never()).queue(any())
    }

    @Test
    fun disconnectedAttackerDoesNotReceiveTheDeathCry() {
        `when`(player.state).thenReturn(EntityState.INACTIVE)

        DeathHookHandler.onDeath(npc, null, DeathStage.PRE_DEATH)

        verify(player, never()).queue(any())
    }

    @Test
    fun highestDamageKillerDoesNotReplaceTheLastAttackingPlayer() {
        val killer = mock(Player::class.java)

        DeathHookHandler.onDeath(npc, killer, DeathStage.PRE_DEATH)

        verifyPrivateSound(0)
        verify(killer, never()).queue(any())
    }

    @Test
    fun explicitSilenceSkipsPlaybackAndPreservesLifecycleDispatch() {
        `when`(npc.combatDef().deathSound).thenReturn(-1)

        DeathStage.entries.forEach { DeathHookHandler.onDeath(npc, null, it) }

        verify(player, never()).queue(any())
        assertEquals(3, defaultCalls)
    }

    /**
     * Checks that the selected player receives exactly one ordinary synth packet with one playback and no delay.
     */
    private fun verifyPrivateSound(id: Int) {
        val packet = ArgumentCaptor.forClass(SoundMessageWriter::class.java)
        verify(player).queue(packet.capture())
        val buffer = Unpooled.buffer()
        try {
            val message = packet.value.write(player, buffer)
            assertEquals(26, message.opcode)
            assertEquals(id, buffer.readUnsignedShort())
            assertEquals(1, buffer.readUnsignedByte().toInt())
            assertEquals(0, buffer.readUnsignedShort())
            assertFalse(buffer.isReadable)
        } finally {
            buffer.release()
        }
    }
}
