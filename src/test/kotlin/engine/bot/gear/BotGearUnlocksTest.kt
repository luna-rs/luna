package engine.bot.gear

import com.google.gson.JsonObject
import api.bot.script.InventoryProductionFixtures
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.OptionalInt

/**
 * Checks permanent equipment unlocks and upgrade selection using controlled gear metadata.
 *
 * @author lare96
 */
class BotGearUnlocksTest {

    private fun bot(vararg levels: Pair<Int, Int>): Bot {
        val bot = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        `when`(bot.attributes()).thenReturn(AttributeMap())
        levels.forEach { (skill, level) ->
            `when`(bot.skill(skill).staticLevel).thenReturn(level)
            `when`(bot.skill(skill).level).thenReturn(level)
        }
        return bot
    }

    private fun gear(id: Int, score: Int, slot: Int = Equipment.WEAPON,
                     purposes: Set<BotGearPurpose> = setOf(BotGearPurpose.MELEE),
                     vararg requirements: Pair<String, Int>): BotGearItem {
        val required = requirements.map { (name, level) ->
            EquipmentDefinition.Requirement(JsonObject().apply {
                addProperty("name", name)
                addProperty("level", level)
            })
        }.toTypedArray()
        val definition = EquipmentDefinition(id, slot, false, false, false, required, IntArray(12))
        val item = mock(BotGearItem::class.java)
        `when`(item.id).thenReturn(id)
        `when`(item.index).thenReturn(slot)
        `when`(item.purposes).thenReturn(purposes)
        `when`(item.def).thenReturn(lazyOf(definition))
        `when`(item.calculateScore()).thenReturn(score)
        return item
    }

    @Test fun delayedCheckIncludesIntermediateUnlocksAndKeepsBestPerSlot() {
        val sword = gear(10, 100, requirements = arrayOf("Attack" to 20))
        val scimitar = gear(11, 120, requirements = arrayOf("Attack" to 20))
        val body = gear(12, 150, Equipment.CHEST, requirements = arrayOf("Attack" to 21))
        val selector = BotGearUnlocks(listOf(sword, scimitar, body))

        assertEquals(setOf(11, 12), selector.upgrades(bot(Skill.ATTACK to 22), Skill.ATTACK, 18, 22))
    }

    @Test fun everyRequirementMustBePermanentlyMet() {
        val item = gear(10, 100, requirements = arrayOf("Attack" to 20, "Strength" to 20))
        val selector = BotGearUnlocks(listOf(item))
        val bot = bot(Skill.ATTACK to 20, Skill.STRENGTH to 19)
        `when`(bot.skill(Skill.STRENGTH).level).thenReturn(25)

        assertEquals(emptySet<Int>(), selector.upgrades(bot, Skill.ATTACK, 18, 20))
        `when`(bot.skill(Skill.STRENGTH).staticLevel).thenReturn(20)
        assertEquals(setOf(10), selector.upgrades(bot, Skill.STRENGTH, 19, 20))
    }

    @Test fun temporaryDrainDoesNotErasePermanentUnlock() {
        val item = gear(10, 100, requirements = arrayOf("Attack" to 20))
        val selector = BotGearUnlocks(listOf(item))
        val bot = bot(Skill.ATTACK to 20)
        `when`(bot.skill(Skill.ATTACK).level).thenReturn(15)

        assertEquals(setOf(10), selector.upgrades(bot, Skill.ATTACK, 18, 20))
    }

    @Test fun ownedAndSuperiorBankedGearSuppressDemand() {
        val upgrade = gear(10, 100, requirements = arrayOf("Attack" to 20))
        val owned = gear(11, 150, requirements = arrayOf("Attack" to 10))
        val selector = BotGearUnlocks(listOf(upgrade, owned))
        val bot = bot(Skill.ATTACK to 20)
        bot.itemTracker.add(10)
        assertEquals(emptySet<Int>(), selector.upgrades(bot, Skill.ATTACK, 18, 20))

        bot.itemTracker.remove(10)
        bot.itemTracker.add(11)
        assertEquals(emptySet<Int>(), selector.upgrades(bot, Skill.ATTACK, 18, 20))
    }

    @Test fun weakerOwnedGearDoesNotSuppressUpgrade() {
        val upgrade = gear(10, 150, requirements = arrayOf("Attack" to 20))
        val owned = gear(11, 100, requirements = arrayOf("Attack" to 10))
        val selector = BotGearUnlocks(listOf(upgrade, owned))
        val bot = bot(Skill.ATTACK to 20)
        bot.itemTracker.add(11)

        assertEquals(setOf(10), selector.upgrades(bot, Skill.ATTACK, 18, 20))
    }

    @Test fun defenceUnlockKeepsIndependentCombatStylesDespiteMeleeFallbackMetadata() {
        val melee = gear(10, 100, Equipment.CHEST, requirements = arrayOf("Defence" to 20))
        val magic = gear(11, 200, Equipment.CHEST, setOf(BotGearPurpose.MELEE, BotGearPurpose.MAGIC),
            "Defence" to 20, "Magic" to 40)
        val ranged = gear(12, 300, Equipment.CHEST, setOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED),
            "Defence" to 20, "Ranged" to 40)
        val selector = BotGearUnlocks(listOf(melee, magic, ranged))

        assertEquals(setOf(10, 11, 12), selector.upgrades(
            bot(Skill.DEFENCE to 20, Skill.MAGIC to 40, Skill.RANGED to 40), Skill.DEFENCE, 18, 20))
    }

    @Test fun unrelatedAndAlreadyPassedThresholdsDoNotBecomeNewDemand() {
        val item = gear(10, 100, requirements = arrayOf("Attack" to 20))
        val selector = BotGearUnlocks(listOf(item))
        val bot = bot(Skill.ATTACK to 30, Skill.DEFENCE to 20)

        assertEquals(emptySet<Int>(), selector.upgrades(bot, Skill.ATTACK, 20, 22))
        assertEquals(emptySet<Int>(), selector.upgrades(bot, Skill.DEFENCE, 18, 20))
        assertEquals(emptySet<Int>(), selector.upgrades(bot, Skill.ATTACK, 22, 20))
    }

    @Test fun nativeMetalWeaponUnlocksPreferScimitarsAndRespectBankOwnership() {
        InventoryProductionFixtures.initializeCombat()
        val bot = bot(Skill.ATTACK to 20, Skill.STRENGTH to 1, Skill.DEFENCE to 1,
            Skill.RANGED to 1, Skill.MAGIC to 1, Skill.PRAYER to 1, Skill.HITPOINTS to 10)

        assertEquals(setOf(1329), BotGearUnlocks.find(bot, Skill.ATTACK, 18, 20))
        bot.itemTracker.add(1329)
        assertEquals(emptySet<Int>(), BotGearUnlocks.find(bot, Skill.ATTACK, 18, 20))
        `when`(bot.skill(Skill.ATTACK).staticLevel).thenReturn(40)
        assertEquals(setOf(1333), BotGearUnlocks.find(bot, Skill.ATTACK, 38, 40))
    }

    @Test fun notedOwnershipSuppressesDemandThroughCachedAlias() {
        InventoryProductionFixtures.initializeCombat()
        val definition = ItemDefinition.ALL.retrieve(1329)
        // The common fixture omits note links. Supply one only while constructing this test's private index,
        // then restore the definition before making assertions or using any shared unlock index.
        val field = ItemDefinition::class.java.getDeclaredField("notedId").apply { isAccessible = true }
        val previous = definition.notedId
        val selector = try {
            field.set(definition, OptionalInt.of(1330))
            BotGearUnlocks(listOf(BotGearSelector.ALL_GEAR_IDS.value.getValue(1329)))
        } finally {
            field.set(definition, previous)
        }
        val bot = bot(Skill.ATTACK to 20)
        bot.itemTracker.add(1330)

        assertEquals(emptySet<Int>(), selector.upgrades(bot, Skill.ATTACK, 18, 20))
        assertEquals(emptySet<Int>(), selector.best(bot, listOf(1329)))
        bot.itemTracker.remove(1330)
        assertEquals(setOf(1329), selector.upgrades(bot, Skill.ATTACK, 18, 20))
    }

    @Test fun nativeRangedAndMagicUnlocksRetainTheirStyleMetadata() {
        InventoryProductionFixtures.initializeCombat()
        val bot = bot(Skill.ATTACK to 1, Skill.STRENGTH to 1, Skill.DEFENCE to 40,
            Skill.RANGED to 40, Skill.MAGIC to 40, Skill.PRAYER to 1, Skill.HITPOINTS to 10)

        val ranged = BotGearUnlocks.find(bot, Skill.RANGED, 38, 40)
        assertTrue(1135 in ranged, "Green dragonhide body should be a ranged upgrade")
        assertTrue(ranged.any { EquipmentDefinition.ALL.retrieve(it).index == Equipment.WEAPON },
            "Ranged progression should discover a weapon")
        val magic = BotGearUnlocks.find(bot, Skill.MAGIC, 38, 40)
        assertTrue(magic.any { EquipmentDefinition.ALL.retrieve(it).index == Equipment.CHEST },
            "Magic progression should discover usable robes")
        assertTrue(magic.all { id -> BotGearPurpose.MAGIC in BotGearSelector.ALL_GEAR_IDS.value.getValue(id).purposes })
    }

    @Test fun pendingBasketDropsIntermediateTiersAndKeepsIndependentSlots() {
        val oldSword = gear(10, 100, requirements = arrayOf("Attack" to 10))
        val newSword = gear(11, 200, requirements = arrayOf("Attack" to 20))
        val body = gear(12, 200, Equipment.CHEST, requirements = arrayOf("Defence" to 20))
        val selector = BotGearUnlocks(listOf(oldSword, newSword, body))
        val bot = bot(Skill.ATTACK to 20, Skill.DEFENCE to 20)

        assertEquals(setOf(11, 12), selector.best(bot, listOf(10, 11, 12)))
        bot.itemTracker.add(11)
        assertEquals(setOf(12), selector.best(bot, listOf(10, 11, 12)))
    }
}
