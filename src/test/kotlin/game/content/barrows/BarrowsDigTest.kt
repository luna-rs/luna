package game.content.barrows

import api.bot.script.InventoryProductionFixtures
import api.event.Matcher
import api.predef.scriptMatchers
import com.google.common.collect.Multimap
import game.item.teleport.SpadeDigSpot
import io.luna.LunaContext
import io.luna.game.event.EventMatcherListener
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.Position
import io.luna.game.model.def.VarpDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.PersistentVarpManager
import io.luna.game.model.mob.varp.Varp
import io.luna.game.task.Task
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Exercises the shared spade script so Barrows initialization does not affect other digging destinations.
 *
 * @author lare96
 */
class BarrowsDigTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initialize() {
            InventoryProductionFixtures.initialize()
            for (id in listOf(452, 453)) {
                if (VarpDefinition.ALL.get(id).isEmpty) VarpDefinition.ALL.storeDefinition(VarpDefinition(id, 0))
            }
        }
    }

    private val context = mock(LunaContext::class.java, RETURNS_DEEP_STUBS)
    private val world = context.world
    private val player = mock(Player::class.java, RETURNS_DEEP_STUBS)
    private val inventory = player.inventory
    private val manager = PersistentVarpManager(player)
    private val tasks = mutableListOf<Task>()
    private val carried = mutableListOf(Item(952))
    private lateinit var script: Any

    @BeforeEach
    fun setup() {
        `when`(player.world).thenReturn(world)
        `when`(player.varpManager).thenReturn(manager)
        `when`(player.cachedVarps).thenReturn(HashMap())
        `when`(player.isAlive).thenReturn(true)
        `when`(player.position).thenReturn(Position(2984, 3387))
        `when`(inventory.contains(952)).thenReturn(true)
        doAnswer { carried.iterator() }.`when`(inventory).iterator()
        `when`(player.equipment[Equipment.HEAD]).thenReturn(null)
        doAnswer { tasks += it.getArgument<Task>(0); null }.`when`(world).schedule(any(Task::class.java))

        // Load the real script, then remove only its added matcher so this fixture cannot duplicate live handlers.
        val first = scriptMatchers.size
        script = Class.forName("game.item.teleport.SpadeDig").getConstructor(Array<String>::class.java)
            .newInstance(emptyArray<String>() as Any)
        val added = scriptMatchers.drop(first)
        val matcher = Matcher.get<ItemFirstClickEvent, Int>()
        @Suppress("UNCHECKED_CAST")
        val actions = Matcher::class.java.getDeclaredField("actions").let {
            it.isAccessible = true
            it.get(matcher) as Multimap<Int, EventMatcherListener<ItemFirstClickEvent>>
        }
        added.forEach { actions.remove(952, it) }
        scriptMatchers.subList(first, scriptMatchers.size).clear()
    }

    private fun dig(spot: SpadeDigSpot?) {
        script.javaClass.getMethod("digWithSpade", Player::class.java, SpadeDigSpot::class.java)
            .invoke(script, player, spot)
    }

    private fun land() {
        val task = tasks.single()
        assertEquals(2, task.delay)
        task.javaClass.getDeclaredMethod("execute").apply { isAccessible = true }.invoke(task)
        tasks.clear()
    }

    @Test
    fun allSixCryptDigsInitializeOnlyWhenTheLivingPlayerLands() {
        for (brother in BarrowsBrother.entries) {
            manager.setValue(PersistentVarp.BARROWS, 0)
            manager.setValue(PersistentVarp.BARROWS_KILLS, 123)
            clearInvocations(player)
            dig(brother.digSpot)
            assertEquals(0, BarrowsRun.state(player))
            assertEquals(123, manager.getValue(PersistentVarp.BARROWS_KILLS))
            verify(player, never()).move(any(Position::class.java))
            verify(player, never()).sendMessage("You've broken into a crypt!")
            land()
            assertNotEquals(0, BarrowsRun.state(player))
            assertNotNull(BarrowsRun.chamber(player))
            assertNotNull(BarrowsRun.tunnelBrother(player))
            assertEquals(0, manager.getValue(PersistentVarp.BARROWS_KILLS))
            verify(player).sendMessage("You've broken into a crypt!")
            verify(player).move(brother.digSpot.landing)
            verify(player).unlock()
        }
    }

    @Test
    fun moleDigWithoutLightDoesNotScheduleEntryOrTouchBarrowsProgress() {
        dig(SpadeDigSpot.FALADOR_MOLE_HILLS)
        assertTrue(tasks.isEmpty())
        verify(player).sendMessage("It's going to be dark down there, I should bring a light source.")
        verify(player, never()).lock()
        verify(player, never()).move(any(Position::class.java))
        verify(player, never()).sendVarp(any(Varp::class.java))
        verify(player, never()).sendMessage("You've broken into a crypt!")
        assertEquals(0, BarrowsRun.state(player))
    }

    @Test
    fun litMoleDigKeepsItsOwnArrivalMessageAndPreservesALootedBarrowsRun() {
        carried += Item(33)
        val savedRun = BarrowsRun.COLLAPSING_MASK or 64
        manager.setValue(PersistentVarp.BARROWS, savedRun)
        manager.setValue(PersistentVarp.BARROWS_KILLS, 123)
        dig(SpadeDigSpot.FALADOR_MOLE_HILLS)
        land()
        verify(player).move(SpadeDigSpot.FALADOR_MOLE_HILLS.landing)
        verify(player).sendMessage("You seem to have dropped down into a network of mole tunnels.")
        verify(player, never()).sendMessage("You've broken into a crypt!")
        verify(player, never()).sendVarp(any(Varp::class.java))
        assertEquals(savedRun, BarrowsRun.state(player))
        assertEquals(123, manager.getValue(PersistentVarp.BARROWS_KILLS))
    }

    @Test
    fun dyingDuringTheDigDelayDoesNotStartARunOrMoveThePlayer() {
        dig(SpadeDigSpot.AHRIM_MOUND)
        `when`(player.isAlive).thenReturn(false)
        land()
        verify(player).unlock()
        verify(player, never()).move(any(Position::class.java))
        verify(player, never()).sendMessage("You've broken into a crypt!")
        verify(player, never()).sendVarp(any(Varp::class.java))
        assertEquals(0, BarrowsRun.state(player))
    }
}
