package game.content.rangingGuild

import api.bot.script.InventoryProductionFixtures
import api.bot.action.BotActionHandler
import api.bot.action.BotWidgetActionHandler
import api.predef.*
import com.google.common.collect.ImmutableList
import game.content.rangingGuild.ArcheryCompetition.count
import game.content.rangingGuild.ArcheryCompetition.score
import game.content.rangingGuild.ArcheryCompetition.hit
import io.luna.game.action.Action
import io.luna.game.action.ActionState
import io.luna.game.cache.Cache
import io.luna.game.cache.codec.*
import io.luna.game.cache.map.MapIndex
import io.luna.game.cache.map.MapIndexTable
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.Event
import io.luna.game.event.EventListener
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemSecondClickEvent
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.Region
import io.luna.game.model.collision.CollisionFlag
import io.luna.game.model.collision.CollisionUpdate
import io.luna.game.model.collision.CollisionUpdateType
import io.luna.game.model.collision.CollisionView
import io.luna.game.model.path.route.LineOfSight
import io.luna.game.model.def.*
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.io.BotClient
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler
import io.luna.game.model.mob.controller.ControllerManager
import io.luna.game.model.mob.attr.Attribute
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.mob.varp.Varp
import io.luna.game.model.`object`.GameObject
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import io.luna.net.msg.GameMessage
import io.luna.net.msg.`in`.WidgetItemClickMessageReader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Native cache, economy, shooting, persistence and event coverage for Ranging Guild archery.
 * Uses a mock world with real inventory, equipment, overlays and action queues; no live server is started.
 *
 * @author lare96
 */
class RangingGuildTest {
    companion object {
        private lateinit var map: MapIndexTable
        private lateinit var widgets: Map<Int, WidgetDefinition>
        private lateinit var objectDefinitions: Map<Int, GameObjectDefinition>
        private val oldItems = mutableMapOf<Int, Definition?>()
        private lateinit var itemEntries: Array<Definition?>

        @Suppress("UNCHECKED_CAST")
        @JvmStatic @BeforeAll fun initialize() {
            assumeTrue(Files.exists(Path.of("data/game/cache/main_file_cache.dat")), "Local revision 377 cache required")
            InventoryProductionFixtures.initializeCombat()
            itemEntries = ArrayDefinitionRepository::class.java.getDeclaredField("definitions").apply {
                isAccessible = true
            }.get(ItemDefinition.ALL) as Array<Definition?>
            Cache().use { cache ->
                cache.open()
                val items = ImmutableList.builder<ItemDefinition>()
                ItemDefinitionDecoder().decode(cache, items)
                for (item in items.build().filter { it.id() in setOf(995, 882, 884, 1464, 841, 47, 1129, 892, 1169, 1135, 829) }) {
                    oldItems[item.id()] = itemEntries[item.id()]; itemEntries[item.id()] = item
                }
                val decoded = ImmutableList.builder<WidgetDefinition>()
                WidgetDefinitionDecoder().decode(cache, decoded)
                widgets = decoded.build().associateBy { it.id() }
                for (id in listOf(446, 11942, 12033, 2469, 2479, 2492)) if (WidgetDefinition.ALL[id].isEmpty)
                    WidgetDefinition.ALL.storeDefinition(widgets.getValue(id))
                val objects = ImmutableList.builder<GameObjectDefinition>()
                ObjectDefinitionDecoder().decode(cache, objects)
                objectDefinitions = objects.build().associateBy { it.id() }
                for (obj in objectDefinitions.values.filter { it.id() in setOf(2513, 2514) })
                    if (GameObjectDefinition.ALL[obj.id()].isEmpty) GameObjectDefinition.ALL.storeDefinition(obj)
                val decoder = MapDecoder()
                val indices = ImmutableList.builder<MapIndex>()
                decoder.decode(cache, indices)
                decoder.handle(mock(io.luna.LunaContext::class.java), cache,
                    ImmutableList.copyOf(indices.build().filter { it.region == Region(41, 53) }))
                map = cache.mapIndexTable
                val varps = ImmutableList.builder<VarpDefinition>()
                VarpDefinitionDecoder().decode(cache, varps)
                for (varp in varps.build().filter { it.id() in 156..158 })
                    if (VarpDefinition.ALL[varp.id()].isEmpty) VarpDefinition.ALL.storeDefinition(varp)
            }
        }

        @JvmStatic @AfterAll fun restore() { oldItems.forEach { (id, old) -> itemEntries[id] = old } }
    }

    private val pending = mutableMapOf<Player, Action<*>>()
    private val targetPosition = Position(2679, 3426)
    private val firingPosition = Position(2674, 3421)

    private fun player(position: Position = ArcheryCompetition.JUDGE): Player = InventoryProductionFixtures.bot().also { p ->
        var current = position
        var locked = false
        `when`(p.position).thenAnswer { current }
        `when`(p.x).thenAnswer { current.x }; `when`(p.y).thenAnswer { current.y }; `when`(p.z).thenAnswer { current.z }
        `when`(p.isLocked).thenAnswer { locked }; `when`(p.isAlive).thenReturn(true)
        `when`(p.asPlr()).thenReturn(p)
        `when`(p.ranged.level).thenReturn(40)
        val equipment = Equipment(p)
        `when`(p.equipment).thenReturn(equipment)
        val overlays = AbstractOverlaySet(p)
        `when`(p.overlays).thenReturn(overlays)
        doCallRealMethod().`when`(p).newDialogue()
        doAnswer { current = it.getArgument(0); null }.`when`(p).move(any(Position::class.java))
        doAnswer { locked = true; null }.`when`(p).lock()
        doAnswer { locked = false; null }.`when`(p).unlock()
        doAnswer {
            val action = it.getArgument<Action<*>>(0)
            pending[p] = action; p.actions.submit(action); null
        }.`when`(p).submitAction(any(Action::class.java))
        `when`(world.collisionManager.raycast(any(Position::class.java), any(Position::class.java))).thenReturn(true)
    }

    private fun npc(id: Int = 693, position: Position = ArcheryCompetition.JUDGE): Npc = mock(Npc::class.java).also {
        `when`(it.id).thenReturn(id); `when`(it.position).thenReturn(position); `when`(it.state).thenReturn(EntityState.ACTIVE)
    }

    private fun obj(id: Int = 2513, position: Position = targetPosition): GameObject = mock(GameObject::class.java).also {
        `when`(it.id).thenReturn(id); `when`(it.position).thenReturn(position); `when`(it.state).thenReturn(EntityState.ACTIVE)
    }

    private fun archer(): Player = player(firingPosition).also {
        it.count = 1; it.equipment.add(Item(841)); it.equipment.add(Item(882, 10))
    }

    private fun finish(p: Player) {
        repeat(35) {
            val action = pending.getValue(p)
            if (action.state != ActionState.PROCESSING) return
            if (action.run()) action.complete()
        }
        fail<Unit>("Action did not finish")
    }

    private fun fill(p: Player) { while (!p.inventory.isFull) p.inventory.add(Item(1511)) }

    private fun visitor(position: Position = ArcheryCompetition.JUDGE): Bot = (player(position) as Bot).also {
        val controllers = ControllerManager(it)
        `when`(it.controllers).thenReturn(controllers)
        `when`(it.walking.isEmpty).thenReturn(true)
        `when`(it.skill(SKILL_RANGED).staticLevel).thenReturn(40)
    }

    @AfterEach fun resetLocator() { reset(world.locator) }

    @Test fun containerHelperBuysViaNativeWidgetPacketAndRejectsWrongMenuOrClosedWindow() {
        val p = visitor(); p.inventory.add(Item(1464, 100)); ArcheryRewards.open(p, npc(694))
        val client = mock(BotClient::class.java); `when`(client.bot).thenReturn(p)
        val output = BotOutputMessageHandler(client); `when`(p.output).thenReturn(output)
        val widgets = BotWidgetActionHandler(p, mock(BotActionHandler::class.java))
        val reader = WidgetItemClickMessageReader()
        doAnswer { call ->
            val message = call.getArgument<GameMessage>(0)
            assertEquals(177, message.opcode)
            val event = reader.decode(p, message)
            assertTrue(event is WidgetItemSecondClickEvent); assertTrue(reader.validate(p, event))
            assertEquals(12033, event.widgetId)
            assertTrue(ArcheryRewards.select(p, event.index, event.itemId, true)); null
        }.`when`(client).queueSimulated(any(GameMessage::class.java))
        assertFalse(widgets.clickContainerAction(11942, 12033, 3, 3, 1169))
        assertFalse(widgets.clickContainerAction(11942, 12031, 2, 3, 1169))
        assertFalse(widgets.clickContainerAction(11942, 12033, 2, -1, 1169))
        assertTrue(widgets.clickContainerAction(11942, 12033, 2, 3, 1169))
        assertTrue(p.inventory.contains(1169)); assertFalse(p.inventory.contains(1464))
        p.overlays.closeWindows(); assertFalse(widgets.clickContainerAction(11942, 12033, 2, 3, 1169))
        verify(client, times(1)).queueSimulated(any(GameMessage::class.java))
    }

    @Test fun cacheContainsNativeTargetsDoorRewardGridAndResultVarps() {
        assertEquals(ArcheryCompetition.targets, map.objectSet.objects.filter { it.objectId == 2513 }.map { it.position }.toSet())
        assertTrue(map.objectSet.objects.any { it.objectId == 2514 && it.position == RangingGuildGate.POSITION })
        assertEquals(listOf("Value", "Buy"), widgets.getValue(12033).options.take(2))
        assertEquals(11942, widgets.getValue(12033).parentId)
        assertEquals("Close Window", widgets.getValue(568).disabledText)
        assertEquals("Ranging Guild Ticket Exchange", widgets.getValue(12031).disabledText)
        assertEquals(5, widgets.getValue(536).getCs1(0, 0))
        assertEquals(158, widgets.getValue(536).getCs1(0, 1))
        assertEquals("Archery ticket", Item(1464).itemDef.name)
        for (id in listOf(1464, 882, 47, 829, 892)) assertTrue(Item(id).itemDef.isStackable)
    }

    @Test fun nativeCollisionAllowsCompetitionLanesAndGateLandingsButBlocksTheEnclosedTarget() {
        val flags = mutableMapOf<Position, Int>()
        for (placement in map.objectSet.objects) {
            val target = obj(placement.objectId, placement.position)
            `when`(target.def()).thenReturn(objectDefinitions.getValue(placement.objectId))
            `when`(target.objectType).thenReturn(placement.type)
            `when`(target.direction).thenReturn(placement.rotation)
            val update = CollisionUpdate.Builder().apply {
                type(CollisionUpdateType.ADDING); `object`(target)
            }.build()
            for ((position, mask) in update.flags) flags[position] = (flags[position] ?: 0) or mask
        }
        val view = mock(CollisionView::class.java)
        `when`(view.get(anyInt(), anyInt(), anyInt())).thenAnswer {
            flags[Position(it.getArgument(0), it.getArgument(1), it.getArgument(2))] ?: 0
        }
        for (target in ArcheryCompetition.targets) {
            val lanes = (2669..2682).flatMap { x -> (3413..3424).map { y -> Position(x, y) } }.filter {
                !map.getTile(it).isBlocked && it.isWithinDistance(target, 10) && !it.isWithinDistance(target, 4) &&
                    LineOfSight.hasLineOfSight(view, 0, it.x, it.y, target.x, target.y)
            }
            // The fifth cache placement is enclosed by diagonal walls, behind the four range targets.
            // Ordinary projectile collision must continue to reject shots through that enclosure.
            if (target == Position(2682, 3425)) { assertTrue(lanes.isEmpty()) }
            else {
                assertTrue(lanes.isNotEmpty(), "No shooting lane behind the haystacks for $target")
            }
        }
        for (position in listOf(RangingGuildGate.OUTSIDE, RangingGuildGate.INSIDE)) {
            assertFalse(map.getTile(position).isBlocked)
            assertEquals(0, (flags[position] ?: 0) and (CollisionFlag.LOC or CollisionFlag.GROUND_DECOR))
        }
    }

    @Test fun entryChargesExactlyOnceAndRequiresNearbyLiveJudge() {
        val p = player(); val judge = npc()
        p.inventory.add(Item(995, 199)); assertFalse(ArcheryCompetition.enter(p, judge))
        assertEquals(199, p.inventory.computeAmountForId(995))
        p.inventory.add(Item(995, 201)); assertTrue(ArcheryCompetition.enter(p, judge))
        assertFalse(ArcheryCompetition.enter(p, judge)); assertEquals(200, p.inventory.computeAmountForId(995))
        assertEquals(10, p.inventory.computeAmountForId(882)); assertEquals(1, p.count)
        p.count = 0; p.move(Position(3200, 3200)); assertFalse(ArcheryCompetition.enter(p, judge))
        p.move(ArcheryCompetition.JUDGE); `when`(judge.state).thenReturn(EntityState.NEW)
        assertFalse(ArcheryCompetition.enter(p, judge))
    }

    @Test fun exactEntryPaymentCanReleaseArrowSlotButInsufficientRoomOrOverflowCostsNothing() {
        val p = player(); p.inventory.add(Item(995, 201)); fill(p)
        assertFalse(ArcheryCompetition.enter(p, npc())); assertEquals(201, p.inventory.computeAmountForId(995))
        p.inventory.remove(Item(995)); assertTrue(ArcheryCompetition.enter(p, npc()))
        assertEquals(10, p.inventory.computeAmountForId(882)); assertTrue(p.inventory.isFull)
        val other = player(); other.inventory.add(Item(995, 200)); other.inventory.add(Item(882, Int.MAX_VALUE))
        assertFalse(ArcheryCompetition.enter(other, npc())); assertEquals(200, other.inventory.computeAmountForId(995))
    }

    @Test fun replacementArrowsRequireActiveRoundNoOwnedArrowsAndFullPayment() {
        val p = player(); p.inventory.add(Item(995, 300))
        assertFalse(ArcheryCompetition.replaceArrows(p, npc())); p.count = 1
        assertTrue(ArcheryCompetition.replaceArrows(p, npc())); assertEquals(200, p.inventory.computeAmountForId(995))
        assertFalse(ArcheryCompetition.replaceArrows(p, npc()))
        p.inventory.remove(Item(882, 10)); p.equipment.add(Item(882))
        assertFalse(ArcheryCompetition.replaceArrows(p, npc())); p.equipment.remove(Item(882))
        p.count = 11; assertFalse(ArcheryCompetition.replaceArrows(p, npc()))
    }

    @Test fun shotsRequirePaidRoundBowBronzeAmmoCorrectTargetRangeAndSight() {
        val p = archer(); val target = obj()
        p.count = 0; assertFalse(ArcheryCompetition.shoot(p, target)); p.count = 1
        p.equipment.remove(Item(841)); assertFalse(ArcheryCompetition.shoot(p, target)); p.equipment.add(Item(841))
        p.equipment.remove(Item(882, 10)); p.equipment.add(Item(884, 10))
        assertFalse(ArcheryCompetition.shoot(p, target)); p.equipment.remove(Item(884, 10)); p.equipment.add(Item(882, 10))
        p.move(targetPosition.translate(-4, 0)); assertFalse(ArcheryCompetition.shoot(p, target))
        p.move(targetPosition.translate(-11, 0)); assertFalse(ArcheryCompetition.shoot(p, target))
        p.move(firingPosition.translate(0, 0, 1)); assertFalse(ArcheryCompetition.shoot(p, target))
        p.move(firingPosition); assertFalse(ArcheryCompetition.shoot(p, obj(position = firingPosition)))
        `when`(world.collisionManager.raycast(firingPosition, targetPosition)).thenReturn(false)
        assertFalse(ArcheryCompetition.shoot(p, target)); assertEquals(10, p.equipment.computeAmountForId(882))
        assertEquals(1, p.count)
    }

    @Test fun normalShotLocksConsumesOneArrowAndRejectsDuplicateClicks() {
        val p = archer()
        assertTrue(ArcheryCompetition.shoot(p, obj())); assertTrue(p.isLocked)
        assertEquals(2, p.count); assertEquals(9, p.equipment.computeAmountForId(882))
        assertFalse(ArcheryCompetition.shoot(p, obj())); assertEquals(0, p.score)
        finish(p); assertFalse(p.isLocked); assertNotNull(p.overlays.getOverlay(ArcheryCompetition.Result::class.java))
        assertTrue(p.score in listOf(0, 10, 20, 30, 50, 100))
    }

    @Test fun everyRingAwardsOnlyRangedXpAndNativeResultTextAfterFlight() {
        for (hit in 0..11) {
            val p = archer(); val shot = ArcheryCompetition.Shot(p, obj(), hit)
            p.submitAction(shot); assertFalse(shot.run()); assertFalse(shot.run()); assertEquals(0, p.score)
            assertTrue(shot.run()); shot.complete()
            val points = listOf(100, 50, 30, 30, 30, 20, 20, 20, 20, 10, 10, 0)[hit]
            assertEquals(points, p.score); assertEquals(hit, p.hit)
            if (points > 0) verify(p.ranged).addExperience(points / 2.0)
            else verify(p.ranged, never()).addExperience(anyDouble())
            verify(p.hitpoints, never()).addExperience(anyDouble())
            shot.run(); assertEquals(points, p.score)
            verify(p).sendText(anyString(), eq(567))
        }
    }

    @Test fun tenShotsFinishRoundAndOnlyJudgeCanClaimOnce() {
        val p = archer()
        repeat(10) { p.submitAction(ArcheryCompetition.Shot(p, obj(), 0)); finish(p) }
        assertEquals(11, p.count); assertEquals(1000, p.score); assertFalse(p.equipment.contains(882))
        assertFalse(ArcheryCompetition.shoot(p, obj())); assertFalse(ArcheryCompetition.claim(p, npc()))
        p.move(ArcheryCompetition.JUDGE); assertTrue(ArcheryCompetition.claim(p, npc()))
        assertEquals(100, p.inventory.computeAmountForId(1464)); assertEquals(0, p.count)
        assertFalse(ArcheryCompetition.claim(p, npc()))
    }

    @Test fun canceledDeadTeleportedAndLoggedOutFlightsStaySpentWithoutXpOrScore() {
        for (reason in 0..3) {
            val p = archer(); p.submitAction(ArcheryCompetition.Shot(p, obj(), 0))
            when (reason) {
                0 -> pending.getValue(p).interrupt()
                1 -> `when`(p.isAlive).thenReturn(false)
                2 -> p.move(ArcheryCompetition.JUDGE)
                3 -> ArcheryCompetition.logout(p)
            }
            finish(p); assertFalse(p.isLocked); assertEquals(2, p.count); assertEquals(0, p.score)
            assertEquals(9, p.equipment.computeAmountForId(882)); verify(p.ranged, never()).addExperience(anyDouble())
            assertNull(p.overlays.getOverlay(ArcheryCompetition.Result::class.java))
        }
    }

    @Test fun completedRoundSurvivesFullInventoryAndTicketOverflowUntilClaimFits() {
        val p = player(); p.count = 11; p.score = 155; fill(p)
        assertFalse(ArcheryCompetition.claim(p, npc())); assertEquals(155, p.score)
        p.inventory.remove(Item(1511)); assertTrue(ArcheryCompetition.claim(p, npc()))
        assertEquals(15, p.inventory.computeAmountForId(1464))
        p.count = 11; p.score = 10; p.inventory.remove(Item(1464, 15)); p.inventory.add(Item(1464, Int.MAX_VALUE))
        assertFalse(ArcheryCompetition.claim(p, npc())); assertEquals(11, p.count)
    }

    @Test fun zeroScoreNeedsNoFreeTicketSlot() {
        val p = player(); p.count = 11; fill(p)
        assertTrue(ArcheryCompetition.claim(p, npc())); assertEquals(0, p.count)
        assertFalse(p.inventory.contains(1464))
    }

    @Test fun roundStatePersistsAndRefreshRestoresAllThreeNativeVarps() {
        val p = player(); p.count = 6; p.score = 170; p.hit = 2
        val gson = Attribute.getGsonInstance()
        val restored = gson.fromJson(gson.toJson(p.attributes()), AttributeMap::class.java)
        val other = player(); `when`(other.attributes()).thenReturn(restored)
        ArcheryCompetition.refresh(other)
        assertEquals(6, other.count); assertEquals(170, other.score); assertEquals(2, other.hit)
        val varps = ArgumentCaptor.forClass(Varp::class.java)
        verify(other, times(3)).sendVarp(varps.capture())
        assertEquals(mapOf(156 to 6, 157 to 170, 158 to 2), varps.allValues.associate { it.id to it.value })
    }

    @Test fun independentRollsCanMissOrOverrideEarlierRingsWithBullsEye() {
        assertEquals(11, ArcheryCompetition.roll(0) { 0 })
        val rolls = ArrayDeque(listOf(9999, 9999, 0, 9999, 0, 3, 9999, 0, 2, 9999, 0, 9999, 0))
        assertEquals(0, ArcheryCompetition.roll(10000) { bound -> rolls.removeFirst().also { assertTrue(it in 0 until bound) } })
        assertTrue(rolls.isEmpty())
        val blue = ArrayDeque(listOf(9999, 0, 0, 9999, 0, 3, 0, 0, 0, 0, 0, 0))
        assertEquals(8, ArcheryCompetition.roll(10000) { blue.removeFirst() })
    }

    @Test fun eachRewardBuysExactBundleUsingOnlyTickets() {
        for (reward in ArcheryRewards.Reward.values()) {
            val p = player(); val merchant = npc(694)
            p.inventory.add(Item(1464, reward.cost)); ArcheryRewards.open(p, merchant)
            assertTrue(ArcheryRewards.select(p, reward.ordinal, reward.item, false))
            assertEquals(reward.cost, p.inventory.computeAmountForId(1464))
            assertTrue(ArcheryRewards.select(p, reward.ordinal, reward.item, true))
            assertEquals(reward.amount, p.inventory.computeAmountForId(reward.item)); assertFalse(p.inventory.contains(1464))
            assertFalse(ArcheryRewards.select(p, reward.ordinal, reward.item, true))
        }
    }

    @Test fun rewardPurchasesRejectForgedSlotsClosedInterfacesAndRemoteOrRemovedMerchants() {
        val p = player(); val merchant = npc(694); p.inventory.add(Item(1464, 100))
        assertFalse(ArcheryRewards.select(p, 3, 1169, true)); ArcheryRewards.open(p, merchant)
        assertFalse(ArcheryRewards.select(p, 0, 1169, true)); assertFalse(ArcheryRewards.select(p, -1, 1169, true))
        p.move(Position(3200, 3200)); assertFalse(ArcheryRewards.select(p, 3, 1169, true))
        p.move(ArcheryCompetition.JUDGE); `when`(merchant.state).thenReturn(EntityState.NEW)
        assertFalse(ArcheryRewards.select(p, 3, 1169, true)); assertEquals(100, p.inventory.computeAmountForId(1464))
    }

    @Test fun rewardCapacityChecksAllowExactPaymentAndExistingStacksButRejectOverflow() {
        val p = player(); p.inventory.add(Item(1464, 101)); fill(p); ArcheryRewards.open(p, npc(694))
        assertFalse(ArcheryRewards.select(p, 3, 1169, true)); p.inventory.remove(Item(1464))
        assertTrue(ArcheryRewards.select(p, 3, 1169, true)); assertTrue(p.inventory.contains(1169))
        val other = player(); other.inventory.add(Item(1464, 4000)); other.inventory.add(Item(892, 1)); fill(other)
        ArcheryRewards.open(other, npc(694)); assertTrue(ArcheryRewards.select(other, 2, 892, true))
        other.inventory.remove(Item(892, 51)); other.inventory.add(Item(892, Int.MAX_VALUE))
        assertFalse(ArcheryRewards.select(other, 2, 892, true)); assertEquals(2000, other.inventory.computeAmountForId(1464))
    }

    @Test fun gateEnforcesCurrentRangedOnEntryAndAllowsExitBelowForty() {
        val p = player(RangingGuildGate.OUTSIDE); val gate = obj(2514, RangingGuildGate.POSITION)
        `when`(p.ranged.level).thenReturn(39)
        assertTrue(RangingGuildGate.enter(p, gate)); finish(p); assertEquals(RangingGuildGate.OUTSIDE, p.position)
        `when`(p.ranged.level).thenReturn(40)
        assertTrue(RangingGuildGate.enter(p, gate)); finish(p); assertEquals(RangingGuildGate.INSIDE, p.position)
        `when`(p.ranged.level).thenReturn(1)
        assertTrue(RangingGuildGate.enter(p, gate)); finish(p); assertEquals(RangingGuildGate.OUTSIDE, p.position)
    }

    @Test fun guildPassageNeverMovesPlayerBackAfterTeleportOrLogout() {
        for (logout in listOf(false, true)) {
            val p = player(RangingGuildGate.OUTSIDE)
            assertTrue(RangingGuildGate.enter(p, obj(2514, RangingGuildGate.POSITION)))
            val approach = pending.getValue(p); assertTrue(approach.run()); approach.complete(); assertTrue(p.isLocked)
            p.move(Position(3200, 3200)); if (logout) RangingGuildGate.logout(p)
            finish(p); assertEquals(Position(3200, 3200), p.position); assertFalse(p.isLocked)
        }
    }

    @Test fun compiledHandlersWireNativeValueBuyAndCloseEvents() {
        val first = scriptListeners.size; val matchers = scriptMatchers.size
        Class.forName("game.content.rangingGuild.ArcheryHandlers").getConstructor(Array<String>::class.java)
            .newInstance(emptyArray<String>() as Any)
        val listeners = scriptListeners.drop(first)
        scriptListeners.subList(first, scriptListeners.size).clear()
        scriptMatchers.subList(matchers, scriptMatchers.size).clear()
        @Suppress("UNCHECKED_CAST")
        fun dispatch(event: Event) {
            for (listener in listeners.filter { it.eventType == event.javaClass })
                (listener as EventListener<Event>).apply(event)
        }
        val p = player(); p.inventory.add(Item(1464, 100)); ArcheryRewards.open(p, npc(694))
        dispatch(WidgetItemFirstClickEvent(p, 3, 12033, 1169))
        assertEquals(100, p.inventory.computeAmountForId(1464))
        dispatch(WidgetItemSecondClickEvent(p, 3, 12033, 1169))
        assertTrue(p.inventory.contains(1169))
        pipelines.get(ButtonClickEvent::class.java).post(ButtonClickEvent(p, 568))
        assertNotNull(p.overlays.getOverlay(ArcheryRewards.Exchange::class.java))
        pipelines.get(ButtonClickEvent::class.java).post(ButtonClickEvent(p, 12032))
        assertNull(p.overlays.getOverlay(ArcheryRewards.Exchange::class.java))
        p.overlays.open(ArcheryCompetition.Result())
        pipelines.get(ButtonClickEvent::class.java).post(ButtonClickEvent(p, 568))
        assertNull(p.overlays.getOverlay(ArcheryCompetition.Result::class.java))
    }
}
