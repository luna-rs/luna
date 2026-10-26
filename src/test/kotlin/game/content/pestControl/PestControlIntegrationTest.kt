package game.content.pestControl

import api.bot.script.InventoryProductionFixtures
import api.combat.death.DeathHookHandler
import api.combat.npc.NpcCombatHandler
import api.combat.player.PlayerCombatHandler
import api.predef.*
import com.google.gson.JsonParser
import game.content.pestControl.PestControl.commendations
import game.content.pestControl.PestControl.unclaimedPestCoins
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.def.NpcCombatDefinition
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.def.VarpDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.MobDeathAction.DeathStage
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.controller.ControllerManager
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.util.parser.impl.NpcCombatDefinitionFileParser
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import java.util.OptionalInt
import java.nio.file.Files
import java.nio.file.Path

/**
 * Runs native registrations, real NPC skills and inventory against a mock world without a network server.
 *
 * @author lare96
 */
class PestControlIntegrationTest {
    companion object {
        private var coinsWereStackable = false
        @JvmStatic @BeforeAll fun initialize() {
            InventoryProductionFixtures.initializeCombat()
            val parser = NpcCombatDefinitionFileParser()
            Files.newBufferedReader(Path.of("data/game/def/npcs/npc_combat.jsonc")).use { reader ->
                JsonParser.parseReader(reader).asJsonArray.forEach {
                    val id = it.asJsonObject.get("id").asInt
                    if ((id in 3727..3782 || id == 1) && NpcCombatDefinition.ALL.get(id).isEmpty) {
                        NpcCombatDefinition.ALL.storeDefinition(parser.convert(it.asJsonObject))
                    }
                }
            }
            for (id in 3727..3782) {
                if (NpcDefinition.ALL.get(id).isPresent) continue
                val definition = mock(NpcDefinition::class.java)
                `when`(definition.id()).thenReturn(id)
                `when`(definition.size).thenReturn(1)
                NpcDefinition.ALL.storeDefinition(definition)
            }
            val widget = mock(WidgetDefinition::class.java)
            `when`(widget.id()).thenReturn(18691)
            WidgetDefinition.ALL.storeDefinition(widget)
            if (VarpDefinition.ALL.get(261).isEmpty) VarpDefinition.ALL.storeDefinition(VarpDefinition(261, 0))
            val coins = ItemDefinition.ALL.retrieve(995)
            coinsWereStackable = coins.isStackable
            ItemDefinition::class.java.getDeclaredField("stackable").apply {
                isAccessible = true
                setBoolean(coins, true)
            }
            Class.forName("game.content.pestControl.PestControlHandlers")
                .getConstructor(Array<String>::class.java).newInstance(emptyArray<String>() as Any)
            PestControl.launch()
        }

        @JvmStatic @AfterAll fun restoreCoinFixture() {
            ItemDefinition::class.java.getDeclaredField("stackable").apply {
                isAccessible = true
                setBoolean(ItemDefinition.ALL.retrieve(995), coinsWereStackable)
            }
        }
    }

    private val npcs = mutableListOf<PestControlNpc>()
    private val objects = linkedSetOf<GameObject>()
    private val volunteers = mutableListOf<Player>()

    @BeforeEach fun worldFixtures() {
        val npcList = world.npcs
        val locator = world.locator
        val objectList = world.objects
        doAnswer {
            val npc = it.getArgument<Npc>(0)
            npc.state = EntityState.ACTIVE
            if (npc is PestControlNpc) npcs += npc
            true
        }.`when`(npcList).add(any(Npc::class.java))
        doAnswer {
            val npc = it.getArgument<Npc>(0)
            npc.state = EntityState.INACTIVE
            npcs.remove(npc)
            true
        }.`when`(npcList).remove(any(Npc::class.java))
        doAnswer { objects.toSet() }.`when`(locator).findObjects(eq(PestControl.KNIGHT), eq(40), any())
        doAnswer { objects.add(it.getArgument(0)) }.`when`(objectList).register(any(GameObject::class.java))
        doAnswer { objects.remove(it.getArgument<GameObject>(0)) }.`when`(objectList).unregister(any(GameObject::class.java))
    }

    @AfterEach fun cleanup() {
        volunteers.forEach { `when`(it.isAlive).thenReturn(true); PestControl.leave(it) }
        PestControl.tick()
        PestControl.defences.reset()
    }

    private fun player(): Player = InventoryProductionFixtures.bot().also { player ->
        var position = PestControl.DOCK
        `when`(player.position).thenAnswer { position }
        doAnswer { position = it.getArgument(0); null }.`when`(player).move(any(Position::class.java))
        `when`(player.controllers).thenReturn(ControllerManager(player))
        `when`(player.overlays).thenReturn(AbstractOverlaySet(player))
        val equipment = Equipment(player)
        `when`(player.equipment).thenReturn(equipment)
        `when`(player.combatLevel).thenReturn(80)
        `when`(player.isAlive).thenReturn(true)
        volunteers += player
    }

    private fun start(): List<Player> = List(5) { player().also(PestControl::board) }.also {
        repeat(500) { PestControl.tick() }
        assertTrue(PestControl.round.active)
        assertEquals(6, npcs.size)
        assertTrue(npcs.all { npc -> PestControl.owns(npc) })
    }

    private fun win() {
        npcs.filter { it.id in 3777..3780 }.forEach { it.health = 0 }
        PestControl.tick()
        assertFalse(PestControl.round.active)
        assertTrue(npcs.isEmpty())
    }

    @Test fun distantPestsNavigateBeforeStartingCombatAndSpinnersHealTheirPortal() {
        start()
        val knight = npcs.single { it.id == 3782 }
        val distant = PestControlNpc(3772, Position(2628, 2594)).also { it.state = EntityState.ACTIVE }
        val think = PestControl::class.java.getDeclaredMethod("think", PestControlNpc::class.java)
            .apply { isAccessible = true }
        think.invoke(PestControl, distant)
        assertNull(distant.combat.target)
        assertSame(knight, distant.navigator.currentTarget)
        distant.move(Position(2651, 2592))
        think.invoke(PestControl, distant)
        assertSame(knight, distant.combat.target)
        val portal = npcs.first { it.id == 3777 }
        portal.health = 100
        val spinner = PestControlNpc(3747, portal.position.translate(1, 0), 0).also { it.state = EntityState.ACTIVE }
        think.invoke(PestControl, spinner)
        assertEquals(110, portal.health)
        val torcher = PestControlNpc(3752, knight.position.translate(5, 0))
        val defiler = PestControlNpc(3762, knight.position.translate(5, 0))
        assertTrue(NpcCombatHandler.supplyAttack(torcher, knight) is io.luna.game.model.mob.combat.attack.MagicCombatAttack<*>)
        assertTrue(NpcCombatHandler.supplyAttack(defiler, knight) is io.luna.game.model.mob.combat.attack.RangedCombatAttack<*>)
    }

    @Test fun entryRejectsLowCombatAndLogoutRemovesWaitingMembership() {
        val player = player()
        `when`(player.combatLevel).thenReturn(39)
        PestControl.board(player)
        assertTrue(PestControl.round.waiting().isEmpty())
        `when`(player.combatLevel).thenReturn(40)
        PestControl.board(player)
        assertEquals(PestControl.LANDER, player.position)
        assertTrue(player.controllers.primary is PestControlController)
        assertTrue(player.controllers.checkLogout())
        assertTrue(PestControl.round.waiting().isEmpty())
        assertEquals(PestControl.DOCK, player.position)
        assertFalse(player.controllers.primary is PestControlController)
    }

    @Test fun safeDeathPreservesItemsAndRoundEndDuringDeathExitsAfterRespawn() {
        val player = start().first()
        player.inventory.add(Item(1275))
        player.equipment.set(Equipment.SHIELD, Item(1171))
        for (stage in DeathStage.entries) DeathHookHandler.onDeath(player, null, stage)
        assertEquals(PestControl.START, player.position)
        assertTrue(player.inventory.contains(1275))
        assertTrue(player.equipment.contains(1171))
        assertTrue(PestControl.round.playing(player))
        `when`(player.isAlive).thenReturn(false)
        win()
        assertTrue(player.controllers.primary is PestControlController)
        `when`(player.isAlive).thenReturn(true)
        DeathHookHandler.onDeath(player, null, DeathStage.POST_DEATH)
        assertEquals(PestControl.DOCK, player.position)
        assertFalse(player.controllers.primary is PestControlController)
    }

    @Test fun victoryCapsPointsAndDefersCoinsWithoutDuplicatingThem() {
        val players = start()
        val player = players.first()
        player.commendations = 15
        repeat(28) { player.inventory.add(Item(1275)) }
        PestControl.round.credit(player, 50)
        win()
        assertEquals(15, player.commendations)
        assertEquals(800, player.unclaimedPestCoins)
        assertEquals(0, players[1].commendations)
        player.inventory.remove(1275)
        repeat(2) { PestControl.claimCoins(player) }
        assertEquals(0, player.unclaimedPestCoins)
        assertEquals(800, player.inventory.computeAmountForId(995))
        PestControl.tick()
        assertEquals(800, player.inventory.computeAmountForId(995))
    }

    @Test fun deadKnightCannotBeRevivedBySimultaneousPortalDestruction() {
        val player = start().first()
        PestControl.round.credit(player, 50)
        npcs.single { it.id == 3782 }.health = 0
        win()
        assertEquals(0, player.commendations)
        assertEquals(0, player.inventory.computeAmountForId(995))
    }

    @Test fun actualKillingDamageCountsAndRemovedNpcCannotDamageReturnedPlayer() {
        val player = start().first()
        val portal = npcs.first { it.id == 3777 }
        portal.health = 7
        val constructor = CombatDamage::class.java.getDeclaredConstructor(
            io.luna.game.model.mob.Mob::class.java, io.luna.game.model.mob.Mob::class.java,
            CombatDamageType::class.java, OptionalInt::class.java).apply { isAccessible = true }
        val hit = constructor.newInstance(player, portal, CombatDamageType.MELEE, OptionalInt.of(100))
        hit.apply(mock(CombatAttack::class.java))
        assertEquals(7, PestControl.round.contribution(player))
        hit.apply(mock(CombatAttack::class.java))
        assertEquals(7, PestControl.round.contribution(player))
        win()
        val late = constructor.newInstance(portal, player, CombatDamageType.MAGIC, OptionalInt.of(10))
        val action = CombatDamageAction(late, mock(CombatAttack::class.java), false)
        PlayerCombatHandler.consumeDefence(player, portal, action)
        assertNull(action.damage)
        val outsider = player()
        val staleHit = constructor.newInstance(outsider, portal, CombatDamageType.MELEE, OptionalInt.of(10))
        val staleAction = CombatDamageAction(staleHit, mock(CombatAttack::class.java), false)
        NpcCombatHandler.consumeDefence(portal, outsider, staleAction)
        assertNull(staleAction.damage)
    }

    @Test fun damagedGateRepairsRequireSuppliesAndRestoreBothLeavesAfterGame() {
        val left = GameObject.createStatic(ctx, 14233, Position(2643, 2593), ObjectType.STRAIGHT_WALL, ObjectDirection.WEST)
        val right = GameObject.createStatic(ctx, 14235, Position(2643, 2592), ObjectType.STRAIGHT_WALL, ObjectDirection.WEST)
        objects += listOf(left, right)
        val player = start().first()
        player.move(Position(2644, 2593))
        PestControl.defences.gate(player, left)
        assertEquals(setOf(14234, 14236), objects.map { it.id }.toSet())
        repeat(2) { PestControl.defences.tick() }
        PestControl.defences.gate(player, objects.first())
        PestControl.defences.damage(left.position, 40)
        val damaged = objects.first { it.position == left.position }
        assertEquals(14237, damaged.id)
        repeat(2) { PestControl.defences.tick() }
        PestControl.defences.repair(player, damaged)
        assertEquals(0, PestControl.round.contribution(player))
        player.inventory.add(Item(2347))
        player.inventory.add(Item(1511))
        PestControl.defences.repair(player, damaged)
        assertEquals(5, PestControl.round.contribution(player))
        assertFalse(player.inventory.contains(1511))
        win()
        assertEquals(setOf(14233, 14235), objects.map { it.id }.toSet())
        assertEquals(setOf(left.position, right.position), objects.map { it.position }.toSet())
        assertTrue(objects.all { it.direction == ObjectDirection.WEST })
    }

    @Test fun rewardConfirmationConsumesOnePointAndRejectsStaleClicks() {
        val player = player()
        player.move(PestControl.OUTPOST)
        player.commendations = 2
        `when`(player.skill(Skill.ATTACK).staticLevel).thenReturn(99)
        PestControlTraining.show(player)
        val training = player.overlays.getOverlay(PestControlTraining::class.java)!!
        training.select(player, 0)
        assertTrue(training.confirm(player))
        assertFalse(training.confirm(player))
        assertEquals(1, player.commendations)
        verify(player.skill(Skill.ATTACK)).addExperience(1633.0)
        training.select(player, 0)
        player.move(Position(3222, 3218))
        assertFalse(training.confirm(player))
        assertEquals(1, player.commendations)
    }

    @Test fun unexpectedMovementLeavesSafelyAndRestartLoginEvacuatesIsland() {
        val player = start().first()
        player.move(Position(3222, 3218))
        PestControl.tick()
        assertFalse(PestControl.round.playing(player))
        assertFalse(player.controllers.primary is PestControlController)
        assertEquals(PestControl.DOCK, player.position)
        player.move(PestControl.KNIGHT)
        PestControl.login(player)
        assertEquals(PestControl.DOCK, player.position)
    }
}
