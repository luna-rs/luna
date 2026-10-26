package io.luna.game.model.mob.combat;

import io.luna.game.action.ActionQueue;
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.state.NpcCombatContext;
import io.luna.game.model.mob.combat.state.PlayerCombatContext;
import io.luna.game.model.mob.combat.state.PlayerCombatWeapon;
import io.luna.game.model.mob.interact.InteractionPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetaliationTimingTest {

    @BeforeAll static void bindings() {
        api.bot.script.InventoryProductionFixtures.INSTANCE.initializeCombat();
    }

    private static class NpcFixture {
        final Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        final Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        final AtomicLong tick = new AtomicLong(100);
        final ActionQueue actions = new ActionQueue(npc);
        final NpcCombatContext combat;

        NpcFixture() {
            when(npc.getWorld().getCurrentTick()).thenAnswer(call -> tick.get());
            when(npc.getActions()).thenReturn(actions);
            when(npc.isAlive()).thenReturn(true);
            when(player.isAlive()).thenReturn(true);
            when(npc.combatDef().getAttackSpeed()).thenReturn(4);
            combat = spy(new NpcCombatContext(npc));
            when(npc.getCombat()).thenReturn(combat);
            combat.setRetreatPolicy(RetreatPolicy.NONE);
        }

        void advance() {
            tick.incrementAndGet();
            combat.processAttackDelay();
        }
    }

    @Test void freshNpcRetaliationWaitsHalfSpeedWithoutRestartingOnEachHit() {
        NpcFixture f = new NpcFixture();
        f.combat.retaliate(f.player);
        assertEquals(2, f.combat.getAttackDelay());
        f.advance();
        f.combat.retaliate(f.player);
        assertEquals(1, f.combat.getAttackDelay());
        f.advance();
        assertTrue(f.combat.isAttackReady());
    }

    @Test void flinchDelaysTheActualFirstRetaliatorySwing() {
        NpcFixture f = new NpcFixture();
        when(f.npc.getPosition()).thenReturn(new Position(100, 100));
        when(f.player.getPosition()).thenReturn(new Position(101, 100));
        when(f.npc.isWithinDistance(f.player, Position.VIEWING_DISTANCE + 5)).thenReturn(true);
        when(f.npc.getWorld().getCollisionManager().reached(any(), eq(f.player), any())).thenReturn(true);
        doReturn(true).when(f.combat).checkMultiCombat(f.player);
        CombatAttack<?> attack = mock(CombatAttack.class);
        when(attack.getInteractionPolicy()).thenReturn(InteractionPolicy.STANDARD_SIZE);
        doReturn(attack).when(f.combat).getNextAttack(f.player);
        List<Long> swings = new ArrayList<>();
        doAnswer(call -> { swings.add(f.tick.get()); f.combat.setAttackDelay(4); return null; })
                .when(attack).apply();
        f.advance(); // Incoming player melee damage at T+1, after the tick's countdown step.
        f.combat.retaliate(f.player);
        for (int turn = 0; turn < 3; turn++) {
            f.actions.process();
            f.actions.processAfterMovement();
            f.actions.normalize();
            f.advance();
        }
        assertEquals(List.of(103L), swings);
    }

    @Test void engagedNpcKeepsCooldownAndOnlyFlinchesAgainAfterTheIdleBoundary() {
        NpcFixture f = new NpcFixture();
        f.combat.attack(f.player);
        f.combat.setAttackDelay(4);
        f.combat.retaliate(f.player);
        assertEquals(4, f.combat.getAttackDelay());
        for (int tick = 0; tick < 12; tick++) f.advance();
        f.combat.retaliate(f.player); // Exactly eight ticks beyond the old deadline.
        assertEquals(0, f.combat.getAttackDelay());
        f.advance();
        f.combat.retaliate(f.player);
        assertEquals(2, f.combat.getAttackDelay());
    }

    @Test void playerFlinchUsesWeaponSpeedAndAppliesRapidAfterHalving() {
        for (CombatStyle style : List.of(CombatStyle.UNARMED_PUNCH, CombatStyle.CROSSBOW_ACCURATE,
                CombatStyle.CROSSBOW_RAPID, CombatStyle.KNIFE_RAPID, CombatStyle.DART_RAPID)) {
            Player player = mock(Player.class, RETURNS_DEEP_STUBS);
            Npc attacker = mock(Npc.class);
            when(player.isAlive()).thenReturn(true);
            when(attacker.isAlive()).thenReturn(true);
            when(player.getWorld().getCurrentTick()).thenReturn(100L);
            when(player.getControllers().checkCombat(attacker)).thenReturn(true);
            ActionQueue actions = new ActionQueue(player);
            when(player.getActions()).thenReturn(actions);
            PlayerCombatContext combat = spy(new PlayerCombatContext(player));
            when(player.getCombat()).thenReturn(combat);
            PlayerCombatWeapon weapon = mock(PlayerCombatWeapon.class, RETURNS_DEEP_STUBS);
            doReturn(weapon).when(combat).getWeapon();
            when(weapon.getStyleDef().getType()).thenReturn(style);
            int speed = switch (style) {
                case UNARMED_PUNCH, CROSSBOW_RAPID -> 4;
                case CROSSBOW_ACCURATE -> 5;
                default -> 2;
            };
            when(weapon.getSpeed()).thenReturn(speed);
            combat.retaliate(attacker);
            int expected = switch (style) {
                case UNARMED_PUNCH, CROSSBOW_ACCURATE -> 2;
                case CROSSBOW_RAPID -> 1;
                default -> 0;
            };
            assertEquals(expected, combat.getAttackDelay(), style.name());
        }
    }

    @Test void playerRetaliationDoesNotExtendAnUnexpiredOrJustReadyCooldown() {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        Npc attacker = mock(Npc.class);
        when(player.isAlive()).thenReturn(true);
        when(attacker.isAlive()).thenReturn(true);
        when(player.getControllers().checkCombat(attacker)).thenReturn(true);
        AtomicLong tick = new AtomicLong(100);
        when(player.getWorld().getCurrentTick()).thenAnswer(call -> tick.get());
        when(player.getActions()).thenReturn(new ActionQueue(player));
        PlayerCombatContext combat = new PlayerCombatContext(player);
        when(player.getCombat()).thenReturn(combat);
        combat.setAttackDelay(1);
        combat.retaliate(attacker);
        assertEquals(1, combat.getAttackDelay());
        tick.incrementAndGet();
        combat.processAttackDelay();
        combat.retaliate(attacker);
        assertEquals(0, combat.getAttackDelay());
    }
}
