package game.content.barrows

import game.item.teleport.SpadeDigSpot
import io.luna.game.model.Position

/**
 * Brother identities and client graphics from the revision 377 npc and spotanim packs.
 *
 * Sources: LostCityRS/Content at 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, pack/npc.pack and
 * pack/spotanim.pack. Digging destinations and bounds remain owned by [SpadeDigSpot]. A graphic ID
 * identifies an asset only; the reconstructed behavior is implemented by [BarrowsCombat].
 *
 * @author lare96
 */
enum class BarrowsBrother(val npcId: Int,
                          val killed: BarrowsVarbit,
                          val digSpot: SpadeDigSpot,
                          val effectGraphicId: Int?,
                          val sarcophagusId: Int,
                          val tunnelExit: Position,
                          val combatLevel: Int) {
    AHRIM(2025, BarrowsVarbit.AHRIM_KILLED, SpadeDigSpot.AHRIM_MOUND, 400, 6821, Position(3555, 9697, 3), 98),
    DHAROK(2026, BarrowsVarbit.DHAROK_KILLED, SpadeDigSpot.DHAROK_MOUND, null, 6771, Position(3557, 9714, 3), 115),
    GUTHAN(2027, BarrowsVarbit.GUTHAN_KILLED, SpadeDigSpot.GUTHAN_MOUND, 398, 6773, Position(3537, 9703, 3), 115),
    KARIL(2028, BarrowsVarbit.KARIL_KILLED, SpadeDigSpot.KARIL_MOUND, 401, 6822, Position(3550, 9681, 3), 98),
    TORAG(2029, BarrowsVarbit.TORAG_KILLED, SpadeDigSpot.TORAG_MOUND, 399, 6772, Position(3569, 9684, 3), 115),
    VERAC(2030, BarrowsVarbit.VERAC_KILLED, SpadeDigSpot.VERAC_MOUND, null, 6823, Position(3573, 9704, 3), 115);
}
