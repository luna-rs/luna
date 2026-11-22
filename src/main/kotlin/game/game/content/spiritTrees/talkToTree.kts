package game.content.spiritTrees

import api.predef.*
import game.content.spiritTrees.SpiritTree.BATTLEFIELD_OF_KHAZARD
import game.content.spiritTrees.SpiritTree.GNOME_STRONGHOLD
import game.content.spiritTrees.SpiritTree.TREE_GNOME_VILLAGE
import game.content.spiritTrees.SpiritTree.VARROCK

object1(SpiritTrees.STRONGHOLD_TREE) {
    // TODO The Grand Tree: until it's complete, the tree only says "The tree doesn't feel like talking."
    SpiritTrees.talkToOldTree(plr, listOf(TREE_GNOME_VILLAGE, VARROCK, BATTLEFIELD_OF_KHAZARD))
}

object1(SpiritTrees.VILLAGE_TREE) {
    // TODO Tree Gnome Village: until it's complete, the tree only says "The tree doesn't feel like talking."
    // TODO The Grand Tree: the trees only send players into the Gnome Stronghold once it's complete.
    SpiritTrees.talkToOldTree(plr, listOf(BATTLEFIELD_OF_KHAZARD, VARROCK, GNOME_STRONGHOLD))
}

object1(SpiritTrees.YOUNG_TREE) {
    // TODO Tree Gnome Village: until it's complete, the tree only says "The tree doesn't feel like talking."
    SpiritTrees.talkToYoungTree(plr)
}
