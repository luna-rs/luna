package io.luna.game.model.mob.combat.attack;

import game.content.barrows.BarrowsBrother;
import game.content.barrows.BarrowsCombat;
import io.luna.LunaContext;
import io.luna.game.model.LocalSound;
import io.luna.game.model.Position;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.combat.CombatImpactQueue;
import io.luna.game.model.mob.combat.CombatRestriction;
import io.luna.game.model.mob.combat.CombatSpell;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageAction;
import io.luna.game.model.mob.interact.InteractionPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import kotlin.random.Random;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that NPC launch sounds follow committed attacks, including misses and silent definitions.
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
        final Mob victim = mock(Mob.class, RETURNS_DEEP_STUBS);
        final CombatDamage damage = mock(CombatDamage.class);
        final CombatImpactQueue impacts = mock(CombatImpactQueue.class);
        final LocalSound sound = mock(LocalSound.class);
        final LunaContext context = mock(LunaContext.class);
        final Position position = new Position(3200, 3200);

        Fixture() {
            when(npc.getPosition()).thenReturn(position);
            when(npc.getContext()).thenReturn(context);
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
            MockedStatic<LocalSound> sounds = mockStatic(LocalSound.class);
            sounds.when(() -> LocalSound.ofId(eq(context), anyInt(), eq(position),
                    any(ChunkUpdatableView.class), anyInt(), eq(1))).thenReturn(sound);
            return sounds;
        }

        void deliverScheduledHit() {
            ArgumentCaptor<Runnable> arrival = ArgumentCaptor.forClass(Runnable.class);
            verify(impacts).schedule(eq(1), arrival.capture(), any());
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
            sounds.verify(() -> LocalSound.ofId(eq(f.context), eq(17), eq(f.position),
                    any(ChunkUpdatableView.class), anyInt(), eq(1)));
            verify(f.sound).display();
            f.deliverScheduledHit();
        }
    }

    @Test
    void cancelledAttackDoesNotPlaySoundOrQueueDamage() {
        Fixture f = new Fixture();
        try (var sounds = f.sounds()) {
            f.melee(true).apply();
            sounds.verifyNoInteractions();
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
            f.deliverScheduledHit();
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
            sounds.verify(() -> LocalSound.ofId(eq(f.context), eq(269), eq(f.position),
                    any(ChunkUpdatableView.class), anyInt(), eq(1)));
            verify(f.sound).display();
            sounds.verifyNoMoreInteractions();
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
            sounds.verify(() -> LocalSound.ofId(eq(f.context), eq(startSound), eq(f.position),
                    any(ChunkUpdatableView.class), eq(Position.VIEWING_DISTANCE), eq(1)));
            verify(f.sound).display();
            sounds.verifyNoMoreInteractions();
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
            sounds.verify(() -> LocalSound.ofId(eq(f.context), eq(expected), eq(f.position),
                    any(ChunkUpdatableView.class), anyInt(), eq(1)));
            verify(f.sound).display();
            verify(attack).attack();
            sounds.verifyNoMoreInteractions();
        }
    }
}
