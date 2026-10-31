package game.skill.fletching.bolts

import api.predef.*

// One gem or pearl per use: there is no tip-making menu.
for (tip in BoltTip.VALUES) {
    useItem(BoltTip.CHISEL).onItem(tip.source) { BoltFletching.shapeTips(plr, tip) }
}

// One set per use: there is no bolt-making menu.
for (bolt in Bolt.VALUES) {
    useItem(Bolt.BOLTS).onItem(bolt.tip) { BoltFletching.attach(plr, bolt) }
}
