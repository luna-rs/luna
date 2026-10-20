package engine.bot.coordinator

import api.bot.script.InventoryProductionFixtures
import api.predef.world
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.bot.scripts.DropPartyBotScript
import game.content.partyRoom.PartyRoom
import game.content.partyRoom.dropParty.DropPartyOption
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*

/**
 * Verifies universal invitations and retryable safety gates for native player-funded parties.
 *
 * @author lare96
 */
class PartyAttendanceTest {
    @BeforeEach fun setup() {
        InventoryProductionFixtures.initialize()
        PartyRoom.option = null
        DropPartyOption.chest.items.clear()
        `when`(world.players.iterator()).thenAnswer { emptyList<Player>().iterator() }
    }
    @AfterEach fun cleanup() {
        PartyRoom.option = null
        `when`(world.players.iterator()).thenAnswer { emptyList<Player>().iterator() }
    }
    private fun bot() = InventoryProductionFixtures.bot().also {
        `when`(it.asPlr()).thenReturn(it)
        `when`(it.dialogues).thenReturn(null)
        `when`(it.reflex.activeReflex).thenReturn(null)
        `when`(it.scriptStack.current()).thenReturn(null)
    }
    private fun start() {
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        mockStatic(Luna::class.java).use { luna ->
            luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
            PartyRoom.option = DropPartyOption
            DropPartyOption.chest.items.add(Item(526))
            DropPartyOption.execute(mock(Player::class.java))
        }
    }
    @Test fun everyPersonalityReceivesExactlyOneInvitationForAPlayerFundedParty() {
        val bots = listOf(bot(), bot(), bot())
        bots.forEachIndexed { i, bot -> `when`(bot.personality.intelligence).thenReturn(i * 0.5) }
        `when`(world.players.iterator()).thenAnswer { bots.map { it as Player }.iterator() }
        start()
        repeat(2) { MinigameCoordinator.onPartyStarted() }
        bots.forEach { verify(it.scriptStack, times(1)).pushHead(any(DropPartyBotScript::class.java)) }
    }
    @Test fun unsafeBotIsReconsideredAfterCombatEnds() {
        val bot = bot()
        `when`(world.players.iterator()).thenAnswer { listOf<Player>(bot).iterator() }
        `when`(bot.combat.inCombat()).thenReturn(true)
        start()
        MinigameCoordinator.onPartyStarted()
        verify(bot.scriptStack, never()).pushHead(any())
        `when`(bot.combat.inCombat()).thenReturn(false)
        MinigameCoordinator.onPartyStarted()
        verify(bot.scriptStack).pushHead(any(DropPartyBotScript::class.java))
    }
    @Test fun lockDeathWildernessAndOpenWindowsProtectExistingActivities() {
        val bots = listOf(
            bot().also { `when`(it.isLocked).thenReturn(true) },
            bot().also { `when`(it.health).thenReturn(0) },
            bot().also { it.wildernessLevel = 1 },
            bot().also { `when`(it.overlays.hasWindow()).thenReturn(true) }
        )
        bots.forEach { assertFalse(MinigameCoordinator.safeToAttend(it)) }
    }
}
