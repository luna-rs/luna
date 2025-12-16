package io.luna.game.persistence;

import io.luna.game.GameService;
import io.luna.game.LogoutService;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.script.BotScriptSnapshot;

import java.util.List;

/**
 * A model acting as a proxy for {@link Bot} save data. It primarily ensures thread safety for interactions between
 * {@link GameService} and {@link LogoutService}. All fields in this class intended for serialization should be public.
 *
 * @author lare96
 */
public final class BotData extends PlayerData {

    /**
     * The script snapshots to load.
     */
    public List<BotScriptSnapshot<?>> scripts;

    /**
     * Creates a new {@link BotData}.
     *
     * @param username The username of the bot this data belongs to.
     */
    public BotData(String username) {
        super(username);
    }

    @Override
    public void load(Player player) {
        super.load(player);
        Bot bot = (Bot) player;
        bot.getScriptStack().load(scripts);
    }

    @Override
    public PlayerData save(Player player) {
        super.save(player);
        Bot bot = (Bot) player;
        scripts = bot.getScriptStack().save();
        return this;
    }
}
