package engine.combat.status

import api.predef.ext.*
import com.google.gson.JsonParser
import game.player.item.consume.potion.Potion
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests potion warning timing and persistence through the shared status action.
 *
 * @author lare96
 */
class PotionStatusEffectTest {

    companion object {

        @JvmStatic
        @BeforeAll
        fun loadItemNames() {
            Files.newBufferedReader(Path.of("data/dumps/items.json")).use { reader ->
                for (entry in JsonParser.parseReader(reader).asJsonArray) {
                    val item = entry.asJsonObject
                    val id = item.get("id").asInt
                    if (ItemDefinition.ALL.get(id).isEmpty) {
                        val definition = mock(ItemDefinition::class.java)
                        `when`(definition.id()).thenReturn(id)
                        `when`(definition.name).thenReturn(item.get("name").asString)
                        ItemDefinition.ALL.storeDefinition(definition)
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [150, 600, 1000, 1200])
    fun warnsOnceAtQuarterDurationAndExpiresOnTime(ticks: Int) {
        val player = mock(Player::class.java)
        val potion = Potion.ANTIPOISON_POTION
        val action = StatusAction(player)
        assertTrue(action.add(PotionStatusEffect(player, StatusEffectType.ANTIPOISON, ticks.ticks, potion)))
        repeat(ticks - ticks / 4 - 1) { action.run() }
        verify(player, never()).sendMessage(anyString())
        action.run()
        verify(player).sendMessage("Your ${potion.formattedName} is going to expire soon.")
        repeat(ticks / 4 - 1) { action.run() }
        assertTrue(action.isAntiPoison())
        action.run()
        assertFalse(action.isAntiPoison())
        verify(player).sendMessage("Your ${potion.formattedName} has expired.")
        action.run()
        verify(player, times(2)).sendMessage(anyString())
    }

    @ParameterizedTest
    @ValueSource(ints = [100, 450, 500])
    fun restoresOriginalWarningThresholdWithoutRepeatingWarning(elapsed: Int) {
        val player = mock(Player::class.java)
        val effect = PotionStatusEffect(player, StatusEffectType.ANTIFIRE, 600.ticks, Potion.ANTI_FIRE_POTION)
        repeat(elapsed) { assertFalse(effect.isReady()) }
        val restored = PotionStatusEffect(player, StatusEffectType.ANTIFIRE)
        restored.load(effect.save())
        restored.start(true)
        assertEquals(600 - elapsed, restored.countdown)
        val action = StatusAction(player)
        action.add(restored)
        repeat(600 - elapsed) { action.run() }
        verify(player).sendMessage("Your ${Potion.ANTI_FIRE_POTION.formattedName} is going to expire soon.")
        verify(player).sendMessage("Your ${Potion.ANTI_FIRE_POTION.formattedName} has expired.")
        assertFalse(action.isAntiFire())
    }

    @Test
    fun refreshUsesNewDurationAndRejectsShorterProtection() {
        val player = mock(Player::class.java)
        val action = StatusAction(player)
        action.add(PotionStatusEffect(player, StatusEffectType.ANTIPOISON, 150.ticks, Potion.ANTIPOISON_POTION))
        repeat(113) { action.run() }
        assertTrue(action.add(PotionStatusEffect(player, StatusEffectType.ANTIPOISON, 600.ticks, Potion.SUPER_ANTIPOISON)))
        assertFalse(action.add(PotionStatusEffect(player, StatusEffectType.ANTIPOISON, 150.ticks, Potion.ANTIPOISON_POTION)))
        repeat(449) { action.run() }
        verify(player, never()).sendMessage("Your ${Potion.SUPER_ANTIPOISON.formattedName} is going to expire soon.")
        action.run()
        verify(player).sendMessage("Your ${Potion.SUPER_ANTIPOISON.formattedName} is going to expire soon.")
        verify(player, never()).sendMessage("Your ${Potion.ANTIPOISON_POTION.formattedName} has expired.")
    }

    @Test
    fun oldSaveRetainsLegacyWarningAndPotionName() {
        val player = mock(Player::class.java)
        val data = JsonParser.parseString("""{"remaining":60,"potion":"SUPER_ANTIPOISON"}""").asJsonObject
        val effect = PotionStatusEffect(player, StatusEffectType.ANTIPOISON)
        effect.load(data)
        repeat(9) { assertFalse(effect.isReady()) }
        verify(player, never()).sendMessage(anyString())
        assertFalse(effect.isReady())
        verify(player).sendMessage("Your ${Potion.SUPER_ANTIPOISON.formattedName} is going to expire soon.")
        assertEquals(50, effect.save()!!.get("warning_ticks").asInt)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 3])
    fun shortEffectsExpireWithoutAnExpirationTickWarning(ticks: Int) {
        val player = mock(Player::class.java)
        val action = StatusAction(player)
        action.add(PotionStatusEffect(player, StatusEffectType.ANTIFIRE, ticks.ticks, Potion.ANTI_FIRE_POTION))
        repeat(ticks.coerceAtLeast(1)) { action.run() }
        assertFalse(action.isAntiFire())
        verify(player).sendMessage("Your ${Potion.ANTI_FIRE_POTION.formattedName} has expired.")
        verify(player, times(1)).sendMessage(anyString())
    }
}
