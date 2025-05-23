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
                           1277 to DEFAULT_STUMP,
                           1278 to 1342,
                           1279 to DEFAULT_STUMP,
                           1280 to DEFAULT_STUMP,
                           1282 to 1347,
                           1283 to 1347,
                           1284 to 1350,
                           1285 to DEFAULT_STUMP,
                           1286 to 1352,
                           1287 to DEFAULT_STUMP,
                           1288 to DEFAULT_STUMP,
                           1289 to 1352,
                           1290 to DEFAULT_STUMP,
                           1291 to 1352,
                           1301 to DEFAULT_STUMP,
                           1303 to DEFAULT_STUMP,
                           1304 to DEFAULT_STUMP,
                           1305 to DEFAULT_STUMP,
                           1318 to 1355,
                           1319 to 1355,
                           1315 to 1342,
                           1316 to 1355,
                           1330 to 1355,
                           1331 to 1355,
                           1332 to 1355,
                           1333 to DEFAULT_STUMP,
                           1383 to DEFAULT_STUMP,
                           1384 to 1352,
                           2409 to DEFAULT_STUMP,
                           2447 to DEFAULT_STUMP,
                           2448 to DEFAULT_STUMP,
                           3033 to DEFAULT_STUMP,
                           3034 to DEFAULT_STUMP,
                           3035 to DEFAULT_STUMP,
                           3036 to DEFAULT_STUMP,
                           3879 to DEFAULT_STUMP,
                           3881 to DEFAULT_STUMP,
                           3883 to DEFAULT_STUMP,
                           3893 to DEFAULT_STUMP,
                           3885 to DEFAULT_STUMP,
                           3886 to DEFAULT_STUMP,
                           3887 to DEFAULT_STUMP,
                           3888 to DEFAULT_STUMP,
                           3892 to DEFAULT_STUMP,
                           3889 to DEFAULT_STUMP,
                           3890 to DEFAULT_STUMP,
                           3891 to DEFAULT_STUMP,
                           3928 to DEFAULT_STUMP,
                           3967 to DEFAULT_STUMP,
                           3968 to DEFAULT_STUMP,
                           4048 to DEFAULT_STUMP,
                           4049 to DEFAULT_STUMP,
                           4050 to DEFAULT_STUMP,
                           4051 to DEFAULT_STUMP,
                           4052 to DEFAULT_STUMP,
                           4053 to DEFAULT_STUMP,
                           4054 to DEFAULT_STUMP,
                           4060 to DEFAULT_STUMP,
                           5004 to DEFAULT_STUMP,
                           5005 to DEFAULT_STUMP,
                           5045 to DEFAULT_STUMP,
                           5902 to DEFAULT_STUMP,
                           5903 to DEFAULT_STUMP,
                           5904 to DEFAULT_STUMP,
                           8973 to DEFAULT_STUMP,
                           8974 to DEFAULT_STUMP)),
        OAK(tree = Tree.OAK,
            all = mapOf(1281 to 1356,
                        3037 to DEFAULT_STUMP,
                        8462 to DEFAULT_STUMP,
                        8463 to DEFAULT_STUMP,
                        8464 to DEFAULT_STUMP,
                        8465 to DEFAULT_STUMP,
                        8466 to DEFAULT_STUMP,
                        8467 to DEFAULT_STUMP,
                        10083 to DEFAULT_STUMP,
                        13413 to DEFAULT_STUMP,
                        13420 to DEFAULT_STUMP)),
        WILLOW(tree = Tree.WILLOW,
               all = mapOf(1308 to 7399,
                           5551 to 5554,
                           5552 to 5554,
                           5553 to 5554,
                           8481 to DEFAULT_STUMP,
                           8482 to DEFAULT_STUMP,
                           8483 to DEFAULT_STUMP,
                           8484 to DEFAULT_STUMP,
                           8485 to DEFAULT_STUMP,
                           8486 to DEFAULT_STUMP,
                           8487 to DEFAULT_STUMP,
                           8488 to DEFAULT_STUMP,
                           8496 to DEFAULT_STUMP,
                           8497 to DEFAULT_STUMP,
                           8498 to DEFAULT_STUMP,
                           8499 to DEFAULT_STUMP,
                           8500 to DEFAULT_STUMP,
                           8501 to DEFAULT_STUMP)),
        MAPLE(tree = Tree.MAPLE,
              all = mapOf(1307 to 1342,
                          4674 to 1342,
                          8435 to DEFAULT_STUMP,
                          8436 to DEFAULT_STUMP,
                          8437 to DEFAULT_STUMP,
                          8438 to DEFAULT_STUMP,
                          8439 to DEFAULT_STUMP,
                          8440 to DEFAULT_STUMP,
                          8441 to DEFAULT_STUMP,
                          8442 to DEFAULT_STUMP,
                          8443 to DEFAULT_STUMP,
                          8444 to DEFAULT_STUMP,
                          8454 to DEFAULT_STUMP,
                          8455 to DEFAULT_STUMP,
                          8456 to DEFAULT_STUMP,
                          8457 to DEFAULT_STUMP,
                          8458 to DEFAULT_STUMP,
                          8459 to DEFAULT_STUMP,
                          8460 to DEFAULT_STUMP,
                          8461 to DEFAULT_STUMP)),
        YEW(tree = Tree.YEW,
            all = mapOf(1309 to 7402,
                        8503 to DEFAULT_STUMP,
                        8504 to DEFAULT_STUMP,
                        8505 to DEFAULT_STUMP,
                        8506 to DEFAULT_STUMP,
                        8507 to DEFAULT_STUMP,
                        8508 to DEFAULT_STUMP,
                        8509 to DEFAULT_STUMP,
                        8510 to DEFAULT_STUMP,
                        8511 to DEFAULT_STUMP,
                        8512 to DEFAULT_STUMP,
                        8513 to DEFAULT_STUMP)),
        MAGIC(tree = Tree.MAGIC,
              all = mapOf(1306 to DEFAULT_STUMP,
                          8396 to DEFAULT_STUMP,
                          8397 to DEFAULT_STUMP,
                          8398 to DEFAULT_STUMP,
                          8399 to DEFAULT_STUMP,
                          8400 to DEFAULT_STUMP,
                          8401 to DEFAULT_STUMP,
                          8402 to DEFAULT_STUMP,
                          8403 to DEFAULT_STUMP,
                          8404 to DEFAULT_STUMP,
                          8405 to DEFAULT_STUMP,
                          8406 to DEFAULT_STUMP,
                          8407 to DEFAULT_STUMP,
                          8408 to DEFAULT_STUMP,
                          840 to DEFAULT_STUMP)),
    }
}