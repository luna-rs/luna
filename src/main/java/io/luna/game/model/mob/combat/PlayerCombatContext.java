package io.luna.game.model.mob.combat;

import engine.combat.prayer.CombatPrayerSet;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.block.Animation.AnimationPriority;
import io.luna.game.model.mob.combat.CombatFormula.PhysicalType;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.interact.InteractionType;
import io.luna.game.model.mob.varp.PersistentVarp;

public final class PlayerCombatContext extends CombatContext {
    private final Player player;
    private final CombatPrayerSet prayers;
    private final CombatWeapon weapon;
    private final CombatSpecialBar specialBar;

    public PlayerCombatContext(Player player) {
        super(player);
        this.player = player;
        prayers = new CombatPrayerSet(player);
        weapon = new CombatWeapon(player);
        specialBar = new CombatSpecialBar(player, this);
    }

    @Override
    public int computeMaxHit(CombatDamageType type) {
        if (type == CombatDamageType.MAGIC) {
            // TODO Roll for currently selected spell, if not available than currently autocasted spell, if not
            //  available than throw exception.
            return CombatFormula.computeMagicalMaxHit(player);
        } else {
            return CombatFormula.computePhysicalMaxHit(player, type == CombatDamageType.RANGED ?
                    PhysicalType.RANGED : PhysicalType.MELEE);
        }
    }

    @Override
    public InteractionPolicy computeInteractionPolicy() {
        int range = weapon.getRange();
        return range < 2 ? new InteractionPolicy(InteractionType.SIZE, 1) :
                new InteractionPolicy(InteractionType.LINE_OF_SIGHT, range);
    }

    @Override
    public int computeAttackSpeed() {
        return weapon.getStyleDef().getSpeed();
    }

    @Override
    public Animation getAttackAnimation() {
        return new Animation(weapon.getStyleDef().getAnimation(), AnimationPriority.HIGH);
    }

    @Override
    public Animation getDefenceAnimation() {
        if (player.getEquipment().occupied(Equipment.SHIELD)) {
            return new Animation(1156);
        } else if (weapon.getType() == Weapon.STAFF) {
            return new Animation(420);
        } else if (weapon.getType() == Weapon.DAGGER) {
            return new Animation(403);
        }
        return new Animation(410);
    }

    @Override
    public boolean isAutoRetaliate() {
        return player.getVarpManager().getValue(PersistentVarp.AUTO_RETALIATE) == 1;
    }

    public EquipmentBonus getAttackStyleBonus() {
        return weapon.getStyleDef().getBonus();
    }

    public CombatPrayerSet getPrayers() {
        return prayers;
    }

    public CombatWeapon getWeapon() {
        return weapon;
    }

    public CombatSpecialBar getSpecialBar() {
        return specialBar;
    }
}
