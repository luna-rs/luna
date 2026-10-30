package io.luna.game.model.mob;

import io.luna.game.model.EntityType;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.World;
import io.luna.game.model.WorldLocator;
import io.luna.game.model.chunk.ChunkManager;
import io.luna.game.model.mob.NpcAggressionProfile.NpcAggressionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Verifies that skipping empty player neighborhoods preserves NPC aggression decisions.
 *
 * @author lare96
 */
class NpcAggressionTest {

    private static final Position ORIGIN = new Position(3207, 3207);

    private World world;
    private ChunkManager chunks;
    private WorldLocator locator;
    private Npc npc;
    private NpcAggression aggression;

    @BeforeEach
    void setUp() {
        world = mock(World.class, RETURNS_DEEP_STUBS);
        chunks = new ChunkManager(world);
        locator = spy(new WorldLocator(world));
        when(world.getChunks()).thenReturn(chunks);
        when(world.getLocator()).thenReturn(locator);

        npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        when(npc.getWorld()).thenReturn(world);
        when(npc.getPosition()).thenReturn(ORIGIN);
        when(npc.abs()).thenReturn(ORIGIN);
        when(npc.getChunk()).thenReturn(ORIGIN.getChunk());
        when(npc.getAggressionProfile()).thenReturn(new NpcAggressionProfile(NpcAggressionPolicy.ALWAYS, -1));
        when(npc.getCombat().isAttackable()).thenReturn(true);
        when(npc.getCombat().getTarget()).thenReturn(null);
        when(npc.getCombat().isWithinLeash(any())).thenReturn(true);
        when(npc.getCombat().checkMultiCombat(any())).thenReturn(true);
        aggression = new NpcAggression(npc);
    }

    private Player addPlayer(Position position) {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.getType()).thenReturn(EntityType.PLAYER);
        when(player.getPosition()).thenReturn(position);
        when(player.abs()).thenReturn(position);
        when(player.isAlive()).thenReturn(true);
        when(player.getTolerance().getDuration()).thenReturn(Duration.ZERO);
        when(player.getTimePlayed()).thenReturn(Duration.ofDays(2));
        doCallRealMethod().when(player).isWithinDistance(any(Locatable.class), anyInt());
        chunks.load(position).add(player);
        return player;
    }

    @Test
    void emptyWorldSkipsTheLocatorAndLoadsNoChunks() {
        aggression.process();

        verify(locator, never()).findViewablePlayers(npc);
        assertEquals(0, chunks.getAll().size());
    }

    @Test
    void remotePlayersDoNotTriggerAnEmptyNeighborhoodSearch() {
        addPlayer(ORIGIN.translate(1000, 1000));

        aggression.process();

        verify(locator, never()).findViewablePlayers(npc);
        assertEquals(1, chunks.getAll().size());
    }

    @ParameterizedTest
    @CsvSource({"5, true", "6, false"})
    void normalPoliciesRetainTheFiveTileRadius(int distance, boolean attacks) {
        when(npc.getAggressionProfile()).thenReturn(new NpcAggressionProfile(NpcAggressionPolicy.WEARING_GUTHIX, -1));
        Player player = addPlayer(ORIGIN.translate(distance, distance));

        aggression.process();

        verify(locator).findViewablePlayers(npc);
        verify(npc.getCombat(), times(attacks ? 1 : 0)).attack(player);
    }

    @ParameterizedTest
    @CsvSource({"10, true", "11, false"})
    void alwaysAggressiveNpcsRetainTheTenTileRadius(int distance, boolean attacks) {
        Player player = addPlayer(ORIGIN.translate(distance, distance));

        aggression.process();

        verify(locator).findViewablePlayers(npc);
        verify(npc.getCombat(), times(attacks ? 1 : 0)).attack(player);
    }

    @ParameterizedTest
    @CsvSource({"15, true", "16, false"})
    void bossesRetainTheFullViewingDistanceAcrossChunkBoundaries(int distance, boolean attacks) {
        when(npc.isBoss()).thenReturn(true);
        Player player = addPlayer(ORIGIN.translate(distance, distance));

        aggression.process();

        verify(locator).findViewablePlayers(npc);
        verify(npc.getCombat(), times(attacks ? 1 : 0)).attack(player);
    }

    @Test
    void playersOnAnotherPlaneRemainIneligible() {
        Player player = addPlayer(new Position(ORIGIN.getX(), ORIGIN.getY(), 1));

        aggression.process();

        verify(locator).findViewablePlayers(npc);
        verify(npc.getCombat(), never()).attack(player);
    }

    @Test
    void theExistingLocatorOrderStillSelectsTheFirstEligiblePlayer() {
        addPlayer(ORIGIN.translate(1, 0));
        addPlayer(ORIGIN.translate(0, 1));
        addPlayer(ORIGIN.translate(-1, 0));
        Player expected = locator.findViewablePlayers(npc).iterator().next();
        clearInvocations(locator);

        aggression.process();

        verify(locator).findViewablePlayers(npc);
        verify(npc.getCombat()).attack(expected);
        verify(npc.getCombat(), times(1)).attack(any());
    }

    @Test
    void youngBotsRemainIgnored() {
        Player player = addPlayer(ORIGIN);
        when(player.isBot()).thenReturn(true);
        when(player.getTimePlayed()).thenReturn(Duration.ofHours(47));

        aggression.process();

        verify(npc.getCombat(), never()).attack(player);
    }

    @Test
    void toleranceStillPreventsAggression() {
        Player player = addPlayer(ORIGIN);
        when(npc.getAggressionProfile()).thenReturn(new NpcAggressionProfile(NpcAggressionPolicy.ALWAYS, 10));
        when(player.getTolerance().getDuration()).thenReturn(Duration.ofMinutes(10));

        aggression.process();

        verify(npc.getCombat(), never()).attack(player);
    }

    @Test
    void retreatingNpcsStillSkipTheSearch() {
        addPlayer(ORIGIN);
        when(npc.getCombat().isRetreating()).thenReturn(true);

        aggression.process();

        verify(locator, never()).findViewablePlayers(npc);
    }

    @Test
    void leashRestrictionsStillPreventAggression() {
        Player player = addPlayer(ORIGIN);
        when(npc.getCombat().isWithinLeash(player)).thenReturn(false);

        aggression.process();

        verify(npc.getCombat(), never()).attack(player);
    }
}
