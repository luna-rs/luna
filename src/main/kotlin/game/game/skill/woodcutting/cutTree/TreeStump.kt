package game.skill.woodcutting.cutTree

/**
 * A class representing a tree stump.
 *
 * @author lare96
 */
class TreeStump(val tree: Tree, val treeId: Int, val stumpId: Int) {

    companion object {

        /**
         * List of every single: alive tree ID -> tree stump ID mapping.
         */
        val ALL_STUMPS: List<TreeStump>

        /**
         * Alive tree ID -> TreeStump instance.
         */
        val TREE_ID_MAP: Map<Int, TreeStump>

        init {
            val stumpList = ArrayList<TreeStump>()
            for (stump in Stump.values()) {
                stumpList.addAll(buildStumpList(stump.tree, stump.all))
            }
            ALL_STUMPS = stumpList
            TREE_ID_MAP = stumpList.associateBy { it.treeId }
        }

        private fun buildStumpList(tree: Tree, stumpMap: Map<Int, Int>) =
            stumpMap.map { TreeStump(tree, it.key, it.value) }.toList()
    }

    /**
     * An enum representing all tree stumps for all tree types.
     */
    enum class Stump(val tree: Tree, val all: Map<Int, Int>) {
        // Alive tree ID -> Dead tree ID.
        NORMAL(tree = Tree.NORMAL,
               all = mapOf(1276 to 1342,
                           1277 to 1343,
                           1278 to 1342,
                           1279 to 1345,
                           1280 to 1343,
                           1282 to 1347,
                           1283 to 1347,
                           1284 to 1350,
                           1285 to 1349,
                           1286 to 1352,
                           1289 to 1352,
                           1290 to 1354,
                           1291 to 1352,
                           1318 to 1355,
                           1319 to 1355,
                           1315 to 1342,
                           1316 to 1355,
                           1330 to 1355,
                           1331 to 1355,
                           1332 to 1355,
                           1383 to 1341,
                           1384 to 1352,
                           2409 to 1342,
                           3033 to 1345,
                           3034 to 1345,
                           3035 to 1348,
                           3036 to 1345,
                           5902 to 1347,
                           5903 to 1353,
                           5904 to 1353)),
        OAK(tree = Tree.OAK,
            all = mapOf(1281 to 1356,
                        3037 to 1357,
                        8467 to 8468)),
        WILLOW(tree = Tree.WILLOW,
               all = mapOf(1308 to 7399,
                           5551 to 5554,
                           5552 to 5554,
                           5553 to 5554,
                           8488 to 8489)),
        MAPLE(tree = Tree.MAPLE,
              all = mapOf(1307 to 1342,
                          4674 to 1342,
                          8444 to 8445)),
        YEW(tree = Tree.YEW,
            all = mapOf(1309 to 7402,
                        8513 to 8514)),
        MAGIC(tree = Tree.MAGIC,
              all = mapOf(1306 to 7401,
                          8409 to 8410)),
    }
}