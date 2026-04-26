package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.game.model.def.WeaponAnimationDefinition;
import io.luna.game.model.def.WeaponModelDefinition;
import io.luna.game.model.mob.combat.CombatStyle;
import io.luna.util.GsonUtils;
import io.luna.util.parser.JsonFileParser;
import kotlin.Pair;

import java.nio.file.Paths;
import java.util.EnumMap;

/**
 * A {@link JsonFileParser} implementation that loads weapon animation definitions from the {@code weapon_animations.jsonc}
 * equipment definition file.
 *
 * @author lare96
 */
public final class WeaponAnimationDefinitionFileParser extends JsonFileParser<WeaponAnimationDefinition> {

    /**
     * Creates a new {@link WeaponAnimationDefinitionFileParser}.
     */
    public WeaponAnimationDefinitionFileParser() {
        super(Paths.get("data", "game", "def", "equipment", "weapon_animations.jsonc"));
    }

    @Override
    public WeaponAnimationDefinition convert(JsonObject token) {
        ImmutableSet<Integer> ids = ImmutableSet.copyOf(GsonUtils.getAsType(token.get("ids"), Integer[].class));
        EnumMap<CombatStyle, Pair<Integer, Integer>> styles = new EnumMap<>(CombatStyle.class);

        JsonArray stylesJson = token.get("styles").getAsJsonArray();
        for (JsonElement element : stylesJson) {
            JsonObject obj = element.getAsJsonObject();
            CombatStyle type = CombatStyle.valueOf(obj.get("type").getAsString());
            int attack = obj.get("attack").getAsInt();
            int defend = obj.get("defend").getAsInt();

            styles.put(type, new Pair<>(attack, defend));
        }

        WeaponModelDefinition model = readModel(token.get("model").getAsJsonObject());
        return new WeaponAnimationDefinition(ids, styles, model);
    }

    @Override
    public void onCompleted(ImmutableList<WeaponAnimationDefinition> tokenObjects) {
        WeaponAnimationDefinition.load(tokenObjects);
    }
}