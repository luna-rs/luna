package io.luna.util.parser.impl;

import io.luna.game.model.mob.combat.state.NpcCombatContext;
import io.luna.util.GsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;

/**
 * Parses the npc boss id definition file and loads the resulting ids into {@link NpcCombatContext}.
 *
 * @author lare96
 */
public final class BossFileParser implements Runnable {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    @Override
    public void run() {
        try {
            Path file = Paths.get("data", "game", "def", "npcs", "bosses.jsonc");
            NpcCombatContext.setBosses(GsonUtils.readAsType(file, HashSet.class));
        } catch (IOException e) {
            logger.catching(e);
        }
    }
}