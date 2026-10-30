package api.combat.death

import api.combat.death.dsl.DeathHookReceiver
import io.luna.LunaContext
import io.luna.game.model.LocalSound
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.MobDeathAction.DeathStage
import io.luna.game.model.mob.Npc
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Covers death cries for default and custom NPC lifecycles, including deaths without a player killer.
 *
 * @author lare96
 */
class NpcDeathSoundTest {
    private val previousDefault = DeathHookHandler.defaultNpcHook
    private val context = mock(LunaContext::class.java)
    private val position = Position(3200, 3200)
    private val npc = mock(Npc::class.java, RETURNS_DEEP_STUBS)
    private val sound = mock(LocalSound::class.java)
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
        `when`(npc.context).thenReturn(context)
        `when`(npc.position).thenReturn(position)
        `when`(npc.combatDef().deathSound).thenReturn(0)
        DeathHookHandler.defaultNpcHook = { defaultCalls++ }
    }

    @AfterEach
    fun restoreHooks() {
        hooks.clear()
        hooks.putAll(previousHooks)
        DeathHookHandler.defaultNpcHook = previousDefault
    }

    @Test
    fun defaultDeathPlaysSoundZeroOnceAtTheOriginalPosition() {
        mockStatic(LocalSound::class.java).use { sounds ->
            sounds.`when`<LocalSound> {
                LocalSound.ofId(context, 0, position, ChunkUpdatableView.globalView(), 12, 1)
            }.thenReturn(sound)
            DeathStage.entries.forEach { DeathHookHandler.onDeath(npc, null, it) }
            sounds.verify {
                LocalSound.ofId(context, 0, position, ChunkUpdatableView.globalView(), 12, 1)
            }
            verify(sound).display()
            sounds.verifyNoMoreInteractions()
        }
        assertEquals(3, defaultCalls)
    }

    @Test
    fun customHookKeepsTheDeathCryWithoutAKillerOrDefaultHook() {
        var customCalls = 0
        DeathHookHandler.addNpcHook(999999) {
            assertSame(npc, victim)
            assertNull(source)
            customCalls++
        }
        mockStatic(LocalSound::class.java).use { sounds ->
            sounds.`when`<LocalSound> {
                LocalSound.ofId(context, 0, position, ChunkUpdatableView.globalView(), 12, 1)
            }.thenReturn(sound)
            DeathStage.entries.forEach { DeathHookHandler.onDeath(npc, null, it) }
            sounds.verify {
                LocalSound.ofId(context, 0, position, ChunkUpdatableView.globalView(), 12, 1)
            }
            verify(sound).display()
            sounds.verifyNoMoreInteractions()
        }
        assertEquals(3, customCalls)
        assertEquals(0, defaultCalls)
    }

    @Test
    fun explicitSilenceSkipsPlaybackAndPreservesLifecycleDispatch() {
        `when`(npc.combatDef().deathSound).thenReturn(-1)
        mockStatic(LocalSound::class.java).use { sounds ->
            DeathStage.entries.forEach { DeathHookHandler.onDeath(npc, null, it) }
            sounds.verifyNoInteractions()
        }
        assertEquals(3, defaultCalls)
    }
}
