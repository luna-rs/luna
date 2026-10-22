package game.content.barrows

import game.item.teleport.SpadeDigSpot

/**
 * Brother identities and client graphics from the revision 377 npc and spotanim packs.
 *
 * Sources: LostCityRS/Content at 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, pack/npc.pack and
 * pack/spotanim.pack. Digging destinations and bounds remain owned by [SpadeDigSpot]. A graphic ID
 * identifies an asset only; effect chance, target and timing still require combat implementation.
 *
 * @author lare96
 */
enum class BarrowsBrother(val npcId: Int,
                          val killed: BarrowsVarbit,
                          val digSpot: SpadeDigSpot,
                          val effectGraphicId: Int?) {
    AHRIM(2025, BarrowsVarbit.AHRIM_KILLED, SpadeDigSpot.AHRIM_MOUND, 400),
    DHAROK(2026, BarrowsVarbit.DHAROK_KILLED, SpadeDigSpot.DHAROK_MOUND, null),
    GUTHAN(2027, BarrowsVarbit.GUTHAN_KILLED, SpadeDigSpot.GUTHAN_MOUND, 398),
    KARIL(2028, BarrowsVarbit.KARIL_KILLED, SpadeDigSpot.KARIL_MOUND, 401),
    TORAG(2029, BarrowsVarbit.TORAG_KILLED, SpadeDigSpot.TORAG_MOUND, 399),
    VERAC(2030, BarrowsVarbit.VERAC_KILLED, SpadeDigSpot.VERAC_MOUND, null);
}
