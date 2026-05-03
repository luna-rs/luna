package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.model.def.ItemNicknameDefinition;
import io.luna.util.GsonUtils;
import io.luna.util.parser.JsonFileParser;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * A parser for item nickname definitions.
 * <p>
 * This parser loads item nickname data from {@code data/game/bots/items/nicknames.jsonc}. Each JSON object may define
 * one or more item ids that share the same nickname list.
 * <p>
 * Since one JSON object can produce multiple {@link ItemNicknameDefinition} instances, parsed definitions are stored in
 * a temporary map during conversion and committed to {@link ItemNicknameDefinition#ALL} after parsing completes.
 *
 * @author lare96
 */
public final class ItemNicknameDefinitionFileParser extends JsonFileParser<ItemNicknameDefinition> {

    /**
     * Temporarily stores parsed nickname definitions by item id.
     * <p>
     * This parser returns {@code null} from {@link #convert(JsonObject)} because each input object can expand into
     * multiple nickname definitions. The collected values are stored into the global repository in
     * {@link #onCompleted(ImmutableList)}.
     */
    private final Map<Integer, ItemNicknameDefinition> temp = new HashMap<>();

    /**
     * Creates a new {@link ItemNicknameDefinitionFileParser}.
     */
    public ItemNicknameDefinitionFileParser() {
        super(Paths.get("data", "game", "bots", "items", "nicknames.jsonc"));
    }

    @Override
    public ItemNicknameDefinition convert(JsonObject token) {
        ImmutableList<Integer> ids = ImmutableList.copyOf(GsonUtils.getAsType(token.get("ids"), Integer[].class));
        ImmutableList<String> nicknames = ImmutableList.copyOf(GsonUtils.getAsType(token.get("nicknames"), String[].class));

        for (int id : ids) {
            temp.put(id, new ItemNicknameDefinition(id, nicknames));
        }
        return null;
    }

    @Override
    public void onCompleted(ImmutableList<ItemNicknameDefinition> tokenObjects) {
        ItemNicknameDefinition.ALL.storeAndLock(temp.values());
    }
}