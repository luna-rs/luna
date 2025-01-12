package world.player.skill.magic.enchantJewellery

import api.predef.*
import io.luna.game.event.impl.UseSpellEvent.MagicOnItemEvent

/* Add all interactions for enchanting spells. */
for (type in EnchantJewelleryType.ALL) {
    on(MagicOnItemEvent::class).filter { spellId == type.spellId }
        .then { plr.submitAction(EnchantJewelleryAction(plr, targetItemIndex, type)) }
}