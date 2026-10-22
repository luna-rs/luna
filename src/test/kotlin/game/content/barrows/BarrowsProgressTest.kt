package game.content.barrows

import api.combat.death.DeathHookHandler
import api.combat.death.dsl.DeathHookReceiver
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.VarpDefinition
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.MobDeathAction.DeathStage
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.PersistentVarpManager
import io.luna.game.model.mob.varp.Varp
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exercises the registered tunnel death hooks and Luna's saved-varp round trip.
 *
 * @author lare96
 */
class BarrowsProgressTest {
    private lateinit var player: Player
    private lateinit var manager: PersistentVarpManager
    private val sent = mutableListOf<Varp>()
    private var defaultCalls = 0
    private val previousDefault = DeathHookHandler.defaultNpcHook

    @Suppress("UNCHECKED_CAST")
    private val hooks = DeathHookHandler::class.java.getDeclaredField("npcHooks").let {
        it.isAccessible = true
        it.get(DeathHookHandler) as MutableMap<Int, DeathHookReceiver<Npc>.() -> Unit>
    }
    private val previousHooks = hooks.toMap()

    @BeforeEach
    fun setup() {
        for (varp in PersistentVarp.entries) {
            if (VarpDefinition.ALL.get(varp.clientId).isEmpty) {
                VarpDefinition.ALL.storeDefinition(VarpDefinition(varp.clientId, 0))
            }
        }
        player = mock(Player::class.java)
        manager = PersistentVarpManager(player)
        `when`(player.varpManager).thenReturn(manager)
        `when`(player.cachedVarps).thenReturn(HashMap())
        `when`(player.position).thenReturn(Position(3551, 9694))
        `when`(player.state).thenReturn(EntityState.ACTIVE)
        doAnswer {
            sent += it.getArgument<Varp>(0)
            null
        }.`when`(player).sendVarp(any(Varp::class.java))
        DeathHookHandler.defaultNpcHook = { defaultCalls++ }
        Class.forName("game.content.barrows.BarrowsKills").getConstructor(Array<String>::class.java)
            .newInstance(emptyArray<String>() as Any)
    }

    @AfterEach
    fun restoreHooks() {
        hooks.clear()
        hooks.putAll(previousHooks)
        DeathHookHandler.defaultNpcHook = previousDefault
    }

    private fun npc(id: Int, position: Position = Position(3551, 9695)): Npc {
        val npc = mock(Npc::class.java)
        `when`(npc.id).thenReturn(id)
        `when`(npc.position).thenReturn(position)
        return npc
    }

    private fun count(): Int = BarrowsVarbit.KILL_COUNT.read(manager.getValue(PersistentVarp.BARROWS_KILLS))

    @Test
    fun monsterDataMatchesRevision377Cache() {
        val definitions = Files.newBufferedReader(Path.of("data/dumps/npcs.json")).use {
            JsonParser.parseReader(it).asJsonArray.filter { entry -> !entry.isJsonNull }.associate { entry ->
                val definition = entry.asJsonObject
                definition.get("id").asInt to definition
            }
        }
        assertEquals((2031..2037).toList(), BarrowsMonster.entries.map { it.npcId })
        for (monster in BarrowsMonster.entries) {
            assertEquals(definitions.getValue(monster.npcId).get("combat_level").asInt, monster.combatLevel)
        }
    }

    @Test
    fun allSevenMonstersCountOncePerDeathAndKeepDefaultLifecycle() {
        for ((index, monster) in BarrowsMonster.entries.withIndex()) {
            val victim = npc(monster.npcId)
            DeathHookHandler.onDeath(victim, player, DeathStage.PRE_DEATH)
            assertEquals(index, count())
            DeathHookHandler.onDeath(victim, player, DeathStage.DEATH)
            assertEquals(index + 1, count())
            DeathHookHandler.onDeath(victim, player, DeathStage.POST_DEATH)
            assertEquals(index + 1, count())
        }
        assertEquals(21, defaultCalls)
        assertEquals(7, sent.size)
        assertEquals(460, BarrowsVarbit.MONSTER_SCORE.read(sent.last().value))
        assertEquals(sent.last().value, player.cachedVarps[453])
    }

    @Test
    fun progressIsUpdatedBeforeDefaultDeathMovesTheNpc() {
        val victim = npc(2031)
        DeathHookHandler.defaultNpcHook = {
            death {
                assertEquals(1, count())
                `when`(victim.position).thenReturn(Position(1, 1))
                defaultCalls++
            }
        }
        DeathHookHandler.onDeath(victim, player, DeathStage.DEATH)
        assertEquals(1, defaultCalls)
        assertEquals(1, sent.size)
    }

    @Test
    fun invalidKillAttributionAndOutsideKillsKeepDefaultDeathWithoutCredit() {
        val victim = npc(2031)
        for (source in listOf<Mob?>(null, npc(1))) {
            DeathHookHandler.onDeath(victim, source, DeathStage.DEATH)
        }
        `when`(player.state).thenReturn(EntityState.INACTIVE)
        DeathHookHandler.onDeath(victim, player, DeathStage.DEATH)
        `when`(player.state).thenReturn(EntityState.ACTIVE)
        `when`(player.position).thenReturn(Position(3563, 3289))
        DeathHookHandler.onDeath(victim, player, DeathStage.DEATH)
        `when`(player.position).thenReturn(Position(3551, 9694))
        DeathHookHandler.onDeath(npc(2031, Position(3563, 3289)), player, DeathStage.DEATH)
        DeathHookHandler.onDeath(npc(86), player, DeathStage.DEATH)
        assertEquals(0, count())
        assertTrue(sent.isEmpty())
        assertEquals(6, defaultCalls)
    }

    @Test
    fun capsScoreAndCountWhilePreservingBrotherAndChestFlags() {
        var value = BarrowsVarbit.MONSTER_SCORE.write(0, 990)
        value = BarrowsVarbit.KILL_COUNT.write(value, 32766)
        value = BarrowsVarbit.AHRIM_KILLED.write(value, 1)
        value = BarrowsVarbit.CHEST_OPEN.write(value, 1)
        manager.setValue(PersistentVarp.BARROWS_KILLS, value)
        repeat(2) { DeathHookHandler.onDeath(npc(2031), player, DeathStage.DEATH) }
        val result = manager.getValue(PersistentVarp.BARROWS_KILLS)
        assertEquals(1000, BarrowsVarbit.MONSTER_SCORE.read(result))
        assertEquals(32767, count())
        assertEquals(1, BarrowsVarbit.AHRIM_KILLED.read(result))
        assertEquals(0, BarrowsVarbit.DHAROK_KILLED.read(result))
        assertEquals(1, BarrowsVarbit.CHEST_OPEN.read(result))
        assertTrue(result < 0)
        assertEquals(listOf(result, result), sent.map { it.value })
    }

    @Test
    fun savedProgressSurvivesSurfaceLoginAndRestoresThroughExistingVarpReplay() {
        manager.setValue(PersistentVarp.BARROWS_KILLS, BarrowsVarbit.KILL_COUNT.write(0, 16383))
        BarrowsProgress.recordMonsterKill(player, BarrowsMonster.BLOODWORM)
        val expected = manager.getValue(PersistentVarp.BARROWS_KILLS)
        assertTrue(expected < 0)
        assertEquals(16384, count())
        val gson = Gson()
        val json = gson.toJson(manager.toMap())
        val saved = gson.fromJson<Map<String, Int>>(json, object : TypeToken<Map<String, Int>>() {}.type)
        val restored = PersistentVarpManager(player)
        restored.fromMap(saved)
        `when`(player.position).thenReturn(Position(3563, 3289))
        // Saving again before entering Barrows must retain progress.
        assertEquals(expected, restored.toMap()["BARROWS_KILLS"])
        sent.clear()
        restored.sendAllValues()
        assertEquals(expected, sent.single { it.id == 453 }.value)
    }

    @Test
    fun existingCharactersWithoutBarrowsDataStartAtZero() {
        manager.fromMap(mapOf("BRIGHTNESS_LEVEL" to 3))
        assertEquals(0, count())
        BarrowsProgress.recordMonsterKill(player, BarrowsMonster.CRYPT_RAT)
        assertEquals(1, count())
        assertEquals(43, BarrowsVarbit.MONSTER_SCORE.read(sent.single().value))
        assertEquals(3, manager.getValue(PersistentVarp.BRIGHTNESS_LEVEL))
    }
}
