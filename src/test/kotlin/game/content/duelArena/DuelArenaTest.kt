package game.content.duelArena

import api.bot.script.InventoryProductionFixtures
import api.bot.action.BotInteractionActionHandler
import game.bot.scripts.DuelArenaBotScript
import engine.controllers.WildernessLocatableController.wildernessLevel
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.PlayerContextMenuOption
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.def.CombatSpellDefinition
import kotlinx.coroutines.runBlocking
import api.predef.*
import api.predef.ext.*
import com.google.common.collect.ImmutableList
import game.content.duelArena.DuelArena.duelEscrow
import io.luna.game.model.Position
import io.luna.game.model.EntityState
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenu
import io.luna.game.model.mob.controller.ControllerManager
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.mob.combat.attack.MeleeCombatAttack
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*

/**
 * Exercises native duel negotiations, escrow, controller restrictions and delayed combat cleanup.
 *
 * @author lare96
 */
class DuelArenaTest {
    companion object {
        @JvmStatic @BeforeAll fun initialize() {
            InventoryProductionFixtures.initializeCombat()
            if (io.luna.game.model.def.VarpDefinition.ALL[286].isEmpty)
                io.luna.game.model.def.VarpDefinition.ALL.storeDefinition(io.luna.game.model.def.VarpDefinition(286, 0))
            if (io.luna.game.model.def.ItemDefinition.ALL[1266].isEmpty)
                io.luna.game.model.def.ItemDefinition.ALL.storeDefinition(io.luna.game.model.def.ItemDefinition(
                    1266, "Bronze pickaxe", "", 0, 0, 0, 0, 0, 0, 0, true, 0, false,
                    emptyArray(), emptyArray(), java.util.OptionalInt.of(1265), 0, 0, 0,
                    java.util.OptionalInt.empty(), 0.0, true))
            for (id in listOf(6575, 6412, 6573, 6733, 6308)) {
                if (WidgetDefinition.ALL[id].isPresent) continue
                val definition = mock(WidgetDefinition::class.java)
                `when`(definition.id()).thenReturn(id)
                `when`(definition.children).thenReturn(ImmutableList.of())
                WidgetDefinition.ALL.storeDefinition(definition)
            }
        }
    }
    private val players = mutableListOf<Player>()
    @AfterEach fun cleanup() { players.forEach(DuelArena::leave) }

    private fun player(): Player = InventoryProductionFixtures.bot().also { p ->
        `when`(world.collisionManager.traversable(any(Position::class.java), eq(io.luna.game.model.EntityType.PLAYER),
            eq(io.luna.game.model.Direction.EAST))).thenReturn(true)
        var position = DuelArena.LOBBY
        `when`(p.position).thenAnswer { position }
        doAnswer { position = it.getArgument(0); null }.`when`(p).move(any(Position::class.java))
        val equipment = Equipment(p)
        val controllers = ControllerManager(p)
        val menu = PlayerContextMenu(p)
        val overlays = AbstractOverlaySet(p)
        `when`(p.equipment).thenReturn(equipment)
        `when`(p.controllers).thenReturn(controllers)
        `when`(p.contextMenu).thenReturn(menu)
        `when`(p.overlays).thenReturn(overlays)
        `when`(p.isAlive).thenReturn(true)
        `when`(p.username).thenReturn("duellist${players.size}")
        players.add(p)
    }

    private fun negotiate(a: Player, b: Player): DuelArena.Duel {
        assertTrue(DuelArena.challenge(a, b))
        assertNull(DuelArena.session(a))
        assertTrue(DuelArena.challenge(b, a))
        return DuelArena.session(a)!!.also { assertSame(it, DuelArena.session(b)) }
    }

    private fun start(duel: DuelArena.Duel) {
        assertTrue(duel.accept(duel.first)); assertTrue(duel.accept(duel.second))
        assertTrue(duel.confirmation)
        assertFalse(duel.started)
        assertTrue(duel.accept(duel.first)); assertTrue(duel.accept(duel.second))
        assertTrue(duel.started)
        assertFalse(duel.fighting(duel.first, duel.second))
        repeat(4) { duel.tick() }
        assertTrue(duel.fighting(duel.first, duel.second))
    }

    private fun visitor(): Bot = (player() as Bot).also { bot ->
        `when`(bot.asPlr()).thenReturn(bot)
        bot.wildernessLevel = 0
        bot.equipment.set(Equipment.WEAPON, Item(1275))
        val style = mock(io.luna.game.model.def.CombatStyleDefinition::class.java)
        `when`(style.exp).thenReturn(ImmutableList.of(SKILL_ATTACK))
        `when`(bot.combat.styleDef).thenReturn(style)
        `when`(bot.combat.magic.selectedSpell).thenReturn(CombatSpellDefinition.NONE)
    }

    @Test fun botRequiresSafeMeleeGearAndChallengesOnlyUnusedVisitorPeers() {
        val a = visitor(); val b = visitor()
        `when`(a.index).thenReturn(1); `when`(b.index).thenReturn(2)
        assertTrue(DuelArenaBotScript.eligible(a))
        assertNull(DuelArenaBotScript.opponent(a, listOf(b)))
        val visitorScript = DuelArenaBotScript(b)
        `when`(b.scriptStack.current()).thenReturn(visitorScript)
        assertSame(b, DuelArenaBotScript.opponent(a, listOf(b)))
        assertNull(DuelArenaBotScript.opponent(b, listOf(a)))
        val usedScript = DuelArenaBotScript(b, attempted = true)
        `when`(b.scriptStack.current()).thenReturn(usedScript)
        assertNull(DuelArenaBotScript.opponent(a, listOf(b)))
        `when`(a.combat.weapon.isRanged).thenReturn(true)
        assertFalse(DuelArenaBotScript.eligible(a))
        `when`(a.combat.weapon.isRanged).thenReturn(false)
        `when`(a.combat.magic.isAutocasting).thenReturn(true)
        assertFalse(DuelArenaBotScript.eligible(a))
        `when`(a.combat.magic.isAutocasting).thenReturn(false)
        a.wildernessLevel = 1
        assertFalse(DuelArenaBotScript.eligible(a))
        a.wildernessLevel = 0
        `when`(a.isLocked).thenReturn(true)
        assertFalse(DuelArenaBotScript.eligible(a))
    }

    @Test fun botSnapshotsAndInterruptionsCannotRenewAnAttempt() = runBlocking {
        val bot = visitor()
        val future = System.currentTimeMillis() + 60_000
        val script = DuelArenaBotScript(bot, attempted = true, started = true,
            waitingUntil = future, deadline = future + 60_000)
        val json = com.google.gson.JsonObject().also { script.snapshot().save(it) }
        val data = DuelArenaBotScript.DuelData().also { it.load(json) }
        assertTrue(data.attempted && data.started)
        assertEquals(future, data.waitingUntil)
        assertEquals(future + 60_000, data.deadline)
        assertTrue(DuelArenaBotScript(bot, data).init(true))
        assertTrue(DuelArenaBotScript(bot, deadline = 1).init(false))
        assertTrue(DuelArenaBotScript(bot, waitingUntil = 1).init(false))
        val unused = DuelArenaBotScript(bot)
        unused.paused()
        assertTrue(unused.init(true))
        assertTrue(unused.snapshot().attempted)
        verifyNoInteractions(bot.actionHandler)
    }

    @Test fun incomingChallengesAreReadOnlyNearbyAndClearedOnInterruption() {
        val a = visitor(); val b = visitor()
        DuelArena.challenge(a, b)
        assertEquals(listOf(a), DuelArena.challengers(b))
        assertEquals(listOf(a), DuelArena.challengers(b))
        a.move(Position(3200, 3200))
        assertTrue(DuelArena.challengers(b).isEmpty())
        a.move(DuelArena.LOBBY)
        DuelArenaBotScript(b).paused()
        assertTrue(DuelArena.challengers(b).isEmpty())
    }

    @Test fun botAcceptsBothScreensThroughWidgetsAndRechecksChangedOffers() = runBlocking {
        val a = visitor(); val b = visitor()
        val duel = negotiate(a, b)
        val widgets = a.actionHandler.widgets
        doAnswer { duel.accept(a) }.`when`(widgets).clickButton(6575, 6674)
        doAnswer { duel.accept(a) }.`when`(widgets).clickButton(6412, 6520)
        val script = DuelArenaBotScript(a)
        assertFalse(script.run())
        assertFalse(script.run())
        verify(widgets, times(1)).clickButton(6575, 6674)
        duel.toggle(b, DuelRules.NO_FOOD)
        assertFalse(script.run())
        verify(widgets, times(2)).clickButton(6575, 6674)
        duel.accept(b)
        assertTrue(duel.confirmation)
        assertFalse(script.run())
        verify(widgets).clickButton(6412, 6520)
        duel.accept(b)
        assertTrue(duel.started)
        script.paused()
        assertTrue(duel.ended)
        assertNull(DuelArena.session(a))
    }

    @Test fun botDeclinesStakesAndIncompatibleRulesWithoutOfferingItsItems() = runBlocking {
        for (staked in listOf(false, true)) {
            val a = visitor(); val b = visitor()
            b.inventory.add(Item(1265))
            val duel = negotiate(a, b)
            if (staked) duel.stake(b, 0, 1265, 1) else duel.toggle(b, DuelRules.NO_MELEE)
            val widgets = a.actionHandler.widgets
            doAnswer { duel.finish(null); true }.`when`(widgets).clickButton(6575, 6676)
            assertTrue(DuelArenaBotScript(a).run())
            verify(widgets).clickButton(6575, 6676)
            verify(widgets, never()).clickButton(6575, 6674)
            assertEquals(1, b.inventory.computeAmountForId(1265))
            assertTrue(duel.ended)
        }
    }

    @Test fun botWaitsForCountdownThenAttacksOnlyItsOpponentThroughInteractions() = runBlocking {
        val a = visitor(); val b = visitor()
        val duel = negotiate(a, b)
        duel.accept(a); duel.accept(b); duel.accept(a); duel.accept(b)
        val script = DuelArenaBotScript(a)
        assertFalse(script.run())
        verifyNoInteractions(a.actionHandler.interactions)
        repeat(4) { duel.tick() }
        val interactions = a.actionHandler.interactions
        doAnswer {
            assertSame(b, it.getArgument<Player>(1))
            assertTrue(it.getArgument<() -> Boolean>(2).invoke())
            true
        }.`when`(interactions).interact(eq(PlayerContextMenuOption.ATTACK.index), eq(b), any<() -> Boolean>() ?: { false })
        assertFalse(script.run())
        verify(interactions).interact(eq(PlayerContextMenuOption.ATTACK.index), eq(b), any<() -> Boolean>() ?: { false })
        duel.finish(b)
        assertTrue(script.run())
    }

    @Test fun noMovementAllowsAlreadyReachedInteractionsButNeverStartsNavigation() = runBlocking {
        val a = visitor(); val b = visitor()
        `when`(a.walking.isLocked).thenReturn(true)
        `when`(a.walking.isEmpty).thenReturn(true)
        `when`(b.isViewableFrom(a)).thenReturn(true)
        val collision = world.collisionManager
        `when`(collision.reached(a, b, InteractionPolicy.STANDARD_SIZE)).thenReturn(true)
        val handler = BotInteractionActionHandler(a, a.actionHandler)
        assertTrue(handler.approach(b))
        `when`(collision.reached(a, b, InteractionPolicy.STANDARD_SIZE)).thenReturn(false)
        assertFalse(handler.approach(b))
        verifyNoInteractions(a.actionHandler)
    }

    @Test fun interruptionSettlesOnceAndCannotAffectALaterDuel() = runBlocking {
        val a = visitor(); val b = visitor()
        val duel = negotiate(a, b)
        start(duel)
        val script = DuelArenaBotScript(a, attempted = true)
        assertFalse(script.init(true))
        script.paused()
        assertTrue(duel.ended)
        a.overlays.closeWindows(); b.overlays.closeWindows()
        val next = negotiate(a, b)
        script.paused()
        assertFalse(next.ended)
        assertSame(next, DuelArena.session(a))
    }

    @Test fun duelOwnsEatingCombatAndSafeDeathWithoutGenericReflexes() = runBlocking {
        val a = visitor(); val b = visitor()
        val duel = negotiate(a, b)
        assertTrue(duel.toggle(a, DuelRules.NO_FOOD))
        start(duel)
        val script = DuelArenaBotScript(a)
        assertFalse(script.allowsCombatResponse)
        `when`(a.emotions.isNervousAboutHp).thenReturn(true)
        `when`(a.actionHandler.inventory.hasAnyFood()).thenReturn(true)
        assertFalse(game.bot.scripts.EatFoodReflexScript(a).shouldReact())
        `when`(a.combat.target).thenReturn(b)
        assertFalse(script.run())
        verify(a.actionHandler.inventory, never()).eat(anyInt(), anyInt())
        `when`(a.health).thenReturn(0)
        assertFalse(game.bot.scripts.DeathReflexScript(a).shouldReact())
    }

    @Test fun botAnswersIncomingChallengesThroughInteractionHandler() = runBlocking {
        val a = visitor(); val b = visitor()
        DuelArena.challenge(b, a)
        val interactions = a.actionHandler.interactions
        doAnswer {
            assertTrue(it.getArgument<() -> Boolean>(2).invoke())
            DuelArena.challenge(a, b)
        }.`when`(interactions).interact(eq(PlayerContextMenuOption.CHALLENGE.index), eq(b), any<() -> Boolean>() ?: { false })
        val script = DuelArenaBotScript(a)
        assertFalse(script.run())
        assertNotNull(DuelArena.session(a))
        assertTrue(script.snapshot().attempted)
        verify(interactions).interact(eq(PlayerContextMenuOption.CHALLENGE.index), eq(b), any<() -> Boolean>() ?: { false })
        script.paused()
        assertNull(DuelArena.session(a))
    }

    @Test fun botEatsPermittedFoodThroughInventoryHandlerAndStopsAtDeadline() = runBlocking {
        val a = visitor(); val b = visitor()
        val duel = negotiate(a, b)
        start(duel)
        // Fixture metadata need only identify the item; food behavior still comes from the native food table.
        val foodId = game.player.item.consume.food.Food.ID_TO_FOOD.keys.first {
            io.luna.game.model.def.ItemDefinition.ALL[it].isPresent
        }
        a.inventory.add(Item(foodId))
        `when`(a.emotions.isNervousAboutHp).thenReturn(true)
        val inventory = a.actionHandler.inventory
        doAnswer { a.inventory.remove(Item(foodId)); true }.`when`(inventory).eat(0, foodId)
        val script = DuelArenaBotScript(a)
        assertFalse(script.run())
        verify(inventory).eat(0, foodId)
        verifyNoInteractions(a.actionHandler.interactions)
        val expired = DuelArenaBotScript(a, attempted = true, started = true, deadline = 1)
        assertTrue(expired.run())
        expired.finish()
        assertTrue(duel.ended)
        assertTrue(expired.allowsCombatResponse)
    }

    @Test fun challengeRequiresMutualNearbyConsentAndAvailablePlayers() {
        val a = player(); val b = player()
        assertFalse(DuelArena.challenge(a, a))
        b.move(Position(3200, 3200))
        assertFalse(DuelArena.challenge(a, b))
        b.move(DuelArena.LOBBY)
        `when`(b.combat.inCombat()).thenReturn(true)
        assertFalse(DuelArena.challenge(a, b))
        `when`(b.combat.inCombat()).thenReturn(false)
        val duel = negotiate(a, b)
        assertFalse(DuelArena.challenge(a, player()))
        assertSame(duel, a.overlays.getOverlay(DuelArena.DuelInterface::class.java).duel)
    }

    @Test fun offerChangesInvalidateAcceptanceAndClosingRefundsBothSidesOnce() {
        val a = player(); val b = player()
        repeat(3) { a.inventory.add(Item(1265)) }
        b.inventory.add(Item(1351))
        val duel = negotiate(a, b)
        assertTrue(duel.stake(a, 0, 1265, -1))
        assertEquals(3, DuelArena.escrow(a).size)
        assertTrue(duel.accept(a))
        assertTrue(duel.stake(b, 0, 1351, 1))
        assertTrue(duel.accept(b))
        assertFalse(duel.confirmation)
        a.overlays.closeWindows()
        assertTrue(duel.ended)
        assertEquals(3, a.inventory.computeAmountForId(1265))
        assertEquals(1, b.inventory.computeAmountForId(1351))
        duel.finish(b)
        DuelArena.claim(a)
        assertEquals(3, a.inventory.computeAmountForId(1265))
    }

    @Test fun stakeRemovalValidatesItemSlotQuantityAndConfirmationStage() {
        val a = player(); val b = player()
        repeat(6) { a.inventory.add(Item(1265)) }
        val duel = negotiate(a, b)
        assertFalse(duel.stake(a, 0, 1351, 1))
        assertFalse(duel.stake(a, -1, 1265, 1))
        assertFalse(duel.stake(a, 0, 1265, Int.MIN_VALUE))
        assertTrue(duel.stake(a, 0, 1265, 5))
        assertEquals(5, DuelArena.escrow(a).size)
        assertTrue(duel.stake(a, 0, 1265, 3, true))
        assertEquals(2, DuelArena.escrow(a).size)
        assertEquals(4, a.inventory.computeAmountForId(1265))
        duel.accept(a); duel.accept(b)
        assertFalse(duel.stake(a, 0, 1265, 1, true))
        assertFalse(duel.toggle(a, DuelRules.NO_FOOD))
    }

    @Test fun rulesRejectImpossibleCombatAndResetAcceptance() {
        val a = player(); val b = player()
        val duel = negotiate(a, b)
        assertTrue(duel.toggle(a, DuelRules.NO_MELEE))
        assertFalse(duel.toggle(b, DuelRules.NO_FORFEIT))
        assertFalse(duel.toggle(b, DuelRules.NO_MOVEMENT))
        assertTrue(duel.toggle(a, DuelRules.NO_RANGED))
        assertFalse(duel.toggle(b, DuelRules.NO_MAGIC))
        duel.accept(a)
        assertTrue(duel.toggle(b, DuelRules.NO_FOOD))
        duel.accept(b)
        assertFalse(duel.confirmation)
        assertFalse(duel.toggle(a, DuelRules.FLOWERS))
    }

    @Test fun victoryTransfersOnlyEscrowOnceAndRestoresControllers() {
        val a = player(); val b = player()
        a.inventory.add(Item(1265)); b.inventory.add(Item(1351)); b.inventory.add(Item(952))
        val duel = negotiate(a, b)
        duel.stake(a, 0, 1265, 1); duel.stake(b, 0, 1351, 1)
        start(duel)
        assertFalse((a.controllers.primary as DuelController).combat(player()))
        assertTrue(duel.forfeit(b))
        assertTrue(a.inventory.contains(1265)); assertTrue(a.inventory.contains(1351))
        assertTrue(b.inventory.contains(952)); assertFalse(b.inventory.contains(1351))
        assertEquals(PlayerController::class.java, a.controllers.primary.javaClass)
        assertEquals(DuelArena.LOBBY, b.position)
        assertFalse(duel.forfeit(b))
        duel.finish(a)
        assertEquals(1, a.inventory.computeAmountForId(1351))
    }

    @Test fun restartAndFullInventoryRefundToBankWithoutDroppingStakes() {
        val a = player()
        a.duelEscrow = "1265:1;1351:1"
        repeat(28) { a.inventory.add(Item(952)) }
        a.move(Position(3334, 3245))
        DuelArena.login(a)
        assertEquals(DuelArena.LOBBY, a.position)
        assertTrue(a.bank.contains(1265)); assertTrue(a.bank.contains(1351))
        assertEquals("", a.duelEscrow)
        DuelArena.login(a)
        assertEquals(1, a.bank.computeAmountForId(1265))
    }

    @Test fun disconnectAwardsOpponentButNegotiationDisconnectRefunds() {
        val a = player(); val b = player()
        a.inventory.add(Item(1265))
        var duel = negotiate(a, b)
        duel.stake(a, 0, 1265, 1)
        DuelArena.leave(b)
        assertTrue(a.inventory.contains(1265))
        duel = negotiate(a, b)
        duel.stake(a, 0, 1265, 1)
        start(duel)
        assertTrue(a.controllers.primary.logout())
        assertTrue(b.inventory.contains(1265))
    }

    @Test fun controllerBlocksBannedConsumablesPrayerAndSpecialButtons() {
        val a = player(); val b = player()
        val duel = negotiate(a, b)
        for (rule in listOf(DuelRules.NO_FOOD, DuelRules.NO_POTIONS, DuelRules.NO_PRAYER, DuelRules.NO_SPECIAL)) duel.toggle(a, rule)
        start(duel)
        val controller = a.controllers.primary
        assertFalse(controller.event(ItemFirstClickEvent(a, 385, 0, 3214)))
        assertFalse(controller.event(ItemFirstClickEvent(a, 2436, 0, 3214)))
        for (prayer in engine.combat.prayer.CombatPrayer.entries) assertFalse(controller.event(ButtonClickEvent(a, prayer.button)))
        assertFalse(controller.event(ButtonClickEvent(a, 7562)))
        assertTrue(controller.event(ItemFirstClickEvent(a, 952, 0, 3214)))
    }

    @Test fun lateHitsAndUnrelatedAttackersCannotDamageDuelParticipants() {
        val a = player(); val b = player()
        val duel = negotiate(a, b)
        val attack = mock(MeleeCombatAttack::class.java)
        `when`(attack.victim).thenReturn(b)
        assertTrue(DuelArena.stopAttack(a, attack))
        start(duel)
        assertFalse(DuelArena.stopAttack(a, attack))
        assertFalse(DuelArena.blockImpact(b, a, attack))
        assertTrue(DuelArena.blockImpact(b, player(), mock(MeleeCombatAttack::class.java)))
        duel.finish(a)
        assertTrue(DuelArena.blockImpact(b, a, attack))
    }

    @Test fun finishingADuelDiscardsCommittedIncomingHitsForBothParticipants() {
        val a = player(); val b = player()
        val duel = negotiate(a, b)
        start(duel)
        val finished = mutableListOf<Boolean>()
        val queues = listOf(a, b).map { participant ->
            io.luna.game.model.mob.combat.CombatImpactQueue(participant).also { queue ->
                `when`(participant.combat.impacts).thenReturn(queue)
                queue.schedule(0, { fail<Unit>("A finished duel cannot deliver pending damage") }, finished::add)
            }
        }
        duel.finish(a)
        queues.forEach { it.process() }
        assertEquals(listOf(true, true), finished)
    }

    @Test fun timerEndsDuelsAndNoForfeitStillLosesOnLogout() {
        val a = player(); val b = player()
        val duel = negotiate(a, b)
        duel.toggle(a, DuelRules.NO_FORFEIT)
        start(duel)
        assertFalse(duel.forfeit(a))
        repeat(6000) { duel.tick() }
        assertTrue(duel.ended)
        assertNull(DuelArena.session(a))
    }

    @Test fun confirmationRequiresRoomForBothStakesWithoutLosingEitherOffer() {
        val a = player(); val b = player()
        repeat(28) { a.inventory.add(Item(1265)); b.inventory.add(Item(1351)) }
        val duel = negotiate(a, b)
        assertTrue(duel.stake(a, 0, 1265, -1))
        assertTrue(duel.stake(b, 0, 1351, -1))
        duel.accept(a); duel.accept(b); duel.accept(a)
        assertFalse(duel.accept(b))
        assertFalse(duel.started)
        assertEquals(28, DuelArena.escrow(a).size)
        assertEquals(28, DuelArena.escrow(b).size)
        duel.finish(null)
        assertEquals(28, a.inventory.computeAmountForId(1265))
        assertEquals(28, b.inventory.computeAmountForId(1351))
    }

    @Test fun blockedArenasKeepTheConfirmedStakeSafeAndRetryable() {
        val a = player(); val b = player()
        val duel = negotiate(a, b)
        `when`(world.collisionManager.isBlocked(any(Position::class.java), eq(false))).thenReturn(true)
        try {
            duel.accept(a); duel.accept(b); duel.accept(a)
            assertFalse(duel.accept(b))
            assertFalse(duel.started)
            assertEquals(DuelArena.LOBBY, a.position)
        } finally {
            `when`(world.collisionManager.isBlocked(any(Position::class.java), eq(false))).thenReturn(false)
        }
    }

    @Test fun safeDeathKeepsNormalItemsAndPaysOnlyTheAgreedStake() {
        val a = player(); val b = player()
        a.inventory.add(Item(1265)); a.inventory.add(Item(952)); b.inventory.add(Item(1351))
        val duel = negotiate(a, b)
        duel.stake(a, 0, 1265, 1); duel.stake(b, 0, 1351, 1)
        start(duel)
        DuelArena.launch()
        api.combat.death.DeathHookHandler.onDeath(a, b, io.luna.game.model.mob.MobDeathAction.DeathStage.DEATH)
        assertTrue(a.inventory.contains(952))
        api.combat.death.DeathHookHandler.onDeath(a, b, io.luna.game.model.mob.MobDeathAction.DeathStage.POST_DEATH)
        assertTrue(a.inventory.contains(952))
        assertTrue(b.inventory.contains(1265)); assertTrue(b.inventory.contains(1351))
        assertEquals(PlayerController::class.java, a.controllers.primary.javaClass)
    }

    @Test fun nativeStakeAmountPromptCannotApplyAfterTheOfferChanges() {
        val first = scriptListeners.size
        Class.forName("game.content.duelArena.DuelHandlers").getConstructor(Array<String>::class.java)
            .newInstance(emptyArray<String>() as Any)
        @Suppress("UNCHECKED_CAST")
        val listener = scriptListeners.drop(first).single { it.eventType == io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFifthClickEvent::class.java }
            as io.luna.game.event.EventListener<io.luna.game.event.impl.WidgetItemClickEvent>
        assertEquals(5, scriptListeners.drop(first).count {
            io.luna.game.event.impl.WidgetItemClickEvent::class.java.isAssignableFrom(it.eventType)
        })
        scriptListeners.subList(first, scriptListeners.size).clear()
        val a = player(); val b = player()
        a.inventory.add(Item(1265))
        val duel = negotiate(a, b)
        listener.apply(io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFifthClickEvent(a, 0, 6574, 1265))
        val prompt = a.overlays.getOverlay(io.luna.game.model.mob.overlay.NumberInput::class.java)
        assertNotNull(prompt)
        duel.toggle(b, DuelRules.NO_FOOD)
        prompt.input(a, 1)
        assertTrue(a.inventory.contains(1265))
        assertTrue(DuelArena.escrow(a).isEmpty())
    }

    @Test fun nativeCacheContainsDuelWindowsRulesAndArenaExits() {
        io.luna.game.cache.Cache().use { cache ->
            cache.open()
            val widgets = ImmutableList.builder<WidgetDefinition>()
            io.luna.game.cache.codec.WidgetDefinitionDecoder().decode(cache, widgets)
            val definitions = widgets.build().associateBy { it.id() }
            for (id in listOf(6412, 6573, 6575, 6733, 6308)) assertTrue(id in definitions)
            for (rule in DuelRules.entries) assertTrue(definitions.getValue(6575).children.contains(rule.button), rule.name)
            for (id in listOf(6574, 6669, 6670, 6822))
                assertEquals(WidgetDefinition.WidgetType.ITEM_GRID, definitions.getValue(id).type, "Widget $id")
            for (id in listOf(6507, 6508))
                assertEquals(WidgetDefinition.WidgetType.ITEM_LIST, definitions.getValue(id).type, "Widget $id")
            val decoder = io.luna.game.cache.codec.MapDecoder()
            val indices = ImmutableList.builder<io.luna.game.cache.map.MapIndex>()
            decoder.decode(cache, indices)
            decoder.handle(mock(io.luna.LunaContext::class.java), cache,
                ImmutableList.copyOf(indices.build().filter { it.region in
                    setOf(DuelArena.arenas.first().region, DuelArena.LOBBY.region,
                        Position(3395, 3206).region, Position(3395, 3275).region) }))
            val objects = cache.mapIndexTable.objectSet.objects
            for (id in listOf(3111, 3112, 3113, 3114, 3203))
                assertTrue(objects.any { it.objectId == id }, "Missing native arena object $id")
        }
    }

    @Test fun stackOverflowIsRejectedWithoutRemovingOrDuplicatingItems() {
        val a = player(); val b = player()
        assertTrue(a.inventory.add(Item(1266, Int.MAX_VALUE)))
        val duel = negotiate(a, b)
        assertTrue(duel.stake(a, 0, 1266, -1))
        assertTrue(a.inventory.add(Item(1266)))
        assertFalse(duel.stake(a, 0, 1266, 1))
        assertEquals(Int.MAX_VALUE, DuelArena.escrow(a).single().amount)
        assertEquals(1, a.inventory.computeAmountForId(1266))
        assertTrue(duel.stake(a, 0, 1266, 5, true))
        assertEquals(Int.MAX_VALUE - 5, DuelArena.escrow(a).single().amount)
        assertEquals(6, a.inventory.computeAmountForId(1266))
    }

    @Test fun attackStyleRulesAndNoMovementAreEnforcedAfterCountdown() {
        val a = player(); val b = player()
        val duel = negotiate(a, b)
        duel.toggle(a, DuelRules.NO_MAGIC)
        duel.toggle(a, DuelRules.NO_MOVEMENT)
        start(duel)
        val magic = mock(MagicCombatAttack::class.java)
        `when`(magic.victim).thenReturn(b)
        assertTrue(DuelArena.stopAttack(a, magic))
        val movement = io.luna.game.event.impl.WalkingEvent(a, java.util.ArrayDeque<Position>(),
            io.luna.game.event.impl.WalkingEvent.WalkingOrigin.MAIN_SCREEN, false)
        assertFalse(a.controllers.primary.event(movement))
        duel.finish(null)
        verify(a.walking, atLeastOnce()).isLocked = false
    }
}
