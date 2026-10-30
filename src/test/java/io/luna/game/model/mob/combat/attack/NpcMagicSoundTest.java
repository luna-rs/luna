package io.luna.game.model.mob.combat.attack;

import game.player.Sound;
import io.luna.game.model.LocalSound;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.CombatSpell;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers private NPC spell impact sounds and preserves player-cast positional sound behavior.
 *
 * @author lare96
 */
class NpcMagicSoundTest {

    @BeforeAll
    static void bindings() {
        api.bot.script.InventoryProductionFixtures.INSTANCE.initializeCombat();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void npcSpellHitsAndSplashesPlayOnlyToTheAffectedPlayer(boolean accurate) {
        Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        MagicCombatAttack<Npc> attack = new MagicCombatAttack<>(npc, player, CombatSpell.WIND_STRIKE, 4);
        CombatDamage damage = damage(npc, player, accurate);
        Sound expected = accurate ? CombatSpell.WIND_STRIKE.getDef().getEndSound() : Sound.SPELLFAIL;

        try (var sounds = mockStatic(LocalSound.class)) {
            attack.onDamageApplied(damage);
            verify(player).playSound(expected);
            sounds.verifyNoInteractions();
        }
    }

    @Test
    void secondaryRecipientHearsItsImpactInsteadOfThePrimaryTarget() {
        Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        Player primary = mock(Player.class, RETURNS_DEEP_STUBS);
        Player secondary = mock(Player.class, RETURNS_DEEP_STUBS);
        Sound impact = CombatSpell.WIND_STRIKE.getDef().getEndSound();
        MagicCombatAttack<Npc> attack = new MagicCombatAttack<>(npc, primary, CombatSpell.WIND_STRIKE, 4);

        try (var sounds = mockStatic(LocalSound.class)) {
            attack.onDamageApplied(damage(npc, secondary, true));
            verify(secondary).playSound(impact);
            verify(primary, never()).playSound(any());
            sounds.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void npcSpellAgainstAnotherNpcDoesNotBroadcastItsImpact(boolean accurate) {
        Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        Npc recipient = mock(Npc.class, RETURNS_DEEP_STUBS);
        MagicCombatAttack<Npc> attack = new MagicCombatAttack<>(npc, recipient, CombatSpell.WIND_STRIKE, 4);

        try (var sounds = mockStatic(LocalSound.class)) {
            attack.onDamageApplied(damage(npc, recipient, accurate));
            sounds.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void playerSpellRetainsItsExistingPositionalImpactSound(boolean accurate) {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        Npc recipient = mock(Npc.class, RETURNS_DEEP_STUBS);
        MagicCombatAttack<Player> attack = new MagicCombatAttack<>(player, recipient, CombatSpell.WIND_STRIKE, 4);
        Sound expected = accurate ? CombatSpell.WIND_STRIKE.getDef().getEndSound() : Sound.SPELLFAIL;
        LocalSound localSound = mock(LocalSound.class);

        try (var sounds = mockStatic(LocalSound.class)) {
            sounds.when(() -> LocalSound.of(recipient, expected)).thenReturn(localSound);
            attack.onDamageApplied(damage(player, recipient, accurate));
            sounds.verify(() -> LocalSound.of(recipient, expected));
            sounds.verifyNoMoreInteractions();
            verify(localSound).display();
            verify(player, never()).playSound(any());
        }
    }

    private CombatDamage damage(Mob attacker, Mob recipient, boolean accurate) {
        CombatDamage damage = mock(CombatDamage.class);
        when(damage.getAttacker()).thenReturn(attacker);
        when(damage.getVictim()).thenReturn(recipient);
        when(damage.isAccurate()).thenReturn(accurate);
        return damage;
    }
}
