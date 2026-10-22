package game.content.barrows

/**
 * Paired door locations and lock fields from the 377 loc pack and Barrows catacombs script.
 * A set lock bit means the corridor is unavailable. Both leaves share the same lock.
 *
 * @author lare96
 */
enum class BarrowsDoor(val rightId: Int, val leftId: Int, val locked: BarrowsVarbit) {
    A(6716, 6735, BarrowsVarbit.DOOR_A),
    B(6717, 6736, BarrowsVarbit.DOOR_B),
    C(6718, 6737, BarrowsVarbit.DOOR_C),
    D(6719, 6738, BarrowsVarbit.DOOR_D),
    E(6720, 6739, BarrowsVarbit.DOOR_E),
    F(6721, 6740, BarrowsVarbit.DOOR_F),
    G(6722, 6741, BarrowsVarbit.DOOR_G),
    H(6723, 6742, BarrowsVarbit.DOOR_H),
    I(6724, 6743, BarrowsVarbit.DOOR_I),
    J(6725, 6744, BarrowsVarbit.DOOR_J),
    K(6726, 6745, BarrowsVarbit.DOOR_K),
    L(6727, 6746, BarrowsVarbit.DOOR_L),
    M(6728, 6747, BarrowsVarbit.DOOR_M),
    N(6729, 6748, BarrowsVarbit.DOOR_N),
    O(6730, 6749, BarrowsVarbit.DOOR_O),
    P(6731, 6750, BarrowsVarbit.DOOR_P);

    val leadsToChest: Boolean get() = this == E || this == I || this == J || this == L
}
