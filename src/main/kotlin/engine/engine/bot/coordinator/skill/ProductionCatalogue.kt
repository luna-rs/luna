package engine.bot.coordinator.skill

import api.bot.script.*
import api.bot.zone.ProductionZones
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.*
import game.obj.resource.fillable.WaterResource
import game.skill.Skills
import game.skill.cooking.cookFood.Food
import game.skill.cooking.prepareFood.IncompleteFood
import game.skill.crafting.armorCrafting.HideArmor
import game.skill.crafting.battlestaffCrafting.Battlestaff
import game.skill.crafting.gemCutting.Gem
import game.skill.crafting.glassMaking.GlassMaterial
import game.skill.crafting.hideTanning.Hide
import game.skill.crafting.potteryCrafting.Unfired
import game.skill.crafting.textileCrafting.Textile
import game.skill.crafting.textileCrafting.TextileType
import game.skill.fletching.attachArrow.Arrow
import game.skill.fletching.bolts.Bolt
import game.skill.fletching.bolts.BoltTip
import game.skill.fletching.cutLog.Log
import game.skill.fletching.darts.Dart
import game.skill.fletching.ogre.BrutalArrow
import game.skill.fletching.ogre.OgreFletching
import game.skill.fletching.stringBow.Bow
import game.skill.herblore.grindIngredient.Ingredient
import game.skill.herblore.identifyHerb.Herb
import game.skill.herblore.makePotion.FinishedPotion
import game.skill.herblore.makeUnfPotion.UnfPotion
import game.skill.magic.chargeOrb.ChargeOrbType
import game.skill.magic.enchantJewellery.EnchantJewelleryType
import game.skill.magic.superheatItem.SuperheatItemAction
import game.skill.prayer.Bone
import game.skill.runecrafting.craftRune.CraftableRune
import game.skill.smithing.BarType
import game.skill.smithing.Smithing
import game.skill.smithing.smithBar.SmithingTable
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import kotlin.math.floor
import kotlin.time.Duration

/**
 * Bounded native recipe metadata shared by economic selection and existing player-interaction scripts.
 * Metadata never creates items, starts an action or searches other accounts. Dynamic spell costs retain
 * equipped staff and combination-rune planning; native scripts repeat all gameplay checks after travel.
 *
 * @author lare96
 */
internal object ProductionCatalogue {
    /**
     * A recipe description and the existing script that executes that exact recipe.
     *
     * @author lare96
     */
    data class Entry(val skill: Int, val describe: (Bot) -> ProductionRecipe?,
                     val create: (Bot, Duration) -> InventoryBotScript, val outputs: Set<Int>, val inputs: Set<Int>)

    private fun materials(items: List<Item>): Map<Int, Int> = items.groupBy { it.id }
        .mapValues { (_, values) -> values.sumOf { it.amount } }

    private fun fixed(recipe: ProductionRecipe, create: (Bot, Duration) -> InventoryBotScript) =
        Entry(recipe.skill, { recipe }, create, recipe.products.keys, recipe.materials.keys)

    private fun recipe(key: String, skill: Int, level: Int, inputs: List<Item>, output: Int,
                       experience: Double, tools: Set<Int> = emptySet(), amount: Int = 1) =
        ProductionRecipe(key, skill, level, materials(inputs), tools, mapOf(output to amount), experience)

    /**
     * Catalogue construction happens once after native item definitions have loaded.
     */
    val entries: List<Entry> by lazy {
        buildList {
            Gem.entries.forEach { gem ->
                add(Entry(SKILL_CRAFTING, { bot ->
                    val chance = gem.chance
                    val rate = if (chance == null) 1.0 else
                        Skills.successRate(chance.first, chance.second, bot.crafting.level).coerceIn(0.0, 1.0)
                    val failedXp = floor((gem.exp * 10 + 2) / 4) / 10
                    recipe("gem:${gem.name}", SKILL_CRAFTING, gem.level, listOf(Item(gem.uncut)), gem.cut,
                        gem.exp * rate + failedXp * (1 - rate), setOf(Gem.CHISEL)).copy(productProbability = rate)
                }, { bot, duration -> CutGemBotScript(bot, gem, duration) }, setOf(gem.cut), setOf(gem.uncut)))
            }
            HideArmor.ALL.forEach { armor ->
                add(fixed(recipe("hide:${armor.name}", SKILL_CRAFTING, armor.level,
                    CraftArmorBotScript.supplies(armor), armor.id, armor.exp,
                    if (armor.hides == null) emptySet() else setOf(1733)),
                    { bot, duration -> CraftArmorBotScript(bot, armor, duration) }))
            }
            Hide.entries.forEach { hide ->
                add(fixed(recipe("tan:${hide.name}", SKILL_CRAFTING, 1,
                    listOf(Item(hide.hide)), hide.tan, 0.0).copy(coinCost = hide.cost.toLong()),
                    { bot, duration -> TanHideBotScript(bot, duration, allowGathering = false) }))
            }
            Battlestaff.entries.forEach { staff ->
                add(fixed(recipe("staff:${staff.name}", SKILL_CRAFTING, staff.level,
                    listOf(Item(Battlestaff.BATTLESTAFF), staff.orbItem), staff.staff, staff.exp),
                    { bot, duration -> MakeBattlestaffBotScript(bot, staff, duration) }))
            }
            CraftJewelleryBotScript.RECIPES.forEach { (id, native) ->
                add(fixed(recipe("jewellery:$id", SKILL_CRAFTING, native.jewellery.level,
                    listOfNotNull(Item(native.bar.id), native.jewellery.requiredItem), id, native.jewellery.xp,
                    setOf(native.mould)), { bot, duration -> CraftJewelleryBotScript(bot, id, duration) }))
            }
            StringJewelleryBotScript.UNSTRUNG_IDS.forEach { id ->
                val output = when (id) { 1714 -> 1716; 1720 -> 1722; 6579 -> 6581; else -> id + 19 }
                add(fixed(recipe("string-jewellery:$id", SKILL_CRAFTING, 1,
                    listOf(Item(id), Item(StringJewelleryBotScript.BALL_OF_WOOL)), output, 4.0),
                    { bot, duration -> StringJewelleryBotScript(bot, id, duration) }))
            }
            GlassMaterial.entries.forEach { glass ->
                add(fixed(recipe("glass:${glass.name}", SKILL_CRAFTING, glass.level,
                    listOf(Item(1775)), glass.id, glass.exp, setOf(1785)),
                    { bot, duration -> BlowGlassBotScript(bot, glass, duration) }))
            }
            add(fixed(recipe("molten-glass", SKILL_CRAFTING, 1,
                listOf(Item(1781), Item(1783)), 1775, 20.0),
                { bot, duration -> MakeMoltenGlassBotScript(bot, duration) }))
            Unfired.entries.forEach { pottery ->
                MakePotteryBotScript.Companion.Stage.entries.forEach { stage ->
                    val shaping = stage == MakePotteryBotScript.Companion.Stage.SHAPE
                    add(fixed(recipe("pottery:${pottery.name}:${stage.name}", SKILL_CRAFTING, pottery.level,
                        listOf(Item(if (shaping) 1761 else pottery.unfiredId)),
                        if (shaping) pottery.unfiredId else pottery.firedId,
                        if (shaping) pottery.shapingExp else pottery.firingExp),
                        { bot, duration -> MakePotteryBotScript(bot, pottery, stage, duration) }))
                }
            }
            Textile.entries.forEach { textile ->
                add(fixed(recipe("textile:${textile.name}", SKILL_CRAFTING, textile.level,
                    listOf(textile.rawItem), textile.processedItem.id, textile.exp, amount = textile.processedItem.amount),
                    { bot, duration -> if (textile.type == TextileType.LOOM)
                        WeaveTextileBotScript(bot, textile, duration)
                    else SpinTextileBotScript(bot, duration, textile = textile) }))
            }
            WaterResource.FILLED_IDS.forEach { water ->
                add(fixed(recipe("soft-clay:$water", SKILL_CRAFTING, 1,
                    listOf(Item(434), Item(water)), 1761, 0.0),
                    { bot, duration -> MakeSoftClayBotScript(bot, water, duration) }))
            }

            Log.entries.forEach { log ->
                log.bows.forEachIndexed { index, bow ->
                    add(fixed(recipe("cut-log:${log.name}:$index", SKILL_FLETCHING, bow.level,
                        listOf(Item(log.id)), bow.unstrung, bow.exp, setOf(Log.KNIFE),
                        if (bow == Bow.ARROW_SHAFT) 15 else 1),
                        { bot, duration -> CutLogBotScript(bot, log, index, duration) }))
                }
            }
            Bow.entries.filter { it != Bow.ARROW_SHAFT && it.strung >= 0 }.forEach { bow ->
                add(fixed(recipe("string-bow:${bow.name}", SKILL_FLETCHING, bow.level,
                    listOf(bow.unstrungItem, Item(Bow.BOW_STRING)), bow.strung, bow.stringExp),
                    { bot, duration -> StringBowBotScript(bot, bow, duration) }))
            }
            Arrow.entries.forEach { arrow ->
                add(fixed(recipe("arrow:${arrow.name}", SKILL_FLETCHING, arrow.level,
                    listOf(Item(arrow.tip), Item(arrow.with)), arrow.id, arrow.exp),
                    { bot, duration -> MakeArrowBotScript(bot, arrow, duration) }))
            }
            FletchAmmunitionBotScript.RECIPES.values.forEach { ammunition ->
                val xp = when {
                    ammunition.key.startsWith("DART:") -> Dart.valueOf(ammunition.key.substringAfter(':')).exp
                    ammunition.key.startsWith("BOLT:") -> Bolt.valueOf(ammunition.key.substringAfter(':')).exp
                    else -> BoltTip.valueOf(ammunition.key.substringAfter(':')).exp
                }
                add(fixed(recipe("ammunition:${ammunition.key}", SKILL_FLETCHING, ammunition.level,
                    ammunition.materials, ammunition.output, xp, ammunition.tools, ammunition.outputPerInput),
                    { bot, duration -> FletchAmmunitionBotScript(bot, ammunition, duration) }))
            }
            OgreFletchingBotScript.RECIPES.values.forEach { ogre ->
                val count = when { ogre.bones -> OgreFletching.TIPS_PER_BONE.first
                    ogre.acheyProduct != null && ogre.key == "SHAFTS" -> OgreFletching.SHAFTS_PER_LOG.first
                    else -> 1 }
                val xp = when {
                    ogre.acheyProduct != null -> ogre.acheyProduct.exp * count
                    ogre.bones -> OgreFletching.TIP_EXP * count
                    ogre.key == "FLIGHTED" -> OgreFletching.FLIGHTED_EXP
                    ogre.key == "OGRE" -> OgreFletching.OGRE_ARROW_EXP
                    ogre.key == "STRING_BOW" -> OgreFletching.COMP_BOW_EXP
                    else -> BrutalArrow.valueOf(ogre.key.substringAfter(':')).exp
                }
                add(fixed(recipe("ogre:${ogre.key}", SKILL_FLETCHING, ogre.level,
                    ogre.materials, ogre.output, xp, ogre.tools, count),
                    { bot, duration -> OgreFletchingBotScript(bot, ogre, duration) }))
            }

            Herb.entries.forEach { herb ->
                add(fixed(recipe("identify:${herb.name}", SKILL_HERBLORE, herb.level,
                    listOf(herb.idItem), herb.identified, herb.exp),
                    { bot, duration -> IdentifyHerbBotScript(bot, herb, duration, allowGathering = false) }))
            }
            UnfPotion.entries.forEach { potion ->
                add(fixed(recipe("unfinished:${potion.name}", SKILL_HERBLORE, potion.level,
                    listOf(potion.herbItem, Item(UnfPotion.VIAL_OF_WATER)), potion.id, 0.0),
                    { bot, duration -> MakeUnfPotionBotScript(bot, potion, duration) }))
            }
            FinishedPotion.entries.forEach { potion ->
                add(fixed(recipe("potion:${potion.name}", SKILL_HERBLORE, potion.level,
                    listOf(potion.unfItem, potion.secondaryItem), potion.id, potion.exp),
                    { bot, duration -> MakePotionBotScript(bot, potion, duration) }))
            }
            Ingredient.entries.forEach { ingredient ->
                add(fixed(recipe("grind:${ingredient.name}", SKILL_HERBLORE, 1,
                    listOf(ingredient.oldItem), ingredient.newId, 0.0, setOf(Ingredient.PESTLE_AND_MORTAR)),
                    { bot, duration -> GrindIngredientBotScript(bot, ingredient, duration) }))
            }

            SmithingTable.entries.flatMap { it.items }.distinctBy { it.item.id }.forEach { item ->
                val bars = SmithingTable.ID_TO_TABLE.getValue(item.item.id).bars
                add(fixed(recipe("smith:${item.item.id}", SKILL_SMITHING, item.level,
                    listOf(Item(item.barType.id, bars)), item.item.id, item.barType.xp * bars,
                    setOf(Smithing.HAMMER), item.item.amount),
                    { bot, duration -> SmithBarBotScript(bot, mutableListOf(item), duration) }))
            }
            BarType.entries.forEach { bar ->
                add(fixed(recipe("smelt:${bar.name}", SKILL_SMITHING, bar.level, bar.oreList,
                    bar.id, bar.smeltXp * if (bar == BarType.IRON) 0.5 else 1.0)
                    .copy(productProbability = if (bar == BarType.IRON) 0.5 else 1.0), { bot, duration ->
                    SmeltOreBotScript(bot, bar, duration, ProductionZones.FURNACES.toMutableList()) }))
            }
            add(fixed(recipe("cannonballs", SKILL_SMITHING, 35, listOf(Item(2353)), 2, 25.6,
                setOf(4), 4), { bot, duration -> MakeCannonballBotScript(bot, duration) }))

            Food.entries.forEach { food ->
                add(Entry(SKILL_COOKING, { bot ->
                    val rate = if (bot.cooking.level >= food.burnStopLvl) 1.0 else
                        Skills.successRate(food.chance.first, food.chance.second, bot.cooking.level).coerceIn(0.0, 1.0)
                    recipe("cook:${food.name}", SKILL_COOKING, food.lvl, listOf(Item(food.raw)),
                        food.cooked, food.exp * rate).copy(productProbability = rate)
                }, { bot, duration -> CookFoodBotScript(bot, food, duration, ProductionZones.RANGES.toMutableList()) },
                    setOf(food.cooked), setOf(food.raw)))
            }
            AssembleFoodBotScript.RECIPES.forEach { food ->
                val alternatives = if (food == IncompleteFood.UNCOOKED_CAKE)
                    listOf(food.otherIngredients.first()) else food.otherIngredients
                alternatives.forEach { secondary ->
                    val inputs = if (food == IncompleteFood.UNCOOKED_CAKE)
                        (listOf(food.baseIngredient) + food.otherIngredients).map { Item(it) }
                    else listOf(Item(food.baseIngredient), Item(secondary,
                        if (food == IncompleteFood.UNCOOKED_CURRY && secondary == 5970) 3 else 1))
                    add(fixed(recipe("assemble:${food.name}:$secondary", SKILL_COOKING, food.lvl,
                        inputs, food.id, food.exp), { bot, duration ->
                        AssembleFoodBotScript(bot, food, duration, secondary = secondary) }))
                }
            }
            IncompleteFood.DOUGH.values.forEach { food ->
                food.otherIngredients.forEach { water ->
                    add(fixed(recipe("dough:${food.name}:$water", SKILL_COOKING, food.lvl,
                        listOf(Item(food.baseIngredient), Item(water)), food.id, food.exp),
                        { bot, duration -> MakeDoughBotScript(bot, food, water, duration) }))
                }
            }
            CutFoodBotScript.RECIPES.forEach { (food, count) ->
                add(fixed(recipe("cut-food:${food.name}", SKILL_COOKING, food.lvl,
                    listOf(Item(food.baseIngredient)), food.id, food.exp, setOf(946), count),
                    { bot, duration -> CutFoodBotScript(bot, food, duration) }))
            }

            EnchantJewelleryType.entries.forEach { spell ->
                spell.enchantMap.forEach { (input, product) ->
                    add(Entry(SKILL_MAGIC, { bot ->
                        if (bot.spellbook != Spellbook.REGULAR) null else
                            recipe("enchant:${spell.name}:$input", SKILL_MAGIC, spell.level,
                                listOf(Item(input)) + bot.productionRuneCosts(spell.requirements), product.id, spell.xp)
                    }, { bot, duration -> EnchantJewelleryBotScript(bot, spell, input, duration) },
                        setOf(product.id), setOf(input)))
                }
            }
            ChargeOrbType.entries.forEach { spell ->
                add(Entry(SKILL_MAGIC, { bot ->
                    if (bot.spellbook != Spellbook.REGULAR) null else
                        recipe("charge-orb:${spell.name}", SKILL_MAGIC, spell.level,
                            listOf(Item(567)) + bot.productionRuneCosts(spell.requirements), spell.chargedOrb, spell.xp)
                }, { bot, duration ->
                    val zone = when (spell) {
                        ChargeOrbType.WATER -> SubZone.WATER_OBELISK
                        ChargeOrbType.EARTH -> SubZone.EARTH_OBELISK
                        ChargeOrbType.FIRE -> SubZone.FIRE_OBELISK
                        ChargeOrbType.AIR -> SubZone.AIR_OBELISK
                    }
                    ChargeOrbBotScript(bot, spell, duration, mutableListOf(zone))
                }, setOf(spell.chargedOrb), setOf(567)))
            }
            BarType.entries.forEach { bar ->
                add(Entry(SKILL_MAGIC, { bot ->
                    if (bot.spellbook != Spellbook.REGULAR || bot.smithing.staticLevel < bar.level) null else
                        recipe("superheat:${bar.name}", SKILL_MAGIC, SuperheatItemAction.LEVEL,
                            bar.oreList + bot.productionRuneCosts(SuperheatItemAction.RUNES), bar.id, 53.0)
                }, { bot, duration -> SuperheatItemBotScript(bot, bar, duration) }, setOf(bar.id),
                    bar.oreList.mapTo(hashSetOf()) { it.id }))
            }
            CraftableRune.entries.forEach { rune ->
                add(Entry(SKILL_RUNECRAFTING, { bot ->
                    val essence = CraftRuneBotScript.compatibleEssence(bot, rune)
                        ?: if (rune.level > 20) CraftRuneBotScript.PURE_ESSENCE else CraftRuneBotScript.RUNE_ESSENCE
                    recipe("runecraft:${rune.name}", SKILL_RUNECRAFTING, rune.level,
                        listOf(Item(essence)), rune.id, rune.exp, setOf(rune.altar.talisman),
                        rune.outputMultiplier(bot.runecrafting.level))
                }, { bot, duration -> CraftRuneBotScript(bot, rune.altar, duration) }, setOf(rune.id),
                    setOf(CraftRuneBotScript.PURE_ESSENCE, CraftRuneBotScript.RUNE_ESSENCE)))
            }

            game.skill.firemaking.Log.entries.forEach { log ->
                val native = ProductionRecipe("burn:${log.name}", SKILL_FIREMAKING, log.level,
                    mapOf(log.id to 1), setOf(game.skill.firemaking.Firemaking.TINDERBOX), emptyMap(), log.exp)
                add(fixed(native, { bot, duration -> FiremakingBotScript(bot, log, duration,
                    FiremakingBotScript.FiremakingSpot.entries.map { it.zone }.toMutableList()) }))
            }
            Bone.entries.forEach { bone ->
                add(fixed(ProductionRecipe("bury:${bone.name}", SKILL_PRAYER, 1,
                    mapOf(bone.id to 1), emptySet(), emptyMap(), bone.exp),
                    { bot, duration -> BuryBoneBotScript(bot, bone, duration) }))
            }
        }
    }

    val bySkill: Map<Int, List<Entry>> by lazy { entries.groupBy { it.skill } }

    /**
     * Native output identities are indexed once; graph searches describe only recipes they actually visit.
     */
    val byOutput: Map<Int, List<Entry>> by lazy {
        entries.flatMap { entry -> entry.outputs.map { it to entry } }.groupBy({ it.first }, { it.second })
    }

    /**
     * Reverse processing links are static too; zero-XP preparation searches only consumers of its actual output.
     */
    val byInput: Map<Int, List<Entry>> by lazy {
        entries.flatMap { entry -> entry.inputs.map { it to entry } }.groupBy({ it.first }, { it.second })
    }
}
