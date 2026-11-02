package engine.item.developerItem

import api.bot.script.BotScript
import api.bot.script.InventoryProductionFixtures
import engine.bot.coordinator.CombatCoordinator
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.overlay.AbstractOverlay
import io.luna.game.model.mob.overlay.TextInput
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption
import io.luna.game.model.item.Inventory
import io.luna.game.model.item.Item
import io.luna.game.model.item.Bank.DynamicBankInterface
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.util.function.Consumer

/**
 * Follows actual menu callbacks to automation and read-only inspection, including revocation checks.
 *
 * @author lare96
 */
class DeveloperItemIntegrationTest {
    private lateinit var actor: Bot
    private lateinit var dialogue: DialogueQueueBuilder
    private var options: List<DialogueOption> = emptyList()

    @BeforeEach fun setup() {
        InventoryProductionFixtures.initialize()
        actor = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        `when`(actor.state).thenReturn(EntityState.ACTIVE)
        `when`(actor.rights).thenReturn(PlayerRights.DEVELOPER)
        `when`(actor.isConnectedPlayer).thenReturn(true)
        dialogue = mock(DialogueQueueBuilder::class.java, RETURNS_SELF)
        `when`(actor.newDialogue()).thenReturn(dialogue)
        doAnswer { call -> options = call.getArgument(0); dialogue }.`when`(dialogue).options(anyList())
    }

    private fun name(option: DialogueOption): String = DialogueOption::class.java.getDeclaredField("name").let {
        it.isAccessible = true; it.get(option) as String
    }

    @Suppress("UNCHECKED_CAST")
    private fun click(label: String) {
        val option = options.first { name(it).startsWith(label) }
        val field = DialogueOption::class.java.getDeclaredField("action").apply { isAccessible = true }
        (field.get(option) as Consumer<Player>).accept(actor)
    }

    @Test fun combatIsFirstAndDispatchesToExistingTrainingCoordinator() = runBlocking {
        DeveloperItemHandler.automation(actor)
        assertTrue(name(options.first()).startsWith("Combat"))
        click("Combat")
        val capture = ArgumentCaptor.forClass(BotScript::class.java)
        verify(actor).startAutomation(capture.capture())
        assertSame(actor, capture.value.bot)
        mockConstruction(CombatCoordinator::class.java) { _, context ->
            assertEquals(listOf(true), context.arguments())
        }.use { construction ->
            assertTrue(capture.value.run())
            verify(construction.constructed().single()).accept(actor)
        }
    }

    @Test fun everySkillHasAnExistingAutomationPath() {
        assertEquals(Skill.IDS.toSet(), DeveloperAutomation.skillIds.toSet() + DeveloperAutomation.combatSkills)
    }

    @Test fun repeatReopensMenuWithoutReexecutingItsCommand() {
        DeveloperItemHandler.automation(actor)
        click("Combat")
        clearInvocations(actor)
        DeveloperItemHandler.repeat(actor)
        assertTrue(name(options.first()).startsWith("Combat"))
        verify(actor, never()).startAutomation(any())
    }

    @Test fun menuCallbacksRecheckDeveloperRights() {
        DeveloperItemHandler.automation(actor)
        `when`(actor.rights).thenReturn(PlayerRights.PLAYER)
        click("Combat")
        verify(actor, never()).startAutomation(any())
    }

    @Test fun bankStyleInspectionDoesNotExposeMutableTargetInventory() {
        val target = mock(Player::class.java, RETURNS_DEEP_STUBS)
        `when`(target.state).thenReturn(EntityState.ACTIVE)
        val inventory = Inventory(target)
        `when`(target.inventory).thenReturn(inventory)
        assertTrue(inventory.add(Item(995, 100)))
        DeveloperInspection.containers(actor, target)
        click("Inventory")
        val overlay = ArgumentCaptor.forClass(io.luna.game.model.mob.overlay.AbstractOverlay::class.java)
        verify(actor.overlays).open(overlay.capture())
        val displayed = (overlay.value as DynamicBankInterface).buildDisplayItems(actor)
        assertEquals(100, displayed.single().amount)
        assertNotSame(inventory[0], displayed.single())
        displayed.clear()
        assertEquals(100, inventory.computeAmountForId(995))
    }

    @Test fun ordinaryTargetsDoNotGetAnAutonomousKernel() {
        val target = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        `when`(target.state).thenReturn(EntityState.ACTIVE)
        DeveloperItemHandler.player(actor, target)
        click("Next")
        assertFalse(options.any { name(it) == "Kernel" })
        `when`(target.isBot).thenReturn(true)
        DeveloperItemHandler.player(actor, target)
        click("Next")
        assertTrue(options.any { name(it) == "Kernel" })
    }

    private fun navigationTarget(): Player {
        val target = mock(Player::class.java, RETURNS_DEEP_STUBS)
        `when`(target.state).thenReturn(EntityState.ACTIVE)
        `when`(target.username).thenReturn("Selected")
        `when`(target.position).thenReturn(Position(3000, 3000, 2))
        return target
    }

    private fun enterDestination(value: String) {
        val overlay = ArgumentCaptor.forClass(AbstractOverlay::class.java)
        verify(actor.overlays, atLeastOnce()).open(overlay.capture())
        (overlay.allValues.last() as TextInput).input(actor, value)
    }

    @Test fun selectedPlayerNavigationMovesOnlyTargetInstantly() {
        val target = navigationTarget()
        DeveloperItemHandler.player(actor, target)
        click("Navigation")
        enterDestination("3200 3201")
        click("Move Selected to")
        verify(target).move(Position(3200, 3201, 2))
        verify(actor, never()).move(any(Position::class.java))
        verify(actor, never()).startAutomation(any())
    }

    @Test fun targetSurvivesInvalidInputAndDestinationChanges() {
        val target = navigationTarget()
        DeveloperItemHandler.player(actor, target)
        click("Navigation")
        enterDestination("bad destination")
        enterDestination("3200 3201 3")
        click("Change destination")
        enterDestination("3202 3203 1")
        click("Move Selected to")
        verify(target).move(Position(3202, 3203, 1))
        verify(actor, never()).startAutomation(any())
    }

    @Test fun targetNavigationRechecksAvailabilityBeforeMoving() {
        val target = navigationTarget()
        DeveloperItemHandler.navigateInput(actor, "3200 3201 0", target)
        `when`(target.client.isPendingLogout).thenReturn(true)
        click("Move Selected to")
        verify(target, never()).move(any(Position::class.java))
    }

    @Test fun selfWalkerStillStartsAutomationInsteadOfInstantMovement() {
        `when`(actor.position).thenReturn(Position(3000, 3000, 0))
        DeveloperItemHandler.navigateInput(actor, "3200 3201 1")
        click("Walk to")
        verify(actor).startAutomation(any())
        verify(actor, never()).move(any(Position::class.java))
    }
}
