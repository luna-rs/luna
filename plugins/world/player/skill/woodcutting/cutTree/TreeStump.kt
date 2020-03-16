package world.player.skill.woodcutting.cutTree

/**
 * A class representing a tree stump.
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
            for(stump in Stump.values()) {
                stumpList.addAll(buildStumpList(stump.tree, stump.all))
            }
            ALL_STUMPS = stumpList
            TREE_ID_MAP = stumpList.associateBy { it.treeId }
        }

        private fun buildStumpList(tree: Tree, stumpMap: Map<Int, Int>) =
            stumpMap.map { TreeStump(tree, it.key, it.value) }.toList()
    }


    enum class Stump(val tree: Tree, val all: Map<Int, Int>) {
        // Alive tree ID -> Dead tree ID.
        NORMAL(tree = Tree.NORMAL,
               all = mapOf(1276 to 1342, 1277 to 1341, 1278 to 1342, 1279 to 1341, 1280 to 1341, 1282 to 1347, 1283 to 1347,
                           1284 to 1350, 1285 to 1341, 1286 to 1352, 1287 to 1341, 1288 to 1341, 1289 to 1352, 1290 to 1341,
                           1291 to 1352, 1301 to 1341, 1303 to 1341, 1304 to 1341, 1305 to 1341, 1318 to 1355, 1319 to 1355,
                           1315 to 1342, 1316 to 1355, 1330 to 1355, 1331 to 1355, 1332 to 1355, 1333 to 1341, 1383 to 1341,
                           1384 to 1352, 2409 to 1341, 2447 to 1341, 2448 to 1341, 3033 to 1341, 3034 to 1341, 3035 to 1341,
                           3036 to 1341, 3879 to 1341, 3881 to 1341, 3883 to 1341, 3893 to 1341, 3885 to 1341, 3886 to 1341,
                           3887 to 1341, 3888 to 1341, 3892 to 1341, 3889 to 1341, 3890 to 1341, 3891 to 1341, 3928 to 1341,
                           3967 to 1341, 3968 to 1341, 4048 to 1341, 4049 to 1341, 4050 to 1341, 4051 to 1341, 4052 to 1341,
                           4053 to 1341, 4054 to 1341, 4060 to 1341, 5004 to 1341, 5005 to 1341, 5045 to 1341, 5902 to 1341,
                           5903 to 1341, 5904 to 1341, 8973 to 1341, 8974 to 1341)),
        OAK(tree = Tree.OAK,
            all = mapOf(1281 to 1356, 3037 to 1341, 8462 to 1341, 8463 to 1341, 8464 to 1341, 8465 to 1341, 8466 to 1341,
                        8467 to 1341, 10083 to 1341, 13413 to 1341, 13420 to 1341)),
        WILLOW(tree = Tree.WILLOW,
               all = mapOf(1308 to 7399, 5551 to 5554, 5552 to 5554, 5553 to 5554, 8481 to 1341, 8482 to 1341, 8483 to 1341,
                           8484 to 1341, 8485 to 1341, 8486 to 1341, 8487 to 1341, 8488 to 1341, 8496 to 1341, 8497 to 1341,
                           8498 to 1341, 8499 to 1341, 8500 to 1341, 8501 to 1341)),
        MAPLE(tree = Tree.MAPLE,
              all = mapOf(1307 to 1342, 4674 to 1342, 8435 to 1341, 8436 to 1341, 8437 to 1341, 8438 to 1341, 8439 to 1341,
                          8440 to 1341, 8441 to 1341, 8442 to 1341, 8443 to 1341, 8444 to 1341, 8454 to 1341, 8455 to 1341,
                          8456 to 1341, 8457 to 1341, 8458 to 1341, 8459 to 1341, 8460 to 1341, 8461 to 1341)),
        YEW(tree = Tree.YEW,
            all = mapOf(1309 to 7402, 8503 to 1341, 8504 to 1341, 8505 to 1341, 8506 to 1341, 8507 to 1341, 8508 to 1341,
                        8509 to 1341, 8510 to 1341, 8511 to 1341, 8512 to 1341, 8513 to 1341)),
        MAGIC(tree = Tree.MAGIC,
              all = mapOf(1306 to 1341, 8396 to 1341, 8397 to 1341, 8398 to 1341, 8399 to 1341, 8400 to 1341, 8401 to 1341,
                          8402 to 1341, 8403 to 1341, 8404 to 1341, 8405 to 1341, 8406 to 1341, 8407 to 1341, 8408 to 1341,
                          840 to 1341)),
    }
}