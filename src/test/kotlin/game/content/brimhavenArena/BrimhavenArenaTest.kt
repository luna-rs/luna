package game.content.brimhavenArena

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import game.content.brimhavenArena.BrimhavenArena.paid
import io.luna.game.action.Action
import io.luna.game.action.ActionState
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.cache.map.MapObject
import io.luna.game.cache.map.MapIndexTable
import io.luna.net.msg.out.PositionHintMessageWriter
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.*
import java.util.function.Predicate
import com.google.common.collect.ImmutableList
import io.luna.game.cache.Cache
import io.luna.game.cache.codec.MapDecoder
import io.luna.game.cache.codec.WidgetDefinitionDecoder
import io.luna.game.cache.map.MapIndex
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*

/**
 * Verifies native arena assets, admission, ticket streaks, rewards and obstacle outcomes.
 *
 * @author lare96
 */
class BrimhavenArenaTest {
    companion object {
        private lateinit var map: MapIndexTable
        private lateinit var nativeWidgets: Map<Int, WidgetDefinition>
        private val originalItems = mutableMapOf<Int, Definition?>()
        private lateinit var itemEntries: Array<Definition?>

        @Suppress("UNCHECKED_CAST")
        @JvmStatic @BeforeAll fun initialize() {
            InventoryProductionFixtures.initializeCombat()
            itemEntries = ArrayDefinitionRepository::class.java.getDeclaredField("definitions").apply {
                isAccessible = true
            }.get(ItemDefinition.ALL) as Array<Definition?>
            Cache().use { cache ->
                cache.open()
                val items = ImmutableList.builder<ItemDefinition>()
                io.luna.game.cache.codec.ItemDefinitionDecoder().decode(cache, items)
                for (item in items.build().filter { it.id() in setOf(995, 2996) }) {
                    originalItems[item.id()] = itemEntries[item.id()]
                    itemEntries[item.id()] = item
                }
                val widgets = ImmutableList.builder<WidgetDefinition>()
                WidgetDefinitionDecoder().decode(cache, widgets)
                nativeWidgets = widgets.build().associateBy { it.id() }
                for (id in listOf(8287, 8292, 2469)) if (WidgetDefinition.ALL[id].isEmpty)
                    WidgetDefinition.ALL.storeDefinition(nativeWidgets.getValue(id))
                val objects = ImmutableList.builder<GameObjectDefinition>()
                io.luna.game.cache.codec.ObjectDefinitionDecoder().decode(cache, objects)
                for (obj in objects.build().filter { it.id() in 3551..3618 })
                    if (GameObjectDefinition.ALL[obj.id()].isEmpty) GameObjectDefinition.ALL.storeDefinition(obj)
                val decoder = MapDecoder()
                val indices = ImmutableList.builder<MapIndex>()
                decoder.decode(cache, indices)
                decoder.handle(mock(io.luna.LunaContext::class.java), cache,
                    ImmutableList.copyOf(indices.build().filter { it.region in setOf(io.luna.game.model.Region(43, 149), io.luna.game.model.Region(43, 49)) }))
                map = cache.mapIndexTable
            }
            if (VarpDefinition.ALL[309].isEmpty) VarpDefinition.ALL.storeDefinition(VarpDefinition(309, 0))
        }
        @JvmStatic @AfterAll fun restoreMetadata() { originalItems.forEach { (id, old) -> itemEntries[id] = old } }
    }

    private val players = mutableListOf<Player>()
    private val pending = mutableMapOf<Player, Action<*>>()
    private val objects = mutableListOf<GameObject>()
    @AfterEach fun cleanup() {
        players.forEach { BrimhavenArena.leave(it) }
        reset(world.locator)
    }

    private fun player(position: Position = BrimhavenArena.LANDING): Player = InventoryProductionFixtures.bot().also { p ->
        var current = position
        var locked = false
        `when`(p.position).thenAnswer { current }
        `when`(p.x).thenAnswer { current.x }
        `when`(p.y).thenAnswer { current.y }
        `when`(p.z).thenAnswer { current.z }
        `when`(p.isLocked).thenAnswer { locked }
        `when`(p.isAlive).thenReturn(true)
        `when`(p.agility.level).thenReturn(50)
        `when`(p.hitpoints.level).thenReturn(40)
        doAnswer { current = it.getArgument(0); null }.`when`(p).move(any(Position::class.java))
        doAnswer { locked = true; null }.`when`(p).lock()
        doAnswer { locked = false; null }.`when`(p).unlock()
        doAnswer {
            val action = it.getArgument<Action<*>>(0)
            pending[p] = action
            p.actions.submit(action)
            null
        }.`when`(p).submitAction(any(Action::class.java))
        val overlays = AbstractOverlaySet(p)
        `when`(p.overlays).thenReturn(overlays)
        doCallRealMethod().`when`(p).newDialogue()
        `when`(world.collisionManager.raycast(any(Position::class.java), any(Position::class.java))).thenReturn(true)
        players += p
    }

    private fun enter(p: Player) { p.paid = true; assertTrue(BrimhavenArena.enter(p)) }
    private fun npc(id: Int): Npc = mock(Npc::class.java).also {
        `when`(it.id).thenReturn(id); `when`(it.position).thenReturn(BrimhavenArena.ENTRANCE)
        `when`(it.state).thenReturn(EntityState.ACTIVE)
    }
    private fun obj(id: Int, position: Position, direction: ObjectDirection = ObjectDirection.WEST): GameObject =
        mock(GameObject::class.java).also {
            `when`(it.id).thenReturn(id); `when`(it.position).thenReturn(position)
            `when`(it.direction).thenReturn(direction); `when`(it.state).thenReturn(EntityState.ACTIVE)
            objects += it
        }
    private fun obj(placement: MapObject) = obj(placement.objectId, placement.position, placement.rotation)
    private fun locateObjects() {
        `when`(world.locator.findObjectsOnTile(any(io.luna.game.model.Locatable::class.java), any<Predicate<GameObject>>()))
            .thenAnswer { call -> objects.filter { it.position == call.getArgument<Position>(0) &&
                call.getArgument<Predicate<GameObject>>(1).test(it) }.toSet() }
    }
    private fun finish(p: Player) {
        val action = pending.getValue(p)
        repeat(100) {
            if (action.state != ActionState.PROCESSING) return
            if (action.run()) { action.complete(); return }
        }
        fail<Unit>("Traversal did not finish")
    }

    @Test fun admissionChargesOnceAndRequiresLiveNearbyClerk() {
        val p = player(BrimhavenArena.ENTRANCE); val clerk = npc(437)
        assertFalse(BrimhavenArena.pay(p, clerk))
        p.inventory.add(Item(995, 199))
        assertFalse(BrimhavenArena.pay(p, clerk))
        assertEquals(199, p.inventory.computeAmountForId(995))
        p.inventory.add(Item(995, 201))
        assertTrue(BrimhavenArena.pay(p, clerk)); assertTrue(BrimhavenArena.pay(p, clerk))
        assertEquals(200, p.inventory.computeAmountForId(995))
        p.move(BrimhavenArena.LANDING); assertTrue(BrimhavenArena.enter(p))
        BrimhavenArena.leave(p)
        assertFalse(p.paid)
        p.move(Position(3200, 3200))
        assertFalse(BrimhavenArena.pay(p, clerk))
        assertEquals(200, p.inventory.computeAmountForId(995))
    }

    @Test fun ladderAdmissionAndExitUseLockedMovementAndConsumeVisitOnlyOnExit() {
        val p = player(BrimhavenArena.ENTRANCE)
        val down = obj(3617, Position(2809, 3194))
        assertFalse(BrimhavenArena.ladder(p, down))
        p.paid = true
        assertTrue(BrimhavenArena.ladder(p, down)); assertTrue(p.isLocked)
        assertEquals(BrimhavenArena.ENTRANCE, p.position)
        finish(p)
        assertFalse(p.isLocked); assertNotNull(BrimhavenArena.session(p))
        assertEquals(BrimhavenArena.LANDING, p.position)
        assertTrue(BrimhavenArena.ladder(p, obj(3618, BrimhavenArena.tile(53, 54))))
        finish(p)
        assertEquals(BrimhavenArena.ENTRANCE, p.position)
        assertNull(BrimhavenArena.session(p)); assertFalse(p.paid)
    }

    @Test fun firstTagPrimesStreakDuplicatesFailAndMissedRoundsResetEligibility() {
        val p = player(); enter(p)
        fun tag(index: Int): Boolean {
            BrimhavenArena.rotate(index)
            p.move(BrimhavenArena.target.translate(1, 0))
            return BrimhavenArena.tag(p, obj(3608, BrimhavenArena.target))
        }
        assertTrue(tag(0)); assertEquals(0, p.inventory.computeAmountForId(2996))
        assertFalse(BrimhavenArena.tag(p, obj(3608, BrimhavenArena.target)))
        assertTrue(tag(1)); assertEquals(1, p.inventory.computeAmountForId(2996))
        BrimhavenArena.rotate(2)
        assertTrue(tag(3)); assertEquals(1, p.inventory.computeAmountForId(2996))
        assertTrue(tag(4)); assertEquals(2, p.inventory.computeAmountForId(2996))
        p.move(BrimhavenArena.target.translate(0, 0, -3))
        assertFalse(BrimhavenArena.tag(p, obj(3608, BrimhavenArena.target)))
    }

    @Test fun everyRecoveryRopeReturnsToAnUnblockedPlatformAndPreservesTheVisit() {
        val p = player(); enter(p)
        val session = BrimhavenArena.session(p)
        val ropes = map.objectSet.objects.filter { it.objectId == 3610 }
        assertTrue(ropes.isNotEmpty())
        for (rope in ropes) {
            p.move(rope.position)
            assertTrue(BrimhavenArena.ladder(p, obj(rope)))
            assertTrue(p.isLocked); assertEquals(0, p.z)
            finish(p)
            assertEquals(rope.position.translate(0, 0, 3), p.position)
            assertFalse(map.getTile(p.position).isBlocked, "Blocked recovery platform ${p.position}")
            assertFalse(p.isLocked); assertSame(session, BrimhavenArena.session(p)); assertTrue(p.paid)
        }
        verify(p.agility, never()).addExperience(anyDouble())
    }

    @Test fun timedBladesHitBothTilesPushPlayersOutAndDoNotGrantLockImmunity() {
        locateObjects()
        `when`(world.locator.findPlayers(any(io.luna.game.model.Locatable::class.java), anyInt(), any<Predicate<Player>>()))
            .thenAnswer { call -> players.filter { call.getArgument<Predicate<Player>>(2).test(it) }.toSet() }
        for ((first, second) in ArenaTraps.bladeTiles) {
            obj(map.objectSet.objects.first { it.objectId == 3568 && it.position == first })
            for (start in listOf(first, second)) {
                val p = player(start); enter(p)
                ArenaTraps.blades(); assertTrue(p.isLocked)
                verify(p, never()).damage(anyInt())
                finish(p)
                assertEquals(if (start == first) first.translate(first.x - second.x, first.y - second.y)
                    else second.translate(second.x - first.x, second.y - first.y), p.position)
                verify(p).damage(4); assertFalse(p.isLocked)
                assertFalse(map.getTile(p.position).isBlocked)
                ArenaTraps.blades(); verify(p, times(1)).damage(4)
                p.move(start); p.lock()
                ArenaTraps.blades(); verify(p, times(2)).damage(4)
                p.unlock(); BrimhavenArena.leave(p)
            }
        }
    }

    @Test fun failedTicketInsertionDoesNotConsumeTagAndStreakIsPerPlayer() {
        val p = player(); val other = player(); enter(p); enter(other)
        BrimhavenArena.rotate(0); p.move(BrimhavenArena.target.translate(1, 0))
        assertTrue(BrimhavenArena.tag(p, obj(3608, BrimhavenArena.target)))
        BrimhavenArena.rotate(1); p.move(BrimhavenArena.target.translate(1, 0)); other.move(p.position)
        repeat(28) { p.inventory.add(Item(1265)) }
        assertFalse(BrimhavenArena.tag(p, obj(3608, BrimhavenArena.target)))
        assertFalse(BrimhavenArena.session(p)!!.tagged)
        p.inventory.remove(1265)
        assertTrue(BrimhavenArena.tag(p, obj(3608, BrimhavenArena.target)))
        assertTrue(BrimhavenArena.tag(other, obj(3608, BrimhavenArena.target)))
        assertEquals(1, p.inventory.computeAmountForId(2996))
        assertEquals(0, other.inventory.computeAmountForId(2996))
    }

    @Test fun logoutPreservesAdmissionButClearsStreakAndAbandonedActionsCannotGrantExperience() {
        val p = player(); enter(p)
        val session = BrimhavenArena.session(p)!!
        session.eligible = true
        assertTrue(BrimhavenArena.traverse(p, listOf(game.skill.agility.AgilityStep(p.position.translate(1, 0), 2)), "Test") {
            p.agility.addExperience(99.0)
        })
        BrimhavenArena.leave(p, clearPayment = false)
        finish(p)
        verify(p.agility, never()).addExperience(anyDouble())
        assertFalse(p.isLocked); assertTrue(p.paid)
        BrimhavenArena.login(p)
        assertNotSame(session, BrimhavenArena.session(p)); assertFalse(BrimhavenArena.session(p)!!.eligible)
    }

    @Test fun rewardsRequireTheirOwnScreenNearbyTraderTicketsAndCapacity() {
        val p = player(BrimhavenArena.ENTRANCE); val trader = npc(1055)
        p.inventory.add(Item(2996, 1003))
        assertFalse(BrimhavenArena.exchange(p, BrimhavenArena.Reward.XP_THOUSAND))
        BrimhavenArena.openRewards(p, trader)
        assertTrue(BrimhavenArena.exchange(p, BrimhavenArena.Reward.XP_THOUSAND))
        verify(p.agility).addExperience(320000.0)
        repeat(27) { p.inventory.add(Item(1265)) }
        assertTrue(BrimhavenArena.exchange(p, BrimhavenArena.Reward.TOADFLAX))
        assertTrue(p.inventory.contains(2998)); assertFalse(p.inventory.contains(2996))
        assertFalse(BrimhavenArena.exchange(p, BrimhavenArena.Reward.TOADFLAX))
        p.inventory.remove(1265); p.inventory.add(Item(2996, 4))
        assertFalse(BrimhavenArena.exchange(p, BrimhavenArena.Reward.TOADFLAX))
        assertEquals(4, p.inventory.computeAmountForId(2996))
        p.move(Position(3200, 3200))
        assertFalse(BrimhavenArena.exchange(p, BrimhavenArena.Reward.XP_ONE))
    }

    @Test fun plankChangesKeepExactlyOneCompleteLaneOnEachBridge() {
        for (first in 0..2) for (second in 0..2) {
            val layout = ArenaTraps.plankLayout(listOf(first, second)) { it - 1 }
            assertEquals(24, layout.size)
            for ((index, pair) in listOf(46 to 53, 13 to 20).withIndex()) {
                for (row in 0..2) {
                    val ids = (0..3).map { layout.getValue(BrimhavenArena.tile(pair.first + it, pair.second + row)) }
                    assertEquals(row == listOf(first, second)[index], ids.none { it == 3576 })
                    if (row != listOf(first, second)[index]) { assertEquals(3576, ids.first()); assertEquals(3576, ids.last()) }
                }
            }
        }
    }

    @Test fun everyNativeObstacleHasValidSuccessAndFailureRoutes() {
        val p = player(); enter(p); locateObjects()
        for (placement in map.objectSet.objects.filter { it.objectId in ArenaObstacles.ids }) {
            val obstacle = obj(placement)
            for (side in listOf(-3, 3)) {
                p.move(placement.position.translate(side, side))
                val route = ArenaObstacles.route(p, obstacle, 0.0)!!
                assertTrue(BrimhavenArena.inside(route.start), "$placement")
                assertFalse(map.getTile(route.start).isBlocked, "Blocked start: ${obstacle.id} ${route.start}")
                assertFalse(map.getTile(route.steps.last().destination).isBlocked,
                    "Blocked landing: ${obstacle.id} ${route.steps.last().destination}")
                p.move(route.start)
                assertTrue(ArenaObstacles.cross(p, obstacle, 0.0))
                verify(p.agility, never()).addExperience(anyDouble())
                finish(p)
                verify(p.agility).addExperience(route.xp)
                clearInvocations(p.agility)
                if (placement.objectId !in setOf(3559, 3561, 3570, 3571, 3572)) {
                    p.move(route.start)
                    assertTrue(ArenaObstacles.cross(p, obstacle, 2.0))
                    finish(p)
                    assertEquals(if (placement.objectId == 3565) 3 else 0, p.z)
                    verify(p.agility, never()).addExperience(anyDouble())
                    assertFalse(p.isLocked)
                }
            }
        }
    }

    @Test fun obstacleApproachWaitsForNavigationAndDepartureCancelsIt() {
        val placement = map.objectSet.objects.first { it.objectId == 3551 }
        val p = player(placement.position.translate(1, 0)); enter(p)
        val obstacle = obj(placement)
        val arrival = java.util.concurrent.CompletableFuture<io.luna.game.model.mob.movement.NavigationResult>()
        `when`(p.navigator.navigate(placement.position, false)).thenReturn(arrival)
        `when`(p.navigator.currentPending).thenReturn(arrival)
        assertTrue(ArenaObstacles.cross(p, obstacle, 0.0))
        val approach = pending.getValue(p)
        assertFalse(approach.run())
        verify(p.navigator).navigate(placement.position, false)
        assertEquals(placement.position.translate(1, 0), p.position)
        assertFalse(p.isLocked)
        BrimhavenArena.leave(p)
        assertEquals(ActionState.INTERRUPTED, approach.state)
        verify(p.navigator).cancel()
        verify(p.agility, never()).addExperience(anyDouble())
    }

    @Test fun registeredRewardButtonsExchangeEveryNativeReward() {
        val first = scriptListeners.size
        val firstMatcher = scriptMatchers.size
        Class.forName("game.content.brimhavenArena.ArenaHandlers").getConstructor(Array<String>::class.java)
            .newInstance(emptyArray<String>() as Any)
        scriptListeners.subList(first, scriptListeners.size).clear()
        scriptMatchers.subList(firstMatcher, scriptMatchers.size).clear()
        val pipeline = pipelines.get(io.luna.game.event.impl.ButtonClickEvent::class.java)
        for (reward in BrimhavenArena.Reward.values()) for (button in reward.buttons) {
            val p = player(BrimhavenArena.ENTRANCE)
            p.inventory.add(Item(2996, reward.cost))
            BrimhavenArena.openRewards(p, npc(1055))
            pipeline.post(io.luna.game.event.impl.ButtonClickEvent(p, button))
            assertFalse(p.inventory.contains(2996), "Button $button did not exchange tickets")
            if (reward.item != -1) assertTrue(p.inventory.contains(reward.item))
            else verify(p.agility).addExperience(reward.xp)
        }
    }

    @Test fun trapsUseCurrentLevelsApplyNativeDamageAndDrainDartsOnlyOnFailure() {
        val p = player(); enter(p); locateObjects()
        for (trap in ArenaTraps.traps.values) obj(trap.objectId, trap.anchor)
        for (id in listOf(3582, 3585, 3580, 3581)) {
            val trap = ArenaTraps.traps.values.first { it.objectId == id }
            p.move(trap.start); `when`(p.agility.level).thenReturn(trap.level - 1)
            assertTrue(ArenaTraps.trigger(p, 0.0)); assertTrue(p.isLocked)
            finish(p)
            assertEquals(trap.failure, p.position)
            verify(p).damage(4)
            if (id == 3581) verify(p.agility).adjustLevel(-2)
            else verify(p.agility, never()).adjustLevel(anyInt())
            verify(p.agility, never()).addExperience(anyDouble())
            clearInvocations(p, p.agility)
            `when`(p.agility.level).thenReturn(trap.level)
            p.move(trap.start); assertTrue(ArenaTraps.trigger(p, 0.0)); finish(p)
            assertEquals(trap.destination, p.position)
            verify(p.agility).addExperience(trap.xp)
            verify(p, never()).damage(anyInt())
            clearInvocations(p, p.agility)
        }
    }

    @Test fun hintPacketsSetNativeHeightAndClearWithFixedLength() {
        val p = player()
        for (target in listOf(BrimhavenArena.target, null)) {
            val buffer = Unpooled.buffer()
            try {
                PositionHintMessageWriter(target, 60).write(p, buffer)
                assertEquals(6, buffer.readableBytes())
                assertEquals(if (target == null) 0 else 2, buffer.readUnsignedByte().toInt())
                assertEquals(target?.x ?: 0, buffer.readUnsignedShort())
                assertEquals(target?.y ?: 0, buffer.readUnsignedShort())
                assertEquals(if (target == null) 0 else 60, buffer.readUnsignedByte().toInt())
            } finally { buffer.release() }
        }
    }

    @Test fun nativeArenaAssets() {
        assertTrue(nativeWidgets.containsKey(8287))
        for (reward in BrimhavenArena.Reward.values()) for (button in reward.buttons)
            assertTrue(nativeWidgets.getValue(8292).children.contains(button), "Missing reward button $button")
        val objects = map.objectSet.objects
        assertTrue(objects.any { it.objectId == 3617 && it.position == Position(2809, 3194) })
        assertTrue(objects.any { it.objectId == 3618 && it.position == BrimhavenArena.tile(53, 54) })
        for (position in BrimhavenArena.pillars) {
            assertTrue(objects.any { it.objectId in setOf(3608, 3581) && it.position == position }, "Missing pillar $position")
        }
        for (trap in ArenaTraps.traps.values)
            assertTrue(objects.any { it.objectId == trap.objectId && it.position == trap.anchor }, "Missing trap $trap")
        assertFalse(map.getTile(BrimhavenArena.LANDING).isBlocked)
        assertTrue(ItemDefinition.ALL.retrieve(2996).isStackable)
        val spawns = java.io.File("data/game/world/npc_spawns.jsonc").reader().use {
            com.google.gson.JsonParser.parseReader(it).asJsonArray
        }
        val traders = spawns.map { it.asJsonObject }.filter { it.get("id").asInt == 1055 }
        assertEquals(1, traders.size)
        val trader = traders.single().getAsJsonObject("position")
        assertEquals(2811, trader.get("x").asInt); assertEquals(3192, trader.get("y").asInt)
    }
}
