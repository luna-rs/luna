package game.skill.fletching.darts

import api.predef.*

// One batch per use: there is no dart-making menu.
for (dart in Dart.VALUES) {
    useItem(Dart.FEATHER).onItem(dart.tip) { DartMaking.make(plr, dart) }
}
