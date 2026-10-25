package api.bot.webwalk

import api.bot.webwalk.generate.SpecialCrossings
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.walk.crossing.ConditionalDoorCrossing
import api.bot.webwalk.walk.crossing.CrossingHandlers
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Checks that conditional door routes require the right skills and carried tools in each direction.
 *
 * @author lare96
 */
class ConditionalDoorCrossingTest {

    @Test
    fun yanilleExitIsFreeButEntryNeedsLevelAndLockpick() {
        val door = SpecialCrossings.YANILLE_DOOR
        val entry = door.reverseRequirements!!
        assertTrue(CapabilitySnapshot().meets(door.requirements))
        assertFalse(CapabilitySnapshot(mapOf(Skill.THIEVING to 81), mapOf(1523 to 1)).meets(entry))
        assertFalse(CapabilitySnapshot(mapOf(Skill.THIEVING to 82)).meets(entry))
        assertTrue(CapabilitySnapshot(mapOf(Skill.THIEVING to 82), mapOf(1523 to 1)).meets(entry))
        assertTrue(door.bidirectional)
    }

    @Test
    fun miningGuildRequiresLevelSixtyBothWays() {
        val door = SpecialCrossings.MINING_GUILD
        val reverse = door.reverseRequirements ?: door.requirements
        for (requirements in listOf(door.requirements, reverse)) {
            assertFalse(CapabilitySnapshot(mapOf(Skill.MINING to 59)).meets(requirements))
            assertTrue(CapabilitySnapshot(mapOf(Skill.MINING to 60)).meets(requirements))
        }
    }

    @Test
    fun generatorAndExecutorRegisterBothDoors() {
        for (spec in listOf(SpecialCrossings.MINING_GUILD, SpecialCrossings.YANILLE_DOOR)) {
            assertTrue(spec in SpecialCrossings.ALL)
            assertSame(ConditionalDoorCrossing, CrossingHandlers.forId(spec.handler))
        }
        assertEquals(setOf(2112), SpecialCrossings.MINING_GUILD.closedObjectIds)
        assertEquals(setOf(2559), SpecialCrossings.YANILLE_DOOR.closedObjectIds)
    }
}
