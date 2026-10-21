package io.luna.game.model.mob.block;

import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Direction;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies that forced movement uses each recipient's region and bypasses shared block caching.
 *
 * @author lare96
 */
final class ExactMovementUpdateBlockTest {

    @Test
    void observersDecodeTheSameWorldRouteWithDifferentRegionBases() {
        Player mover = mover();
        assertRoute(mover, viewer(3200, 3200), UpdateState.UPDATE_LOCAL, 48, 48, 48, 54);
        assertRoute(mover, viewer(3216, 3192), UpdateState.UPDATE_LOCAL, 32, 56, 32, 62);
        verify(mover, never()).acquireCachedBlock();
        verify(mover, never()).cacheBlockIfAbsent(any());
    }

    @Test
    void selfUpdateUsesCurrentRegionInsteadOfRegionCapturedByRoute() {
        Player mover = mover();
        when(mover.getLastRegion()).thenReturn(new Position(3208, 3208));
        assertRoute(mover, mover, UpdateState.UPDATE_SELF, 40, 40, 40, 46);
    }

    @Test
    void ordinaryLocalUpdatesStillUseCache() {
        Player mover = mover();
        UpdateBlockData data = new UpdateBlockData.Builder(mover).speak("Hello").build();
        when(mover.getBlockData()).thenReturn(data);
        when(mover.getFlagData()).thenReturn(ImmutableSet.of(UpdateFlag.SPEAK));
        ByteMessage msg = ByteMessage.raw();
        try {
            new PlayerUpdateBlockSet().encode(mover, msg, UpdateState.UPDATE_LOCAL, viewer(3200, 3200));
            verify(mover).acquireCachedBlock();
            verify(mover).cacheBlockIfAbsent(any());
        } finally {
            msg.release();
        }
    }

    private static Player mover() {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.asPlr()).thenReturn(player);
        when(player.getType()).thenReturn(EntityType.PLAYER);
        when(player.getFlagData()).thenReturn(ImmutableSet.of(UpdateFlag.EXACT_MOVEMENT));
        when(player.acquireCachedBlock()).thenReturn(null);
        ExactMovement route = new ExactMovement(new Position(3200, 3200), new Position(3200, 3200),
                new Position(3200, 3206), 3, 120, Direction.NORTH);
        UpdateBlockData data = new UpdateBlockData.Builder(player).move(route).build();
        when(player.getBlockData()).thenReturn(data);
        clearInvocations(player);
        return player;
    }

    private static Player viewer(int x, int y) {
        Player player = mock(Player.class);
        when(player.getLastRegion()).thenReturn(new Position(x, y));
        return player;
    }

    private static void assertRoute(Player mover, Player viewer, UpdateState state,
                                    int startX, int startY, int endX, int endY) {
        ByteMessage msg = ByteMessage.raw();
        try {
            new PlayerUpdateBlockSet().encode(mover, msg, state, viewer);
            assertEquals(0x120, msg.getShort(false, ByteOrder.LITTLE));
            assertEquals(startX, msg.get(false, ValueType.ADD));
            assertEquals(startY, msg.get(false, ValueType.NEGATE));
            assertEquals(endX, msg.get(false, ValueType.SUBTRACT));
            assertEquals(endY, msg.get(false));
            assertEquals(3, msg.getShort(false));
            assertEquals(120, msg.getShort(false, ValueType.ADD));
            assertEquals(Direction.NORTH.toForcedMovementId(), msg.get(false));
            assertEquals(0, msg.getBuffer().readableBytes());
        } finally {
            msg.release();
        }
    }
}
