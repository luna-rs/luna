package game.bot.scripts

import api.bot.script.ZonedBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import com.google.common.collect.HashMultimap
import com.google.gson.JsonObject
import game.bot.scripts.NpcCombatScript.Companion.NpcCombatData
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.bot.script.BotScriptStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class NpcCombatPersistenceTest {
    private fun bot() = mock(Bot::class.java, RETURNS_DEEP_STUBS)

    private fun accepts(script: NpcCombatScript, name: String): Boolean {
        ZonedBotScript::class.java.getDeclaredField("activeZone").apply {
            isAccessible = true
            set(script, SubZone.ICE_MOUNTAIN)
        }
        val npc = mock(Npc::class.java, RETURNS_DEEP_STUBS)
        `when`(npc.def().name).thenReturn(name)
        return NpcCombatScript::class.java.getDeclaredMethod("canAttack", Npc::class.java).run {
            isAccessible = true
            invoke(script, npc) as Boolean
        }
    }

    @Test fun snapshotRoundTripPreservesPerZoneTargetRestrictions() {
        val names = HashMultimap.create<SubZone, String>().apply {
            put(SubZone.ICE_MOUNTAIN, "Dwarf")
            put(SubZone.LUMBRIDGE_COW_PEN, "Cow")
            put(SubZone.LUMBRIDGE_COW_PEN, "Cow calf")
        }
        val script = NpcCombatScript(bot(), 30.minutes,
            mutableListOf(SubZone.ICE_MOUNTAIN, SubZone.LUMBRIDGE_COW_PEN), names)
        val json = JsonObject().also { script.snapshot().save(it) }
        val data = NpcCombatData().apply { load(json) }
        val restored = NpcCombatScript(bot(), data)
        assertEquals(names, restored.snapshot().names)
        assertEquals(30.minutes, restored.duration)
        assertEquals(script.zones, restored.zones)
        assertTrue(accepts(restored, "Dwarf"))
        assertFalse(accepts(restored, "Icefiend"))
    }

    @Test fun snapshotDoesNotShareMutableTargetFilterState() {
        val names = HashMultimap.create<SubZone, String>().apply { put(SubZone.ICE_MOUNTAIN, "Dwarf") }
        val data = NpcCombatScript(bot(), 30.minutes, mutableListOf(SubZone.ICE_MOUNTAIN), names).snapshot()
        names.clear()
        assertEquals(setOf("Dwarf"), data.names[SubZone.ICE_MOUNTAIN])
    }

    @Test fun actualStackSaveAndLoadAcceptsBothNewAndLegacyDataClasses() {
        val bot = bot()
        val manager = BotScriptManager().apply {
            addScript<ZonedBotScriptData>(NpcCombatScript::class) { owner, data -> NpcCombatScript(owner, data) }
        }
        val stack = BotScriptStack(bot, manager)
        val names = HashMultimap.create<SubZone, String>().apply { put(SubZone.ICE_MOUNTAIN, "Dwarf") }
        stack.push(NpcCombatScript(bot, 30.minutes, mutableListOf(SubZone.ICE_MOUNTAIN), names))
        val saved = stack.save()
        assertEquals(NpcCombatData::class.java.name, saved.single().scriptDataClass)
        stack.load(saved)
        assertEquals(1, stack.size())
        assertEquals(saved.single().data, stack.save().single().data)

        val legacy = ZonedBotScriptData().apply { duration = 30.minutes; zones = mutableListOf(SubZone.ICE_MOUNTAIN) }
        val json = JsonObject().also { legacy.save(it) }
        val snapshot = io.luna.game.model.mob.bot.script.BotScriptSnapshot(0,
            NpcCombatScript::class.java.name, ZonedBotScriptData::class.java.name, json)
        stack.load(listOf(snapshot))
        assertEquals(1, stack.size())
        assertEquals(JsonObject(), stack.save().single().data.asJsonObject.getAsJsonObject("names"))
    }

    @Test fun missingNamesInOlderJsonDefaultsToUnrestrictedTargets() {
        val json = JsonObject().also {
            ZonedBotScriptData().apply { duration = 30.minutes; zones = mutableListOf(SubZone.ICE_MOUNTAIN) }.save(it)
        }
        val restored = NpcCombatScript(bot(), NpcCombatData().apply { load(json) })
        assertTrue(restored.snapshot().names.isEmpty)
        assertTrue(accepts(restored, "Dwarf"))
        assertTrue(accepts(restored, "Icefiend"))
    }
}
