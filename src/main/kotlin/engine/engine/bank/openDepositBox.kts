package engine.bank

import api.predef.*

/**
 * A set of deposit boxes.
 */
val depositBoxObjects: Set<Int> = hashSetOf(9398)

for (id in depositBoxObjects) {
    object1(id) { plr.interfaces.open(DepositBoxInterface()) }
}