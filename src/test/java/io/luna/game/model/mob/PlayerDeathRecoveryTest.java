package io.luna.game.model.mob;

import io.luna.Luna;
import io.luna.game.model.Position;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.persistence.PlayerData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies recovery of saves captured before an in-memory death action completed.
 *
 * @author lare96
 */
final class PlayerDeathRecoveryTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void zeroHealthSaveRecoversWithoutReplayingItemLoss(boolean bot) throws Exception {
        Player player = player(bot);
        PlayerData data = savedHealth(player, 0);

        player.loadData(data);

        assertTrue(player.getHealth() > 0);
        assertEquals(player.skill(Skill.HITPOINTS).getStaticLevel(), player.getHealth());
        verify(player).setSkullIcon(SkullIcon.NONE);
        verify(player).setPosition(Luna.settings().game().startingPosition());
        verify(player, never()).submitAction(any());
        verifyNoInteractions(player.getInventory(), player.getEquipment());

        // A second load of the repaired state must not trigger another recovery.
        clearInvocations(player);
        player.loadData(savedHealth(player, player.getHealth()));
        verify(player, never()).setPosition(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void livingSaveKeepsReducedHealthAndPosition(boolean bot) throws Exception {
        Player player = player(bot);

        player.loadData(savedHealth(player, 3));

        assertEquals(3, player.getHealth());
        verify(player, never()).setPosition(any(Position.class));
        verify(player, never()).setSkullIcon(any());
        verify(player, never()).submitAction(any());
    }

    private Player player(boolean bot) throws Exception {
        Player player = bot ? mock(Bot.class, RETURNS_DEEP_STUBS) : mock(Player.class, RETURNS_DEEP_STUBS);
        SkillSet skills = new SkillSet(player);
        var field = Mob.class.getDeclaredField("skills");
        field.setAccessible(true);
        field.set(player, skills);
        when(player.skill(anyInt())).thenAnswer(call -> skills.getSkill(call.getArgument(0)));
        when(player.getHealth()).thenCallRealMethod();
        doCallRealMethod().when(player).loadData(any());
        return player;
    }

    private PlayerData savedHealth(Player player, int health) {
        PlayerData data = mock(PlayerData.class);
        doAnswer(call -> {
            // Bulk skill loading bypasses Mob.setHealth(), so there is no death action.
            player.skill(Skill.HITPOINTS).setLevel(health);
            return null;
        }).when(data).load(player);
        return data;
    }
}
