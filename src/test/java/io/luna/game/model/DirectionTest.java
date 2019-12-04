package io.luna.game.model;

import io.luna.game.model.mob.WalkingQueue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectionTest {

    private WalkingQueue.Step step1;
    private WalkingQueue.Step step2;

    private Direction actual; // the calculated direction

    @Test
    void whenStepIncreasesY_returnNorth() {
        step1 = new WalkingQueue.Step(0, 0);
        step2 = new WalkingQueue.Step(0, 1);

        // we're moving from step1 to step, where (0,0) is the origin
        actual = Direction.between(step1, step2);
        assertEquals(Direction.NORTH, actual);

        actual = Direction.between(step1.getX(), step1.getY(), step2.getX(), step2.getY());
        assertEquals(Direction.NORTH, actual);
    }

    @Test
    void whenStepDecreaseY_returnSouth() {
        step1 = new WalkingQueue.Step(0, 1);
        step2 = new WalkingQueue.Step(0, 0);

        actual = Direction.between(step1, step2);
        assertEquals(Direction.SOUTH, actual);

        actual = Direction.between(step1.getX(), step1.getY(), step2.getX(), step2.getY());
        assertEquals(Direction.SOUTH, actual);
    }

    @Test
    void whenStepIncreasesX_returnEast() {
        step1 = new WalkingQueue.Step(0, 0);
        step2 = new WalkingQueue.Step(1, 0);

        actual = Direction.between(step1, step2);
        assertEquals(Direction.EAST, actual);

        actual = Direction.between(step1.getX(), step1.getY(), step2.getX(), step2.getY());
        assertEquals(Direction.EAST, actual);
    }

    @Test
    void whenStepDecreasesX_returnWest() {
        step1 = new WalkingQueue.Step(1, 0);
        step2 = new WalkingQueue.Step(0, 0);

        actual = Direction.between(step1, step2);
        assertEquals(Direction.WEST, actual);

        actual = Direction.between(step1.getX(), step1.getY(), step2.getX(), step2.getY());
        assertEquals(Direction.WEST, actual);
    }
}