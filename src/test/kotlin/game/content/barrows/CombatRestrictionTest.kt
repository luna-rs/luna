package game.content.barrows

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatRestriction
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.state.NpcCombatContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Checks encounter ownership at combat initiation and delayed impact boundaries.
 *
 * @author lare96
 */
class CombatRestrictionTest {
    @Test
    fun unrestrictedMobsAreUnchangedAndOwnershipAppliesInBothDirections() {
        val owner = mock(Player::class.java)
        val stranger = mock(Player::class.java)
        val npc = mock(Npc::class.java, withSettings().extraInterfaces(CombatRestriction::class.java))
        val restriction = npc as CombatRestriction
        `when`(restriction.canFight(owner)).thenReturn(true)
        assertTrue(CombatRestriction.permits(owner, npc))
        assertTrue(CombatRestriction.permits(npc, owner))
        assertFalse(CombatRestriction.permits(stranger, npc))
        assertFalse(CombatRestriction.permits(npc, stranger))
        assertTrue(CombatRestriction.permits(owner, mock(Npc::class.java)))
        val combat = NpcCombatContext(npc)
        combat.attack(stranger)
        assertNull(combat.target)
        verify(npc, never()).actions
    }

    @Test
    fun ownershipIsRecheckedBeforeDelayedDamageAndRetaliation() {
        val owner = mock(Player::class.java)
        val npc = mock(Npc::class.java, withSettings().extraInterfaces(CombatRestriction::class.java))
        `when`(npc.isAlive).thenReturn(true)
        val damage = mock(CombatDamage::class.java)
        `when`(damage.attacker).thenReturn(owner)
        `when`(damage.victim).thenReturn(npc)
        val source = mock(CombatAttack::class.java)
        val action = CombatDamageAction(damage, source, false)
        assertTrue(action.run())
        verify(damage, never()).apply(source)
        verify(npc, never()).combat
    }
}
