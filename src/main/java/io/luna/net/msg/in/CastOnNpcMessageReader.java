package io.luna.net.msg.in;

import io.luna.game.action.InteractionAction;
import io.luna.game.event.Event;
import io.luna.game.event.impl.CastOnNpcEvent;
import io.luna.game.event.impl.CastOnPlayerEvent;
import io.luna.game.model.mob.MobList;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import java.util.Optional;

/**
 * A {@link GameMessageReader} that generates and submits a {@link CastOnNpcEvent} when a player attempts to cast
 * any spell onto an npc.
 *
 * @author searledan
 * @see io.luna.game.action.ActionManager#submit
 */
public final class CastOnNpcMessageReader extends GameMessageReader {

    @Override
    public Event read(Player caster, GameMessage msg) {
        int targetId = msg.getPayload().getShort(false, ValueType.ADD, ByteOrder.LITTLE);
        int spellId = msg.getPayload().getShort(false, ValueType.ADD);

        Optional<Npc> targetNpc = findTarget(caster, targetId);
        if (targetNpc.isPresent()) {
            Npc target = targetNpc.get();
            submitEventFor(target, new CastOnNpcEvent(caster, spellId, target));
        }
        return null;
    }

    /**
     * Searches for the target npc within the caster's {@link io.luna.game.model.World} instance.
     *
     * @param caster   The player casting the spell
     * @param targetId The target npc id to search for.
     */
    private Optional<Npc> findTarget(Player caster, int targetId) {
        MobList<Npc> npcs = caster.getWorld().getNpcs();
        return npcs.retrieve(targetId);
    }

    private void submitEventFor(Npc target, CastOnNpcEvent event) {
        Player caster = event.getPlr();
        caster.submitAction(new InteractionAction(caster, target) {
            @Override
            public void execute() {
                caster.getPlugins().post(event);
            }
        });
    }
}
