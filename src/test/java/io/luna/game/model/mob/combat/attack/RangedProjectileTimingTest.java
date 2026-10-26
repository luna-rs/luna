package io.luna.game.model.mob.combat.attack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.luna.LunaContext;
import io.luna.game.action.Action;
import io.luna.game.action.ActionQueue;
import io.luna.game.action.ActionType;
import io.luna.game.model.LocalProjectile;
import io.luna.game.model.EntityState;
import io.luna.game.model.mob.combat.CombatImpactQueue;
import io.luna.game.model.Position;
import io.luna.game.model.def.AmmoDefinition;
import io.luna.game.model.def.CombatStyleDefinition;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.AddProjectileMessageWriter;
import io.luna.util.GsonUtils;
import io.luna.util.parser.impl.AmmoDefinitionFileParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Checks the ranged wire protocol and arrival callbacks against the Lost City projectile profiles.
 *
 * @author lare96
 */
class RangedProjectileTimingTest {

    @Test
    void everyAmmoProfileEncodesDistanceDependentTiming() throws Exception {
        JsonArray definitions;
        try (var reader = Files.newBufferedReader(Path.of("data/game/def/equipment/ammo.jsonc"))) {
            definitions = GsonUtils.GSON.fromJson(reader, JsonArray.class);
        }
        Player source = mock(Player.class);
        Npc target = mock(Npc.class);
        when(source.getContext()).thenReturn(mock(LunaContext.class));
        when(source.getPosition()).thenReturn(new Position(3200, 3200));
        when(target.size()).thenReturn(1);
        when(target.getIndex()).thenReturn(7);
        when(target.getType()).thenReturn(io.luna.game.model.EntityType.NPC);
        AmmoDefinitionFileParser parser = new AmmoDefinitionFileParser();
        for (var element : definitions) {
            JsonObject data = element.getAsJsonObject();
            String type = data.get("type").getAsString();
            boolean bow = type.contains("ARROW") || type.contains("BOLT");
            int delay = bow ? 41 : 32;
            int length = bow ? 5 : 0;
            AmmoDefinition ammo = parser.convert(data);
            for (int distance : new int[]{1, 3, 8, 10}) {
                when(target.getPosition()).thenReturn(new Position(3200 + distance, 3200 + distance));
                LocalProjectile projectile = ammo.getProjectile().apply(source, target);
                int duration = delay + length + distance * 5;
                assertEquals(duration, projectile.getEndCycle(), type);
                ByteBuf buffer = Unpooled.buffer();
                try {
                    ((AddProjectileMessageWriter) projectile.displayMessage(0)).write(null, buffer);
                    buffer.skipBytes(3);
                    assertEquals(8, buffer.readShort(), type);
                    assertEquals(data.getAsJsonObject("projectile").get("id").getAsInt(), buffer.readUnsignedShort(), type);
                    assertEquals(40, buffer.readUnsignedByte(), type);
                    assertEquals(36, buffer.readUnsignedByte(), type);
                    assertEquals(delay, buffer.readUnsignedShort(), type);
                    assertEquals(duration, buffer.readUnsignedShort(), type);
                    assertEquals(15, buffer.readUnsignedByte(), type);
                    assertEquals(11, buffer.readUnsignedByte(), type);
                } finally {
                    buffer.release();
                }
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"3204,3204,1,66", "3204,3204,4,51", "3199,3199,4,51", "3202,3202,4,46"})
    void measuresDistanceToNearestNpcTile(int x, int y, int size, int expectedEnd) throws Exception {
        JsonObject data;
        try (var reader = Files.newBufferedReader(Path.of("data/game/def/equipment/ammo.jsonc"))) {
            data = GsonUtils.GSON.fromJson(reader, JsonArray.class).get(0).getAsJsonObject();
        }
        Player source = mock(Player.class);
        Npc target = mock(Npc.class);
        when(source.getContext()).thenReturn(mock(LunaContext.class));
        when(source.getPosition()).thenReturn(new Position(x, y));
        when(target.getPosition()).thenReturn(new Position(3200, 3200));
        when(target.size()).thenReturn(size);
        LocalProjectile projectile = new AmmoDefinitionFileParser().convert(data).getProjectile().apply(source, target);
        assertEquals(expectedEnd, projectile.getEndCycle());
    }

    @Test
    void individualTimingSettersAndDefaultOffsetRemainUnchanged() {
        LocalProjectile projectile = LocalProjectile.followPath(mock(LunaContext.class))
                .setSourcePosition(new Position(3200, 3200))
                .setTargetPosition(new Position(3201, 3201))
                .setTicksToStart(3)
                .setTicksToEnd(44)
                .build();
        ByteBuf buffer = Unpooled.buffer();
        try {
            ((AddProjectileMessageWriter) projectile.displayMessage(0)).write(null, buffer);
            buffer.skipBytes(9);
            assertEquals(3, buffer.readUnsignedShort());
            assertEquals(44, buffer.readUnsignedShort());
            buffer.skipBytes(1);
            assertEquals(64, buffer.readUnsignedByte());
        } finally {
            buffer.release();
        }
    }

    @ParameterizedTest
    @CsvSource({"37,2", "51,2", "60,3", "86,3", "96,4"})
    void launchesWithAnimationAndHitsOnArrivalTick(int endCycle, int hitTick) {
        Player source = mock(Player.class, RETURNS_DEEP_STUBS);
        Npc target = mock(Npc.class, RETURNS_DEEP_STUBS);
        AtomicLong clock = new AtomicLong();
        when(target.isAlive()).thenReturn(true);
        when(target.getState()).thenReturn(EntityState.ACTIVE);
        when(target.getWorld().getCurrentTick()).thenAnswer(call -> clock.get());
        CombatImpactQueue impacts = new CombatImpactQueue(target);
        when(target.getCombat().getImpacts()).thenReturn(impacts);
        LocalProjectile projectile = mock(LocalProjectile.class);
        when(projectile.getEndCycle()).thenReturn(endCycle);
        AmmoDefinition ammo = mock(AmmoDefinition.class);
        when(ammo.getProjectile()).thenReturn((a, b) -> projectile);
        CombatStyleDefinition style = mock(CombatStyleDefinition.class);
        when(style.getSpeed()).thenReturn(4);
        when(style.getRange()).thenReturn(10);
        AtomicInteger hits = new AtomicInteger();
        PlayerRangedCombatAttack attack = new PlayerRangedCombatAttack(source, target, style, ammo) {
            @Override
            public void onProjectileReached() {
                hits.incrementAndGet();
            }
        };
        ActionQueue queue = new ActionQueue(source);
        doAnswer(call -> {
            queue.submit(call.getArgument(0));
            return null;
        }).when(source).submitAction(any());
        queue.submit(new Action<>(source, ActionType.SOFT) {
            @Override
            public boolean run() {
                attack.attack();
                return true;
            }
        });
        queue.process();
        queue.normalize();
        verify(projectile).display();
        assertEquals(0, hits.get());
        for (int tick = 1; tick <= hitTick + 1; tick++) {
            clock.set(tick);
            impacts.process();
            queue.process();
            queue.normalize();
            assertEquals(tick < hitTick ? 0 : 1, hits.get(), "tick " + tick);
        }
        verify(projectile, times(1)).display();
    }
}
