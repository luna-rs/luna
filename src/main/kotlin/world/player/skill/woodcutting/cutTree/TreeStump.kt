package world.player.skill.woodcutting.cutTree

/**
 * A class representing a tree stump.
 */
class TreeStump(val tree: Tree, val treeId: Int, val stumpId: Int) {

    companion object {

        /**
         * The default stump.
         */
        val DEFAULT_STUMP = 1341

        /**
         * The default oak stump.
         */
        val DEFAULT_OAK_STUMP = 1341

        /**
         * The default willow stump.
         */
        val DEFAULT_WILLOW_STUMP = 5554

        /**
         * The default maple stump.
         */
        val DEFAULT_MAPLE_STUMP = -1

        /**
         * The default maple stump.
         */
        val DEFAULT_YEW_STUMP = -1

        /**
         * The default magic stump.
         */
        val DEFAULT_MAGIC_STUMP = 1341

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
        NORMAL(tree = Tree.NORMAL, // 1343 orange, 1345 dark
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
                           3879 to DEFAULT_STUMP,
                           3881 to DEFAULT_STUMP,
                           3883 to DEFAULT_STUMP,
                           4060 to DEFAULT_STUMP,
                           5902 to DEFAULT_STUMP,
                           5903 to DEFAULT_STUMP,
                           5904 to DEFAULT_STUMP)),
        OAK(tree = Tree.OAK,
            all = mapOf(1281 to 1356,
                        3037 to DEFAULT_OAK_STUMP,
                        8467 to DEFAULT_OAK_STUMP)),
        WILLOW(tree = Tree.WILLOW,
               all = mapOf(1308 to 7399,
                           5551 to 5554,
                           5552 to 5554,
                           5553 to 5554,
                           8488 to DEFAULT_WILLOW_STUMP)), // farming stump,
        MAPLE(tree = Tree.MAPLE,
              all = mapOf(1307 to 1342,
                          4674 to 1342,
                          8435 to DEFAULT_MAPLE_STUMP,
                          8436 to DEFAULT_MAPLE_STUMP,
                          8444 to DEFAULT_MAPLE_STUMP)),
        YEW(tree = Tree.YEW,
            all = mapOf(1309 to 7402,
                        8503 to DEFAULT_YEW_STUMP,
                        8504 to DEFAULT_YEW_STUMP,
                        8505 to DEFAULT_YEW_STUMP,
                        8506 to DEFAULT_YEW_STUMP,
                        8507 to DEFAULT_YEW_STUMP,
                        8508 to DEFAULT_YEW_STUMP,
                        8509 to DEFAULT_YEW_STUMP,
                        8510 to DEFAULT_YEW_STUMP,
                        8511 to DEFAULT_YEW_STUMP,
                        8512 to DEFAULT_YEW_STUMP,
                        8513 to DEFAULT_YEW_STUMP)),
        MAGIC(tree = Tree.MAGIC,
              all = mapOf(1306 to DEFAULT_MAGIC_STUMP,
                          8396 to DEFAULT_MAGIC_STUMP,
                          8397 to DEFAULT_MAGIC_STUMP,
                          8398 to DEFAULT_MAGIC_STUMP,
                          8399 to DEFAULT_MAGIC_STUMP,
                          8400 to DEFAULT_MAGIC_STUMP,
                          8401 to DEFAULT_MAGIC_STUMP,
                          8402 to DEFAULT_MAGIC_STUMP,
                          8403 to DEFAULT_MAGIC_STUMP,
                          8404 to DEFAULT_MAGIC_STUMP,
                          8405 to DEFAULT_MAGIC_STUMP,
                          8406 to DEFAULT_MAGIC_STUMP,
                          8407 to DEFAULT_MAGIC_STUMP,
                          8408 to DEFAULT_MAGIC_STUMP,
                          840 to DEFAULT_MAGIC_STUMP)),
    }
}