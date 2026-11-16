package game.item.consumable.potion

import api.event.Matcher
import game.player.item.consume.potion.PotionDecanting
import game.player.item.consume.potion.PotionFamily
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.mob.interact.InteractionPolicy

val decantMatcher = Matcher.get<ItemOnItemEvent, Pair<Int, Int>>()

// Register each ordered pair once, including identical bottle ids in different inventory slots.
PotionFamily.ALL.forEach { family ->
    val partials = family.ids.take(3)
    partials.forEach { usedId ->
        partials.forEach { targetId ->
            decantMatcher.set(usedId to targetId, { PotionDecanting.combine(this) },
                InteractionPolicy.UNSPECIFIED_BIF)
        }
    }
}
