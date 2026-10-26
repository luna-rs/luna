package game.content.brimhavenArena

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import game.content.brimhavenArena.BrimhavenArena.paid
import game.bot.scripts.BrimhavenArenaBotScript
import game.bot.scripts.BrimhavenArenaBotScript.ArenaData
import game.bot.scripts.BrimhavenArenaRoute
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.ControllerManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
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
        `when`(world.locator.findObjects(any(io.luna.game.model.Locatable::class.java), anyInt(), any<Predicate<GameObject>>()))
            .thenAnswer { call -> objects.filter { it.position.isWithinDistance(call.getArgument<Position>(0),
                call.getArgument<Int>(1)) && call.getArgument<Predicate<GameObject>>(2).test(it) }.toSet() }
    }

    private fun visitor(position: Position = BrimhavenArena.LANDING): Bot = (player(position) as Bot).also {
        `when`(it.asPlr()).thenReturn(it)
        `when`(it.controllers).thenReturn(ControllerManager(it))
        `when`(it.health).thenReturn(40); `when`(it.totalHealth).thenReturn(40)
        `when`(it.walking.isEmpty).thenReturn(true)
        `when`(it.actionHandler.inventory.hasAnyFood()).thenReturn(true)
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

    @Test fun botRoutesReachEveryPillarAndExitUsingNativePlacementsAtEveryTrapTier() {
        val p = visitor(); locateObjects()
        map.objectSet.objects.filter { BrimhavenArena.inside(it.position) }.forEach { obj(it) }
        for (level in listOf(1, 20, 40)) {
            `when`(p.agility.level).thenReturn(level)
            val edges = BrimhavenArenaRoute.crossings(p, objects)
            for (from in 0..24) for (to in 0..24) {
                val path = BrimhavenArenaRoute.path(from, to, edges)
                assertNotNull(path, "No path $from -> $to at level $level")
                for (edge in path!!) {
                    assertFalse(map.getTile(edge.start).isBlocked, "Blocked start $edge")
                    assertFalse(map.getTile(edge.end).isBlocked, "Blocked landing $edge")
                    assertEquals(edge.from, BrimhavenArenaRoute.platform(edge.start))
                    assertEquals(edge.to, BrimhavenArenaRoute.platform(edge.end))
                }
            }
        }
    }

    @Test fun botEligibilityRequiresOwnedFeeFoodAndSafeControllerButAllowsNativeRecovery() {
        val p = visitor(BrimhavenArena.ENTRANCE)
        assertFalse(BrimhavenArenaBotScript.eligible(p))
        p.inventory.add(Item(385)); p.inventory.add(Item(995, 199))
        assertFalse(BrimhavenArenaBotScript.eligible(p))
        p.bank.add(Item(995))
        assertTrue(BrimhavenArenaBotScript.eligible(p))
        `when`(p.health).thenReturn(5)
        assertFalse(BrimhavenArenaBotScript.eligible(p))
        p.move(BrimhavenArena.LANDING); enter(p)
        assertTrue(BrimhavenArenaBotScript.eligible(p))
        p.lock(); assertFalse(BrimhavenArenaBotScript.eligible(p)); p.unlock()
        p.controllers.register(object : io.luna.game.model.mob.controller.PlayerController(p) {})
        assertFalse(BrimhavenArenaBotScript.eligible(p))
    }

    @Test fun botPauseAndSnapshotsKeepAdmissionAndCannotRepeatTheFeeOrManufactureAVisit() = runBlocking {
        val p = visitor(); enter(p)
        val native = BrimhavenArena.session(p)
        val data = ArenaData().also { it.attempted = true; it.entered = true; it.failures = 2; it.initialTickets = 7 }
        val script = BrimhavenArenaBotScript(p, data)
        assertFalse(script.init(true))
        script.paused(); script.finish()
        assertSame(native, BrimhavenArena.session(p)); assertTrue(p.paid)
        verifyNoInteractions(p.actionHandler.banking)
        val json = com.google.gson.JsonObject(); script.snapshot().save(json)
        val restored = ArenaData().also { it.load(json) }
        assertEquals(data.deadline, restored.deadline); assertEquals(2, restored.failures)
        assertEquals(7, restored.initialTickets); assertTrue(restored.leaving)
        BrimhavenArena.leave(p); p.move(Position(3200, 3200))
        assertTrue(BrimhavenArenaBotScript(p, restored).init(true))
        assertNull(BrimhavenArena.session(p)); assertFalse(p.paid)
        verifyNoInteractions(p.actionHandler.banking)
    }

    @Test fun botCrossesAndRecoversThroughInteractionsWithoutManufacturingMovementOrExperience() = runBlocking {
        val p = visitor(); enter(p); locateObjects()
        map.objectSet.objects.filter { BrimhavenArena.inside(it.position) }.forEach { obj(it) }
        val data = ArenaData().also { it.entered = true }
        val script = BrimhavenArenaBotScript(p, data)
        assertFalse(script.init(true)); data.leaving = false
        val edge = BrimhavenArenaRoute.crossings(p, objects).first { it.obj?.id == 3566 }
        p.move(edge.start)
        val interactions = p.actionHandler.interactions
        doAnswer { call ->
            assertTrue(call.getArgument<() -> Boolean>(3).invoke())
            assertEquals(2, call.getArgument<io.luna.game.model.mob.interact.InteractionPolicy>(2).distance)
            assertTrue(ArenaObstacles.cross(p, edge.obj!!, 2.0)); finish(p); true
        }.`when`(interactions).interact(eq(1), eq(edge.obj),
            any(io.luna.game.model.mob.interact.InteractionPolicy::class.java)
                ?: io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE, any<() -> Boolean>() ?: { false })
        assertTrue(script.cross(edge, BrimhavenArena.targetIndex)); assertEquals(0, p.z)
        verify(p.agility, never()).addExperience(anyDouble())
        val rope = objects.filter { it.id == 3610 }.minBy { it.position.computeLongestDistance(p.position) }
        p.move(rope.position)
        doAnswer { call ->
            assertTrue(call.getArgument<() -> Boolean>(2).invoke())
            assertTrue(BrimhavenArena.ladder(p, rope)); finish(p); true
        }.`when`(interactions).interact(eq(1), eq(rope), any<() -> Boolean>() ?: { false })
        assertTrue(script.recover()); assertEquals(rope.position.translate(0, 0, 3), p.position)
        verifyNoInteractions(p.botClient.output)
    }

    @Test fun botTagsOnlyCurrentPillarAndExchangesEarnedTicketsWithNativeWidgets() = runBlocking {
        val p = visitor(); enter(p); locateObjects()
        BrimhavenArena.rotate(0); p.move(BrimhavenArena.target.translate(1, 0))
        val pillar = obj(3608, BrimhavenArena.target)
        val data = ArenaData().also { it.entered = true; it.initialTickets = 5 }
        val script = BrimhavenArenaBotScript(p, data)
        assertFalse(script.init(true)); data.leaving = false
        val interactions = p.actionHandler.interactions
        val widgets = p.actionHandler.widgets
        doAnswer { call ->
            assertTrue(call.getArgument<() -> Boolean>(2).invoke()); BrimhavenArena.tag(p, pillar)
        }.`when`(interactions).interact(eq(1), eq(pillar), any<() -> Boolean>() ?: { false })
        assertFalse(script.run()); assertTrue(BrimhavenArena.session(p)!!.tagged)
        verify(p.actionHandler.interactions).interact(eq(1), eq(pillar), any<() -> Boolean>() ?: { false })
        BrimhavenArena.leave(p); p.move(BrimhavenArena.ENTRANCE); data.leaving = true
        p.inventory.add(Item(2996, 15)); val trader = npc(1055)
        `when`(world.locator.findNpcs(eq(BrimhavenArena.ENTRANCE), eq(16), any<Predicate<Npc>>())).thenReturn(setOf(trader))
        doAnswer { BrimhavenArena.openRewards(p, trader); true }.`when`(interactions)
            .interact(eq(1), eq(trader), any<() -> Boolean>() ?: { false })
        doAnswer { BrimhavenArena.exchange(p, BrimhavenArena.Reward.XP_TEN) }.`when`(widgets)
            .clickButton(8292, 8389)
        assertFalse(script.run()); assertEquals(5, p.inventory.computeAmountForId(2996))
        verify(p.agility).addExperience(2480.0)
        assertTrue(script.run()); verifyNoInteractions(p.botClient.output)
    }

    @Test fun botPaysOwnedCoinsThroughDialogueAndEntersThroughTheNativeLadder() = runBlocking {
        val p = visitor(BrimhavenArena.ENTRANCE); locateObjects()
        val clerk = npc(437); val ladder = obj(3617, Position(2809, 3194))
        p.bank.add(Item(995, 200)); p.bank.add(Item(385, 12))
        val handler = p.actionHandler; val banking = handler.banking
        val interactions = handler.interactions; val widgets = handler.widgets
        `when`(banking.travelToBankDepositAll()).thenReturn(true)
        doAnswer { p.bank.remove(Item(385, 12)) && p.inventory.add(Item(385, 12)) }.`when`(banking)
            .withdrawAnyFood(12, minimumHeal = 6, retry = false)
        doAnswer { p.bank.remove(Item(995, 200)) && p.inventory.add(Item(995, 200)) }.`when`(banking)
            .withdraw(Item(995, 200))
        `when`(widgets.clickCloseInterface()).thenReturn(true)
        `when`(handler.webWalkTo(BrimhavenArena.ENTRANCE, bank = api.bot.webwalk.plan.BankAccess.NONE)).thenReturn(true)
        `when`(world.locator.findNpcs(eq(BrimhavenArena.ENTRANCE), eq(16), any<Predicate<Npc>>())).thenReturn(setOf(clerk))
        doAnswer { BrimhavenArena.talk(p, clerk); true }.`when`(interactions)
            .interact(eq(1), eq(clerk), any<() -> Boolean>() ?: { false })
        doAnswer {
            p.overlays.getOverlay(io.luna.game.model.mob.dialogue.OptionDialogue::class.java)!!.first(p); true
        }.`when`(widgets).clickDialogueOption("Pay 200 coins to enter")
        doAnswer { BrimhavenArena.ladder(p, ladder).also { if (it) finish(p) } }.`when`(interactions)
            .interact(eq(1), eq(ladder), any<() -> Boolean>() ?: { false })
        val script = BrimhavenArenaBotScript(p)
        assertFalse(script.init(false))
        assertEquals(0, p.inventory.computeAmountForId(995)); assertEquals(0, p.bank.computeAmountForId(995))
        assertEquals(BrimhavenArena.LANDING, p.position); assertTrue(p.paid)
        assertTrue(script.snapshot().entered); assertFalse(script.snapshot().leaving)
        assertFalse(script.init(true)); assertTrue(script.snapshot().leaving)
        verify(widgets, times(1)).clickDialogueOption("Pay 200 coins to enter")
        verifyNoInteractions(p.botClient.output)
    }

    @Test fun ropePolicyUsesHighLevelInteractionWithoutWalkingIntoTheGap() = runBlocking<Unit> {
        val placement = map.objectSet.objects.first { it.objectId == 3566 }
        val p = visitor(); val rope = obj(placement)
        val route = ArenaObstacles.route(p, rope, 0.0)!!; p.move(route.start)
        val policy = ArenaObstacles.policy(rope)
        val handler = api.bot.action.BotInteractionActionHandler(p, p.actionHandler)
        clearInvocations(p.navigator, p.actionHandler)
        `when`(world.collisionManager.reached(p, rope, policy)).thenReturn(true)
        val runTask = io.luna.game.task.Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { runTask.invoke(it.getArgument<io.luna.game.task.Task>(0)); null }.`when`(world)
            .schedule(any(io.luna.game.task.Task::class.java))
        try {
            assertTrue(handler.interact(1, rope, policy) { true })
            verify(p.output).sendObjectInteraction(1, rope)
            verifyNoInteractions(p.navigator, p.actionHandler)
            assertFalse(handler.interact(1, rope, policy) { false })
            verify(p.output, times(1)).sendObjectInteraction(1, rope)
        } finally { doNothing().`when`(world).schedule(any(io.luna.game.task.Task::class.java)) }
    }

    @Test fun plannerRechecksBrokenPlanksAndCurrentLevelAfterDartDrain() {
        val p = visitor(); locateObjects()
        map.objectSet.objects.filter { BrimhavenArena.inside(it.position) }.forEach { obj(it) }
        val edges = BrimhavenArenaRoute.crossings(p, objects)
        val plank = edges.first { it.obj?.id in 3570..3572 }
        obj(3576, plank.start.translate((plank.end.x - plank.start.x) / 7 * 2, 0))
        val updated = BrimhavenArenaRoute.crossings(p, objects)
        assertTrue(updated.none { it.start == plank.start && it.obj === plank.obj })
        assertNotNull(BrimhavenArenaRoute.path(plank.from, plank.to, updated))
        `when`(p.agility.level).thenReturn(39)
        val drained = BrimhavenArenaRoute.crossings(p, objects)
        assertTrue(drained.none { edge -> edge.obj == null && ArenaTraps.traps[edge.trigger]?.level == 40 })
    }

    @Test fun centralRegistrationRestoresTheArenaBudget() {
        val manager = io.luna.game.model.mob.bot.script.BotScriptManager()
        val previous = world.botManager.scriptManager
        `when`(world.botManager.scriptManager).thenReturn(manager)
        val first = scriptListeners.size
        try {
            Class.forName("game.bot.scripts.RegisterScripts").getConstructor(Array<String>::class.java)
                .newInstance(emptyArray<String>() as Any)
            @Suppress("UNCHECKED_CAST")
            val listener = scriptListeners.drop(first).single() as
                io.luna.game.event.EventListener<io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent>
            listener.apply(mock(io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent::class.java))
            val data = ArenaData().also { it.attempted = true; it.deadline = 12345; it.failures = 4 }
            val restored = manager.loadScript(BrimhavenArenaBotScript::class.java.name, visitor(), data) as BrimhavenArenaBotScript
            assertEquals(12345, restored.snapshot().deadline); assertEquals(4, restored.snapshot().failures)
            assertTrue(restored.snapshot().attempted)
        } finally {
            scriptListeners.subList(first, scriptListeners.size).clear()
            `when`(world.botManager.scriptManager).thenReturn(previous)
        }
    }

    @Test fun cancelledApproachPreservesAdmissionAndDoesNotCancelReplacementNavigation() = runBlocking {
        for (replace in listOf(false, true)) {
            val p = visitor(); enter(p); locateObjects()
            val placement = map.objectSet.objects.first { it.objectId == 3551 }
            val obstacle = obj(placement)
            val edge = BrimhavenArenaRoute.crossings(p, listOf(obstacle)).first()
            p.move(edge.start.translate(1, 0))
            val data = ArenaData().also { it.entered = true }
            val script = BrimhavenArenaBotScript(p, data)
            assertFalse(script.init(true)); data.leaving = false
            val pending = java.util.concurrent.CompletableFuture<io.luna.game.model.mob.movement.NavigationResult>()
            `when`(p.navigator.navigate(edge.start, true)).thenReturn(pending)
            `when`(p.navigator.currentPending).thenReturn(pending)
            val crossing = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                script.cross(edge, BrimhavenArena.targetIndex)
            }
            if (replace) `when`(p.navigator.currentPending).thenReturn(java.util.concurrent.CompletableFuture())
            script.paused(); crossing.cancelAndJoin(); script.finish()
            verify(p.navigator, times(if (replace) 0 else 1)).cancel()
            assertTrue(p.paid); assertNotNull(BrimhavenArena.session(p)); assertTrue(script.snapshot().leaving)
            verifyNoInteractions(p.actionHandler.interactions)
        }
    }

    @Test fun expiredAndReplacedVisitsCannotDispatchMoreGameplay() = runBlocking {
        val p = visitor(); enter(p)
        val expired = ArenaData().also { it.deadline = System.currentTimeMillis() - 121_000 }
        val old = BrimhavenArenaBotScript(p, expired)
        assertTrue(old.init(true)); assertTrue(old.run())
        val script = BrimhavenArenaBotScript(p)
        assertFalse(script.init(true))
        BrimhavenArena.leave(p); enter(p)
        val replacement = BrimhavenArena.session(p)
        assertTrue(script.run()); script.finish()
        assertSame(replacement, BrimhavenArena.session(p))
        verifyNoInteractions(p.actionHandler.interactions, p.actionHandler.banking)
    }
}
