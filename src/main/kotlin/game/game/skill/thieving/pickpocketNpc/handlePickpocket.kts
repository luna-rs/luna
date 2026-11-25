package game.skill.thieving.pickpocketNpc

import api.predef.*
import io.luna.game.model.def.NpcDefinition

/**
 * The definition the player sees for an NPC spawned with [def]. NPCs that change with quest progress are spawned by
 * their parent id, and the client shows the first child while the quest varbit is 0.
 *
 * TODO Resolve the child from the player's varbit once quest progress is tracked (The Feud, Creature of Fenkenstrain,
 *  The Lost Tribe and The Hand in the Sand).
 */
fun shownDef(def: NpcDefinition): NpcDefinition? {
    val children = def.varpDef.childIdList
    if (children.isEmpty()) {
        return def
    }
    val child = children.first()
    return if (child in 0 until NpcDefinition.SIZE) NpcDefinition.ALL.get(child).orElse(null) else null
}

/* Add NPC interactions for all NPCs able to be stolen from. */
for (def in NpcDefinition.ALL) {
    if (def == null) {
        continue
    }
    val shown = shownDef(def)
    if (shown != null && shown.actions.contains("Pickpocket")) {
        val thievable = ThievingNpcType.forNpc(shown)
        if (thievable != null) {
            npc2(def.id(), false) {
                PickpocketAction.start(plr, targetNpc, thievable, shown)
            }
        }
    }
}
