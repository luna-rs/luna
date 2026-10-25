package game.content.gamesRoom

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import api.bot.script.InventoryProductionFixtures
import api.predef.*
import io.luna.game.model.EntityState
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenu
import io.luna.game.model.mob.PlayerContextMenuOption
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import org.mockito.Mockito.*
import java.util.function.Predicate
import com.google.common.collect.ImmutableList
import io.luna.LunaContext
import io.luna.game.cache.Cache
import io.luna.game.cache.codec.MapDecoder
import io.luna.game.cache.codec.WidgetDefinitionDecoder
import io.luna.game.cache.map.MapIndex
import java.nio.file.Files
import java.nio.file.Path
import api.bot.action.BotActionHandler
import api.bot.action.BotWidgetActionHandler
import game.bot.scripts.GamesRoomBotScript
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.io.BotClient
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.controller.ControllerManager
import io.luna.net.msg.GameMessage
import io.luna.net.msg.`in`.ArrangeItemMessageReader
import org.mockito.ArgumentCaptor
import kotlinx.coroutines.runBlocking
import com.google.gson.JsonObject
import engine.controllers.WildernessLocatableController.wildernessLevel

/**
 * Checks adversarial moves and completed games without a running client.
 *
 * @author lare96
 */
class GamesRoomTest {

    companion object {
        @JvmStatic @BeforeAll fun initialize() {
            InventoryProductionFixtures.initializeCombat()
            for (id in listOf(10380, 10617, 10507, 10874)) {
                val definition = mock(WidgetDefinition::class.java)
                `when`(definition.id()).thenReturn(id)
                `when`(definition.children).thenReturn(ImmutableList.copyOf(when (id) {
                    10380 -> listOf(10494)
                    10617 -> (10736..10742).toList()
                    else -> emptyList()
                }))
                WidgetDefinition.ALL.storeDefinition(definition)
            }
            val grid = mock(WidgetDefinition::class.java)
            `when`(grid.id()).thenReturn(10494)
            `when`(grid.type).thenReturn(WidgetDefinition.WidgetType.ITEM_GRID)
            WidgetDefinition.ALL.storeDefinition(grid)
        }
    }

    private val players = mutableListOf<Player>()

    @BeforeEach fun table() {
        val table = mock(GameObject::class.java)
        `when`(table.id).thenReturn(4651)
        `when`(table.position).thenReturn(Position(2200, 4950))
        `when`(table.objectType).thenReturn(ObjectType.DEFAULT)
        `when`(table.direction).thenReturn(ObjectDirection.SOUTH)
        val locator = world.locator
        doAnswer { call ->
            val filter = call.getArgument<Predicate<GameObject>>(2)
            val base = call.getArgument<Locatable>(0).abs()
            if (base.z == table.position.z && filter.test(table)) setOf(table) else emptySet<GameObject>()
        }.`when`(locator).findObjects(any(Locatable::class.java), anyInt(), any())
    }

    @AfterEach fun leave() { players.forEach(GamesRoom::leave) }

    private fun player(): Player = InventoryProductionFixtures.bot().also { player ->
        players += player
        var position = Position(2200, 4948)
        `when`(player.position).thenAnswer { position }
        doAnswer { position = it.getArgument(0); null }.`when`(player).move(any(Position::class.java))
        `when`(player.state).thenReturn(EntityState.ACTIVE)
        `when`(player.isAlive).thenReturn(true)
        `when`(player.overlays).thenReturn(AbstractOverlaySet(player))
        `when`(player.contextMenu).thenReturn(PlayerContextMenu(player))
    }

    private fun start(runeLink: Boolean = false, seconds: Int = 15): Pair<Player, Player> {
        val first = player()
        val second = player()
        assertTrue(GamesRoom.offer(first, second, runeLink, seconds))
        assertTrue(GamesRoom.accept(second, first))
        return first to second
    }

    private fun position(vararg pieces: Pair<Int, Int>): DraughtsBoard = DraughtsBoard().also { board ->
        val field = DraughtsBoard::class.java.getDeclaredField("cells").apply { isAccessible = true }
        val cells = field.get(board) as IntArray
        cells.fill(0)
        pieces.forEach { (slot, piece) -> cells[slot] = piece }
    }

    @Test fun nativeDragPacketRoundTripsThroughThePlayerReader() {
        val bot = player() as Bot
        val client = mock(BotClient::class.java)
        `when`(client.bot).thenReturn(bot)
        val output = BotOutputMessageHandler(client)
        assertFalse(output.sendArrangeItem(10494, -1, 33))
        assertFalse(output.sendArrangeItem(10494, 40, 40))
        assertFalse(output.sendArrangeItem(40000, 40, 33))
        assertFalse(output.sendArrangeItem(10494, 40, 65536))
        assertTrue(output.sendArrangeItem(10494, 40, 33))
        val captured = ArgumentCaptor.forClass(GameMessage::class.java)
        verify(client).queueSimulated(captured.capture())
        val packet = captured.value
        try {
            assertEquals(123, packet.opcode)
            assertEquals(7, packet.size)
            val reader = ArrangeItemMessageReader()
            val event = reader.decode(bot, packet)
            assertTrue(reader.validate(bot, event))
            assertEquals(10494, event.widgetId)
            assertEquals(40, event.fromIndex)
            assertEquals(33, event.toIndex)
            assertEquals(0, event.insertionMode)
            assertEquals(0, packet.payload.buffer.readableBytes())
        } finally { packet.payload.buffer.release() }
    }

    @Test fun widgetActionsRejectClosedAndUnrelatedInterfaces() {
        val bot = player() as Bot
        val handler = BotWidgetActionHandler(bot, mock(BotActionHandler::class.java))
        assertFalse(handler.clickButton(10617, 10736))
        assertFalse(handler.dragItem(10380, 10494, 40, 33))
        bot.overlays.open(StandardInterface(10617))
        assertFalse(handler.clickButton(10617, 2458))
        assertFalse(handler.dragItem(10380, 10494, 40, 33))
        `when`(bot.output.clickButton(10739)).thenReturn(true)
        assertTrue(handler.clickButton(10617, 10739))
        bot.overlays.open(StandardInterface(10380))
        assertFalse(handler.dragItem(10380, 10494, -1, 33))
        assertFalse(handler.dragItem(10380, 10494, 40, 40))
        assertFalse(handler.dragItem(10380, 10380, 40, 33))
        `when`(bot.output.sendArrangeItem(10494, 40, 33)).thenReturn(true)
        assertTrue(handler.dragItem(10380, 10494, 40, 33))
        verify(bot.output).clickButton(10739)
        verify(bot.output).sendArrangeItem(10494, 40, 33)
        verifyNoMoreInteractions(bot.output)
    }

    @Test fun botSnapshotsCannotRenewUsedAttemptsOrDeadlines() = runBlocking {
        val bot = player() as Bot
        val future = System.currentTimeMillis() + 60_000
        val script = GamesRoomBotScript(bot, attempted = true, started = true,
            runeLink = true, waitingUntil = future, deadline = future + 60_000)
        val json = JsonObject().also { script.snapshot().save(it) }
        val data = GamesRoomBotScript.BoardGameData().also { it.load(json) }
        assertTrue(data.attempted && data.started && data.runeLink)
        assertEquals(future, data.waitingUntil)
        assertEquals(future + 60_000, data.deadline)
        assertTrue(GamesRoomBotScript(bot, data).init(true))
        assertTrue(GamesRoomBotScript(bot, deadline = 1).init(false))
        assertTrue(GamesRoomBotScript(bot, waitingUntil = 1).init(false))
        verifyNoInteractions(bot.actionHandler)
    }

    @Test fun botInterruptionsResignAndClearIncomingChallenges() {
        val (first, second) = start(true)
        val bot = first as Bot
        val script = GamesRoomBotScript(bot)
        script.paused()
        assertFalse(GamesRoom.playing(first))
        assertFalse(GamesRoom.playing(second))
        assertFalse(bot.overlays.hasWindow())
        assertTrue(GamesRoom.offer(second, bot, false, 15))
        assertEquals(listOf(second), GamesRoom.challengers(bot))
        script.paused()
        assertTrue(GamesRoom.challengers(bot).isEmpty())
        verify(bot.navigator, times(2)).cancel()
    }

    @Test fun botSelectionHonorsSafetyAndUsesOnlyAvailableVisitorPeers() {
        val bot = player() as Bot
        val other = player() as Bot
        `when`(bot.asPlr()).thenReturn(bot)
        `when`(bot.controllers).thenReturn(ControllerManager(bot))
        bot.wildernessLevel = 0
        assertTrue(GamesRoomBotScript.eligible(bot))
        bot.wildernessLevel = 1
        assertFalse(GamesRoomBotScript.eligible(bot))
        bot.wildernessLevel = 0
        `when`(bot.isLocked).thenReturn(true)
        assertFalse(GamesRoomBotScript.eligible(bot))
        `when`(bot.isLocked).thenReturn(false)
        `when`(bot.index).thenReturn(1)
        `when`(other.index).thenReturn(2)
        assertNull(GamesRoomBotScript.opponent(bot, listOf(other)))
        val visitor = GamesRoomBotScript(other)
        `when`(other.scriptStack.current()).thenReturn(visitor)
        assertSame(other, GamesRoomBotScript.opponent(bot, listOf(other)))
        assertNull(GamesRoomBotScript.opponent(other, listOf(bot)))
        val usedVisit = GamesRoomBotScript(other, attempted = true)
        `when`(other.scriptStack.current()).thenReturn(usedVisit)
        assertNull(GamesRoomBotScript.opponent(bot, listOf(other)))
    }

    @Test fun botChoosesOnlyLegalColumnsAndPrefersDraughtsPromotion() {
        assertNull(GamesRoomBotScript.chooseColumn(emptyList()))
        assertEquals(3, GamesRoomBotScript.chooseColumn((0..6).toList()))
        assertEquals(1, GamesRoomBotScript.chooseColumn(listOf(0, 1, 6)))
        assertNull(GamesRoomBotScript.chooseMove(emptyList()))
        assertEquals(17 to 3, GamesRoomBotScript.chooseMove(listOf(42 to 33, 17 to 3)))
    }

    @Test fun upstairsChallengesAllocateTablesOnTheNativeBoardGameFloor() {
        val first = player()
        val second = player()
        first.move(Position(2208, 4934, 1))
        second.move(Position(2209, 4934, 1))
        assertTrue(GamesRoom.offer(first, second, false, 30))
        assertTrue(GamesRoom.accept(second, first))
        assertEquals(0, first.position.z)
        assertEquals(0, second.position.z)
        assertEquals(0, GamesRoomBotScript.LOBBY.z)
    }

    @Test fun botPlaysBothGamesThroughWidgetActionsAndDoesNotPlayOffTurn() = runBlocking {
        for (runeLink in listOf(false, true)) {
            val (first, second) = start(runeLink)
            val bot = (if (GamesRoom.columns(first).isNotEmpty() || GamesRoom.moves(first).isNotEmpty()) first else second) as Bot
            val actions = bot.actionHandler.widgets
            if (runeLink) {
                doAnswer {
                    GamesRoom.column(bot, it.getArgument<Int>(1) - 10736)
                    true
                }.`when`(actions).clickButton(eq(10617), anyInt())
            } else {
                doAnswer {
                    GamesRoom.move(bot, it.getArgument(2), it.getArgument(3))
                    true
                }.`when`(actions).dragItem(eq(10380), eq(10494), anyInt(), anyInt())
            }
            val script = GamesRoomBotScript(bot)
            assertFalse(script.run())
            assertTrue(GamesRoom.columns(bot).isEmpty())
            assertTrue(GamesRoom.moves(bot).isEmpty())
            assertFalse(script.run())
            if (runeLink) verify(actions).clickButton(eq(10617), anyInt())
            else verify(actions).dragItem(eq(10380), eq(10494), anyInt(), anyInt())
            verifyNoInteractions(bot.output)
            script.paused()
            assertTrue(script.run())
        }
    }

    @Test fun challengesRequireConsentAndExpire() {
        val first = player()
        val second = player()
        val third = player()
        assertFalse(GamesRoom.offer(first, first, false, 15))
        assertFalse(GamesRoom.offer(first, second, false, 10))
        assertTrue(GamesRoom.offer(first, second, false, 15))
        assertFalse(GamesRoom.playing(first))
        assertFalse(GamesRoom.accept(third, first))
        repeat(100) { GamesRoom.tick() }
        assertFalse(GamesRoom.accept(second, first))
    }

    @Test fun unavailablePlayersCannotAcceptOrStartGames() {
        val first = player()
        val second = player()
        assertTrue(GamesRoom.offer(first, second, false, 15))
        second.move(Position(2200, 4950, 2))
        assertFalse(GamesRoom.accept(second, first))
        second.move(Position(2200, 4948))
        `when`(first.state).thenReturn(EntityState.INACTIVE)
        assertFalse(GamesRoom.accept(second, first))
    }

    @Test fun occupiedTablesCannotBeAssignedTwiceAndAreReleasedOnClose() {
        val (first, second) = start()
        val third = player()
        val fourth = player()
        assertTrue(GamesRoom.offer(third, fourth, true, 15))
        assertFalse(GamesRoom.accept(fourth, third))
        first.overlays.closeWindows()
        assertFalse(GamesRoom.playing(first))
        assertFalse(GamesRoom.playing(second))
        assertFalse(second.overlays.hasWindow())
        assertTrue(GamesRoom.accept(fourth, third))
    }

    @Test fun runeLinkTimeoutMovesOnlyAtTheAgreedDeadline() {
        val (first, second) = start(true, 30)
        val active = if (GamesRoom.columns(first).isNotEmpty()) first else second
        val other = if (active === first) second else first
        GamesRoom.column(other, 0)
        GamesRoom.column(active, 7)
        repeat(49) { GamesRoom.tick() }
        assertTrue(GamesRoom.columns(active).isNotEmpty())
        GamesRoom.tick()
        assertTrue(GamesRoom.columns(active).isEmpty())
        assertTrue(GamesRoom.columns(other).isNotEmpty())
    }

    @Test fun draughtsMovesUseEachPlayersBoardOrientation() {
        val (first, second) = start()
        repeat(2) {
            val active = if (GamesRoom.moves(first).isNotEmpty()) first else second
            val other = if (active === first) second else first
            val moves = GamesRoom.moves(active)
            assertTrue(moves.all { it.first / 8 == 5 && it.second / 8 == 4 })
            GamesRoom.move(other, 40, 33)
            assertEquals(moves, GamesRoom.moves(active))
            GamesRoom.move(active, moves[0].first, moves[0].second)
            assertTrue(GamesRoom.moves(active).isEmpty())
        }
    }

    @Test fun drawsRequireBothPlayersAndMovesCancelOffers() {
        val (first, second) = start(true)
        GamesRoom.draw(first)
        GamesRoom.draw(first)
        assertTrue(GamesRoom.playing(first))
        val active = if (GamesRoom.columns(first).isNotEmpty()) first else second
        GamesRoom.column(active, 0)
        GamesRoom.draw(second)
        assertTrue(GamesRoom.playing(first))
        GamesRoom.draw(first)
        assertFalse(GamesRoom.playing(first))
        assertFalse(GamesRoom.playing(second))
    }

    @Test fun departureRejectsLateMovesAndFreesBothPlayers() {
        val (first, second) = start(true)
        first.move(Position(3200, 3200))
        GamesRoom.column(second, 0)
        assertFalse(GamesRoom.playing(first))
        assertFalse(GamesRoom.playing(second))
        GamesRoom.leave(first)
        GamesRoom.draw(second)
    }

    @Test fun logoutAndAreaExitCleanUpChallengesAndSessions() {
        val (first, second) = start()
        GamesRoomAreaListener.enter(first)
        assertTrue(first.contextMenu.contains(PlayerContextMenuOption.CHALLENGE))
        GamesRoomAreaListener.exit(first)
        assertFalse(first.contextMenu.contains(PlayerContextMenuOption.CHALLENGE))
        assertFalse(GamesRoom.playing(second))
        assertTrue(GamesRoom.offer(first, second, false, 15))
        GamesRoom.leave(first)
        assertFalse(GamesRoom.accept(second, first))
    }

    @Test fun spectatorsCannotMoveOrEndTheGameAndCloseWithTheSession() {
        val (first, second) = start(true)
        val spectator = player()
        GamesRoom.view(spectator, Position(2200, 4950))
        assertTrue(spectator.overlays.hasWindow())
        GamesRoom.view(spectator, Position(2200, 4950))
        assertTrue(spectator.overlays.hasWindow())
        GamesRoom.column(spectator, 0)
        GamesRoom.draw(spectator)
        GamesRoom.move(spectator, 40, 33)
        assertFalse(GamesRoom.playing(spectator))
        assertTrue(GamesRoom.playing(first))
        spectator.overlays.closeWindows()
        assertTrue(GamesRoom.playing(second))
        GamesRoom.view(spectator, Position(2200, 4950))
        GamesRoom.leave(first)
        assertFalse(spectator.overlays.hasWindow())
    }

    @Test fun challengeMenuDoesNotTriggerTheDefaultAttackHandler() {
        val first = player()
        val second = player()
        val initialSize = scriptListeners.size
        try {
            Class.forName("engine.combat.AttackMob").getConstructor(Array<String>::class.java)
                .newInstance(emptyArray<String>() as Any)
            @Suppress("UNCHECKED_CAST")
            val listener = scriptListeners.drop(initialSize).single {
                it.eventType == io.luna.game.event.impl.PlayerClickEvent.PlayerFirstClickEvent::class.java
            } as io.luna.game.event.EventListener<io.luna.game.event.impl.PlayerClickEvent.PlayerFirstClickEvent>
            `when`(first.combat.isAttackable).thenReturn(true)
            `when`(second.combat.isAttackable).thenReturn(true)
            first.contextMenu.show(PlayerContextMenuOption.CHALLENGE)
            listener.policy.apply(first, second)
            listener.apply(io.luna.game.event.impl.PlayerClickEvent.PlayerFirstClickEvent(first, second))
            verify(first.combat, never()).getNextAttack(second)
            verify(first.combat, never()).attack(second)
            first.contextMenu.show(PlayerContextMenuOption.ATTACK)
            listener.apply(io.luna.game.event.impl.PlayerClickEvent.PlayerFirstClickEvent(first, second))
            verify(first.combat).attack(second)
        } finally {
            scriptListeners.subList(initialSize, scriptListeners.size).clear()
        }
    }

    @Test fun draughtsChainsOnlyTheCapturingPieceAndPromotesAtTheBackRank() {
        val board = position(42 to 1, 62 to 1, 33 to -1, 17 to -1, 1 to -1)
        assertTrue(board.move(1, 42, 24))
        assertEquals(24, board.forcedPiece)
        assertEquals(1, board.turn)
        assertFalse(board.move(1, 62, 53))
        assertTrue(board.move(1, 24, 10))
        assertEquals(2, board.turn)
        val promotion = position(17 to 1, 10 to -1, 12 to -1)
        assertTrue(promotion.move(1, 17, 3))
        assertEquals(2, promotion.snapshot()[3])
        assertEquals(2, promotion.turn)
    }

    @Test fun draughtsKingsMoveBackwardsAndFortyQuietMovesDraw() {
        val board = position(1 to 2, 62 to -2)
        repeat(10) {
            assertTrue(board.move(1, 1, 8))
            assertTrue(board.move(2, 62, 55))
            assertTrue(board.move(1, 8, 1))
            assertTrue(board.move(2, 55, 62))
        }
        assertTrue(board.finished)
        assertEquals(0, board.winner)
        assertTrue(board.legalMoves().isEmpty())
    }

    @Test fun draughtsPlayerWithoutAnyLegalMoveLoses() {
        val board = position(42 to 1, 56 to -1)
        assertTrue(board.move(1, 42, 33))
        assertTrue(board.finished)
        assertEquals(1, board.winner)
    }

    @Test fun runeLinkFullBoardWithoutFourIsADraw() {
        val board = RuneLinkBoard()
        val columns = listOf(6,0,2,2,2,5,5,6,3,3,0,1,1,5,5,5,6,4,3,6,4,0,0,2,5,1,1,0,2,2,0,3,3,4,4,4,6,1,4,3,1,6)
        columns.forEach { assertTrue(board.move(board.turn, it)) }
        assertTrue(board.finished)
        assertEquals(0, board.winner)
        assertTrue(board.legalColumns().isEmpty())
    }

    @Test fun runeLinkDetectsBothDiagonalDirections() {
        for (mirror in listOf(false, true)) {
            val board = RuneLinkBoard()
            for (column in listOf(0, 1, 1, 2, 4, 2, 2, 3, 4, 3, 5, 3, 3)) {
                assertTrue(board.move(board.turn, if (mirror) 6 - column else column))
            }
            assertEquals(1, board.winner)
        }
    }

    @Test fun nativeCacheContainsBoardsColumnsTablesAndNoticeboard() {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(Path.of("data/game/cache/main_file_cache.dat")))
        Cache().use { cache ->
            cache.open()
            val widgets = ImmutableList.builder<WidgetDefinition>()
            WidgetDefinitionDecoder().decode(cache, widgets)
            val definitions = widgets.build().associateBy { it.id() }
            for ((root, inventory) in listOf(10380 to 10494, 10617 to 10730, 10507 to 10600, 10874 to 10965)) {
                assertTrue(definitions.getValue(root).children.contains(inventory))
                assertEquals(WidgetDefinition.WidgetType.ITEM_GRID, definitions.getValue(inventory).type)
            }
            for (column in 10736..10742) assertTrue(definitions.getValue(10617).children.contains(column))
            val decoder = MapDecoder()
            val indices = ImmutableList.builder<MapIndex>()
            decoder.decode(cache, indices)
            decoder.handle(mock(LunaContext::class.java), cache,
                ImmutableList.copyOf(indices.build().filter { it.region == Position(2200, 4950).region }))
            val objects = cache.mapIndexTable.objectSet.objects
            assertTrue(objects.any { it.objectId == 4651 && GamesRoom.inRoom(it.position) })
            assertTrue(objects.any { it.objectId == 4642 && GamesRoom.inRoom(it.position) })
        }
    }

    @Test
    fun `RuneLink rejects duplicate turns and stops after a vertical win`() {
        val board = RuneLinkBoard()
        assertFalse(board.move(2, 0))
        repeat(3) {
            assertTrue(board.move(1, 0))
            assertFalse(board.move(1, 0))
            assertTrue(board.move(2, 1))
        }
        assertTrue(board.move(1, 0))
        assertEquals(1, board.winner)
        assertTrue(board.finished)
        assertFalse(board.move(2, 1))
    }

    @Test
    fun `RuneLink rejects full and invalid columns without changing turns`() {
        val board = RuneLinkBoard()
        repeat(6) { assertTrue(board.move(board.turn, 3)) }
        val before = board.snapshot()
        assertFalse(board.move(board.turn, 3))
        assertFalse(board.move(board.turn, -1))
        assertFalse(board.move(board.turn, 7))
        assertArrayEquals(before, board.snapshot())
        assertEquals(1, board.turn)
    }

    @Test
    fun `RuneLink detects horizontal wins without wrapping rows`() {
        val board = RuneLinkBoard()
        for (column in 0..2) {
            assertTrue(board.move(1, column))
            assertTrue(board.move(2, 6))
        }
        assertTrue(board.move(1, 3))
        assertEquals(1, board.winner)
    }

    @Test
    fun `Draughts starts with twelve pieces per player and seven legal moves`() {
        val board = DraughtsBoard()
        assertEquals(12, board.snapshot().count { it == 1 })
        assertEquals(12, board.snapshot().count { it == -1 })
        assertEquals(7, board.legalMoves().size)
        assertFalse(board.move(2, 17, 24))
        assertFalse(board.move(1, -1, 100))
        assertFalse(board.move(1, 40, 39))
        assertTrue(board.move(1, 40, 33))
        assertEquals(2, board.turn)
    }

    @Test
    fun `Draughts capture is mandatory and removes exactly one opponent piece`() {
        val board = DraughtsBoard()
        assertTrue(board.move(1, 40, 33))
        assertTrue(board.move(2, 17, 24))
        assertTrue(board.move(1, 42, 35))
        assertTrue(board.move(2, 24, 42))
        assertEquals(11, board.snapshot().count { it == 1 })
        assertTrue(board.legalMoves().all { kotlin.math.abs(it.first / 8 - it.second / 8) == 2 })
        assertFalse(board.move(1, 35, 28))
        assertTrue(board.move(1, 51, 33))
        assertEquals(11, board.snapshot().count { it == -1 })
    }

    @Test
    fun `board snapshots cannot mutate live games`() {
        val link = RuneLinkBoard()
        link.snapshot()[0] = 2
        assertEquals(0, link.snapshot()[0])
        val draughts = DraughtsBoard()
        draughts.snapshot().fill(0)
        assertEquals(24, draughts.snapshot().count { it != 0 })
    }
}
