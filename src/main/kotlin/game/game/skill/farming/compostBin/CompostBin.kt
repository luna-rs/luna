package game.skill.farming.compostBin

import com.google.gson.JsonObject

/**
 * Represents one of a player's compost bins. It is filled with up to [MAX_ITEMS] items, closed to rot for
 * [ROT_MINUTES], then opened, and its products are taken out one at a time until it is empty again.
 *
 * Only the state is changed here. Requirements, items, animations and experience are handled by the caller.
 *
 * @author hydrozoa
 */
class CompostBin(val location: CompostBinLocation) {

    companion object {

        /**
         * How many items fill a compost bin (wiki).
         */
        const val MAX_ITEMS = 15

        /**
         * The value drawn for a closed bin. Every closed bin looks the same, so the server tracks whether it has
         * finished rotting.
         */
        const val CLOSED_VALUE = 31

        /**
         * How many minutes a closed bin takes to rot (wiki).
         */
        val ROT_MINUTES = 35..70
    }

    /**
     * What the bin is filled with, or `null` if it is empty.
     */
    var type: CompostBinType? = null

    /**
     * How many items are in the bin, or how many products are left once it is open.
     */
    var amount = 0

    /**
     * If the bin is closed and rotting.
     */
    var closed = false

    /**
     * If the bin has been opened after rotting, so its products can be taken out.
     */
    var open = false

    /**
     * The time the closed bin finishes rotting, in epoch milliseconds.
     */
    var readyAt = 0L

    /**
     * @return True if items can be put in the bin.
     */
    fun canFill(): Boolean = !closed && !open && amount < MAX_ITEMS

    /**
     * Puts [count] items of [itemType] in the bin. Mixing different types makes compost (wiki).
     */
    fun fill(itemType: CompostBinType, count: Int) {
        val current = type
        type = if (current == null || current == itemType) itemType else CompostBinType.COMPOST
        amount = minOf(MAX_ITEMS, amount + count)
    }

    /**
     * @return True if the bin is full and can be closed.
     */
    fun canClose(): Boolean = !closed && !open && amount >= MAX_ITEMS

    /**
     * Closes the bin at time [now], in epoch milliseconds, to rot for [minutes].
     */
    fun close(now: Long, minutes: Int) {
        closed = true
        readyAt = now + minutes * 60_000L
    }

    /**
     * @return True if the bin is closed and has finished rotting at time [now], in epoch milliseconds.
     */
    fun isRotted(now: Long): Boolean = closed && now >= readyAt

    /**
     * Opens the rotted bin, so its products can be taken out.
     */
    fun open() {
        closed = false
        open = true
        readyAt = 0L
    }

    /**
     * @return True if a product can be taken out of the bin.
     */
    fun canTakeOut(): Boolean = open && amount > 0

    /**
     * Takes one product out of the bin, emptying it once the last one is taken.
     */
    fun takeOut() {
        amount--
        if (amount <= 0) {
            reset()
        }
    }

    /**
     * Empties the bin.
     */
    fun reset() {
        type = null
        amount = 0
        closed = false
        open = false
        readyAt = 0L
    }

    /**
     * The value of this bin's byte within its varp: 0 when empty, [CompostBinType.fillValue] plus the items while
     * filling, [CLOSED_VALUE] while closed, and [CompostBinType.productValue] plus the products left once open.
     */
    fun encode(): Int {
        val current = type ?: return 0
        return when {
            closed -> CLOSED_VALUE
            open -> current.productValue + amount
            else -> current.fillValue + amount
        }
    }

    /**
     * The value this bin contributes to its varp.
     */
    fun getVarpValue(): Int = encode() shl location.shifts

    /**
     * Saves the state of this bin.
     */
    fun save(): JsonObject {
        val obj = JsonObject()
        type?.let { obj.addProperty("type", it.name) }
        obj.addProperty("amount", amount)
        obj.addProperty("closed", closed)
        obj.addProperty("open", open)
        obj.addProperty("ready_at", readyAt)
        return obj
    }

    /**
     * Loads the state of this bin, previously written by [save].
     */
    fun load(obj: JsonObject) {
        type = CompostBinType.values().find { it.name == obj.get("type")?.asString }
        amount = if (type == null) 0 else obj.get("amount")?.asInt ?: 0
        closed = obj.get("closed")?.asBoolean ?: false
        open = obj.get("open")?.asBoolean ?: false
        readyAt = obj.get("ready_at")?.asLong ?: 0L
    }
}
