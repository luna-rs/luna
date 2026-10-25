package game.content.treasureTrails

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import com.google.common.collect.ImmutableList
import game.content.treasureTrails.TreasureTrails.trailItem
import game.content.treasureTrails.TreasureTrails.trailSteps
import io.luna.LunaContext
import io.luna.game.cache.Cache
import io.luna.game.cache.codec.MapDecoder
import io.luna.game.cache.codec.WidgetDefinitionDecoder
import io.luna.game.cache.map.MapIndex
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.util.OptionalInt
import game.skill.magic.telekineticGrab.TelekineticGrabAction
import io.luna.game.event.EventListener
import io.luna.game.event.impl.GroundItemClickEvent.PickupItemEvent
import io.luna.game.model.mob.attr.Attribute
import io.luna.game.model.mob.attr.AttributeMap
import game.content.treasureTrails.TreasureTrails.trailRevision
import game.content.treasureTrails.TrailPuzzle.puzzleBox
import game.content.treasureTrails.TrailPuzzle.puzzleTiles
import game.content.treasureTrails.TrailNavigation.chartProgress
import game.content.treasureTrails.TrailGuardians.defeatedGuardian
import io.luna.game.event.impl.ItemClickEvent.ItemFifthClickEvent
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.net.msg.`in`.ItemClickMessageReader
import io.luna.game.task.Task

/**
 * Exercises all easy targets, progression, acquisition guards and reward delivery with real containers.
 *
 * @author lare96
 */
class TreasureTrailsTest {
    companion object {
        @JvmStatic @BeforeAll fun initialize() {
            InventoryProductionFixtures.initializeCombat()
            // The shared fixture omits notes; restore their native metadata for reward tests.
            val ids = TrailTier.entries.flatMap { TrailRewards.normal(it) + TrailRewards.rare(it) }.map { it.id }.toSet()
            Files.newBufferedReader(Path.of("data/dumps/items.json")).use { reader ->
                for (entry in JsonParser.parseReader(reader).asJsonArray) {
                    val item = entry.asJsonObject
                    if (item["id"].asInt !in ids) continue
                    if (ItemDefinition.ALL.get(item["id"].asInt).isPresent) continue
                    val unnoted = item.getAsJsonObject("unnoted_id")
                    ItemDefinition.ALL.storeDefinition(ItemDefinition(
                        item["id"].asInt, item["name"].asString, "", 0, 0, 0, 0, 0, 0, 0,
                        item["stackable"].asBoolean, 0, false, emptyArray(), emptyArray(),
                        if (unnoted["is_present"].asBoolean) OptionalInt.of(unnoted["value"].asInt) else OptionalInt.empty(),
                        0, 0, 0, OptionalInt.empty(), 0.0, item["tradeable"].asBoolean
                    ))
                }
            }
            for (id in listOf(6946, 6960, 6965, 6976, 6988, 6994, 7045, 7113, 7221, 7271)) {
                if (WidgetDefinition.ALL.get(id).isPresent) continue
                val definition = mock(WidgetDefinition::class.java)
                `when`(definition.id()).thenReturn(id)
                `when`(definition.children).thenReturn(ImmutableList.of())
                WidgetDefinition.ALL.storeDefinition(definition)
            }
        }
    }

    private fun player(position: Position = Position(3200, 3200)): Player =
        InventoryProductionFixtures.bot().also {
            `when`(it.position).thenReturn(position)
            `when`(it.isAlive).thenReturn(true)
            val overlays = AbstractOverlaySet(it)
            `when`(it.overlays).thenReturn(overlays)
        }

    private fun npc(id: Int, position: Position): Npc = mock(Npc::class.java).also {
        `when`(it.id).thenReturn(id)
        `when`(it.position).thenReturn(position)
        `when`(it.state).thenReturn(EntityState.ACTIVE)
    }

    private fun obj(id: Int, position: Position): GameObject = mock(GameObject::class.java).also {
        `when`(it.id).thenReturn(id)
        `when`(it.position).thenReturn(position)
        `when`(it.state).thenReturn(EntityState.ACTIVE)
        `when`(it.objectType).thenReturn(ObjectType.DEFAULT)
        `when`(it.direction).thenReturn(ObjectDirection.SOUTH)
    }

    private fun add(player: Player, id: Int) {
        assertTrue(player.inventory.add(Item(id)))
        TreasureTrails.acquired(player, id)
    }

    private fun objectIds(clue: EasyClues.Clue): Set<Int> = when (clue.kind) {
        EasyClues.Kind.CHEST_CLOSED -> EasyClues.closedChests
        EasyClues.Kind.EMPTY_CRATE -> EasyClues.emptyCrate
        EasyClues.Kind.EMPTY_CRATES -> EasyClues.emptyCrates
        EasyClues.Kind.EMPTY_BOXES -> EasyClues.emptyBoxes
        EasyClues.Kind.FALADOR_CRATES -> setOf(11745)
        EasyClues.Kind.EMPTY_DRAWER, EasyClues.Kind.DRAWER -> EasyClues.drawer
        else -> clue.targetObjects
    }

    @Test fun catalogContainsAllReleasedCluesAndRealItems() {
        assertEquals(39, EasyClues.all.size)
        assertEquals(39, EasyClues.byId.size)
        assertEquals(3, EasyClues.byCasket.size)
        assertEquals(11, EasyClues.all.count { it.npcId != -1 })
        assertEquals(25, EasyClues.all.count { it.kind != null })
        for (clue in EasyClues.all) {
            assertTrue(ItemDefinition.ALL.get(clue.id).isPresent)
            assertTrue(clue.id in EasyClues.trailItems)
            assertTrue(clue.text.split('|').size <= 4)
        }
        assertEquals(27, EasyTrailRewards.normal.size)
        assertEquals(22, EasyTrailRewards.rare.size)
        for (reward in EasyTrailRewards.normal + EasyTrailRewards.rare) {
            assertTrue(ItemDefinition.ALL.get(reward.id).isPresent)
        }
    }

    @Test fun allNpcCluesAdvanceOnlyForTheirOwnNpcNearby() {
        for (clue in EasyClues.all.filter { it.npcId != -1 }) {
            val player = player()
            add(player, clue.id)
            assertFalse(TreasureTrails.talk(player, npc(9999, player.position)) { 0 })
            assertFalse(TreasureTrails.talk(player, npc(clue.npcId, Position(3210, 3200))) { 0 })
            assertFalse(TreasureTrails.talk(player, npc(clue.npcId, Position(3200, 3200, 1))) { 0 })
            assertTrue(TreasureTrails.talk(player, npc(clue.npcId, player.position)) { 0 })
            assertEquals(1, player.trailSteps)
            assertEquals(2713, player.trailItem)
            assertFalse(player.inventory.contains(clue.id))
        }
    }

    @Test fun allObjectCluesRequireTheRightObjectPositionPlaneAndOption() {
        for (clue in EasyClues.all.filter { it.kind != null }) {
            val position = clue.position!!
            val player = player(position)
            add(player, clue.id)
            val drawer = clue.kind == EasyClues.Kind.EMPTY_DRAWER
            val id = if (drawer) EasyClues.emptyDrawer.first() else objectIds(clue).first()
            val option = if (drawer) 2 else 1
            assertFalse(TreasureTrails.search(player, obj(9999, position), option) { 0 })
            assertFalse(TreasureTrails.search(player, obj(id, Position(position.x + 1, position.y, position.z)), option) { 0 })
            assertFalse(TreasureTrails.search(player, obj(id, Position(position.x, position.y, (position.z + 1) % 4)), option) { 0 })
            assertFalse(TreasureTrails.search(player, obj(id, position), 3) { 0 })
            assertTrue(TreasureTrails.search(player, obj(id, position), option) { 0 })
            assertEquals(1, player.trailSteps)
            assertFalse(TreasureTrails.search(player, obj(id, position), option) { 0 })
        }
    }

    @Test fun mapsRequireASpadeAndDiggingDoesNotAdvanceUntilCasketIsOpened() {
        for (clue in EasyClues.all.filter { it.map != -1 }) {
            val player = player(clue.position!!)
            add(player, clue.id)
            player.trailSteps = 1
            assertFalse(TreasureTrails.dig(player))
            player.inventory.add(Item(952))
            assertTrue(TreasureTrails.dig(player))
            assertEquals(1, player.trailSteps)
            assertEquals(clue.casket, player.trailItem)
            assertFalse(TreasureTrails.dig(player))
            assertTrue(TreasureTrails.open(player, clue.casket) { it - 1 })
            assertEquals(2, player.trailSteps)
            assertFalse(TreasureTrails.open(player, clue.casket) { error("Consumed casket rerolled") })
        }
    }

    @Test fun ordinaryDrawersOpenBeforeSearchAndOpenChestsAlsoSolveClues() {
        val clue = EasyClues.byId.getValue(2687)
        val position = clue.position!!
        val player = player(position)
        add(player, clue.id)
        val closed = obj(348, position)
        val objects = world.objects
        `when`(objects.unregister(closed)).thenReturn(true)
        clearInvocations(objects)
        assertTrue(TreasureTrails.search(player, closed, 1) { error("Opening advanced the clue") })
        assertEquals(0, player.trailSteps)
        assertTrue(player.inventory.contains(clue.id))
        val opened = ArgumentCaptor.forClass(GameObject::class.java)
        verify(objects).register(opened.capture())
        assertEquals(349, opened.value.id)
        assertEquals(clue.position, opened.value.position)
        assertTrue(TreasureTrails.search(player, obj(349, position), 2) { 0 })
        assertEquals(1, player.trailSteps)
        for (chest in EasyClues.all.filter { it.kind == EasyClues.Kind.CHEST_CLOSED }) {
            val visitor = player(chest.position!!)
            add(visitor, chest.id)
            assertTrue(TreasureTrails.search(visitor, obj(378, chest.position!!), 2) { 0 })
        }
    }

    @Test fun mapsRejectDistantAndOtherPlaneDigsButAcceptOneTileTolerance() {
        val clue = EasyClues.all.first()
        val target = clue.position!!
        for ((position, expected) in listOf(
            Position(target.x + 2, target.y) to false,
            Position(target.x, target.y, 1) to false,
            Position(target.x + 1, target.y + 1) to true
        )) {
            val player = player(position)
            add(player, clue.id)
            player.inventory.add(Item(952))
            assertEquals(expected, TreasureTrails.dig(player))
        }
    }

    @Test fun trailsFinishBetweenTwoAndFourSteps() {
        assertFalse(TreasureTrails.complete(1) { error("First step must not roll completion") })
        assertTrue(TreasureTrails.complete(2) { 0 })
        assertFalse(TreasureTrails.complete(2) { 1 })
        assertTrue(TreasureTrails.complete(3) { 0 })
        assertFalse(TreasureTrails.complete(3) { 1 })
        assertTrue(TreasureTrails.complete(4) { error("Fourth step must always finish") })
    }

    @Test fun everyTierInInventoryOrBankPreventsAnotherClue() {
        val player = player()
        for (id in EasyClues.trailItems) {
            assertTrue(player.bank.add(Item(id)))
            assertFalse(TreasureTrails.canAcquire(player, 2713), "Banked $id")
            assertTrue(player.bank.remove(id))
            assertTrue(player.inventory.add(Item(id)))
            assertFalse(TreasureTrails.canAcquire(player, 2714), "Carried $id")
            assertTrue(player.inventory.remove(id))
        }
        assertTrue(TreasureTrails.canAcquire(player, 2713))
        add(player, 2713)
        assertTrue(TreasureTrails.canAcquire(player, 995))
    }

    @Test fun acquisitionResetsOldProgressAndOrdinaryPickupDoesNot() {
        val player = player()
        player.trailSteps = 3
        player.trailItem = 2713
        TreasureTrails.acquired(player, 995)
        assertEquals(3, player.trailSteps)
        TreasureTrails.acquired(player, 2713)
        assertEquals(0, player.trailSteps)
    }

    @Test fun bankingPreservesProgressButAMismatchedScrollResetsIt() {
        val player = player()
        add(player, 2714)
        player.trailSteps = 2
        player.inventory.remove(2714)
        player.bank.add(Item(2714))
        assertFalse(TreasureTrails.open(player, 2714))
        player.bank.remove(2714)
        player.inventory.add(Item(2714))
        assertTrue(TreasureTrails.open(player, 2714) { it - 1 })
        assertEquals(3, player.trailSteps)
        player.inventory.remove(player.trailItem)
        player.inventory.add(Item(2717))
        assertTrue(TreasureTrails.open(player, 2717) { it - 1 })
        assertEquals(1, player.trailSteps)
    }

    @Test fun savedProgressSurvivesAttributeSerialization() {
        val player = player()
        add(player, 2714)
        player.trailSteps = 2
        val gson = Attribute.getGsonInstance()
        val restored = gson.fromJson(gson.toJson(player.attributes()), AttributeMap::class.java)
        val reconnected = player()
        `when`(reconnected.attributes()).thenReturn(restored)
        reconnected.inventory.add(Item(2714))
        assertEquals(2714, reconnected.trailItem)
        assertEquals(2, reconnected.trailSteps)
        assertTrue(TreasureTrails.open(reconnected, 2714) { it - 1 })
        assertEquals(3, reconnected.trailSteps)
    }

    private fun groundItem(id: Int, player: Player): GroundItem = mock(GroundItem::class.java).also {
        `when`(it.id).thenReturn(id)
        `when`(it.amount).thenReturn(1)
        `when`(it.toItem()).thenReturn(Item(id))
        `when`(it.def()).thenReturn(ItemDefinition.ALL.retrieve(id))
        val position = player.position
        `when`(it.position).thenReturn(position)
        `when`(it.state).thenReturn(EntityState.ACTIVE)
    }

    @Test fun nativePickupRejectsDuplicatesAndResetsOnlySuccessfulAcquisitions() {
        val first = scriptListeners.size
        Class.forName("engine.widget.PickupItem").getConstructor(Array<String>::class.java)
            .newInstance(emptyArray<String>() as Any)
        @Suppress("UNCHECKED_CAST")
        val listener = scriptListeners.drop(first).single() as EventListener<PickupItemEvent>
        scriptListeners.subList(first, scriptListeners.size).clear()
        val player = player()
        val ground = groundItem(2713, player)
        val items = world.items
        `when`(items.unregister(ground)).thenReturn(true)
        clearInvocations(items)
        player.bank.add(Item(2717))
        listener.apply(PickupItemEvent(player, ground))
        verify(items, never()).unregister(ground)
        assertFalse(player.inventory.contains(2713))
        player.bank.remove(2717)
        player.trailSteps = 3
        listener.apply(PickupItemEvent(player, ground))
        assertTrue(player.inventory.contains(2713))
        assertEquals(0, player.trailSteps)
        assertEquals(2713, player.trailItem)
        listener.apply(PickupItemEvent(player, ground))
        verify(items, times(1)).unregister(ground)
    }

    @Test fun telegrabRechecksOwnershipWhenTheProjectileLands() {
        val player = player()
        val ground = groundItem(2713, player)
        val action = spy(TelekineticGrabAction(player, ground))
        doReturn(4).`when`(action).executions
        val items = world.items
        `when`(items.unregister(ground)).thenReturn(true)
        clearInvocations(items)
        player.bank.add(Item(2717))
        assertTrue(action.run())
        verify(items, never()).unregister(ground)
        assertFalse(player.inventory.contains(2713))
        player.bank.remove(2717)
        player.trailSteps = 3
        assertTrue(action.run())
        verify(items, times(1)).unregister(ground)
        assertEquals(2713, player.trailItem)
        assertEquals(0, player.trailSteps)
    }

    @Test fun telegrabRejectsAnExistingClueBeforeChargingRunes() {
        val player = player()
        val ground = groundItem(2713, player)
        val collision = world.collisionManager
        `when`(collision.raycast(player.position, ground.position)).thenReturn(true)
        player.bank.add(Item(2717))
        player.inventory.add(Item(563))
        player.inventory.add(Item(556))
        TelekineticGrabAction(player, ground).onLock()
        assertTrue(player.inventory.contains(563))
        assertTrue(player.inventory.contains(556))
        verify(player).sendMessage("You already have a clue scroll or casket in your inventory or bank.")
    }

    @Test fun clueReadingClearsUnusedLinesAndRequiresTheScroll() {
        val player = player()
        TreasureTrails.read(player, 2685)
        verify(player, never()).sendText(anyString(), anyInt())
        add(player, 2685)
        TreasureTrails.read(player, 2685)
        verify(player).sendText("Search the boxes in", 6991)
        verify(player).sendText("the house near the", 6992)
        verify(player).sendText("south entrance to Varrock.", 6993)
        verify(player).sendText("", 7331)
    }

    @Test fun finalCasketAwardsOnceAndClearsProgress() {
        val player = player()
        add(player, 2714)
        player.trailSteps = 3
        assertTrue(TreasureTrails.open(player, 2714) { 0 })
        assertEquals(2, player.inventory.computeAmountForId(EasyTrailRewards.rare.first().id))
        assertEquals(0, player.trailSteps)
        assertEquals(0, player.trailItem)
        assertFalse(TreasureTrails.open(player, 2714) { error("Duplicate reward roll") })
    }

    @Test fun fullInventoryReplacesCluesAndDropsOnlyRewardOverflow() {
        val player = player()
        add(player, 2714)
        repeat(27) { assertTrue(player.inventory.add(Item(1205))) }
        assertTrue(TreasureTrails.open(player, 2714) { 0 })
        assertEquals(1, player.trailSteps)
        assertTrue(TreasureTrails.dig(player).not())
        player.inventory.replace(player.trailItem, 2714)
        player.trailItem = 2714
        player.trailSteps = 3
        val items = world.items
        clearInvocations(items)
        assertTrue(TreasureTrails.open(player, 2714) { 0 })
        assertEquals(1, player.inventory.computeAmountForId(EasyTrailRewards.rare.first().id))
        val dropped = ArgumentCaptor.forClass(GroundItem::class.java)
        verify(items).register(dropped.capture())
        assertEquals(EasyTrailRewards.rare.first().id, dropped.value.id)
        assertEquals(player.position, dropped.value.position)
    }

    @Test fun rewardsExerciseEveryEntryAndInclusiveQuantityRange() {
        for ((table, rareRoll) in listOf(EasyTrailRewards.normal to 1, EasyTrailRewards.rare to 0)) {
            table.forEachIndexed { index, reward ->
                for (maximum in listOf(false, true)) {
                    val rolls = ArrayDeque<Int>()
                    rolls.add(if (maximum) 2 else 0)
                    repeat(if (maximum) 4 else 2) {
                        rolls.add(rareRoll)
                        rolls.add(index)
                        rolls.add(if (maximum) reward.max - reward.min else 0)
                    }
                    val items = EasyTrailRewards.roll { bound ->
                        rolls.removeFirst().also { assertTrue(it in 0 until bound) }
                    }
                    assertEquals(if (maximum) 4 else 2, items.size)
                    assertTrue(items.all { it.id == reward.id && it.amount == if (maximum) reward.max else reward.min })
                    assertTrue(rolls.isEmpty())
                }
            }
        }
    }

    @Test fun deathDropsRequireAnEligibleNpcNoExistingClueAndSuccessfulRoll() {
        val player = player()
        val items = world.items
        clearInvocations(items)
        TreasureTrails.drop(player, npc(9999, player.position)) { error("Ineligible NPC rolled") }
        add(player, 2713)
        TreasureTrails.drop(player, npc(1, player.position)) { error("Existing clue rolled") }
        player.inventory.remove(2713)
        TreasureTrails.drop(player, npc(1, player.position)) { 1 }
        verify(items, never()).register(any(GroundItem::class.java))
        TreasureTrails.drop(player, npc(1, player.position)) { 0 }
        val dropped = ArgumentCaptor.forClass(GroundItem::class.java)
        verify(items).register(dropped.capture())
        assertEquals(2713, dropped.value.id)
    }

    @Test fun lockedDeadAndInactiveInteractionsCannotAdvance() {
        val player = player()
        add(player, 2681)
        val hans = npc(0, player.position)
        `when`(player.isLocked).thenReturn(true)
        assertFalse(TreasureTrails.talk(player, hans))
        `when`(player.isLocked).thenReturn(false)
        `when`(player.isAlive).thenReturn(false)
        assertFalse(TreasureTrails.talk(player, hans))
        `when`(player.isAlive).thenReturn(true)
        `when`(hans.state).thenReturn(EntityState.INACTIVE)
        assertFalse(TreasureTrails.talk(player, hans))
        assertEquals(0, player.trailSteps)
    }

    @Test fun allTiersHaveCompleteCatalogsAndNativeNpcSpawns() {
        assertEquals(101, TrailClues.all.size)
        assertEquals(101, TrailClues.byId.size)
        assertEquals(31, TrailClues.medium.size)
        assertEquals(31, TrailClues.hard.size)
        assertEquals(6, TrailClues.all.count { it.answer >= 0 })
        assertEquals(3, TrailClues.all.count { it.puzzle })
        assertEquals(13, TrailClues.all.count { it.guardian })
        val spawns = Files.newBufferedReader(Path.of("data/game/world/npc_spawns.jsonc")).use {
            JsonParser.parseReader(it).asJsonArray.map { row -> row.asJsonObject["id"].asInt }.toSet()
        }
        for (clue in TrailClues.all) {
            assertTrue(clue.text.split('|').size <= 8)
            assertTrue(clue.id in EasyClues.trailItems)
            assertTrue(clue.npcId >= 0 || clue.position != null, "No target for ${clue.id}")
            if (clue.npcId >= 0) assertTrue(clue.npcId in spawns, "Missing NPC ${clue.npcId}")
            for (id in listOf(clue.id, clue.casket, clue.key, clue.auxiliary).filter { it >= 0 }) {
                assertTrue(ItemDefinition.ALL.get(id).isPresent, "Missing item $id")
            }
        }
        assertTrue(spawns.containsAll(TrailNavigation.suppliers))
    }

    @Test fun tierLengthsAndRewardsMatchTheirOriginalBoundaries() {
        for (tier in TrailTier.entries) {
            for (step in 1 until tier.maximumSteps - 2) {
                assertFalse(TreasureTrails.complete(step, { error("Premature completion roll") }, tier))
            }
            assertTrue(TreasureTrails.complete(tier.maximumSteps - 2, { 0 }, tier))
            assertFalse(TreasureTrails.complete(tier.maximumSteps - 1, { 1 }, tier))
            assertTrue(TreasureTrails.complete(tier.maximumSteps, { error("Must complete") }, tier))
            for (rare in listOf(true, false)) {
                val table = if (rare) TrailRewards.rare(tier) else TrailRewards.normal(tier)
                table.forEachIndexed { index, reward ->
                    val rolls = ArrayDeque<Int>()
                    rolls.add(2)
                    repeat(tier.rewardRolls + 2) {
                        rolls.add(if (rare) 0 else 1)
                        rolls.add(index)
                        rolls.add(reward.max - reward.min)
                    }
                    val items = TrailRewards.roll(tier) { bound ->
                        rolls.removeFirst().also { assertTrue(it in 0 until bound) }
                    }
                    assertEquals(tier.rewardRolls + 2, items.size)
                    assertTrue(items.all { it.id == reward.id && it.amount == reward.max })
                }
            }
        }
        assertEquals(26, TrailRewards.mediumNormal.size)
        assertEquals(18, TrailRewards.mediumRare.size)
        assertEquals(20, TrailRewards.hardNormal.size)
        assertEquals(33, TrailRewards.hardRare.size)
    }

    @Test fun allCoordinateCluesRequireEquipmentAndCorrectGuardianCredit() {
        for (clue in TrailClues.all.filter { it.coordinate }) {
            val player = player(clue.position!!)
            add(player, clue.id)
            player.inventory.add(Item(952))
            assertTrue(TreasureTrails.dig(player))
            assertTrue(player.inventory.contains(clue.id))
            assertFalse(player.inventory.contains(clue.casket))
            TrailNavigation.equipment.forEach { player.inventory.add(Item(it)) }
            if (clue.guardian) {
                val guardian = npc(1007, player.position)
                TrailGuardians.track(player, guardian, clue.id)
                assertTrue(TreasureTrails.dig(player))
                assertTrue(player.inventory.contains(clue.id))
                assertFalse(TrailGuardians.defeated(player(), guardian))
                assertTrue(TrailGuardians.defeated(player, guardian))
            }
            assertTrue(TreasureTrails.dig(player))
            assertTrue(player.inventory.contains(clue.casket))
            assertEquals(0, player.trailSteps)
            assertTrue(TreasureTrails.open(player, clue.casket) { 0 })
            assertEquals(clue.tier, TrailClues.byId.getValue(player.trailItem).tier)
            assertEquals(1, player.trailSteps)
            assertEquals(0, player.defeatedGuardian)
        }
    }

    @Test fun hardRiddleDigsDoNotRequireNavigationEquipment() {
        for (clue in TrailClues.hard.filter { !it.coordinate && it.casket >= 0 && it.position != null }) {
            val player = player(clue.position!!)
            add(player, clue.id)
            player.inventory.add(Item(952))
            assertTrue(TreasureTrails.dig(player))
            assertTrue(player.inventory.contains(clue.casket))
            assertTrue(TreasureTrails.open(player, clue.casket) { 0 })
            assertEquals(TrailTier.HARD, TrailClues.byId.getValue(player.trailItem).tier)
        }
    }

    @Test fun allLockedCluesNeedTheirOwnKeyAndConsumeItExactlyOnce() {
        for (clue in TrailClues.medium.filter { it.key >= 0 }) {
            val position = clue.position!!
            val player = player(position)
            add(player, clue.id)
            val target = obj(objectIds(clue).first(), position)
            assertTrue(TreasureTrails.search(player, target, 1) { error("Missing key advanced") })
            assertTrue(player.inventory.contains(clue.id))
            assertEquals(0, player.trailSteps)
            player.inventory.add(Item(clue.key))
            assertTrue(TreasureTrails.search(player, target, 1) { 0 })
            assertFalse(player.inventory.contains(clue.key))
            assertEquals(1, player.trailSteps)
            assertFalse(TreasureTrails.search(player, target, 1) { error("Duplicate search advanced") })
        }
    }

    @Test fun keyDropsComeOnlyFromMatchingNpcsAndRespectBankOwnership() {
        for (clue in TrailClues.medium.filter { it.key >= 0 }) {
            val player = player()
            add(player, clue.id)
            val items = world.items
            clearInvocations(items)
            TreasureTrails.drop(player, npc(9999, player.position)) { 1 }
            verify(items, never()).register(any(GroundItem::class.java))
            TreasureTrails.drop(player, npc(clue.keyNpcs.first(), player.position)) { 1 }
            val dropped = ArgumentCaptor.forClass(GroundItem::class.java)
            verify(items).register(dropped.capture())
            assertEquals(clue.key, dropped.value.id)
            player.bank.add(Item(clue.key))
            TreasureTrails.drop(player, npc(clue.keyNpcs.first(), player.position)) { 1 }
            verify(items, times(1)).register(any(GroundItem::class.java))
        }
    }

    @Test fun everyChallengeRequiresTheCorrectAnswerNpcAndCurrentPrompt() {
        for (clue in TrailClues.medium.filter { it.answer >= 0 }) {
            val player = player()
            add(player, clue.id)
            val target = npc(clue.npcId, player.position)
            assertTrue(TreasureTrails.talk(player, target) { 0 })
            assertTrue(player.inventory.contains(clue.auxiliary))
            assertEquals(0, player.trailSteps)
            assertFalse(TreasureTrails.answer(player, target, clue.id, player.trailRevision, clue.answer + 1) { 0 })
            assertFalse(TreasureTrails.answer(player, npc(9999, player.position), clue.id, player.trailRevision, clue.answer) { 0 })
            assertFalse(TreasureTrails.answer(player, target, clue.id, player.trailRevision - 1, clue.answer) { 0 })
            val revision = player.trailRevision
            assertTrue(TreasureTrails.answer(player, target, clue.id, revision, clue.answer) { 0 })
            assertEquals(1, player.trailSteps)
            assertFalse(player.inventory.contains(clue.auxiliary))
            assertFalse(TreasureTrails.answer(player, target, clue.id, revision, clue.answer) { 0 })
        }
    }

    @Test fun everyPuzzleNpcRequiresASolvedBoardAndConsumesTheBox() {
        for (clue in TrailClues.hard.filter { it.puzzle }) {
            val player = player()
            add(player, clue.id)
            val target = npc(clue.npcId, player.position)
            assertTrue(TreasureTrails.talk(player, target) { 0 })
            assertTrue(player.inventory.contains(clue.auxiliary))
            assertFalse(TrailPuzzle.solved(player, clue.auxiliary))
            assertTrue(TreasureTrails.talk(player, target) { 0 })
            assertEquals(0, player.trailSteps)
            player.puzzleTiles = ((1..24).toList() + 0).joinToString(",")
            assertTrue(TreasureTrails.talk(player, target) { 0 })
            assertEquals(1, player.trailSteps)
            assertFalse(player.inventory.contains(clue.auxiliary))
            assertEquals(0, player.puzzleBox)
        }
    }

    @Test fun puzzleMovesRejectRowWrapForgedItemsAndClosedOrStaleInterfaces() {
        val player = player()
        add(player, 2794)
        player.inventory.add(Item(2795))
        TrailPuzzle.start(player, 2795) { 0 }
        assertFalse(TrailPuzzle.move(player, 23, 2772))
        assertTrue(TrailPuzzle.open(player, 2795))
        player.puzzleTiles = ((1..23).toList() + listOf(0, 24)).joinToString(",")
        assertFalse(TrailPuzzle.move(player, 24, 2771))
        assertTrue(TrailPuzzle.move(player, 24, 2772))
        assertTrue(TrailPuzzle.solved(player, 2795))
        assertFalse(TrailPuzzle.move(player, 20, 2769))
        assertFalse(TrailPuzzle.adjacent(4, 5))
        assertFalse(TrailPuzzle.adjacent(19, 20))
        assertFalse(TrailPuzzle.move(player, -1, 2772))
        player.inventory.remove(2794)
        assertFalse(TrailPuzzle.move(player, 23, 2772))
    }

    @Test fun puzzleShufflesAreSolvableAndSurviveSaving() {
        repeat(100) { seed ->
            val random = kotlin.random.Random(seed)
            val tiles = TrailPuzzle.shuffle { random.nextInt(it) }
            val values = tiles.filter { it != 0 }
            val inversions = values.indices.sumOf { i -> values.drop(i + 1).count { it < values[i] } }
            assertEquals(0, inversions % 2)
            assertEquals((0..24).toSet(), tiles.toSet())
            assertNotEquals((1..24).toList() + 0, tiles)
        }
        val player = player()
        add(player, 2794)
        player.inventory.add(Item(2795))
        TrailPuzzle.start(player, 2795) { 0 }
        val gson = Attribute.getGsonInstance()
        val restored = gson.fromJson(gson.toJson(player.attributes()), AttributeMap::class.java)
        val other = player()
        `when`(other.attributes()).thenReturn(restored)
        assertEquals(player.puzzleBox, other.puzzleBox)
        assertEquals(TrailPuzzle.tiles(player), TrailPuzzle.tiles(other))
    }

    @Test fun equipmentFollowsProfessorMurphyKojoAndReplacementSequence() {
        val player = player()
        add(player, 2801)
        val professor = npc(488, player.position)
        val murphy = npc(463, player.position)
        val kojo = npc(223, player.position)
        assertFalse(TreasureTrails.talk(player, murphy))
        assertTrue(TreasureTrails.talk(player, professor))
        assertEquals(1, player.chartProgress)
        assertFalse(TreasureTrails.talk(player, kojo))
        assertTrue(TreasureTrails.talk(player, murphy))
        assertTrue(TreasureTrails.talk(player, kojo))
        assertTrue(TreasureTrails.talk(player, professor))
        assertEquals(4, player.chartProgress)
        assertTrue(TrailNavigation.hasEquipment(player))
        player.inventory.remove(2574)
        player.bank.add(Item(2574))
        assertFalse(TreasureTrails.talk(player, murphy))
        player.bank.remove(2574)
        assertTrue(TreasureTrails.talk(player, murphy))
        assertTrue(TrailNavigation.hasEquipment(player))
    }

    @Test fun sextantControlsAreBoundedAndEveryStartingPositionCanBeAligned() {
        repeat(100) { seed ->
            val random = kotlin.random.Random(seed)
            val sextant = TrailNavigation.SextantInterface { random.nextInt(it) }
            while (sextant.frame < 8) assertTrue(sextant.adjust(6952))
            while (sextant.frame > 8) assertTrue(sextant.adjust(6953))
            var moves = 0
            while (sextant.sun != 0 && moves++ < 60) {
                assertTrue(sextant.adjust(if (sextant.sun < 0) 6954 else 6955))
            }
            assertEquals(0, sextant.sun)
            assertFalse(sextant.adjust(9999))
        }
        assertEquals(listOf("2 degrees, 48 minutes North", "22 degrees, 30 minutes East"),
            TrailNavigation.coordinates(Position(3160, 3251)))
        assertFalse(TrailNavigation.usable(Position(3200, 9600)))
        assertFalse(TrailNavigation.usable(Position(2700, 4700)))
        assertTrue(TrailNavigation.usable(Position(3200, 3400)))
        val player = player()
        assertFalse(TrailNavigation.button(player, 6959))
        assertFalse(TrailNavigation.open(player))
        player.inventory.add(Item(2574))
        assertTrue(TrailNavigation.open(player) { 0 })
        assertTrue(TrailNavigation.button(player, 6959))
        verify(player).sendMessage("You need a watch and navigation chart to work out your location.")
    }

    @Test fun guardianCreditCannotBeStolenReplayedOrCarriedToANewClue() {
        val player = player()
        val other = player()
        add(player, 2723)
        val guardian = npc(1007, player.position)
        TrailGuardians.track(player, guardian, 2723)
        assertTrue(TrailGuardians.mayAttack(player, guardian))
        assertFalse(TrailGuardians.mayAttack(other, guardian))
        assertFalse(TrailGuardians.defeated(other, guardian))
        assertTrue(TrailGuardians.defeated(player, guardian))
        assertFalse(TrailGuardians.defeated(player, guardian))
        assertEquals(2723, player.defeatedGuardian)
        TrailGuardians.leave(player, reset = false)
        assertEquals(2723, player.defeatedGuardian)
        TreasureTrails.acquired(player, 2723)
        assertEquals(0, player.defeatedGuardian)
        TrailGuardians.track(player, guardian, 2723)
        player.trailRevision++
        assertFalse(TrailGuardians.defeated(player, guardian))
        TrailGuardians.leave(player)
        assertNull(TrailGuardians.owner(guardian))
    }

    @Test fun nativeClueDropsCannotDoubleRollAndStillSupportOtherLunaSources() {
        val player = player()
        assertNull(TreasureTrails.nativeDrop(player, 1, Item(2677)))
        assertNull(TreasureTrails.nativeDrop(player, 9, Item(2801)))
        assertNull(TreasureTrails.nativeDrop(player, 49, Item(2722)))
        assertNull(TreasureTrails.nativeDrop(null, 9999, Item(2722)))
        val rolled = TreasureTrails.nativeDrop(player, 9999, Item(2722)) { it - 1 }!!
        assertEquals(TrailTier.HARD, TrailClues.byId.getValue(rolled.id).tier)
        player.bank.add(Item(2713))
        assertNull(TreasureTrails.nativeDrop(player, 9999, Item(2722)))
        val coins = Item(995, 100)
        assertSame(coins, TreasureTrails.nativeDrop(player, 49, coins))
        assertEquals(TrailTier.EASY, TrailClues.tierForNpc(1648))
        assertEquals(TrailTier.MEDIUM, TrailClues.tierForNpc(202))
        assertEquals(TrailTier.HARD, TrailClues.tierForNpc(1608))
    }

    @Test fun nativePuzzleReaderAndRegisteredHandlerMoveOnlyTheDisplayedTile() {
        val first = scriptListeners.size
        Class.forName("game.content.treasureTrails.ClueHandlers").getConstructor(Array<String>::class.java)
            .newInstance(emptyArray<String>() as Any)
        @Suppress("UNCHECKED_CAST")
        val listener = scriptListeners.drop(first).single { it.eventType == ItemFifthClickEvent::class.java }
            as EventListener<ItemFifthClickEvent>
        scriptListeners.subList(first, scriptListeners.size).clear()
        val player = player()
        add(player, 2794)
        player.inventory.add(Item(2795))
        val reader = ItemClickMessageReader()
        val click = ItemFifthClickEvent(player, 2772, 24, 6980)
        assertFalse(reader.validate(player, click))
        assertTrue(TrailPuzzle.open(player, 2795))
        player.puzzleTiles = ((1..23).toList() + listOf(0, 24)).joinToString(",")
        assertFalse(reader.validate(player, ItemFirstClickEvent(player, 2772, 24, 6980)))
        assertFalse(reader.validate(player, ItemFifthClickEvent(player, 2771, 24, 6980)))
        assertFalse(reader.validate(player, ItemFifthClickEvent(player, 2772, 24, 6985)))
        assertTrue(reader.validate(player, click))
        reader.handle(player, click)
        assertNotNull(player.overlays.getOverlay(TrailPuzzle.PuzzleInterface::class.java))
        listener.apply(click)
        assertTrue(TrailPuzzle.solved(player, 2795))
        assertFalse(reader.validate(player, click))
    }

    @Test fun nativeDigSpawnsOneTemporaryGuardianAndRemovesItWhenClueIsLost() {
        val clue = TrailClues.byId.getValue(2723)
        val player = player(clue.position!!)
        add(player, clue.id)
        player.inventory.add(Item(952))
        TrailNavigation.equipment.forEach { player.inventory.add(Item(it)) }
        val position = player.position
        val taskCaptor = ArgumentCaptor.forClass(Task::class.java)
        clearInvocations(world)
        mockConstruction(Npc::class.java, withSettings().defaultAnswer(RETURNS_DEEP_STUBS)) { guardian, _ ->
            `when`(guardian.id).thenReturn(1007)
            `when`(guardian.position).thenReturn(position)
            `when`(guardian.state).thenReturn(EntityState.ACTIVE)
            `when`(guardian.isAlive).thenReturn(true)
        }.use { construction ->
            assertTrue(TreasureTrails.dig(player))
            assertEquals(1, construction.constructed().size)
            val guardian = construction.constructed().single()
            verify(guardian).setRespawnTicks(-1)
            verify(guardian.combat).attack(player)
            assertTrue(TreasureTrails.dig(player))
            assertEquals(1, construction.constructed().size)
            assertTrue(player.inventory.contains(clue.id))
            verify(world).schedule(taskCaptor.capture())
            player.inventory.remove(clue.id)
            val task = taskCaptor.value
            task.javaClass.getDeclaredMethod("execute").apply { isAccessible = true }.invoke(task)
            assertNull(TrailGuardians.owner(guardian))
            verify(world.npcs).remove(guardian)
            assertEquals(0, player.defeatedGuardian)
        }
    }

    @Test fun auxiliaryItemsRespectBankOwnershipAndInventoryCapacity() {
        val player = player()
        add(player, 2841)
        repeat(27) { player.inventory.add(Item(1205)) }
        assertTrue(TreasureTrails.talk(player, npc(669, player.position)))
        assertFalse(player.inventory.contains(2842))
        player.inventory.remove(1205)
        player.bank.add(Item(2844))
        assertFalse(TreasureTrails.canAcquire(player, 2842))
        assertTrue(TreasureTrails.talk(player, npc(669, player.position)))
        assertFalse(player.inventory.contains(2842))
        player.bank.add(Item(2795))
        assertFalse(TreasureTrails.canAcquire(player, 2798))
    }

    @Test fun nativeCacheContainsEveryObjectTargetAndClueInterface() {
        Cache().use { cache ->
            cache.open()
            val widgets = ImmutableList.builder<WidgetDefinition>()
            WidgetDefinitionDecoder().decode(cache, widgets)
            val definitions = widgets.build().associateBy { it.id() }
            for (id in TrailClues.all.filter { it.map != -1 }.map { it.map } + listOf(6946, 6960, 6965, 6976, 6988)) {
                assertTrue(id in definitions)
            }
            assertTrue(definitions.getValue(6988).children.containsAll(listOf(6991, 6992, 6993, 7331)))
            assertTrue(definitions.getValue(6960).children.contains(6963))
            assertTrue(definitions.getValue(6976).children.contains(6980))
            assertEquals(WidgetDefinition.WidgetType.ITEM_GRID, definitions.getValue(6980).type)
            assertTrue(definitions.getValue(6965).children.containsAll((6968..6975).toList()))
            assertTrue(definitions.getValue(6946).children.containsAll((6952..6959).toList()))
            val decoder = MapDecoder()
            val indices = ImmutableList.builder<MapIndex>()
            decoder.decode(cache, indices)
            val regions = TrailClues.all.mapNotNull { it.position?.region }.toSet()
            decoder.handle(mock(LunaContext::class.java), cache,
                ImmutableList.copyOf(indices.build().filter { it.region in regions }))
            val objects = cache.mapIndexTable.objectSet.objects
            for (clue in TrailClues.all.filter { it.kind != null || it.targetObjects.isNotEmpty() }) {
                assertTrue(objects.any { it.position == clue.position && it.objectId in objectIds(clue) },
                    "Missing native target for ${clue.id} at ${clue.position}")
            }
        }
    }
}
