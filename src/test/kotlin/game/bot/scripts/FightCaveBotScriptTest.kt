package game.bot.scripts

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import com.google.gson.JsonObject
import engine.combat.prayer.CombatPrayer
import game.combat.npcHooks.tzhaar.JadAttack
import game.combat.npcHooks.tzhaar.JadHealers
import game.content.fightCave.FightCave
import game.content.fightCave.FightCaveController
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.UpdateBlockData
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.controller.PlayerController
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*

/**
 * Exercises observable Jad tells, encounter maintenance and admission without starting a game server.
 *
 * @author lare96
 */
class FightCaveBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun definitions() = InventoryProductionFixtures.initializeCombat()
    }

    private fun bot(inCave: Boolean = false): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.totalHealth).thenReturn(99)
        `when`(it.world.currentTick).thenReturn(100L)
        `when`(it.botClient.output.clickButton(anyInt())).thenReturn(true)
        `when`(it.spellbook).thenReturn(Spellbook.REGULAR)
        `when`(it.username).thenReturn("fight cave fixture")
        for (id in 0..6) {
            val skill = mock(Skill::class.java)
            `when`(skill.staticLevel).thenReturn(99)
            `when`(skill.level).thenReturn(99)
            `when`(it.skill(id)).thenReturn(skill)
            `when`(it.skills.getSkill(id)).thenReturn(skill)
        }
        `when`(it.controllers.primary).thenReturn(if (inCave) mock(FightCaveController::class.java)
            else PlayerController(it))
    }

    private fun npc(id: Int, bot: Bot): Npc = mock(Npc::class.java, RETURNS_DEEP_STUBS).also {
        `when`(it.id).thenReturn(id)
        `when`(it.isViewableFrom(bot)).thenReturn(true)
    }

    private fun tell(npc: Npc, animationId: Int?): UpdateBlockData = UpdateBlockData.Builder(npc).apply {
        if (animationId != null) animation(Animation(animationId))
    }.build()

    private fun supply(bot: Bot) = InventoryProductionFixtures.bank(bot,
        Item(861), Item(2497), Item(2491), Item(2493), Item(3749), Item(892, FightCaveBotScript.AMMO_COUNT),
        Item(FightCaveBotScript.FOOD, FightCaveBotScript.FOOD_COUNT),
        Item(FightCaveBotScript.PRAYER, FightCaveBotScript.POTION_COUNT), Item(995, 3000))

    @Test fun synchronizedTellRemainsReadableAfterTheMutableBuilderChanges() {
        val jad = npc(2745, bot())
        val builder = UpdateBlockData.Builder(jad).animation(Animation(JadAttack.MAGIC_ANIMATION))
        val magic = builder.build()
        val ranged = builder.animation(Animation(JadAttack.RANGED_ANIMATION)).build()

        assertEquals(JadAttack.MAGIC_ANIMATION, magic.animation.id)
        assertEquals(JadAttack.RANGED_ANIMATION, ranged.animation.id)
        assertNull(tell(jad, null).animation)
        assertEquals(CombatPrayer.PROTECT_FROM_MAGIC, FightCaveBotScript.tellPrayer(magic.animation.id))
        assertEquals(CombatPrayer.PROTECT_FROM_MISSILES, FightCaveBotScript.tellPrayer(ranged.animation.id))
        assertEquals(CombatPrayer.PROTECT_FROM_MELEE, FightCaveBotScript.tellPrayer(2655))
        assertNull(FightCaveBotScript.tellPrayer(2639), "A healer animation must not become a Jad tell")
    }

    @Test fun jadTellControlsPrayerEvenWhileAHealerIsPresentAndAfterAnEmptyUpdate() {
        val bot = bot(inCave = true)
        val jad = npc(2745, bot)
        val healer = npc(JadHealers.HEALER, bot)
        val script = FightCaveBotScript(bot)
        val output = bot.botClient.output
        val rangedTell = tell(jad, JadAttack.RANGED_ANIMATION)
        val emptyUpdate = tell(jad, null)
        val magicTell = tell(jad, JadAttack.MAGIC_ANIMATION)
        `when`(jad.blockData).thenReturn(rangedTell)

        script.maintain(listOf(healer, jad))
        verify(output).clickButton(CombatPrayer.PROTECT_FROM_MISSILES.button)
        verify(output, never()).clickButton(CombatPrayer.PROTECT_FROM_MELEE.button)

        clearInvocations(output)
        `when`(jad.blockData).thenReturn(emptyUpdate)
        script.maintain(listOf(healer, jad))
        verifyNoInteractions(output)

        clearInvocations(output)
        `when`(jad.blockData).thenReturn(magicTell)
        script.maintain(listOf(healer, jad))
        verify(output).clickButton(CombatPrayer.PROTECT_FROM_MAGIC.button)

        clearInvocations(output)
        `when`(bot.combat.prayers.isActive(CombatPrayer.PROTECT_FROM_MAGIC)).thenReturn(true)
        script.maintain(listOf(healer, jad))
        verifyNoInteractions(output)
    }

    @Test fun queuedPrayerTogglesSettleBeforeRetryWithoutDelayingANewTell() {
        val bot = bot(inCave = true)
        val script = FightCaveBotScript(bot)
        val jad = npc(2745, bot)
        val rangedTell = tell(jad, JadAttack.RANGED_ANIMATION)
        val magicTell = tell(jad, JadAttack.MAGIC_ANIMATION)
        val output = bot.botClient.output
        `when`(jad.blockData).thenReturn(rangedTell)

        repeat(3) { script.maintain(listOf(jad)) }
        verify(output, times(1)).clickButton(CombatPrayer.PROTECT_FROM_MISSILES.button)
        `when`(bot.world.currentTick).thenReturn(101L)
        script.maintain(listOf(jad))
        verify(output, times(1)).clickButton(CombatPrayer.PROTECT_FROM_MISSILES.button)

        `when`(jad.blockData).thenReturn(magicTell)
        script.maintain(listOf(jad))
        verify(output).clickButton(CombatPrayer.PROTECT_FROM_MAGIC.button)

        `when`(bot.world.currentTick).thenReturn(102L)
        `when`(jad.blockData).thenReturn(rangedTell)
        script.maintain(listOf(jad))
        verify(output, times(2)).clickButton(CombatPrayer.PROTECT_FROM_MISSILES.button)
        verify(output, never()).clickButton(CombatPrayer.PROTECT_FROM_MELEE.button)
    }

    @Test fun ordinaryWaveProtectionTracksTheStrongestUnblockedStyle() {
        val bot = bot(inCave = true)
        val script = FightCaveBotScript(bot)
        val output = bot.botClient.output
        val ranger = npc(2739, bot)
        val mage = npc(2743, bot)
        val melee = npc(2741, bot)

        script.maintain(listOf(melee, ranger, mage))
        verify(output).clickButton(CombatPrayer.PROTECT_FROM_MAGIC.button)
        clearInvocations(output)
        `when`(bot.world.currentTick).thenReturn(101L)
        script.maintain(listOf(melee, ranger))
        verifyNoInteractions(output)
        clearInvocations(output)
        `when`(bot.world.currentTick).thenReturn(104L)
        script.maintain(listOf(melee, ranger))
        verify(output).clickButton(CombatPrayer.PROTECT_FROM_MAGIC.button)
        clearInvocations(output)
        `when`(bot.world.currentTick).thenReturn(105L)
        script.maintain(listOf(melee, ranger))
        verify(output).clickButton(CombatPrayer.PROTECT_FROM_MISSILES.button)
        clearInvocations(output)
        `when`(bot.world.currentTick).thenReturn(106L)
        script.maintain(listOf(melee))
        verify(output).clickButton(CombatPrayer.PROTECT_FROM_MELEE.button)
    }

    @ParameterizedTest @ValueSource(ints = [JadAttack.MAGIC_ANIMATION, JadAttack.RANGED_ANIMATION])
    fun jadDeathKeepsProtectionAgainstTheFinalLaunchedHitWhileHealersRemain(animation: Int) {
        val bot = bot(inCave = true)
        val script = FightCaveBotScript(bot)
        val jad = npc(2745, bot)
        val healer = npc(JadHealers.HEALER, bot)
        val prayer = FightCaveBotScript.tellPrayer(animation)!!
        val output = bot.botClient.output
        val snapshot = tell(jad, animation)
        `when`(jad.blockData).thenReturn(snapshot)

        script.maintain(listOf(jad, healer))
        verify(output).clickButton(prayer.button)
        clearInvocations(output)
        `when`(bot.world.currentTick).thenReturn(101L)
        script.maintain(listOf(healer))
        verifyNoInteractions(output)
        `when`(bot.world.currentTick).thenReturn(106L)
        script.maintain(listOf(healer))
        verify(output).clickButton(prayer.button)
        verify(output, never()).clickButton(CombatPrayer.PROTECT_FROM_MELEE.button)

        clearInvocations(output)
        `when`(bot.world.currentTick).thenReturn(107L)
        script.maintain(listOf(healer))
        verify(output).clickButton(CombatPrayer.PROTECT_FROM_MELEE.button)
        verify(output, never()).clickButton(prayer.button)
    }

    @Test fun lockedOrOutsideBotsCannotSendEncounterInputs() {
        val bot = bot(inCave = true)
        val script = FightCaveBotScript(bot)
        val jad = npc(2745, bot)
        val snapshot = tell(jad, JadAttack.MAGIC_ANIMATION)
        `when`(jad.blockData).thenReturn(snapshot)
        val output = bot.botClient.output

        `when`(bot.isLocked).thenReturn(true)
        script.maintain(listOf(jad))
        `when`(bot.isLocked).thenReturn(false)
        `when`(bot.controllers.primary).thenReturn(PlayerController(bot))
        script.maintain(listOf(jad))
        verifyNoInteractions(output)
    }

    @Test fun admissionRequiresOwnedSuppliesAndAnUnclaimedCape() {
        val bot = bot()
        supply(bot)
        assertTrue(FightCaveBotScript.canStart(bot), "A supplied high-level ranged account can attempt the cave")

        assertTrue(bot.bank.remove(Item(FightCaveBotScript.PRAYER)))
        assertFalse(FightCaveBotScript.canStart(bot), "One missing prayer potion blocks the long trip")
        InventoryProductionFixtures.bank(bot, Item(FightCaveBotScript.PRAYER))
        assertTrue(FightCaveBotScript.canStart(bot))

        InventoryProductionFixtures.bank(bot, Item(FightCave.FIRE_CAPE))
        assertFalse(FightCaveBotScript.canStart(bot), "Banked capes suppress repeat cape farming")
    }

    @Test fun admissionRejectsAmmunitionShortagesAndAccountRestrictions() {
        val bot = bot()
        supply(bot)
        assertTrue(FightCaveBotScript.canStart(bot))

        assertTrue(bot.bank.remove(Item(892)))
        assertFalse(FightCaveBotScript.canStart(bot), "Owned ammunition must cover the admission reserve")
        InventoryProductionFixtures.bank(bot, Item(892))
        `when`(bot.preferences.isSkiller).thenReturn(true)
        assertFalse(FightCaveBotScript.canStart(bot))
        `when`(bot.preferences.isSkiller).thenReturn(false)
        `when`(bot.skill(Skill.PRAYER).staticLevel).thenReturn(59)
        assertFalse(FightCaveBotScript.canStart(bot))
    }

    @Test fun lostRestoredAttemptCannotRestockOrStartAnotherCave() = runBlocking<Unit> {
        val bot = bot()
        supply(bot)
        val data = FightCaveBotScript.FightCaveData().apply { attempted = true }
        val script = FightCaveBotScript(bot, data)
        val handler = bot.actionHandler
        val output = bot.botClient.output

        assertTrue(script.init(resumed = true))
        assertTrue(script.snapshot().attempted)
        verifyNoInteractions(handler, output)
        assertEquals(FightCaveBotScript.POTION_COUNT, bot.bank.computeAmountForId(FightCaveBotScript.PRAYER))
    }

    @Test fun registrationRestoresAttemptOwnershipAndAbsoluteBudgets() {
        val original = FightCaveBotScript.FightCaveData().apply {
            attempted = true
            retreating = true
            deadline = 123456789L
            lastProgress = 123450000L
            previousRetaliate = 0
        }
        val json = JsonObject().also { original.save(it) }
        val restored = FightCaveBotScript.FightCaveData().also { it.load(json) }
        val manager = BotScriptManager()
        val previous = world.botManager.scriptManager
        `when`(world.botManager.scriptManager).thenReturn(manager)
        try {
            InventoryProductionFixtures.register("FightCave")
            val script = manager.loadScript(FightCaveBotScript::class.java.name, bot(), restored)
            assertInstanceOf(FightCaveBotScript::class.java, script)
            val roundTrip = JsonObject().also { script.snapshot()!!.save(it) }
            assertEquals(json, roundTrip)
            assertTrue(restored.attempted)
            assertTrue(restored.retreating)
            assertEquals(123456789L, restored.deadline)
            assertEquals(123450000L, restored.lastProgress)
            assertEquals(0, restored.previousRetaliate)
        } finally {
            `when`(world.botManager.scriptManager).thenReturn(previous)
        }
    }
}
