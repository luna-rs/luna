package io.luna.game.event;

import io.luna.game.event.impl.SkillChangeEvent;
import io.luna.game.model.mob.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class SkillChangeEventTest {
    @Test
    void laterLevelUpDoesNotReclassifyEarlierXpEvent() {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.getSkills().getSkill(0).getStaticLevel()).thenReturn(10);
        SkillChangeEvent xpOnly = new SkillChangeEvent(player, 1200, 10, 10, 0);
        when(player.getSkills().getSkill(0).getStaticLevel()).thenReturn(11);
        SkillChangeEvent actualLevelUp = new SkillChangeEvent(player, 1300, 10, 10, 0);
        assertFalse(xpOnly.isLevelUp());
        assertTrue(actualLevelUp.isLevelUp());
    }

    @Test
    void laterSkillResetDoesNotEraseRecordedLevelUp() {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.getSkills().getSkill(0).getStaticLevel()).thenReturn(11);
        SkillChangeEvent event = new SkillChangeEvent(player, 1300, 10, 10, 0);
        when(player.getSkills().getSkill(0).getStaticLevel()).thenReturn(1);
        assertTrue(event.isLevelUp());
    }

    @Test
    void temporaryLevelChangesAndMaxLevelXpAreNotLevelUps() {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.getSkills().getSkill(0).getStaticLevel()).thenReturn(10);
        assertFalse(new SkillChangeEvent(player, 1200, 10, 15, 0).isLevelUp());
        when(player.getSkills().getSkill(0).getStaticLevel()).thenReturn(99);
        assertFalse(new SkillChangeEvent(player, 14000000, 99, 99, 0).isLevelUp());
    }
}
