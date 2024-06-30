package io.luna.game.model.mob.controller;

import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.task.Task;

/**
 * A {@link Task} that runs the {@link PlayerController#process(Player, int)} method for all controllers.
 *
 * @author lare96
 */
public final class ControllerProcessTask extends Task {

    /**
     * The world instance.
     */
    private final World world;

    /**
     * Creates a new {@link ControllerProcessTask}.
     *
     * @param world The world instance.
     */
    public ControllerProcessTask(World world) {
        super(false, 1);
        this.world = world;
    }

    @Override
    protected void execute() {
        for (Player player : world.getPlayers()) {
            for (PlayerController controller : player.getControllers()) {
                controller.process(player, getExecutionCounter());
            }
        }
    }
}
