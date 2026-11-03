package game.item.teleport

import api.bot.script.InventoryProductionFixtures
import api.event.Matcher
import api.predef.ctx
import api.predef.scriptMatchers
import api.predef.world as scriptWorld
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Multimap
import io.luna.LunaContext
import io.luna.game.action.Action
import io.luna.game.action.ActionQueue
import io.luna.game.cache.map.MapIndex
import io.luna.game.cache.map.MapIndexTable
import io.luna.game.cache.map.MapTile
import io.luna.game.event.EventMatcherListener
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.task.Task
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Exercises the shared spade script's delayed Mole descent with and without a carried light source.
 *
 * @author lare96
 */
class MoleDigTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initialize() = InventoryProductionFixtures.initialize()
    }

    private val context = mock(LunaContext::class.java, RETURNS_DEEP_STUBS)
    private val world = context.world
    private val player = mock(Player::class.java, RETURNS_DEEP_STUBS)
    private val inventory = player.inventory
    private val actions = ActionQueue(player)
    private val tasks = mutableListOf<Task>()
    private val carried = mutableListOf(Item(952))
    private var position = Position(2984, 3387)
    private var locked = false
    private lateinit var script: Any

    @BeforeEach
    fun setup() {
        `when`(player.world).thenReturn(world)
        `when`(player.isAlive).thenReturn(true)
        `when`(player.state).thenReturn(EntityState.ACTIVE)
        `when`(player.type).thenReturn(EntityType.PLAYER)
        `when`(player.asPlr()).thenReturn(player)
        `when`(player.position).thenAnswer { position }
        `when`(player.actions).thenReturn(actions)
        `when`(player.isLocked).thenAnswer { locked }
        doAnswer { locked = true; null }.`when`(player).lock()
        doAnswer { locked = false; null }.`when`(player).unlock()
        doAnswer { position = it.getArgument(0); null }.`when`(player).move(any(Position::class.java))
        doAnswer { actions.submit(it.getArgument<Action<*>>(0)); null }.`when`(player).submitAction(any())
        `when`(inventory.contains(952)).thenReturn(true)
        doAnswer { carried.iterator() }.`when`(inventory).iterator()
        `when`(player.equipment[Equipment.HEAD]).thenReturn(null)
        doAnswer { tasks += it.getArgument<Task>(0); null }.`when`(world).schedule(any(Task::class.java))

        val first = scriptMatchers.size
        script = Class.forName("game.item.teleport.SpadeDig").getConstructor(Array<String>::class.java)
            .newInstance(emptyArray<String>() as Any)
        val added = scriptMatchers.drop(first)
        val matcher = Matcher.get<ItemFirstClickEvent, Int>()
        @Suppress("UNCHECKED_CAST")
        val registered = Matcher::class.java.getDeclaredField("actions").let {
            it.isAccessible = true
            it.get(matcher) as Multimap<Int, EventMatcherListener<ItemFirstClickEvent>>
        }
        added.forEach { registered.remove(952, it) }
        scriptMatchers.subList(first, scriptMatchers.size).clear()
    }

    private fun dig() {
        script.javaClass.getMethod("digWithSpade", Player::class.java, SpadeDigSpot::class.java)
            .invoke(script, player, SpadeDigSpot.FALADOR_MOLE_HILLS)
    }

    private fun withMoleLanding(block: () -> Unit) {
        val landing = SpadeDigSpot.FALADOR_MOLE_HILLS.landing
        val cache = ctx.cache
        val previousMap = cache.mapIndexTable
        val collision = scriptWorld.collisionManager
        val direction = Direction.NESW.first()
        val beside = landing.translate(1, direction)
        val previousTraversal = collision.traversable(beside, EntityType.PLAYER, direction.opposite())
        val map = mock(MapIndexTable::class.java)
        val indices = ImmutableMap.of(landing.region, mock(MapIndex::class.java))
        `when`(map.indexTable).thenReturn(indices)
        `when`(map.getTile(any(Position::class.java))).thenReturn(MapTile(0, 0, 0, 0, 0, 0, 0, 0, 1))
        `when`(cache.mapIndexTable).thenReturn(map)
        `when`(collision.traversable(beside, EntityType.PLAYER, direction.opposite())).thenReturn(true)
        try {
            block()
        } finally {
            `when`(cache.mapIndexTable).thenReturn(previousMap)
            `when`(collision.traversable(beside, EntityType.PLAYER, direction.opposite())).thenReturn(previousTraversal)
        }
    }

    private fun landMole() {
        fun tick() {
            actions.process()
            actions.normalize()
        }
        assertEquals(1, actions.size())
        tick() // Process the input/submission turn before either full delay tick has elapsed.
        tick()
        assertTrue(locked)
        verify(player, never()).move(any(Position::class.java))
        tick()
        assertEquals(SpadeDigSpot.FALADOR_MOLE_HILLS.landing, position)
        assertEquals(0, actions.size())
        assertFalse(locked)
    }

    @Test
    fun moleDigWithoutLightEntersAfterTheDelay() = withMoleLanding {
        dig()
        assertTrue(tasks.isEmpty())
        verify(player, never()).sendMessage("It's going to be dark down there, I should bring a light source.")
        landMole()
        verify(player).move(SpadeDigSpot.FALADOR_MOLE_HILLS.landing)
        verify(player).sendMessage("You seem to have dropped down into a network of mole tunnels.")
        verify(player).unlock()
    }

    @Test
    fun litMoleDigKeepsItsOwnArrivalMessage() = withMoleLanding {
        carried += Item(33)
        dig()
        landMole()
        verify(player).move(SpadeDigSpot.FALADOR_MOLE_HILLS.landing)
        verify(player).sendMessage("You seem to have dropped down into a network of mole tunnels.")
    }
}
