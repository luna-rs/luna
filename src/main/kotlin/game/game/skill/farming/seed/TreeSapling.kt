package game.skill.farming.seed

import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.item.Item

/**
 * Data for the saplings planted in tree patches, and the trees they grow into. [seedId] is the sapling's item id, not
 * the tree seed (such as an acorn).
 *
 * The tree is drawn as its [configIndexOffset] plus its stage minus one, so the seedling is drawn as [configIndexOffset]
 * itself. [stages] is the fully grown stage, where the tree is waiting to have its health checked. [plantXp] is given
 * when the sapling is planted and [harvestXp] when the tree's health is checked. [produce] is the logs the tree gives
 * when chopped, which uses [tree] for the Woodcutting level, experience and regrowth time.
 *
 * The [baseDiseaseChance] values of oak, willow and yew are unchecked placeholders, the wiki only gives maple and
 * magic.
 *
 * @author hydrozoa
 */
enum class TreeSapling(override val seedId: Int,
                       override val level: Int,
                       val configIndexOffset: Int,
                       override val stages: Int,
                       override val plantXp: Double,
                       override val harvestXp: Double,
                       override val baseDiseaseChance: Int,
                       val roots: Int,
                       val tree: Tree) : Crop {

    OAK(seedId = 5370,
        level = 15,
        configIndexOffset = 8,
        stages = 5,
        plantXp = 14.0,
        harvestXp = 467.3,
        baseDiseaseChance = 13,
        roots = 6043,
        tree = Tree.OAK),
    WILLOW(seedId = 5371,
           level = 30,
           configIndexOffset = 15,
           stages = 7,
           plantXp = 25.0,
           harvestXp = 1456.5,
           baseDiseaseChance = 13,
           roots = 6045,
           tree = Tree.WILLOW),
    MAPLE(seedId = 5372,
          level = 45,
          configIndexOffset = 24,
          stages = 9,
          plantXp = 45.0,
          harvestXp = 3403.4,
          baseDiseaseChance = 13,
          roots = 6047,
          tree = Tree.MAPLE),
    YEW(seedId = 5373,
        level = 60,
        configIndexOffset = 35,
        stages = 11,
        plantXp = 81.0,
        harvestXp = 7069.9,
        baseDiseaseChance = 13,
        roots = 6049,
        tree = Tree.YEW),
    MAGIC(seedId = 5374,
          level = 75,
          configIndexOffset = 48,
          stages = 13,
          plantXp = 145.5,
          harvestXp = 13768.3,
          baseDiseaseChance = 9,
          roots = 6051,
          tree = Tree.MAGIC);

    override val produce = Item(tree.logId)

    companion object {
        private val saplings = values().associateBy(TreeSapling::seedId)
        fun lookup(saplingId: Int): TreeSapling? = saplings[saplingId]
    }
}
