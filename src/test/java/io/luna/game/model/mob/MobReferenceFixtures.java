package io.luna.game.model.mob;

import io.luna.game.model.Entity;
import io.luna.game.model.EntityState;
import io.luna.game.model.EntityType;
import io.luna.game.model.World;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.*;

/**
 * Registers isolated mob fixtures in mock world slots without starting client or server lifecycle hooks.
 *
 * @author lare96
 */
public final class MobReferenceFixtures {

    /**
     * Distinguishes independently created mock incarnations, including reused world slots.
     */
    private static final AtomicLong NEXT_INCARNATION = new AtomicLong(1_000_000);

    private MobReferenceFixtures() {
    }

    /**
     * Binds a mob and its original incarnation to a live mock world slot.
     *
     * @param mob A real player or mock mob with its world configured.
     * @param slot The positive player or NPC index.
     * @return The mob's session handle.
     */
    public static MobReference register(Mob mob, int slot) {
        EntityType type = mob instanceof Player ? EntityType.PLAYER : EntityType.NPC;
        MobReference reference;
        if (mockingDetails(mob).isMock()) {
            if (mob.getWorld() == null) {
                World world = mock(World.class, RETURNS_DEEP_STUBS);
                when(mob.getWorld()).thenReturn(world);
            }
            when(mob.getType()).thenReturn(type);
            when(mob.getIndex()).thenReturn(slot);
            when(mob.getIncarnation()).thenReturn(NEXT_INCARNATION.getAndIncrement());
            when(mob.getState()).thenReturn(EntityState.ACTIVE);
            reference = new MobReference(mob.getWorld(), type, slot, mob.getIncarnation());
            when(mob.getReference()).thenReturn(reference);
        } else {
            mob.setIndex(slot);
            try {
                Field state = Entity.class.getDeclaredField("state");
                state.setAccessible(true);
                state.set(mob, EntityState.ACTIVE);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
            reference = mob.getReference();
        }
        if (mob instanceof Player player) {
            var players = mob.getWorld().getPlayers();
            doReturn(player).when(players).get(slot);
        } else {
            var npcs = mob.getWorld().getNpcs();
            doReturn((Npc) mob).when(npcs).get(slot);
        }
        return reference;
    }
}
