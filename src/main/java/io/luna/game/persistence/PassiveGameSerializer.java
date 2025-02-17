package io.luna.game.persistence;

import io.luna.game.model.World;
import io.luna.game.model.mob.bot.BotSchedule;

import java.util.Map;
import java.util.Set;

/**
 * A {@link GameSerializer} implementation that is passive to all persistent data.
 *
 * @author lare96
 */
public final class PassiveGameSerializer extends GameSerializer {

    @Override
    public PlayerData loadPlayer(World world, String username) throws Exception {
        return null;
    }

    @Override
    public void savePlayer(World world, String username, PlayerData data) throws Exception {

    }

    @Override
    public boolean deletePlayer(World world, String username) throws Exception {
        return false;
    }

    @Override
    public Set<String> loadBotUsernames(World world) throws Exception {
        return Set.of();
    }

    @Override
    public Map<String, BotSchedule> synchronizeBotSchedules(World world) throws Exception {
        return Map.of();
    }

    @Override
    public boolean saveBotSchedule(World world, BotSchedule schedule) throws Exception {
        return false;
    }
}