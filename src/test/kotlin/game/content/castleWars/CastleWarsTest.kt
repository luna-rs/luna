package game.content.castleWars

import api.bot.script.InventoryProductionFixtures
import api.predef.ext.*
import api.predef.*
import game.bot.scripts.CastleWarsBotScript
import engine.controllers.WildernessLocatableController.wildernessLevel
import io.luna.game.model.`object`.ObjectType
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.chunk.ChunkUpdatableView
import game.skill.magic.teleportSpells.TeleportAction
import api.combat.death.DeathHookHandler
import com.google.common.collect.ImmutableList
import game.content.castleWars.CastleWarsGame.Companion.castleWarsTickets
import game.content.castleWars.CastleWarsMatch.Team
import io.luna.game.cache.Cache
import io.luna.game.cache.codec.MapDecoder
import io.luna.game.cache.map.MapIndex
import io.luna.game.event.impl.EquipItemEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.controller.ControllerManager
import io.luna.game.model.mob.MobDeathAction.DeathStage
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenu
import io.luna.game.model.Position
import io.luna.LunaContext
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.Mockito.mock

/**
 * Covers Castle Wars matches, native interactions, routes, rewards and bot lifecycle boundaries.
 *
 * @author lare96
 */
class CastleWarsTest {
    companion object {
        @JvmStatic @BeforeAll fun initialize() {
            InventoryProductionFixtures.initializeCombat()
            for (id in listOf(11367, 11169)) {
                val definition = mock(io.luna.game.model.def.WidgetDefinition::class.java)
                `when`(definition.id()).thenReturn(id)
                io.luna.game.model.def.WidgetDefinition.ALL.storeDefinition(definition)
            }
        }
    }

    @org.junit.jupiter.api.BeforeEach fun clearMap() {
        val locator = world.locator
        doReturn(emptySet<io.luna.game.model.`object`.GameObject>()).`when`(locator)
            .findObjectsOnTile(any(io.luna.game.model.Locatable::class.java), any())
    }

    private fun player(): Player = InventoryProductionFixtures.bot().also { player ->
        var position = CastleWarsGame.LOBBY
        `when`(player.position).thenAnswer { position }
        doAnswer { position = it.getArgument(0); null }.`when`(player).move(any(Position::class.java))
        val equipment = Equipment(player)
        `when`(player.equipment).thenReturn(equipment)
        `when`(player.controllers).thenReturn(ControllerManager(player))
        `when`(player.contextMenu).thenReturn(PlayerContextMenu(player))
        `when`(player.overlays).thenReturn(AbstractOverlaySet(player))
        `when`(player.isAlive).thenReturn(true)
    }

    private fun start(game: CastleWarsGame, sara: Player, zam: Player) {
        assertTrue(game.enter(sara, Team.SARADOMIN))
        assertTrue(game.enter(zam, Team.ZAMORAK))
        repeat(500) { game.tick() }
        assertTrue(game.match.active)
    }

    @Test fun rejectsFoodAndHeadgearAndBalancesTeams() {
        val game = CastleWarsGame()
        val sara = player()
        val zam = player()
        sara.inventory.add(Item(379))
        assertFalse(game.enter(sara))
        sara.inventory.clear()
        sara.equipment.set(Equipment.HEAD, Item(1153))
        assertFalse(game.enter(sara))
        sara.equipment.clear()
        assertTrue(game.enter(sara, Team.SARADOMIN))
        assertFalse(game.enter(zam, Team.SARADOMIN))
        assertTrue(game.enter(zam, Team.ZAMORAK))
        assertEquals(Team.SARADOMIN.waiting, sara.position)
        assertEquals(4041, sara.equipment.get(Equipment.CAPE).id)
    }

    @Test fun flagPickupIsAtomicAndPreservesWeaponAndShield() {
        val game = CastleWarsGame()
        val sara = player()
        start(game, sara, player())
        sara.move(Team.ZAMORAK.home)
        sara.equipment.set(Equipment.WEAPON, Item(1275))
        sara.equipment.set(Equipment.SHIELD, Item(1171))
        repeat(27) { sara.inventory.add(Item(4049)) }
        assertFalse(game.pickup(sara, Team.ZAMORAK))
        assertEquals(1275, sara.equipment.get(Equipment.WEAPON).id)
        assertEquals(1171, sara.equipment.get(Equipment.SHIELD).id)
        assertNull(game.match.flag(Team.ZAMORAK).carrier)
        sara.inventory.remove(4049)
        assertTrue(game.pickup(sara, Team.ZAMORAK))
        assertTrue(sara.inventory.containsAll(1275, 1171))
        assertEquals(4039, sara.equipment.get(Equipment.WEAPON).id)
        assertFalse(game.pickup(sara, Team.ZAMORAK))
        assertFalse(sara.controllers.checkEvent(EquipItemEvent(sara, 0, 1275, 3214)))
        sara.move(Team.SARADOMIN.home)
        assertTrue(game.capture(sara))
        assertFalse(game.capture(sara))
        assertEquals(1, game.match.score(Team.SARADOMIN))
        assertFalse(sara.equipment.contains(4039))
    }

    @Test fun unequippingFlagDropsItInsteadOfAddingItToInventory() {
        val game = CastleWarsGame()
        val sara = player()
        start(game, sara, player())
        sara.move(Team.ZAMORAK.home)
        assertTrue(game.pickup(sara, Team.ZAMORAK))
        sara.move(Position(2399, 3100))
        assertFalse(sara.controllers.checkEvent(WidgetItemFirstClickEvent(sara, Equipment.WEAPON, 1688, 4039)))
        assertEquals(sara.position, game.match.flag(Team.ZAMORAK).position)
        assertFalse(sara.inventory.contains(4039))
        assertFalse(sara.equipment.contains(4039))
    }

    @Test fun combatExcludesTeammatesWaitingPlayersAndSpawnRooms() {
        val game = CastleWarsGame()
        val sara = player()
        val zam = player()
        start(game, sara, zam)
        assertFalse(game.canFight(sara, zam))
        sara.move(Position(2399, 3100))
        zam.move(Position(2399, 3101))
        assertTrue(sara.controllers.checkCombat(zam))
        assertFalse(sara.controllers.checkCombat(sara))
        val waiting = player()
        assertTrue(game.enter(waiting))
        assertFalse(game.canFight(sara, waiting))
    }

    @Test fun rewardsSurviveFullInventoryAndCannotBeClaimedTwice() {
        val game = CastleWarsGame()
        val sara = player()
        start(game, sara, player())
        repeat(28) { sara.inventory.add(Item(1275)) }
        repeat(2000) { game.tick() }
        assertEquals(CastleWarsGame.LOBBY, sara.position)
        assertEquals(2, sara.castleWarsTickets)
        assertFalse(sara.equipment.contains(4041))
        sara.inventory.remove(1275)
        // Fixture items are all non-stackable; exercise partial delivery as well.
        game.claimTickets(sara)
        assertEquals(1, sara.castleWarsTickets)
        sara.inventory.remove(1275)
        game.claimTickets(sara)
        game.claimTickets(sara)
        assertEquals(0, sara.castleWarsTickets)
        assertEquals(2, sara.inventory.computeAmountForId(4067))
    }

    @Test fun logoutDropsFlagAndStripsOnlyMinigameEquipment() {
        val game = CastleWarsGame()
        val sara = player()
        start(game, sara, player())
        sara.move(Team.ZAMORAK.home)
        assertTrue(game.pickup(sara, Team.ZAMORAK))
        sara.inventory.add(Item(4049))
        sara.inventory.add(Item(1275))
        assertTrue(sara.controllers.checkLogout())
        assertNull(game.match.team(sara))
        assertNull(game.match.flag(Team.ZAMORAK).carrier)
        assertTrue(sara.inventory.contains(1275))
        assertFalse(sara.inventory.contains(4049))
        assertFalse(sara.equipment.containsAny(4039, 4041))
        assertEquals(CastleWarsGame.LOBBY, sara.position)
    }

    @Test fun matchEndDuringDeathKeepsSafeControllerUntilRespawn() {
        val game = CastleWarsGame()
        val sara = player()
        start(game, sara, player())
        `when`(sara.isAlive).thenReturn(false)
        repeat(2000) { game.tick() }
        assertTrue(sara.controllers.primary is CastleWarsController)
        `when`(sara.isAlive).thenReturn(true)
        game.respawn(sara)
        assertFalse(sara.controllers.primary is CastleWarsController)
        assertEquals(CastleWarsGame.LOBBY, sara.position)
    }

    @Test fun registeredDeathHookRetainsNormalItemsAndRespawnsAtOwnBase() {
        val game = CastleWarsGame()
        val sara = player()
        val zam = player()
        start(game, sara, zam)
        sara.inventory.add(Item(1275))
        sara.equipment.set(Equipment.SHIELD, Item(1171))
        sara.move(Team.ZAMORAK.home)
        assertTrue(game.pickup(sara, Team.ZAMORAK))
        CastleWarsGame.registerDeathHook()
        for (stage in listOf(DeathStage.PRE_DEATH, DeathStage.DEATH, DeathStage.POST_DEATH)) {
            DeathHookHandler.onDeath(sara, zam, stage)
        }
        assertTrue(sara.inventory.containsAll(1275, 1171))
        assertFalse(sara.equipment.contains(4039))
        assertEquals(Team.SARADOMIN.spawn, sara.position)
        assertTrue(game.match.isPlaying(sara))
    }

    @Test fun portalsRequeueOwnTeamAndExitFromOpposingBase() {
        val game = CastleWarsGame()
        val sara = player()
        start(game, sara, player())
        game.quit(sara, Team.SARADOMIN)
        assertTrue(game.match.isWaiting(sara))
        assertFalse(game.match.isPlaying(sara))
        assertEquals(Team.SARADOMIN.waiting, sara.position)
        assertTrue(game.joinInProgress(sara))
        game.quit(sara, Team.ZAMORAK)
        assertNull(game.match.team(sara))
        assertEquals(CastleWarsGame.LOBBY, sara.position)
    }

    @Test fun restartRecoveryRemovesOnlyCastleWarsSupplies() {
        val game = CastleWarsGame()
        val player = player()
        player.move(Team.SARADOMIN.spawn)
        player.inventory.add(Item(4049))
        player.inventory.add(Item(1275))
        player.equipment.set(Equipment.WEAPON, Item(4039))
        game.login(player)
        assertEquals(CastleWarsGame.LOBBY, player.position)
        assertTrue(player.inventory.contains(1275))
        assertFalse(player.inventory.contains(4049))
        assertFalse(player.equipment.contains(4039))
    }

    @Test fun waitingAndPlayingControllersBlockTeleportsAndTrades() {
        val game = CastleWarsGame()
        val sara = player()
        val other = player()
        assertTrue(game.enter(sara))
        val teleport = mock(TeleportAction::class.java)
        assertFalse(sara.controllers.primary.teleport(teleport))
        assertFalse(sara.controllers.checkEvent(io.luna.game.event.impl.PlayerClickEvent.PlayerFourthClickEvent(sara, other)))
        assertTrue(game.enter(other))
        repeat(500) { game.tick() }
        assertFalse(sara.controllers.primary.teleport(teleport))
    }

    @Test fun bandagesOnlyHealNearbyTeammatesAndConsumeOne() {
        val game = CastleWarsGame()
        val sara = player()
        val zam = player()
        start(game, sara, zam)
        sara.inventory.add(Item(4049))
        sara.move(Position(2400, 3100))
        zam.move(Position(2400, 3101))
        assertFalse(game.bandage(sara, zam))
        assertTrue(sara.inventory.contains(4049))
        assertTrue(game.bandage(sara))
        verify(sara.hitpoints).adjustLevel(6)
        assertFalse(game.bandage(sara))
    }

    @Test fun rewardExchangeRequiresOpenShopPaymentAndSpace() {
        val player = player()
        repeat(4) { player.inventory.add(Item(4067)) }
        assertFalse(CastleWarsRewards.exchange(player, 4071, true))
        player.overlays.open(CastleWarsRewardInterface())
        repeat(24) { player.inventory.add(Item(1275)) }
        assertTrue(CastleWarsRewards.exchange(player, 4071, true))
        assertTrue(player.inventory.contains(4071))
        assertFalse(player.inventory.contains(4067))
        assertFalse(CastleWarsRewards.exchange(player, 4071, true))
        player.inventory.clear()
        player.inventory.add(Item(4071))
        assertTrue(CastleWarsRewards.exchange(player, 4071, false))
        assertEquals(4, player.inventory.computeAmountForId(4067))
        assertFalse(CastleWarsRewards.exchange(player, 4071, false))
        player.move(Position(3200, 3200))
        assertFalse(CastleWarsRewards.exchange(player, 4071, true))
    }

    @Test fun explosionsClearTunnelInTwoStagesAndResetRestoresOriginal() {
        val game = CastleWarsGame()
        val siege = CastleWarsSiege(game)
        val sara = player()
        start(game, sara, player())
        val position = CastleWarsSiege.tunnels.first()
        sara.move(position.translate(-1, 0))
        repeat(2) { sara.inventory.add(Item(4045)) }
        val ctx = mock(LunaContext::class.java, RETURNS_DEEP_STUBS)
        val rock = io.luna.game.model.`object`.GameObject.createDynamic(ctx, 4437, position, ObjectType.DEFAULT, ObjectDirection.WEST, ChunkUpdatableView.globalView())
        clearInvocations(world.objects)
        siege.rocks(sara, rock, true)
        assertEquals(1, sara.inventory.computeAmountForId(4045))
        val captured = org.mockito.ArgumentCaptor.forClass(io.luna.game.model.`object`.GameObject::class.java)
        verify(world.objects).register(captured.capture())
        val partial = captured.value
        assertEquals(4438, partial.id)
        siege.rocks(sara, partial, true)
        assertEquals(1, sara.inventory.computeAmountForId(4045), "Clicks cannot bypass the action cooldown")
        repeat(4) { siege.tick() }
        clearInvocations(world.objects)
        siege.rocks(sara, partial, true)
        verify(world.objects).register(captured.capture())
        assertEquals(4439, captured.value.id)
        assertFalse(sara.inventory.contains(4045))
        clearInvocations(world.objects)
        siege.reset()
        verify(world.objects).register(captured.capture())
        assertEquals(4437, captured.value.id)
        assertEquals(position, captured.value.position)
    }

    @Test fun catapultSabotageAndRepairRequireOppositeTeamsAndSupplies() {
        val game = CastleWarsGame()
        val siege = CastleWarsSiege(game)
        val sara = player()
        val zam = player()
        start(game, sara, zam)
        val position = Position(2413, 3088)
        sara.move(position.translate(-1, 0))
        zam.move(position.translate(-1, 0))
        val catapult = io.luna.game.model.`object`.GameObject.createDynamic(
            mock(LunaContext::class.java, RETURNS_DEEP_STUBS), 4382, position, ObjectType.DEFAULT, ObjectDirection.WEST, ChunkUpdatableView.globalView())
        sara.inventory.add(Item(4045))
        siege.catapult(sara, catapult, 4045)
        assertTrue(sara.inventory.contains(4045))
        zam.inventory.add(Item(4045))
        clearInvocations(world.objects)
        siege.catapult(zam, catapult, 4045)
        val captured = org.mockito.ArgumentCaptor.forClass(io.luna.game.model.`object`.GameObject::class.java)
        verify(world.objects).register(captured.capture())
        val broken = captured.value
        assertEquals(4386, broken.id)
        assertFalse(zam.inventory.contains(4045))
        clearInvocations(world.objects)
        siege.catapult(sara, broken)
        verifyNoInteractions(world.objects)
        sara.inventory.add(Item(4051))
        siege.catapult(sara, broken)
        verify(world.objects).register(captured.capture())
        assertEquals(4382, captured.value.id)
        assertTrue(sara.inventory.contains(4051))
    }

    @Test fun botInterruptionDropsFlagAndPreservesOwnedEquipment() {
        val game = CastleWars.game
        val bot = player() as io.luna.game.model.mob.bot.Bot
        val opponent = player()
        try {
            start(game, bot, opponent)
            bot.move(Team.ZAMORAK.home)
            bot.equipment.set(Equipment.WEAPON, Item(1275))
            assertTrue(game.pickup(bot, Team.ZAMORAK))
            CastleWarsBotScript(bot).paused()
            assertNull(game.match.team(bot))
            assertNull(game.match.flag(Team.ZAMORAK).carrier)
            assertFalse(bot.equipment.contains(4039))
            assertTrue(bot.inventory.contains(1275))
            assertEquals(CastleWarsGame.LOBBY, bot.position)
            verify(bot.navigator).cancel()
            verify(bot.walking).clear()
        } finally {
            game.exit(bot)
            game.exit(opponent)
            repeat(2000) { game.tick() }
        }
    }

    @Test fun routesReachBothStandardsAndReturnThroughTheCurrentTower() {
        assertNull(game.bot.scripts.CastleWarsBotScript.nextStair(Position(2370, 3130), Position(2425, 3075)))
        for (team in Team.entries) {
            var position = team.spawn
            repeat(8) {
                val step = game.bot.scripts.CastleWarsBotScript.nextStair(position, team.enemy.home) ?: return@repeat
                position = CastleWarsRoutes.stairs.getValue(step)
            }
            assertEquals(3, position.z)
            assertTrue(position.isWithinDistance(team.enemy.home, 8))
            val down = game.bot.scripts.CastleWarsBotScript.nextStair(position, team.home)!!
            assertEquals(2, CastleWarsRoutes.stairs.getValue(down).z)
        }
    }

    @Test fun restoredAndExpiredAttemptsCannotEnterAnotherMatch() = kotlinx.coroutines.runBlocking {
        val bot = InventoryProductionFixtures.bot()
        val script = game.bot.scripts.CastleWarsBotScript(bot, true, true, System.currentTimeMillis() + 60_000)
        val json = com.google.gson.JsonObject().also { script.snapshot().save(it) }
        val restored = game.bot.scripts.CastleWarsBotScript.MatchData().also { it.load(json) }
        assertTrue(restored.entered)
        assertTrue(restored.started)
        assertEquals(json.get("deadline").asLong, restored.deadline)
        assertTrue(game.bot.scripts.CastleWarsBotScript(bot, restored).init(true))
        assertTrue(game.bot.scripts.CastleWarsBotScript(bot, deadline = 1).init(false))
        verifyNoInteractions(bot.output)
        verify(bot, never()).move(any())
    }

    @Test fun selectionRequiresOwnedGearUncappedStyleAndSafeState() {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.asPlr()).thenReturn(bot)
        bot.wildernessLevel = 0
        `when`(bot.controllers).thenReturn(ControllerManager(bot))
        val equipment = Equipment(bot)
        `when`(bot.equipment).thenReturn(equipment)
        val style = mock(io.luna.game.model.def.CombatStyleDefinition::class.java)
        `when`(style.exp).thenReturn(ImmutableList.of(SKILL_ATTACK))
        `when`(bot.combat.styleDef).thenReturn(style)
        assertFalse(game.bot.scripts.CastleWarsBotScript.eligible(bot))
        bot.equipment.set(Equipment.WEAPON, Item(1275))
        assertTrue(game.bot.scripts.CastleWarsBotScript.eligible(bot))
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(game.bot.scripts.CastleWarsBotScript.eligible(bot))
        `when`(bot.combat.inCombat()).thenReturn(false)
        bot.wildernessLevel = 1
        assertFalse(game.bot.scripts.CastleWarsBotScript.eligible(bot))
    }

    private fun match(): CastleWarsMatch<String> = CastleWarsMatch<String>().also {
        assertTrue(it.join("sara", Team.SARADOMIN))
        assertTrue(it.join("zammy", Team.ZAMORAK))
        repeat(499) { _ -> assertTrue(it.tick().started.isEmpty()) }
        assertEquals(2, it.tick().started.size)
    }

    @Test fun teamsRequireOppositionAndBalancedQueue() {
        val match = CastleWarsMatch<String>()
        assertTrue(match.join("one", Team.SARADOMIN))
        assertFalse(match.join("two", Team.SARADOMIN))
        repeat(600) { match.tick() }
        assertFalse(match.active)
        assertTrue(match.join("two", Team.ZAMORAK))
        assertFalse(match.join("two", Team.ZAMORAK))
        repeat(499) { match.tick() }
        match.leave("two", Team.ZAMORAK.waiting)
        assertTrue(match.tick().started.isEmpty())
        assertFalse(match.active)
    }

    @Test fun captureStillWorksWhileOwnStandardIsAwayAndCannotBeRepeated() {
        val match = match()
        assertTrue(match.pickup("sara", Team.ZAMORAK, false))
        assertFalse(match.pickup("sara", Team.ZAMORAK, false))
        assertTrue(match.pickup("zammy", Team.SARADOMIN, false))
        assertTrue(match.capture("sara"))
        assertFalse(match.capture("sara"))
        assertEquals(1, match.score(Team.SARADOMIN))
        assertEquals(Team.ZAMORAK.home, match.flag(Team.ZAMORAK).position)
    }

    @Test fun droppedOwnStandardCanBeCarriedHomeWithoutScoring() {
        val match = match()
        val middle = Position(2400, 3100)
        assertTrue(match.pickup("zammy", Team.SARADOMIN, false))
        match.leave("zammy", middle)
        assertEquals(middle, match.flag(Team.SARADOMIN).position)
        assertTrue(match.pickup("sara", Team.SARADOMIN, false))
        assertTrue(match.capture("sara"))
        assertEquals(0, match.score(Team.SARADOMIN))
        assertEquals(Team.SARADOMIN.home, match.flag(Team.SARADOMIN).position)
    }

    @Test fun matchAwardsWinnerThreeLoserZeroOnlyOnce() {
        val match = match()
        match.pickup("sara", Team.ZAMORAK, false)
        match.capture("sara")
        repeat(1999) { assertFalse(match.tick().ended) }
        assertEquals(mapOf("sara" to 3, "zammy" to 0), match.tick().rewards)
        assertFalse(match.active)
        assertTrue(match.tick().rewards.isEmpty())
    }

    @Test fun drawAwardsTwoAndDepartedPlayersGetNoTickets() {
        val match = match()
        match.leave("zammy", Team.ZAMORAK.spawn)
        repeat(1999) { match.tick() }
        assertEquals(mapOf("sara" to 2), match.tick().rewards)
    }

    @Test fun lateJoinFillsOnlySmallerTeam() {
        val match = match()
        match.join("late", Team.SARADOMIN)
        assertFalse(match.joinInProgress("late"))
        match.leave("sara", Team.SARADOMIN.spawn)
        assertTrue(match.joinInProgress("late"))
        assertTrue(match.pickup("late", Team.ZAMORAK, false))
        assertFalse(match.canTakeFrom("zammy", "late"))
        assertFalse(match.takeFrom("absent", "late"))
    }

    @Test fun teammateTakeFromRequiresThreeMinutesAndTransfersExactlyOnce() {
        val match = CastleWarsMatch<String>()
        match.join("sara1", Team.SARADOMIN)
        match.join("zammy1", Team.ZAMORAK)
        match.join("sara2", Team.SARADOMIN)
        match.join("zammy2", Team.ZAMORAK)
        repeat(500) { match.tick() }
        assertTrue(match.pickup("sara1", Team.ZAMORAK, false))
        repeat(300) { match.tick() }
        assertFalse(match.takeFrom("sara2", "sara1"))
        match.tick()
        assertTrue(match.takeFrom("sara2", "sara1"))
        assertNull(match.carried("sara1"))
        assertEquals(Team.ZAMORAK, match.carried("sara2"))
        assertFalse(match.takeFrom("sara1", "sara2"))
        assertFalse(match.takeFrom("zammy1", "sara2"))
    }

    @Test fun stairsRemainInsideArenaAndOwnFlagCannotBeTakenDownstairs() {
        for ((key, destination) in CastleWarsRoutes.stairs) {
            val (id, origin) = key
            assertTrue(CastleWarsGame.inArena(destination))
            assertEquals(destination, CastleWarsRoutes.destination(id, origin, origin, Team.SARADOMIN, null))
            assertNull(CastleWarsRoutes.destination(id, origin, Position(3200, 3200), Team.SARADOMIN, null))
            if (id == 4415) assertNull(CastleWarsRoutes.destination(id, origin, origin, Team.SARADOMIN, Team.SARADOMIN))
        }
    }

    @Test fun spawnLadderRejectsEnemyAndCarriedFlags() {
        val position = Position(2427, 3076)
        assertNull(CastleWarsRoutes.destination(6280, position, position, Team.ZAMORAK, null))
        assertNull(CastleWarsRoutes.destination(6280, position, position, Team.SARADOMIN, Team.ZAMORAK))
        assertEquals(Team.SARADOMIN.spawn,
            CastleWarsRoutes.destination(6280, position, position, Team.SARADOMIN, null))
    }

    @Test fun castleCacheContainsStandardsSuppliesAndSourceStaircases() {
        assumeTrue(Files.exists(Path.of("data/game/cache/main_file_cache.dat")), "Local revision 377 cache required")
        Cache().use { cache ->
            cache.open()
            val decoder = MapDecoder()
            val indices = ImmutableList.builder<MapIndex>()
            decoder.decode(cache, indices)
            val regions = setOf(Position(2399, 3100).region, Position(2399, 9500).region,
                CastleWarsGame.LOBBY.region)
            decoder.handle(mock(LunaContext::class.java), cache,
                ImmutableList.copyOf(indices.build().filter { it.region in regions }))
            val objects = cache.mapIndexTable.objectSet.objects
            for (team in Team.entries) {
                assertTrue(objects.any { it.position == team.home && it.objectId in 4902..4907 },
                    "Missing ${team.name} standard")
            }
            for (id in CastleWarsGame.TABLES.keys + listOf(4387, 4388, 4408, 4469, 4470)) {
                assertTrue(objects.any { it.objectId == id }, "Missing Castle Wars object $id")
            }
            // One source route accepts a compatibility id at the same position as 4417.
            for ((key, _) in CastleWarsRoutes.stairs) {
                val (id, position) = key
                if (id == 4415 && position == Position(2419, 3078)) continue
                assertTrue(objects.any { it.objectId == id && it.position == position }, "Missing staircase $id at $position")
            }
        }
    }
}
