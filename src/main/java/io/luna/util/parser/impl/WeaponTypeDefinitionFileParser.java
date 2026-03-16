package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.game.model.def.WeaponSpecialBarDefinition;
import io.luna.game.model.def.WeaponStyleDefinition;
import io.luna.game.model.def.WeaponTypeDefinition;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.combat.CombatStance;
import io.luna.game.model.mob.combat.CombatStyle;
import io.luna.game.model.mob.combat.Weapon;
import io.luna.util.GsonUtils;
import io.luna.util.parser.JsonFileParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A {@link JsonFileParser} that loads {@link WeaponTypeDefinition} instances from the weapon type
 * definition file.
 * <p>
 * Each entry defines a {@link Weapon} type, interface metadata, available {@link WeaponStyleDefinition}
 * entries, and optional {@link WeaponSpecialBarDefinition} data.
 *
 * @author lare96
 */
public final class WeaponTypeDefinitionFileParser extends JsonFileParser<WeaponTypeDefinition> {

    /**
     * The logger used to report weapon type definition loading progress.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Creates a new {@link WeaponTypeDefinitionFileParser}.
     */
    public WeaponTypeDefinitionFileParser() {
        super(Paths.get("data", "game", "def", "equipment", "weapon_types.json"));
    }

    @Override
    public WeaponTypeDefinition convert(JsonObject token) {
        Weapon weaponType = Weapon.valueOf(token.get("type").getAsString());
        int id = token.get("id").getAsInt();
        int line = token.get("line").getAsInt();
        JsonArray jsonStyles = token.get("styles").getAsJsonArray();
        List<WeaponStyleDefinition> styles = new ArrayList<>(jsonStyles.size());
        for (JsonElement element : jsonStyles) {
            JsonObject obj = element.getAsJsonObject();
            CombatStyle styleType = CombatStyle.valueOf(obj.get("type").getAsString());
            int speed = obj.get("speed").getAsInt();
            int animation = obj.get("animation").getAsInt();
            int config = obj.get("config").getAsInt();
            EquipmentBonus bonus = EquipmentBonus.valueOf(obj.get("bonus").getAsString());
            int button = obj.has("button") ? obj.get("button").getAsInt() : -1;
            CombatStance stance = CombatStance.valueOf(obj.get("stance").getAsString());
            int range = obj.get("range").getAsInt();
            ImmutableList<Integer> exp = Arrays.stream(GsonUtils.getAsType(obj.get("exp"), String[].class)).
                    map(Skill::getId).collect(ImmutableList.toImmutableList());
            styles.add(new WeaponStyleDefinition(styleType, speed, animation, config, bonus, button, stance, range, exp));
        }
        WeaponSpecialBarDefinition specialBar = GsonUtils.getAsType(token.get("special"), WeaponSpecialBarDefinition.class);
        return new WeaponTypeDefinition(weaponType, id, line, styles, specialBar);
    }

    @Override
    public void onCompleted(ImmutableList<WeaponTypeDefinition> tokenObjects) {
        tokenObjects.forEach(WeaponTypeDefinition::addWeaponType);
        WeaponTypeDefinition.lock();
        logger.debug("Loaded {} weapon type definitions!", box(tokenObjects.size()));
    }
}