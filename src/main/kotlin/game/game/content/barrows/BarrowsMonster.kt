package game.content.barrows

/**
 * Tunnel monsters and their combat-level contributions to the Barrows monster score.
 *
 * IDs and levels match Luna's revision 377 NPC dump and barrows.npc in Lost City's contribution
 * at 0e7d7a8291d837f48646b2a962f3c5e208e744ad. Ordinary rats, spiders and skeletons do not count.
 *
 * @author lare96
 */
enum class BarrowsMonster(val npcId: Int, val combatLevel: Int) {
    BLOODWORM(2031, 52),
    CRYPT_RAT(2032, 43),
    GIANT_CRYPT_RAT(2033, 76),
    CRYPT_SPIDER(2034, 56),
    GIANT_CRYPT_SPIDER(2035, 79),
    UNARMED_SKELETON(2036, 77),
    ARMED_SKELETON(2037, 77)
}
