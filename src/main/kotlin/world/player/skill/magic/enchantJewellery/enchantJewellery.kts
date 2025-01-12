package world.player.skill.magic.enchantJewellery

import api.predef.*
import io.luna.game.event.impl.UseSpellEvent.MagicOnItemEvent

for (type in EnchantJewelleryType.ALL) {
    on(MagicOnItemEvent::class).filter { spellId == type.spellId }
        .then { plr.submitAction(EnchantJewelleryAction(plr, targetItemId, type)) }
}