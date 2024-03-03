package world.npc.death.dropItems

import api.predef.*
import io.luna.game.event.impl.NpcDeathEvent

on(NpcDeathEvent::class) {
    // TODO Npc Drops.
}

// TODO Generialized drop tables should be done in Java for all monsters, a best estimation or even custom made tiers
// of loot. Then an option to "override" the drop table with specific conditions related to how the mob died, etc. eg.
// if(player.completedxyz) {
//  table.add/remove/replace
//  table.adddynamic/addstatic
// }
// etc etc still need to figure out exactly how the drop table system will work
// And once the table is modified, the modified table will be passed back into java to be droppped
