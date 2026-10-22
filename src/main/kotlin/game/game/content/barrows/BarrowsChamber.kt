package game.content.barrows

import io.luna.game.model.Position

/**
 * Possible entrance chambers, ladder locations, arrival anchors and adjoining corridors.
 * Coordinates follow barrows_catacombs.rs2 at 0e7d7a8291d837f48646b2a962f3c5e208e744ad.
 *
 * @author lare96
 */
enum class BarrowsChamber(val enabled: BarrowsVarbit, val ladderId: Int,
                          val arrival: Position, val doors: List<BarrowsDoor>) {
    A(BarrowsVarbit.CHAMBER_A, 6709, Position(3537, 9711),
      listOf(BarrowsDoor.A, BarrowsDoor.B, BarrowsDoor.C, BarrowsDoor.D)),
    C(BarrowsVarbit.CHAMBER_C, 6710, Position(3568, 9711),
      listOf(BarrowsDoor.A, BarrowsDoor.F, BarrowsDoor.G, BarrowsDoor.H)),
    G(BarrowsVarbit.CHAMBER_G, 6711, Position(3534, 9677),
      listOf(BarrowsDoor.B, BarrowsDoor.K, BarrowsDoor.N, BarrowsDoor.P)),
    I(BarrowsVarbit.CHAMBER_I, 6712, Position(3568, 9677),
      listOf(BarrowsDoor.H, BarrowsDoor.M, BarrowsDoor.O, BarrowsDoor.P))
}
