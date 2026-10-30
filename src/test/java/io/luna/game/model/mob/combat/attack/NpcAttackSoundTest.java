package io.luna.game.model.mob.combat.attack;

import game.content.barrows.BarrowsBrother;
import game.content.barrows.BarrowsCombat;
import io.luna.game.model.LocalSound;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.CombatImpactQueue;
import io.luna.game.model.mob.combat.CombatRestriction;
import io.luna.game.model.mob.combat.CombatSpell;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageAction;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.net.msg.out.SoundMessageWriter;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import kotlin.random.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies private NPC launch sounds for committed attacks, including misses and silent definitions.
 *
 * @author lare96
 */
class NpcAttackSoundTest {

    @BeforeAll
    static void bindings() {
        api.bot.script.InventoryProductionFixtures.INSTANCE.initialize();
    }

    /**
     * Controlled combat participants with no actual world or damage processing.
     *
     * @author lare96
     */
    private static final class Fixture {
        final Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        final Player victim = mock(Player.class, RETURNS_DEEP_STUBS);
        final CombatDamage damage = mock(CombatDamage.class);
        final CombatImpactQueue impacts = mock(CombatImpactQueue.class);
        final Position position = new Position(3200, 3200);

        Fixture() {
            when(npc.getPosition()).thenReturn(position);
            when(npc.combatDef().getAttackSound()).thenReturn(17);
            when(npc.combatDef().getRangedAttackSound()).thenReturn(269);
            when(damage.getAttacker()).thenReturn(npc);
            when(damage.getVictim()).thenReturn(victim);
            when(victim.getCombat().getImpacts()).thenReturn(impacts);
        }

        MeleeCombatAttack<Npc> melee(boolean cancel) {
            return new MeleeCombatAttack<>(npc, victim, -1, 1, 4) {
                @Override
                public CombatDamage calculateDamage(Mob other) {
                    return damage;
                }

                @Override
                public CombatDamage onAttack(CombatDamage hit) {
                    return cancel ? null : hit;
                }
            };
        }

        MockedStatic<LocalSound> sounds() {
            return mockStatic(LocalSound.class);
        }

        void verifyPrivateSound(int id) {
            ArgumentCaptor<SoundMessageWriter> packet = ArgumentCaptor.forClass(SoundMessageWriter.class);
            verify(victim).queue(packet.capture());
            var buffer = Unpooled.buffer();
            try {
                var message = packet.getValue().write(victim, buffer);
                assertEquals(26, message.getOpcode());
                assertEquals(id, buffer.readUnsignedShort());
                assertEquals(1, buffer.readUnsignedByte());
                assertEquals(0, buffer.readUnsignedShort());
                assertFalse(buffer.isReadable());
            } finally {
                buffer.release();
            }
        }

        void deliverScheduledHit() {
            ArgumentCaptor<Runnable> arrival = ArgumentCaptor.forClass(Runnable.class);
            verify(impacts).schedule(eq(0), arrival.capture(), any());
            verify(victim, never()).submitAction(any());
            arrival.getValue().run();
            verify(victim).submitAction(any(CombatDamageAction.class));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missesAndAccurateZeroHitsPlayTheLaunchSoundOnce(boolean accurate) {
        Fixture f = new Fixture();
        when(f.damage.isAccurate()).thenReturn(accurate);
        when(f.damage.getRawAmount()).thenReturn(0);
        try (var sounds = f.sounds()) {
            f.melee(false).apply();
            f.verifyPrivateSound(17);
            sounds.verifyNoInteractions();
            f.deliverScheduledHit();
        }
    }

    @Test
    void cancelledAttackDoesNotPlaySoundOrQueueDamage() {
        Fixture f = new Fixture();
        try (var sounds = f.sounds()) {
            f.melee(true).apply();
            sounds.verifyNoInteractions();
            verify(f.victim, never()).queue(any());
            verifyNoInteractions(f.impacts);
            verify(f.victim, never()).submitAction(any());
        }
    }

    @Test
    void encounterRestrictionPreventsSoundAndDamagePreparation() {
        Fixture f = new Fixture();
        Npc restricted = mock(Npc.class, withSettings().extraInterfaces(CombatRestriction.class)
                .defaultAnswer(RETURNS_DEEP_STUBS));
        when(((CombatRestriction) restricted).canFight(f.victim)).thenReturn(false);
        MeleeCombatAttack<Npc> attack = spy(new MeleeCombatAttack<>(restricted, f.victim, -1, 1, 4));
        try (var sounds = f.sounds()) {
            attack.apply();
            sounds.verifyNoInteractions();
            verify(f.victim, never()).queue(any());
            verify(attack, never()).calculateDamage(any());
            verifyNoInteractions(f.impacts);
            verify(f.victim, never()).submitAction(any());
        }
    }

    @Test
    void negativeSoundIdKeepsAnOtherwiseCommittedAttackSilent() {
        Fixture f = new Fixture();
        when(f.npc.combatDef().getAttackSound()).thenReturn(-1);
        try (var sounds = f.sounds()) {
            f.melee(false).apply();
            sounds.verifyNoInteractions();
            verify(f.victim, never()).queue(any());
            f.deliverScheduledHit();
        }
    }

    @Test
    void soundZeroRemainsAValidPrivateLaunchSound() {
        Fixture f = new Fixture();
        when(f.npc.combatDef().getAttackSound()).thenReturn(0);
        try (var sounds = f.sounds()) {
            f.melee(false).apply();
            f.verifyPrivateSound(0);
            sounds.verifyNoInteractions();
        }
    }

    @Test
    void rangedLaunchUsesItsOwnSoundInsteadOfTheMeleeSound() {
        Fixture f = new Fixture();
        RangedCombatAttack<Npc> attack = new RangedCombatAttack<>(f.npc, f.victim, 426,
                null, null, null, 4, 8) {
            @Override
            public CombatDamage calculateDamage(Mob other) {
                return f.damage;
            }
        };
        try (var sounds = f.sounds()) {
            attack.apply();
            f.verifyPrivateSound(269);
            sounds.verifyNoInteractions();
        }
    }

    @Test
    void bespokeAttackKeepsItsOwnAudioInsteadOfInheritingTheMeleeSound() {
        Fixture f = new Fixture();
        CombatAttack<Npc> attack = new CombatAttack<>(f.npc, f.victim, InteractionPolicy.STANDARD_SIZE, 4) {
            @Override
            public CombatDamage calculateDamage(Mob other) {
                return f.damage;
            }

            @Override
            public void attack() {
            }
        };
        try (var sounds = f.sounds()) {
            attack.apply();
            sounds.verifyNoInteractions();
            verify(f.victim, never()).queue(any());
        }
    }

    @Test
    void spellbookMagicUsesTheSpellStartSoundInsteadOfTheMeleeSound() {
        api.bot.script.InventoryProductionFixtures.INSTANCE.initializeCombat();
        Fixture f = new Fixture();
        when(f.victim.getPosition()).thenReturn(f.position.translate(10, 0));
        int startSound = CombatSpell.WIND_STRIKE.getDef().getStartSound().getId();
        MagicCombatAttack<Npc> attack = new MagicCombatAttack<>(f.npc, f.victim, CombatSpell.WIND_STRIKE, 4) {
            @Override
            public CombatDamage calculateDamage(Mob other) {
                return f.damage;
            }

            @Override
            protected void scheduleHit() {
            }
        };
        try (var sounds = f.sounds()) {
            attack.apply();
            f.verifyPrivateSound(startSound);
            sounds.verifyNoInteractions();
        }
    }

    @Test
    void customMagicWithoutASpellKeepsItsOwnAudio() {
        Fixture f = new Fixture();
        MagicCombatAttack<Npc> attack = new MagicCombatAttack<>(f.npc, f.victim,
                null, null, null, null, null, null, 4, 10) {
            @Override
            public CombatDamage calculateDamage(Mob other) {
                return f.damage;
            }

            @Override
            protected void scheduleHit() {
            }
        };
        try (var sounds = f.sounds()) {
            attack.apply();
            sounds.verifyNoInteractions();
            verify(f.victim, never()).queue(any());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ahrimUsesHisDedicatedCastSoundWithASpellSoundFallback(boolean configured) {
        api.bot.script.InventoryProductionFixtures.INSTANCE.initializeCombat();
        Fixture f = new Fixture();
        when(f.npc.combatDef().getAttackSound()).thenReturn(configured ? 1070 : -1);
        Random random = mock(Random.class);
        when(random.nextInt(16)).thenReturn(3);
        CombatAttack<Npc> attack = spy(BarrowsCombat.INSTANCE.attack(f.npc, f.victim, BarrowsBrother.AHRIM, random));
        doReturn(f.damage).when(attack).calculateDamage(f.victim);
        doNothing().when(attack).attack();
        int expected = configured ? 1070 : CombatSpell.FIRE_WAVE.getDef().getStartSound().getId();
        try (var sounds = f.sounds()) {
            attack.apply();
            f.verifyPrivateSound(expected);
            verify(attack).attack();
            sounds.verifyNoInteractions();
        }
    }

    @Test
    void npcVictimDoesNotBroadcastTheAttackSound() {
        Fixture f = new Fixture();
        Npc victim = mock(Npc.class, RETURNS_DEEP_STUBS);
        when(f.damage.getVictim()).thenReturn(victim);
        MeleeCombatAttack<Npc> attack = new MeleeCombatAttack<>(f.npc, victim, -1, 1, 4) {
            @Override
            public CombatDamage calculateDamage(Mob other) {
                return f.damage;
            }
        };
        try (var sounds = f.sounds()) {
            attack.apply();
            sounds.verifyNoInteractions();
            verify(f.victim, never()).queue(any());
        }
    }
}
