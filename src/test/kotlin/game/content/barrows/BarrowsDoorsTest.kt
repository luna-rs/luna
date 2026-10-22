package game.content.barrows

import io.luna.LunaContext
import io.luna.game.model.EntityState
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.chunk.Chunk
import io.luna.game.model.chunk.ChunkRepository
import io.luna.game.model.chunk.ChunkUpdatableRequest
import io.luna.game.model.def.VarpDefinition
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.PersistentVarpManager
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.game.task.Task
import io.luna.net.msg.out.AddObjectMessageWriter
import io.luna.net.msg.out.RemoveObjectMessageWriter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.function.Predicate

/**
 * Checks paired geometry, personal lock enforcement, animation visibility and delayed restoration.
 *
 * @author lare96
 */
class BarrowsDoorsTest {
    private val context = mock(LunaContext::class.java, RETURNS_DEEP_STUBS)
    private val world = context.world
    private val player = mock(Player::class.java, RETURNS_DEEP_STUBS)
    private val manager = PersistentVarpManager(player)
    private val overlays = AbstractOverlaySet(player)
    private val repository = mock(ChunkRepository::class.java)
    private val updates = mutableListOf<ChunkUpdatableRequest>()
    private val tasks = mutableListOf<Task>()
    private val objects = mutableListOf<GameObject>()
    private val tile = Position(3550, 9690)

    @BeforeEach
    fun setup() {
        if (VarpDefinition.ALL.get(452).isEmpty) VarpDefinition.ALL.storeDefinition(VarpDefinition(452, 0))
        if (WidgetDefinition.ALL.get(4543).isEmpty) {
            val definition = mock(WidgetDefinition::class.java)
            `when`(definition.id()).thenReturn(4543)
            WidgetDefinition.ALL.storeDefinition(definition)
        }
        `when`(player.context).thenReturn(context)
        `when`(player.world).thenReturn(world)
        `when`(player.attributes()).thenReturn(AttributeMap())
        `when`(player.overlays).thenReturn(overlays)
        `when`(player.varpManager).thenReturn(manager)
        `when`(player.position).thenReturn(tile)
        `when`(player.state).thenReturn(EntityState.ACTIVE)
        `when`(player.isAlive).thenReturn(true)
        `when`(world.chunks.load(any(Chunk::class.java))).thenReturn(repository)
        doAnswer { updates += it.getArgument<ChunkUpdatableRequest>(0); null }
            .`when`(repository).queueUpdate(any(ChunkUpdatableRequest::class.java))
        doAnswer { tasks += it.getArgument<Task>(0); null }.`when`(world).schedule(any(Task::class.java))
        val locator = world.locator
        doAnswer { call ->
            val position = call.getArgument<Locatable>(0).abs()
            val predicate = call.getArgument<Predicate<GameObject>>(1)
            objects.filter { it.position == position && predicate.test(it) }.toSet()
        }.`when`(locator).findObjectsOnTile(any(Locatable::class.java), any())
    }

    private fun pair(door: BarrowsDoor, direction: ObjectDirection, left: Boolean): GameObject {
        val selected = GameObject.createStatic(context, if (left) door.leftId else door.rightId,
                                               tile, ObjectType.STRAIGHT_WALL, direction)
        val counterpart = GameObject.createStatic(context, if (left) door.rightId else door.leftId,
            BarrowsDoors.otherPosition(tile, direction, left), ObjectType.STRAIGHT_WALL, direction)
        objects.clear()
        objects += selected
        objects += counterpart
        return selected
    }

    private fun restore() {
        val task = tasks.last()
        assertEquals(3, task.delay)
        task.javaClass.getDeclaredMethod("execute").apply { isAccessible = true }.invoke(task)
    }

    @Test
    fun bothLeavesCrossInEitherDirectionForEveryOrientationAndRestorePrivately() {
        val outside = listOf(Position(3549, 9690), Position(3550, 9691),
                             Position(3551, 9690), Position(3550, 9689))
        for (angle in 0..3) {
            val direction = ObjectDirection.ALL[angle]!!
            for (left in listOf(false, true)) {
                for (reverse in listOf(false, true)) {
                    val selected = pair(BarrowsDoor.A, direction, left)
                    manager.setValue(PersistentVarp.BARROWS, 64)
                    `when`(player.position).thenReturn(if (reverse) outside[angle] else tile)
                    clearInvocations(player)
                    updates.clear()
                    BarrowsDoors.cross(player, selected, BarrowsDoor.A)
                    verify(player).move(if (reverse) tile else outside[angle])
                    assertEquals(4, updates.size)
                    assertTrue(updates.take(2).all { it.message is RemoveObjectMessageWriter })
                    assertTrue(updates.drop(2).all { it.message is AddObjectMessageWriter })
                    val visuals = updates.drop(2).map { it.updatable as GameObject }
                    assertEquals(outside[angle], visuals.first().position)
                    assertEquals((angle + if (left) 3 else 1) % 4, visuals.first().direction.id)
                    assertEquals((angle + 2) % 4, visuals.last().direction.id)
                    assertEquals(if (left) listOf(6732, 6713) else listOf(6713, 6732), visuals.map { it.id })
                    val count = tasks.size
                    BarrowsDoors.cross(player, selected, BarrowsDoor.A)
                    assertEquals(count, tasks.size)
                    restore()
                    assertEquals(8, updates.size)
                    val stranger = mock(Player::class.java)
                    for (update in updates) {
                        assertFalse(update.isPersistent)
                        assertTrue(update.updatable.computeCurrentView().isViewableFor(player))
                        assertFalse(update.updatable.computeCurrentView().isViewableFor(stranger))
                    }
                    verifyNoInteractions(world.objects, world.collisionManager)
                }
            }
        }
    }

    @Test
    fun eachDoorUsesItsOwnLockAndChestDoorsRequireThePuzzle() {
        for (door in BarrowsDoor.entries) {
            for (left in listOf(false, true)) {
                val selected = pair(door, ObjectDirection.WEST, left)
                clearInvocations(player)
                manager.setValue(PersistentVarp.BARROWS, door.locked.write(64, 1))
                BarrowsDoors.cross(player, selected, door)
                verify(player, never()).move(any(Position::class.java))
                assertNull(overlays.getOverlay(BarrowsPuzzleInterface::class.java))
                manager.setValue(PersistentVarp.BARROWS, 64)
                BarrowsDoors.cross(player, selected, door)
                if (door.leadsToChest) {
                    assertNotNull(overlays.getOverlay(BarrowsPuzzleInterface::class.java))
                    verify(player, never()).move(any(Position::class.java))
                    overlays.closeWindows()
                    manager.setValue(PersistentVarp.BARROWS, BarrowsVarbit.PUZZLE.write(64, 3))
                    BarrowsDoors.cross(player, selected, door)
                }
                verify(player).move(Position(3549, 9690))
                restore()
            }
        }
    }

    @Test
    fun missingCounterpartAndRemoteClicksDoNothing() {
        manager.setValue(PersistentVarp.BARROWS, 64)
        val selected = pair(BarrowsDoor.A, ObjectDirection.WEST, true)
        objects.removeLast()
        BarrowsDoors.cross(player, selected, BarrowsDoor.A)
        pair(BarrowsDoor.A, ObjectDirection.WEST, true)
        `when`(player.position).thenReturn(tile.translate(5, 0))
        BarrowsDoors.cross(player, selected, BarrowsDoor.A)
        assertTrue(updates.isEmpty())
        assertTrue(tasks.isEmpty())
        verify(player, never()).move(any(Position::class.java))
    }
}
