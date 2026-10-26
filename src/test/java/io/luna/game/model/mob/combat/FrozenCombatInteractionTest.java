package io.luna.game.model.mob.combat;

import io.luna.game.action.ActionQueue;
import io.luna.game.event.impl.NpcClickEvent.AttackNpcEvent;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.interact.InteractionAction;
import io.luna.game.model.mob.interact.InteractionActionListener;
import io.luna.game.model.mob.interact.InteractionPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Verifies that ordinary immobilization blocks required melee approach steps without blocking cardinal reach.
 * <p>
 * The real interaction action receives cardinal, diagonal, and same-tile targets while its player is frozen.
 * Only the already-reached interaction may invoke its listener.
 *
 * @author lare96
 */
class FrozenCombatInteractionTest {

    @Test void freezingAllowsCardinalMeleeButPreventsCorrectiveMovement() {
        for (Position targetPosition : List.of(new Position(101, 100), new Position(101, 101),
                new Position(100, 100))) {
            Player player = mock(Player.class, RETURNS_DEEP_STUBS);
            Npc target = mock(Npc.class);
            when(player.getActions()).thenReturn(new ActionQueue(player));
            when(player.getPosition()).thenReturn(new Position(100, 100));
            when(target.getPosition()).thenReturn(targetPosition);
            when(target.isWithinDistance(player, 1)).thenReturn(true);
            when(player.getStatus().isImmobilized()).thenReturn(true);
            var policy = InteractionPolicy.STANDARD_SIZE;
            when(player.getWorld().getCollisionManager().reached(player, target, policy)).thenReturn(true);
            AtomicInteger callbacks = new AtomicInteger();
            var listener = new InteractionActionListener(policy, callbacks::incrementAndGet);
            player.getActions().submit(new InteractionAction(player, new ArrayList<>(List.of(listener)),
                    target, new AttackNpcEvent(player, target)));

            assertEquals(targetPosition.equals(new Position(101, 100)) ? 1 : 0, callbacks.get());
            verify(player.getNavigator(), never()).step(any());
            verify(player.getNavigator(), never()).stepRandom(anyBoolean());
        }
    }
}
