package game.content.randomEvents

import api.bot.script.InventoryProductionFixtures
import api.predef.SKILL_WOODCUTTING
import io.luna.Luna
import io.luna.LunaContext
import io.luna.LunaSettings
import io.luna.game.action.Action
import io.luna.game.action.ActionState
import io.luna.game.action.ActionType
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.chunk.Chunk
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.collision.CollisionFlag
import io.luna.game.model.collision.CollisionManager
import io.luna.game.model.collision.CollisionMatrix
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.game.task.Task
import game.skill.woodcutting.cutTree.Axe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito.*

/**
 * Exercises Ent ownership, delayed restoration and tool damage with native footprint reach checks.
 * The registries stand in for a server; collision, inventories and player actions run normally.
 *
 * @author lare96
 */
class WoodcuttingEntTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initialize() = InventoryProductionFixtures.initialize()
    }

    private lateinit var luna: MockedStatic<Luna>
    private lateinit var npcs: MockedConstruction<Npc>
    private lateinit var objects: MockedConstruction<GameObject>
    private lateinit var fixtureContext: LunaContext
    private lateinit var fixtureWorld: World
    private lateinit var matrix: CollisionMatrix
    private val liveObjects = mutableListOf<GameObject>()
    private val liveNpcs = mutableListOf<Npc>()
    private val tasks = mutableListOf<Task>()
    private var npcSize = 2
    private val origin = Position(3200, 3200)

    @BeforeEach
    fun prepare() {
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        luna = mockStatic(Luna::class.java, CALLS_REAL_METHODS)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
        fixtureWorld = mock(World::class.java, RETURNS_DEEP_STUBS)
        fixtureContext = mock(LunaContext::class.java, RETURNS_DEEP_STUBS)
        `when`(fixtureContext.world).thenReturn(fixtureWorld)
        `when`(fixtureWorld.context).thenReturn(fixtureContext)
        val chunks = fixtureWorld.chunks
        val repository = mock(io.luna.game.model.chunk.ChunkRepository::class.java)
        val matrices = CollisionMatrix.createMatrices(4, 8, 8)
        matrix = matrices[0]
        `when`(repository.matrices).thenReturn(matrices)
        `when`(repository.snapshot).thenReturn(matrices)
        `when`(chunks.load(any(Chunk::class.java))).thenReturn(repository)
        `when`(chunks.load(any(Position::class.java))).thenReturn(repository)
        val collision = CollisionManager(fixtureWorld)
        `when`(fixtureWorld.collisionManager).thenReturn(collision)
        npcs = mockConstruction(Npc::class.java, withSettings().defaultAnswer(RETURNS_DEEP_STUBS)) { npc, args ->
            val position = args.arguments()[2] as Position
            `when`(npc.position).thenReturn(position)
            `when`(npc.abs()).thenReturn(position)
            `when`(npc.world).thenReturn(fixtureWorld)
            `when`(npc.context).thenReturn(fixtureContext)
            `when`(npc.size()).thenReturn(npcSize)
            `when`(npc.state).thenReturn(EntityState.NEW)
        }
        objects = mockConstruction(GameObject::class.java) { obj, args ->
            configureObject(obj, args.arguments()[1] as Int, args.arguments()[2] as Position,
                            args.arguments()[3] as ObjectType, args.arguments()[4] as ObjectDirection,
                            args.arguments()[5] as ChunkUpdatableView, EntityState.NEW)
        }
        val objectList = fixtureWorld.objects
        val npcList = fixtureWorld.npcs
        doAnswer { invocation ->
            val obj = invocation.getArgument<GameObject>(0)
            liveObjects += obj
            `when`(obj.state).thenReturn(EntityState.ACTIVE)
            true
        }.`when`(objectList).register(any(GameObject::class.java))
        doAnswer { invocation ->
            val obj = invocation.getArgument<GameObject>(0)
            liveObjects.remove(obj).also { if (it) `when`(obj.state).thenReturn(EntityState.INACTIVE) }
        }.`when`(objectList).unregister(any(GameObject::class.java))
        doAnswer { invocation ->
            val position = invocation.getArgument<Position>(0)
            liveObjects.filter { it.position == position }.stream()
        }.`when`(objectList).findAll(any(Position::class.java))
        doAnswer { invocation ->
            val npc = invocation.getArgument<Npc>(0)
            liveNpcs += npc
            `when`(npc.state).thenReturn(EntityState.ACTIVE)
            true
        }.`when`(npcList).add(any(Npc::class.java))
        doAnswer { invocation ->
            val npc = invocation.getArgument<Npc>(0)
            liveNpcs.remove(npc).also { if (it) `when`(npc.state).thenReturn(EntityState.INACTIVE) }
        }.`when`(npcList).remove(any(Npc::class.java))
        doAnswer { invocation -> tasks += invocation.getArgument<Task>(0); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
    }

    @AfterEach
    fun restore() {
        npcs.constructed().toList().forEach { WoodcuttingRandomEvents.finishEnt(it) }
        objects.close()
        npcs.close()
        luna.close()
    }

    private fun configureObject(obj: GameObject, id: Int, position: Position, type: ObjectType,
                                direction: ObjectDirection, view: ChunkUpdatableView, state: EntityState) {
        `when`(obj.id).thenReturn(id)
        `when`(obj.position).thenReturn(position)
        `when`(obj.world).thenReturn(fixtureWorld)
        `when`(obj.context).thenReturn(fixtureContext)
        `when`(obj.objectType).thenReturn(type)
        `when`(obj.direction).thenReturn(direction)
        `when`(obj.view).thenReturn(view)
        `when`(obj.state).thenReturn(state)
        `when`(obj.replaces(any(GameObject::class.java))).thenAnswer { invocation ->
            val other = invocation.getArgument<GameObject>(0)
            position == other.position && type == other.objectType && view == other.view
        }
    }

    private fun player(): Bot = InventoryProductionFixtures.bot().also { player ->
        `when`(player.world).thenReturn(fixtureWorld)
        `when`(player.context).thenReturn(fixtureContext)
        doReturn(origin.translate(-1, 0)).`when`(player).position
        `when`(player.abs()).thenAnswer { player.position }
        `when`(player.size()).thenReturn(1)
        `when`(player.isAlive).thenReturn(true)
        `when`(player.skill(SKILL_WOODCUTTING).level).thenReturn(99)
        player.inventory[0] = Item(Axe.RUNE.id)
        doAnswer { invocation -> player.actions.submit(invocation.getArgument<Action<*>>(0)); null }
            .`when`(player).submitAction(any())
    }

    private fun tree(size: Int, id: Int = if (size == 2) 1276 else 1281): GameObject {
        val obj = mock(GameObject::class.java)
        val definition = mock(GameObjectDefinition::class.java)
        `when`(definition.sizeX).thenReturn(size)
        `when`(definition.sizeY).thenReturn(size)
        configureObject(obj, id, origin, ObjectType.DEFAULT, ObjectDirection.WEST,
                        ChunkUpdatableView.globalView(), EntityState.ACTIVE)
        `when`(obj.def()).thenReturn(definition)
        liveObjects += obj
        npcSize = size
        return obj
    }

    private fun expire(task: Task) {
        task.javaClass.getDeclaredMethod("execute").apply { isAccessible = true }.invoke(task)
    }

    @Test
    fun transformationOwnsItsBlockersForSixtyTicksAndRestoresAfterThePlayerLogsOut() {
        val player = player()
        val tree = tree(3)
        assertTrue(WoodcuttingRandomEvents.startEnt(player, tree))
        val ent = liveNpcs.single()
        val blockers = liveObjects.toList()
        assertEquals(EntityState.INACTIVE, tree.state)
        assertEquals(4, blockers.size)
        val expiry = tasks.single { it.delay == 60 }
        `when`(player.state).thenReturn(EntityState.INACTIVE)
        expire(expiry)
        assertEquals(EntityState.INACTIVE, ent.state)
        assertTrue(blockers.all { it.state == EntityState.INACTIVE })
        val restored = liveObjects.single()
        assertEquals(tree.id, restored.id)
        assertEquals(tree.position, restored.position)
        assertEquals(tree.objectType, restored.objectType)
        assertEquals(tree.direction, restored.direction)
        assertEquals(tree.view, restored.view)
        WoodcuttingRandomEvents.finishEnt(ent)
        assertEquals(1, liveObjects.size)
    }

    @Test
    fun aReplacedBlockerPreventsAnOldExpiryFromRestoringOverNewContent() {
        val player = player()
        assertTrue(WoodcuttingRandomEvents.startEnt(player, tree(2)))
        val blocker = liveObjects.single()
        liveObjects.remove(blocker)
        `when`(blocker.state).thenReturn(EntityState.INACTIVE)
        expire(tasks.single { it.delay == 60 })
        assertTrue(liveObjects.isEmpty())
        assertTrue(liveNpcs.isEmpty())
    }

    @Test
    fun interruptedAttemptsAwardNoProductsAndDamageTheAxeOnTheSeventhCompletedSwing() {
        val player = player()
        assertTrue(WoodcuttingRandomEvents.startEnt(player, tree(2)))
        WoodcuttingRandomEvents.chopEnt(player, liveNpcs.single())
        val first = player.actions.getAll<Action<*>>(ActionType.WEAK).single()
        repeat(5) { assertFalse(first.run()) }
        assertTrue(player.inventory.contains(Axe.RUNE.id))
        player.actions.interruptWeak()
        assertEquals(ActionState.INTERRUPTED, first.state)
        WoodcuttingRandomEvents.chopEnt(player, liveNpcs.single())
        val resumed = player.actions.getAll<Action<*>>(ActionType.WEAK).single()
        assertFalse(resumed.run())
        assertTrue(player.inventory.contains(Axe.RUNE.id))
        assertTrue(resumed.run())
        assertFalse(player.inventory.contains(Axe.RUNE.id))
        assertTrue(player.inventory.contains(506))
        assertEquals(1, player.inventory.size())
        verify(player.skill(SKILL_WOODCUTTING), never()).addExperience(anyDouble())
    }

    @Test
    fun nativeReachAllowsNorthAndEastFootprintEdgesAndRejectsWallsAndDiagonals() {
        for (size in 2..3) {
            liveObjects.clear()
            val player = player()
            assertTrue(WoodcuttingRandomEvents.startEnt(player, tree(size)))
            val ent = liveNpcs.last()
            for (position in listOf(origin.translate(0, size), origin.translate(size, 0))) {
                player.actions.clear()
                `when`(player.position).thenReturn(position)
                WoodcuttingRandomEvents.chopEnt(player, ent)
                assertEquals(1, player.actions.size(ActionType.WEAK))
            }
            player.actions.clear()
            doReturn(origin.translate(size, size)).`when`(player).position
            WoodcuttingRandomEvents.chopEnt(player, ent)
            assertEquals(0, player.actions.size(ActionType.WEAK))
            val north = origin.translate(0, size)
            `when`(player.position).thenReturn(north)
            CollisionMatrix::class.java.getDeclaredMethod("set", Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
                .invoke(matrix, north.x % 8, north.y % 8, CollisionFlag.WALL_SOUTH)
            WoodcuttingRandomEvents.chopEnt(player, ent)
            assertEquals(0, player.actions.size(ActionType.WEAK))
            CollisionMatrix::class.java.getDeclaredMethod("set", Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
                .invoke(matrix, north.x % 8, north.y % 8, 0)
            WoodcuttingRandomEvents.finishEnt(ent)
        }
    }

    @Test
    fun continuingTheOriginalClickUsesNativeReachInsideAnEntThatIsLargerThanItsTree() {
        val player = player()
        doReturn(origin.translate(0, 1)).`when`(player).position
        val controls = player.controllers
        `when`(controls.checkEvent(any(ControllableEvent::class.java))).thenReturn(true)
        val original = tree(1, 1383)
        npcSize = 2
        assertTrue(WoodcuttingRandomEvents.startEnt(player, original))
        val ent = liveNpcs.single()
        val walking = player.walking
        assertTrue(fixtureWorld.collisionManager.reached(player, ent,
            io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE))

        WoodcuttingRandomEvents.continueAtEnt(player, ent)

        verify(walking, never()).replacePath(any())
        val continuation = player.actions.getAll<Action<*>>(ActionType.STRONG).single()
        assertTrue(continuation.run())
        assertEquals(1, player.actions.size(ActionType.WEAK))
        assertTrue(player.inventory.contains(Axe.RUNE.id))
        verify(player.skill(SKILL_WOODCUTTING), never()).addExperience(anyDouble())
    }

    @Test
    fun anUnreachedEntContinuationRoutesToItsNativePerimeter() {
        val player = player()
        doReturn(origin.translate(0, 3)).`when`(player).position
        val controls = player.controllers
        `when`(controls.checkEvent(any(ControllableEvent::class.java))).thenReturn(true)
        val original = tree(1, 1383)
        npcSize = 2
        assertTrue(WoodcuttingRandomEvents.startEnt(player, original))
        val ent = liveNpcs.single()
        assertFalse(fixtureWorld.collisionManager.reached(player, ent,
            io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE))
        val routes = mutableListOf<java.util.Deque<Position>>()
        val walking = player.walking
        doAnswer { invocation ->
            routes += java.util.ArrayDeque(invocation.getArgument<java.util.Deque<Position>>(0))
            null
        }.`when`(walking).replacePath(any())
        WoodcuttingRandomEvents.continueAtEnt(player, ent)
        assertEquals(1, routes.size)
        assertFalse(routes.single().isEmpty())
        val destination = routes.single().last
        assertTrue(fixtureWorld.collisionManager.reached(destination, ent,
            io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE))
        assertTrue(player.inventory.contains(Axe.RUNE.id))
        verify(player.skill(SKILL_WOODCUTTING), never()).addExperience(anyDouble())
    }
}
