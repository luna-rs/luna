package io.luna.game.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author notjuanortiz
 */
class DirectionTest {

    @ParameterizedTest
    @MethodSource("createStepsWithResult")
    void testDirectionBetweenSteps(int step1X, int step1Y, int step2X, int step2Y, Direction expected) {
        Direction actual = Direction.between(step1X, step1Y, step2X, step2Y);
        assertEquals(expected, actual);
    }

    private static Stream<Arguments> createStepsWithResult() {
        return Stream.of(
                Arguments.of(0, 0, 0, 0, Direction.NONE),
                Arguments.of(0, 0, 0, 1, Direction.NORTH),
                Arguments.of(0, 0, 1, 0, Direction.EAST),
                Arguments.of(0, 0, 1, 1, Direction.NORTH_EAST),
                Arguments.of(0, 1, 0, 0, Direction.SOUTH),
                Arguments.of(0, 1, 0, 1, Direction.NONE),
                Arguments.of(0, 1, 1, 0, Direction.SOUTH_EAST),
                Arguments.of(0, 1, 1, 1, Direction.EAST),
                Arguments.of(1, 0, 0, 0, Direction.WEST),
                Arguments.of(1, 0, 0, 1, Direction.NORTH_WEST),
                Arguments.of(1, 0, 1, 0, Direction.NONE),
                Arguments.of(1, 0, 1, 1, Direction.NORTH),
                Arguments.of(1, 1, 0, 0, Direction.SOUTH_WEST),
                Arguments.of(1, 1, 0, 1, Direction.WEST),
                Arguments.of(1, 1, 1, 0, Direction.SOUTH),
                Arguments.of(1, 1, 1, 1, Direction.NONE));
    }
}
