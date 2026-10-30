package game.content.barrows

import api.combat.magic.CombatSpellHandler
import engine.controllers.MultiCombatAreaListener
import io.luna.game.model.Entity
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatAction
import io.luna.game.model.mob.combat.CombatRestriction
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.state.NpcCombatContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Checks encounter ownership at combat initiation, delayed impact, and area-spell selection boundaries.
 *
 * @author lare96
 */
class CombatRestrictionTest {
    @Test
    fun unrestrictedMobsAreUnchangedAndOwnershipAppliesInBothDirections() {
        val owner = mock(Player::class.java)
        val stranger = mock(Player::class.java)
        val npc = mock(Npc::class.java, withSettings().extraInterfaces(CombatRestriction::class.java)
            .defaultAnswer(RETURNS_DEEP_STUBS))
        val restriction = npc as CombatRestriction
        `when`(npc.isAlive).thenReturn(true)
        `when`(owner.isAlive).thenReturn(true)
        `when`(stranger.isAlive).thenReturn(true)
        `when`(npc.combat.isWithinLeash(owner)).thenReturn(true)
        `when`(npc.combat.isWithinLeash(stranger)).thenReturn(true)
        `when`(restriction.canFight(owner)).thenReturn(true)
        assertTrue(CombatRestriction.permits(owner, npc))
        assertTrue(CombatRestriction.permits(npc, owner))
        assertFalse(CombatRestriction.permits(stranger, npc))
        assertFalse(CombatRestriction.permits(npc, stranger))
        assertTrue(CombatRestriction.permits(owner, mock(Npc::class.java)))
        val combat = NpcCombatContext(npc)
        clearInvocations(npc, stranger)
        combat.attack(stranger)
        assertNull(combat.target)
        verify(restriction).canFight(stranger)
        verify(npc, never()).actions
        combat.attack(owner)
        assertSame(owner, combat.target)
        verify(npc.actions).submitIfAbsent(any(CombatAction::class.java))
    }

    @Test
    fun ownershipIsRecheckedBeforeDelayedDamageAndRetaliation() {
        val owner = mock(Player::class.java)
        val npc = mock(Npc::class.java, withSettings().extraInterfaces(CombatRestriction::class.java))
        `when`(npc.isAlive).thenReturn(true)
        val restriction = npc as CombatRestriction
        `when`(restriction.canFight(owner)).thenReturn(true)
        val damage = mock(CombatDamage::class.java)
        `when`(damage.attacker).thenReturn(owner)
        `when`(damage.victim).thenReturn(npc)
        val source = mock(CombatAttack::class.java)
        val action = CombatDamageAction(damage, source, false)
        `when`(restriction.canFight(owner)).thenReturn(false)
        assertTrue(action.run())
        verify(damage, never()).apply(source)
        verify(npc, never()).combat
    }

    @Test
    fun ownerDamageStillAppliesDefensiveHooksAndRetaliation() {
        val owner = mock(Player::class.java)
        `when`(owner.isAlive).thenReturn(true)
        `when`(owner.state).thenReturn(EntityState.ACTIVE)
        val npc = mock(Npc::class.java, withSettings().extraInterfaces(CombatRestriction::class.java)
            .defaultAnswer(RETURNS_DEEP_STUBS))
        `when`(npc.isAlive).thenReturn(true)
        `when`((npc as CombatRestriction).canFight(owner)).thenReturn(true)
        `when`(npc.combat.isAutoRetaliate).thenReturn(true)
        val damage = mock(CombatDamage::class.java)
        `when`(damage.attacker).thenReturn(owner)
        `when`(damage.victim).thenReturn(npc)
        val source = mock(CombatAttack::class.java)
        val action = CombatDamageAction(damage, source, false)

        assertTrue(action.run())
        verify(npc.combat).onNextDefence(owner, action)
        verify(damage).apply(source)
        verify(npc.combat).retaliate(owner)
    }

    @Test
    fun ancientAreaSpellsExcludeOtherPlayersEncountersButAllowOwnedAndUnrestrictedNpcs() {
        val caster = mock(Player::class.java, RETURNS_DEEP_STUBS)
        `when`(caster.asPlr()).thenReturn(caster)
        `when`(caster.controllers.contains(MultiCombatAreaListener)).thenReturn(true)
        `when`(caster.controllers.checkCombat(any(Mob::class.java))).thenReturn(true)
        `when`(caster.combat.checkMultiCombat(any(Mob::class.java))).thenReturn(true)

        val primary = areaNpc(Position(3485, 9500))
        val owned = areaNpc(Position(3486, 9500), restricted = true)
        val otherPlayers = areaNpc(Position(3485, 9501), restricted = true)
        val ordinary = areaNpc(Position(3484, 9500))
        `when`((owned as CombatRestriction).canFight(caster)).thenReturn(true)
        `when`(caster.world.locator.findViewable<Mob>(EntityType.NPC, primary.position))
            .thenReturn(linkedSetOf<Mob>(primary, otherPlayers, owned, ordinary))

        val attack = mock(MagicCombatAttack::class.java, RETURNS_DEEP_STUBS)
        val spell = mock(CombatSpellDefinition::class.java)
        `when`(spell.spell).thenReturn(CombatSpell.ICE_BURST)
        `when`(spell.radius).thenReturn(1)
        `when`(attack.spellEffect).thenReturn(spell)
        doReturn(caster).`when`(attack).attacker
        `when`(attack.victim).thenReturn(primary)
        `when`(attack.nextDamage.isAccurate).thenReturn(true)
        val ownedHit = mock(CombatDamage::class.java)
        val ordinaryHit = mock(CombatDamage::class.java)
        `when`(attack.calculateDamage(owned)).thenReturn(ownedHit)
        `when`(attack.calculateDamage(ordinary)).thenReturn(ordinaryHit)

        assertEquals(listOf(ownedHit, ordinaryHit), CombatSpellHandler.secondaryHits(attack))
        verify(otherPlayers as CombatRestriction).canFight(caster)
        verify(attack, never()).calculateDamage(otherPlayers)
        verify(attack, never()).calculateDamage(primary)
        verify(attack).calculateDamage(owned)
        verify(attack).calculateDamage(ordinary)
    }

    private fun areaNpc(position: Position, restricted: Boolean = false): Npc {
        val settings = withSettings().defaultAnswer(RETURNS_DEEP_STUBS)
        if (restricted) settings.extraInterfaces(CombatRestriction::class.java)
        val npc = mock(Npc::class.java, settings)
        // Area.contains(Entity) reads the backing position directly instead of its mocked getter.
        Entity::class.java.getDeclaredField("position").apply { isAccessible = true }.set(npc, position)
        `when`(npc.position).thenReturn(position)
        `when`(npc.type).thenReturn(EntityType.NPC)
        `when`(npc.isAlive).thenReturn(true)
        `when`(npc.combat.isAttackable).thenReturn(true)
        return npc
    }
}
