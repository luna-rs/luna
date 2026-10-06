package game.bot.scripts.skills

import api.bot.Suspendable.naturalDexterityDelay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ZonedBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.fletching.stringBow.Bow
import game.skill.magic.CombinationRune
import game.skill.magic.Magic
import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import game.skill.magic.Staff
import game.skill.magic.lowHighAlch.AlchemyType
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Trains Magic by casting low or high alchemy on sensible banked items.
 *
 * Alchable items are deliberately whitelisted so bots never decide to destroy arbitrary valuables. Bows may be alched
 * in either noted or unnoted form, while armour is withdrawn and alched as notes.
 *
 * Intelligent bots usually select the item with the best current alchemy margin using Luna's economy guide price.
 * Less intelligent bots are increasingly likely to pick a random valid item instead.
 *
 * The script stops when no valid items remain. Missing rune supplies are added to the wanted-item system before stopping.
 *
 * @property type Whether this script casts low or high alchemy.
 * @author lare96
 */
class AlchemyBotScript(
    bot: Bot,
    val type: AlchemyType,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : ZonedBotScript(bot, duration, zones) {

    companion object {

        /**
         * Armour that bots may safely alch.
         *
         * These are the unnoted ids. Armour is always withdrawn from the bank as notes before being alched.
         */
        private val ARMOUR_IDS = setOf(
            // Bronze.
            1117, // Bronze platebody.
            1075, // Bronze platelegs.
            1087, // Bronze plateskirt.
            1155, // Bronze full helm.
            1189, // Bronze kiteshield.

            // Iron.
            1115, // Iron platebody.
            1067, // Iron platelegs.
            1081, // Iron plateskirt.
            1153, // Iron full helm.
            1191, // Iron kiteshield.

            // Steel.
            1119, // Steel platebody.
            1069, // Steel platelegs.
            1083, // Steel plateskirt.
            1157, // Steel full helm.
            1193, // Steel kiteshield.

            // Mithril.
            1121, // Mithril platebody.
            1071, // Mithril platelegs.
            1085, // Mithril plateskirt.
            1159, // Mithril full helm.
            1197, // Mithril kiteshield.

            // Adamant.
            1123, // Adamant platebody.
            1073, // Adamant platelegs.
            1091, // Adamant plateskirt.
            1161, // Adamant full helm.
            1199, // Adamant kiteshield.

            // Rune.
            1127, // Rune platebody.
            1079, // Rune platelegs.
            1093, // Rune plateskirt.
            1163, // Rune full helm.
            1201, // Rune kiteshield.

            // Dragonhide.
            1135, // Green d'hide body.
            1099, // Green d'hide chaps.
            2499, // Blue d'hide body.
            2493, // Blue d'hide chaps.
            2501, // Red d'hide body.
            2495, // Red d'hide chaps.
            2503, // Black d'hide body.
            2497  // Black d'hide chaps.
        )

        /**
         * Smelted metal bars that bots may safely alch.
         *
         * These are the unnoted ids. Bars are withdrawn from the bank as notes before being alched.
         */
        private val BAR_IDS = setOf(
            2349, // Bronze bar.
            2351, // Iron bar.
            2353, // Steel bar.
            2359, // Mithril bar.
            2361, // Adamantite bar.
            2363  // Runite bar.
        )

        /**
         * Items that must be alched in noted form.
         */
        private val NOTE_REQUIRED_IDS: Set<Int> by lazy {
            ARMOUR_IDS + BAR_IDS
        }

        /**
         * Every base item id this script may alch.
         */
        private val ALCHABLE_IDS: Set<Int> by lazy {
            BOW_IDS + NOTE_REQUIRED_IDS
        }

        /**
         * Unstrung and strung bow ids that may be alched.
         */
        private val BOW_IDS: Set<Int> by lazy {
            buildSet {
                Bow.entries
                    .filter { it != Bow.ARROW_SHAFT }
                    .forEach { bow ->
                        add(bow.unstrung)

                        if (bow.strung != -1) {
                            add(bow.strung)
                        }
                    }
            }
        }

        /**
         * Rune ids that can satisfy a fire-rune requirement.
         *
         * Staff substitution is handled separately through [Staff].
         */
        private val FIRE_RUNE_IDS: Set<Int> by lazy {
            buildSet {
                add(Rune.FIRE.id)

                CombinationRune.entries
                    .filter { Rune.FIRE in it.represents }
                    .forEach { add(it.id) }
            }
        }

        /**
         * Serializable state for [AlchemyBotScript].
         */
        class AlchemyData : ZonedBotScriptData() {

            /**
             * The type of alchemy being performed.
             */
            var type: AlchemyType = AlchemyType.LOW

            override fun load(data: JsonObject) {
                super.load(data)
                type = AlchemyType.valueOf(data.get("type").asString)
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("type", type.name)
            }
        }

        /**
         * Returns whether [bot] currently owns at least one item that can be used by an alchemy script.
         *
         * Both the inventory and bank are checked. Unnoted armour is considered valid because the script can bank it and
         * withdraw it again as a note before alching.
         *
         * @param bot The bot to check.
         * @return `true` if at least one valid alchemy target is available.
         */
        fun hasAlchableItems(bot: Bot): Boolean {
            return ALCHABLE_IDS.any { baseId ->
                if (baseId in bot.inventory || baseId in bot.bank) {
                    return@any true
                }

                val notedId = itemDef(baseId).notedId
                notedId.isPresent && (notedId.asInt in bot.inventory || notedId.asInt in bot.bank)
            }
        }
    }

    /**
     * Recreates an alchemy script from persisted data.
     */
    constructor(bot: Bot, data: AlchemyData) : this(bot, data.type, data.duration, data.zones)

    /**
     * The item currently being alched in the inventory.
     */
    private var targetId: Int? = null

    /**
     * The fire-providing staff selected for this script, if one is available.
     */
    private var fireStaffId: Int? = null

    override suspend fun equipment(): BotGearLocator? {
        // Do not let generic skilling gear replace a useful elemental staff.
        return null
    }

    override suspend fun onInit(resumed: Boolean): Boolean {
        if (bot.spellbook != Spellbook.REGULAR) {
            bot.log("Alchemy requires the regular spellbook.")
            return false
        }

        if (bot.magic.level < type.level) {
            bot.log("Magic level ${bot.magic.level} is too low for ${type.name.lowercase()} alchemy.")
            return false
        }

        if (remainingAlchableCount() == 0) {
            bot.log("No alchable items available. Ending script.")
            return false
        }

        fireStaffId = resolveFireStaff()

        if (bot.itemTracker.count(Rune.NATURE.id) < natureRunesPerCast()) {
            requestNatureRunes()
            return false
        }

        if (fireStaffId == null && inventoryFireRunes() + bankedFireRunes() < fireRunesPerCast()) {
            requestFireRunes()
            return false
        }

        return true
    }

    override suspend fun onBankOpen(initial: Boolean) {
        targetId = null

        /*
         * Make sure rune and staff withdrawals use normal withdrawal mode. The mode is switched to notes only when
         * withdrawing the actual alchemy target.
         */
        handler.banking.clickBankingMode(false)

        prepareFireStaff()

        if (Rune.NATURE.id in bot.bank) {
            handler.banking.withdrawAll(Rune.NATURE.id)
        }

        if (!hasEquippedFireStaff()) {
            for (id in FIRE_RUNE_IDS) {
                if (id in bot.bank) {
                    handler.banking.withdrawAll(id)
                }
            }
        }

        if (!hasNatureRunesForOneCast()) {
            requestNatureRunes()
            stop()
            return
        }

        if (!hasEquippedFireStaff() && !hasFireRunesForOneCast()) {
            requestFireRunes()
            stop()
            return
        }

        val baseId = selectBankTarget()
        if (baseId == null) {
            bot.log("No alchable items remain in the bank.")
            stop()
            return
        }

        val notedId = notedId(baseId)
        if (notedId == null) {
            bot.log("${itemName(baseId)} cannot be withdrawn as a note.")
            stop()
            return
        }

        handler.banking.clickBankingMode(true)

        if (!handler.banking.withdrawAll(baseId)) {
            bot.log("Could not withdraw ${itemName(baseId)} for alchemy.")
            stop()
            return
        }

        handler.banking.clickBankingMode(false)

        targetId = when {
            notedId in bot.inventory -> notedId
            baseId in BOW_IDS && baseId in bot.inventory -> baseId
            else -> null
        }

        if (targetId == null) {
            bot.log("Alchemy target was not found after withdrawal.")
            stop()
        }
    }

    override suspend fun executeInZone(): Boolean {
        val target = currentInventoryTarget()

        if (target == null) {
            if (hasBankedAlchableItems()) {
                forceBanking = true
            } else {
                bot.log("No alchable items remain. Ending script.")
                stop()
            }
            return true
        }

        targetId = target

        if (!prepareRunesForCast()) {
            return true
        }

        if (Magic.checkRequirements(bot, type.level, type.requirements) == null) {
            bot.log("Alchemy requirements unexpectedly failed after rune preparation.")
            return true
        }

        val before = bot.inventory.computeAmountForId(target)

        if (!handler.inventory.useSpellOnItem(type.spellId, target)) {
            bot.log("Could not cast ${type.name.lowercase()} alchemy on ${itemName(target)}.")
            return true
        }

        if (!waitFor(5.seconds) { bot.inventory.computeAmountForId(target) < before }) {
            bot.log("Alchemy cast did not consume ${itemName(target)}.")
            return true
        }

        bot.naturalDexterityDelay()

        if (target !in bot.inventory) {
            targetId = null

            if (currentInventoryTarget() == null) {
                if (hasBankedAlchableItems()) {
                    forceBanking = true
                } else {
                    bot.log("Finished all available alchable items.")
                    stop()
                }
            }
        }

        return true
    }

    override fun snapshot(): BotScriptData {
        val data = AlchemyData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.type = type
        return data
    }

    /**
     * Selects an alchable item from the bank.
     *
     * Intelligent bots usually choose the item with the best alchemy margin. Lower-intelligence bots increasingly choose
     * randomly instead.
     */
    private fun selectBankTarget(): Int? {
        val candidates = ALCHABLE_IDS.filter {
            bot.bank.computeAmountForId(it) > 0 && notedId(it) != null
        }

        if (candidates.isEmpty()) {
            return null
        }

        if (!rand(bot.personality.intelligence)) {
            return candidates.random()
        }

        return candidates.maxByOrNull { alchMargin(it) }
    }

    /**
     * Returns the current inventory alchemy target.
     *
     * Bows may be alched noted or unnoted. Armour must be noted.
     */
    private fun currentInventoryTarget(): Int? {
        targetId?.let {
            if (it in bot.inventory && isValidInventoryTarget(it)) {
                return it
            }
        }

        val candidates = ArrayList<Int>()

        for (baseId in BOW_IDS) {
            if (baseId in bot.inventory) {
                candidates += baseId
            }

            notedId(baseId)?.let { note ->
                if (note in bot.inventory) {
                    candidates += note
                }
            }
        }

        for (baseId in NOTE_REQUIRED_IDS) {
            notedId(baseId)?.let { note ->
                if (note in bot.inventory) {
                    candidates += note
                }
            }
        }

        if (candidates.isEmpty()) {
            return null
        }

        return if (rand(bot.personality.intelligence)) {
            candidates.maxByOrNull { alchMargin(baseId(it)) }
        } else {
            candidates.random()
        }
    }

    /**
     * Ensures enough runes are in the inventory for one cast.
     */
    private fun prepareRunesForCast(): Boolean {
        if (!hasNatureRunesForOneCast()) {
            if (bot.bank.computeAmountForId(Rune.NATURE.id) > 0) {
                forceBanking = true
            } else {
                requestNatureRunes()
                stop()
            }
            return false
        }

        if (hasEquippedFireStaff()) {
            return true
        }

        if (!hasFireRunesForOneCast()) {
            if (bankedFireRunes() > 0 || resolveFireStaff() != null) {
                forceBanking = true
            } else {
                requestFireRunes()
                stop()
            }
            return false
        }

        return true
    }

    /**
     * Equips an owned staff that supplies fire runes when one is available.
     */
    private suspend fun prepareFireStaff() {
        val staffId = resolveFireStaff() ?: return
        fireStaffId = staffId

        if (bot.equipment.weapon?.id == staffId) {
            return
        }

        if (staffId !in bot.inventory && !handler.banking.withdraw(Item(staffId))) {
            bot.log("Could not withdraw ${itemName(staffId)}.")
            fireStaffId = null
            return
        }

        if (!handler.equipment.equip(staffId)) {
            bot.log("Could not equip ${itemName(staffId)}.")
            fireStaffId = null
        }
    }

    /**
     * Finds a fire-providing staff the bot owns and can equip.
     */
    private fun resolveFireStaff(): Int? {
        val equipped = bot.equipment.weapon?.id
        if (equipped != null && providesFireRunes(equipped)) {
            return equipped
        }

        return Staff.entries
            .asSequence()
            .filter { Rune.FIRE in it.represents }
            .flatMap { it.ids.asSequence() }
            .filter { it in bot.itemTracker }
            .firstOrNull { canEquip(it) }
    }

    private fun canEquip(id: Int): Boolean {
        return EquipmentDefinition.ALL.get(id)
            .map { it.meetsAllRequirements(bot) }
            .orElse(false)
    }

    private fun providesFireRunes(id: Int): Boolean {
        return Staff.ID_TO_STAFF[id]?.let { Rune.FIRE in it.represents } == true
    }

    private fun hasEquippedFireStaff(): Boolean {
        val weapon = bot.equipment.weapon?.id ?: return false
        return providesFireRunes(weapon)
    }

    private fun hasNatureRunesForOneCast(): Boolean {
        return bot.inventory.computeAmountForId(Rune.NATURE.id) >= natureRunesPerCast()
    }

    private fun hasFireRunesForOneCast(): Boolean {
        return inventoryFireRunes() >= fireRunesPerCast()
    }

    private fun inventoryFireRunes(): Int {
        return FIRE_RUNE_IDS.sumOf { bot.inventory.computeAmountForId(it) }
    }

    private fun bankedFireRunes(): Int {
        return FIRE_RUNE_IDS.sumOf { bot.bank.computeAmountForId(it) }
    }

    private fun natureRunesPerCast(): Int {
        return type.requirements
            .filterIsInstance<RuneRequirement>()
            .first { it.rune == Rune.NATURE }
            .amount
    }

    private fun fireRunesPerCast(): Int {
        return type.requirements
            .filterIsInstance<RuneRequirement>()
            .first { it.rune == Rune.FIRE }
            .amount
    }

    /**
     * Requests enough nature runes to alch the bot's remaining valid items.
     */
    private fun requestNatureRunes() {
        val amount = (remainingAlchableCount() * natureRunesPerCast()).coerceAtLeast(natureRunesPerCast())

        bot.log("Not enough nature runes. Adding $amount to wanted items.")
        bot.preferences.addWantedItem(Rune.NATURE.id, amount)
    }

    /**
     * Requests enough fire runes to alch the bot's remaining valid items.
     *
     * This is only used when no usable fire-providing staff is available.
     */
    private fun requestFireRunes() {
        val amount = (remainingAlchableCount() * fireRunesPerCast()).coerceAtLeast(fireRunesPerCast())

        bot.log("Not enough fire runes. Adding $amount to wanted items.")
        bot.preferences.addWantedItem(Rune.FIRE.id, amount)
    }

    private fun remainingAlchableCount(): Int {
        return ALCHABLE_IDS.sumOf { baseId ->
            val noteId = notedId(baseId)

            var count = bot.bank.computeAmountForId(baseId)

            if (baseId in BOW_IDS) {
                count += bot.inventory.computeAmountForId(baseId)
            }

            if (noteId != null) {
                count += bot.inventory.computeAmountForId(noteId)
            }

            count
        }
    }

    private fun hasBankedAlchableItems(): Boolean {
        return ALCHABLE_IDS.any { bot.bank.computeAmountForId(it) > 0 }
    }

    private fun isValidInventoryTarget(id: Int): Boolean {
        val baseId = baseId(id)

        if (baseId in BOW_IDS) {
            return true
        }

        return baseId in NOTE_REQUIRED_IDS && itemDef(id).isNoted
    }

    private fun baseId(id: Int): Int {
        return itemDef(id).unnotedId.orElse(id)
    }

    private fun notedId(id: Int): Int? {
        val noted = itemDef(id).notedId
        return if (noted.isPresent) noted.asInt else null
    }

    private fun alchValue(baseId: Int): Long {
        return (itemDef(baseId).value * type.valueMultiplier).toLong().coerceAtLeast(1)
    }

    private fun alchMargin(baseId: Int): Long {
        return alchValue(baseId) - world.economy.getPrice(baseId)
    }
}