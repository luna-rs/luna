package game.obj.wildernessObelisk

import com.google.common.collect.ImmutableSet
import io.luna.game.model.Position
import io.luna.game.model.area.Area

/**
 * An enumerated type representing wilderness obelisks.
 *
 * @author lare96
 */
enum class Obelisk(val id: Int, val anchor: Position, val bounds: Area, val objectPositions: List<Position>) {
    LEVEL_13(id = 14829,
             anchor = Position(3156, 3620),
             bounds = Area.of(3154, 3618, 3158, 3622),
             objectPositions = listOf(Position(3154, 3618), Position(3158, 3618), Position(3154, 3622), Position(3158, 3622))),
    LEVEL_19(id = 14830,
             anchor = Position(3227, 3667),
             bounds = Area.of(3225, 3665, 3229, 3669),
             objectPositions = listOf(Position(3225, 3665), Position(3229, 3665), Position(3225, 3669), Position(3229, 3669))),
    LEVEL_27(id = 14827,
             anchor = Position(3035, 3732),
             bounds = Area.of(3033, 3730, 3037, 3734),
             objectPositions = listOf(Position(3033, 3730), Position(3037, 3730), Position(3033, 3734), Position(3037, 3734))),
    LEVEL_35(id = 14828,
             anchor = Position(3106, 3794),
             bounds = Area.of(3104, 3792, 3108, 3796),
             objectPositions = listOf(Position(3104, 3792), Position(3108, 3792), Position(3104, 3796), Position(3108, 3796))),
    LEVEL_44(id = 14826,
             anchor = Position(2980, 3866),
             bounds = Area.of(2978, 3864, 2982, 3868),
             objectPositions = listOf(Position(2978, 3864), Position(2982, 3864), Position(2978, 3868), Position(2982, 3868))),
    LEVEL_50(id = 14831,
             anchor = Position(3307, 3916),
             bounds = Area.of(3306, 3914, 3310, 3918),
             objectPositions = listOf(Position(3305, 3914), Position(3309, 3914), Position(3305, 3918), Position(3309, 3918)));

    companion object {

        /**
         * A set of all obelisks in this enumeration.
         */
        val ALL: ImmutableSet<Obelisk> = ImmutableSet.copyOf(values())
    }
}