package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import game.player.Sound;
import game.skill.magic.EquipmentRequirement;
import game.skill.magic.ItemRequirement;
import game.skill.magic.Rune;
import game.skill.magic.RuneRequirement;
import game.skill.magic.SpellRequirement;
import io.luna.game.model.LocalProjectile;
import io.luna.game.model.def.CombatSpellDefinition;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Spellbook;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.block.Graphic;
import io.luna.game.model.mob.combat.CombatSpell;
import io.luna.game.model.mob.combat.CombatSpellType;
import io.luna.util.GsonUtils;
import io.luna.util.parser.JsonFileParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;
import java.util.function.BiFunction;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * Parses combat spell definitions from {@code data/game/def/spells.json}.
 *
 * @author lare96
 */
public final class CombatSpellDefinitionFileParser extends JsonFileParser<CombatSpellDefinition> {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Creates a new {@link CombatSpellDefinitionFileParser}.
     */
    public CombatSpellDefinitionFileParser() {
        super(Paths.get("data", "game", "def", "spells.json"));
    }

    @Override
    public CombatSpellDefinition convert(JsonObject token) {
        int id = token.get("id").getAsInt();
        CombatSpell spell = CombatSpell.valueOf(token.get("spell").getAsString());
        CombatSpellType spellType = CombatSpellType.valueOf(token.get("spell_type").getAsString());
        int level = token.get("level").getAsInt();
        int maxHit = token.has("max_hit") ? token.get("max_hit").getAsInt() : -1;
        double exp = token.get("exp").getAsDouble();
        Spellbook spellbook = Spellbook.valueOf(token.get("spellbook").getAsString());
        int castAnimation = token.get("cast_animation").getAsInt();

        Graphic startGraphic = token.has("start_graphic") ?
                readGraphic(token.get("start_graphic").getAsJsonObject()) : null;
        BiFunction<Mob, Mob, LocalProjectile> projectile = token.has("projectile") ?
                readProjectile(token.get("projectile").getAsJsonObject()) : null;
        Graphic endGraphic = readGraphic(token.get("end_graphic").getAsJsonObject());

        Item[] requiredEquipment = GsonUtils.getAsType(token.get("required_equipment"), Item[].class);
        Item[] requiredInventory = GsonUtils.getAsType(token.get("required_inventory"), Item[].class);
        ImmutableList<SpellRequirement> required = readRequirements(requiredEquipment, requiredInventory);

        Sound startSound = Sound.valueOf(token.get("start_sound").getAsString());
        Sound endSound = Sound.valueOf(token.get("end_sound").getAsString());

        return new CombatSpellDefinition(
                id, spell, spellType, level, maxHit, exp, spellbook,
                new Animation(castAnimation), startGraphic, projectile, endGraphic, required,
                startSound, endSound
        );
    }

    @Override
    public void onCompleted(ImmutableList<CombatSpellDefinition> tokenObjects) {
        CombatSpellDefinition.ALL.storeAndLock(tokenObjects);
        logger.debug("Loaded {} spell definitions!", box(tokenObjects.size()));
    }

    /**
     * Builds the full spell requirement list from equipped-item and inventory-item data.
     * <p>
     * Equipment entries are converted into {@link EquipmentRequirement}s. Inventory entries are converted as follows:
     * <ul>
     *     <li>If the item id maps to a {@link Rune}, a {@link RuneRequirement} is created.</li>
     *     <li>Otherwise, an {@link ItemRequirement} is created.</li>
     * </ul>
     *
     * @param requiredEquipment Items that must be equipped before casting.
     * @param requiredInventory Items or runes that must be present in inventory before casting.
     * @return An immutable list containing all parsed {@link SpellRequirement}s.
     */
    private ImmutableList<SpellRequirement> readRequirements(Item[] requiredEquipment, Item[] requiredInventory) {
        ImmutableList.Builder<SpellRequirement> list = ImmutableList.builder();

        for (Item item : requiredEquipment) {
            list.add(new EquipmentRequirement(item.getId()));
        }

        for (Item item : requiredInventory) {
            Rune rune = Rune.Companion.getID_TO_RUNE().get(item.getId());
            int amount = item.getAmount();

            if (rune != null) {
                list.add(new RuneRequirement(rune, amount));
            } else {
                list.add(new ItemRequirement(item.getId(), amount));
            }
        }

        return list.build();
    }
}