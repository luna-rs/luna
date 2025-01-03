package world.player.wilderness.obelisk

import io.luna.game.model.Area
import io.luna.game.model.Position

/**
 * An enumerated type representing wilderness obelisks.
 */
enum class Obelisk(val id: Int, val teleportTo: Position, val teleportFrom: Area, val objectPositions: List<Position>) {
    LEVEL_13(id = 14829,
             teleportTo = Position(3156, 3620),
             teleportFrom = Area.of(3154, 3618, 3158, 3622),
             objectPositions = listOf(Position(3154, 3618), Position(3158, 3618), Position(3154, 3622), Position(3158, 3622))),
    LEVEL_19(id = 14830,
             teleportTo = Position(3227, 3667),
             teleportFrom = Area.of(3225, 3665, 3229, 3669),
             objectPositions = listOf(Position(3225, 3665), Position(3229, 3665), Position(3225, 3669), Position(3229, 3669))),
    LEVEL_27(id = 14827,
             teleportTo = Position(3035, 3732),
             teleportFrom = Area.of(3033, 3730, 3037, 3733),
             objectPositions = listOf(Position(3033, 3730), Position(3037, 3730), Position(3033, 3734), Position(3037, 3734))),
    LEVEL_35(id = 14828,
             teleportTo = Position(3106, 3794),
             teleportFrom = Area.of(3104, 3792, 3108, 3796),
             objectPositions = listOf(Position(3104, 3792), Position(3108, 3792), Position(3104, 3796), Position(3108, 3796))),
    LEVEL_44(id = 14826,
             teleportTo = Position(2980, 3866),
             teleportFrom = Area.of(2978, 3864, 2982, 3868),
             objectPositions = listOf(Position(2978, 3864), Position(2982, 3864), Position(2978, 3868), Position(2982, 3868))),
    LEVEL_50(id = 14831,
             teleportTo = Position(3307, 3916),
             teleportFrom = Area.of(3306, 3914, 3310, 3918),
             objectPositions = listOf(Position(3305, 3914), Position(3309, 3914), Position(3305, 3918), Position(3309, 3918)));

    companion object {

        /**
         * A set of all obelisks in this enumeration.
         */
        val ALL: Set<Obelisk> = HashSet(values().toList())
    }
}