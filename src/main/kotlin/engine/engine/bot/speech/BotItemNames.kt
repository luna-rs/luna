package engine.bot.speech

import com.google.gson.JsonElement
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.ItemNicknameDefinition
import io.luna.util.GsonUtils
import org.apache.logging.log4j.LogManager
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Turns the item names players type ("d long", "addy plate", "lobs") into item ids, so bots can understand them.
 *
 * Every name is reduced to a key: lowercase words, apostrophes dropped, other punctuation split into spaces and a
 * trailing "s" removed from each word. Keys come from, in priority order:
 * - item nicknames, which bots also say (`nicknames.jsonc`);
 * - the exact names of tradeable unnoted items;
 * - potion and jewellery names without their dose or charge, which mean the fullest one;
 * - the exact names of untradeable items, so bots can say they can't be traded.
 *
 * A phrase with no key is retried with the word shorthand in `shorthand.jsonc`, replacing as few words as possible,
 * after splitting joined shorthand such as "dmed". As a last resort, one typo is forgiven in names of six or more
 * characters, when only one item is that close.
 *
 * The index is built on first use, after the cache and nicknames have loaded.
 *
 * @author TheLining
 */
object BotItemNames {

    /**
     * The logger.
     */
    private val logger = LogManager.getLogger()

    /**
     * The word shorthand file.
     */
    private val SHORTHAND_PATH = Paths.get("data", "game", "bots", "items", "shorthand.jsonc")

    /**
     * Matches a name ending in a dose or charge count, such as "Prayer potion(4)" or "Amulet of glory(4)".
     */
    private val DOSE = Regex("""(.+)\((\d+)\)""")

    /**
     * Items left out of name lookups, matching [io.luna.game.model.item.Item.byName].
     */
    private val RESTRICTED = setOf(617, 6564, 6565, 6566)

    /**
     * The longest phrase, in words, worth looking up.
     */
    private const val MAX_WORDS = 5

    /**
     * The most shorthand combinations tried for one phrase.
     */
    private const val MAX_COMBINATIONS = 512

    /**
     * The shortest key that may match despite a typo. Shorter keys are too close to everyday words.
     */
    private const val MIN_TYPO_LENGTH = 6

    /**
     * Marks a phrase whose shorthand names two different items.
     */
    private val AMBIGUOUS = IntArray(0)

    /**
     * The built lookup tables.
     *
     * @property keys Item ids by key. A nickname can name more than one item.
     * @property shorthand The words each shorthand word can stand for.
     * @property rewrites Multi-word shorthand, always replaced before lookup.
     */
    private class Index(val keys: Map<String, IntArray>,
                        val shorthand: Map<String, List<List<String>>>,
                        val rewrites: List<Pair<List<String>, List<String>>>) {

        /**
         * The keys grouped by length, for typo matching.
         */
        val byLength = keys.keys.groupBy { it.length }
    }

    /**
     * The index, built on first use.
     */
    private val index by lazy { build() }

    /**
     * Resolves a typed item name.
     *
     * @param phrase The name as typed, without quantities.
     * @return The unnoted ids it names (several for a group nickname like "phat"), or `null` if it names no item, or
     * two different items equally well.
     */
    fun resolve(phrase: String): IntArray? {
        val index = index
        val typed = rewrite(words(phrase), index.rewrites)
        if (typed.isEmpty() || typed.size > MAX_WORDS) {
            return null
        }
        val key = typed.joinToString(" ")
        index.keys[key]?.let { return it }
        val expanded = expand(index, split(index, typed))
        return if (expanded === AMBIGUOUS) null else expanded ?: closest(index, key)
    }

    /**
     * Splits joined shorthand such as "dmed" or "rpb" into its two words.
     */
    private fun split(index: Index, words: List<String>): List<String> = words.flatMap {
        if (it.length >= 3 && (it[0] == 'd' || it[0] == 'r') && it !in index.shorthand &&
            it.substring(1) in index.shorthand) listOf(it.substring(0, 1), it.substring(1)) else listOf(it)
    }

    /**
     * Tries the phrase with its shorthand replaced, fewest replacements first.
     *
     * @return The ids named at the lowest number of replacements, `null` if none, or [AMBIGUOUS] if two different
     * items tie there.
     */
    private fun expand(index: Index, words: List<String>): IntArray? {
        if (words.size > MAX_WORDS) {
            return null
        }
        index.keys[words.joinToString(" ")]?.let { return it }

        // Each word is kept (option 0) or swapped for one of its shorthand meanings.
        val options = words.map { listOf(listOf(it)) + (index.shorthand[it] ?: emptyList()) }
        val combinations = options.fold(1) { total, it -> total * it.size }
        if (combinations == 1 || combinations > MAX_COMBINATIONS) {
            return null
        }
        val found = arrayOfNulls<IntArray>(words.size + 1)
        val ambiguous = BooleanArray(words.size + 1)
        val picks = IntArray(words.size)
        for (combination in 1 until combinations) {
            var rest = combination
            for (word in words.indices) {
                picks[word] = rest % options[word].size
                rest /= options[word].size
            }
            val key = words.indices.joinToString(" ") { options[it][picks[it]].joinToString(" ") }
            val ids = index.keys[key] ?: continue
            val replaced = picks.count { it > 0 }
            val previous = found[replaced]
            if (previous == null) {
                found[replaced] = ids
            } else if (!previous.contentEquals(ids)) {
                ambiguous[replaced] = true
            }
        }
        for (replaced in 1..words.size) {
            if (ambiguous[replaced]) {
                return AMBIGUOUS
            }
            found[replaced]?.let { return it }
        }
        return null
    }

    /**
     * Finds the one item whose key is a single typo away from [key]: a letter missing, added or changed, or two
     * letters swapped. A typo never changes a number or "+", so "ppot 3" can't become "ppot 2".
     *
     * @return The ids, or `null` if no key or more than one item is that close.
     */
    private fun closest(index: Index, key: String): IntArray? {
        if (key.length < MIN_TYPO_LENGTH) {
            return null
        }
        val marks = key.filter { it.isDigit() || it == '+' }
        var match: IntArray? = null
        for (length in key.length - 1..key.length + 1) {
            for (candidate in index.byLength[length] ?: continue) {
                if (!oneTypoApart(key, candidate) || candidate.filter { it.isDigit() || it == '+' } != marks) {
                    continue
                }
                val ids = index.keys.getValue(candidate)
                if (match == null) match = ids else if (!match.contentEquals(ids)) return null
            }
        }
        return match
    }

    /**
     * Whether two different strings are one typo apart.
     */
    private fun oneTypoApart(first: String, second: String): Boolean {
        val (short, long) = if (first.length <= second.length) first to second else second to first
        if (long.length - short.length > 1) {
            return false
        }
        var same = 0
        while (same < short.length && short[same] == long[same]) {
            same++
        }
        if (short.length < long.length) {
            return short.regionMatches(same, long, same + 1, short.length - same)
        }
        if (same == short.length) {
            return false
        }
        val rest = short.length - same
        return short.regionMatches(same + 1, long, same + 1, rest - 1) ||
            rest >= 2 && short[same] == long[same + 1] && short[same + 1] == long[same] &&
            short.regionMatches(same + 2, long, same + 2, rest - 2)
    }

    /**
     * Splits [text] into key words.
     */
    private fun words(text: String): List<String> {
        val words = ArrayList<String>(4)
        val word = StringBuilder()
        for (c in text) {
            when (c) {
                '\'' -> continue
                in 'a'..'z', in '0'..'9', '+' -> word.append(c)
                in 'A'..'Z' -> word.append(c.lowercaseChar())
                else -> if (word.isNotEmpty()) {
                    words += stem(word.toString())
                    word.setLength(0)
                }
            }
        }
        if (word.isNotEmpty()) {
            words += stem(word.toString())
        }
        return words
    }

    /**
     * Drops a plural ending, so "lobs" and "lobster" share keys with their singular forms.
     */
    private fun stem(word: String): String = when {
        word.length <= 3 || word[0].isDigit() -> word
        word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
        word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }

    /**
     * Applies the multi-word shorthand to [words].
     */
    private fun rewrite(words: List<String>, rewrites: List<Pair<List<String>, List<String>>>): List<String> {
        var result = words
        for ((from, to) in rewrites) {
            var start = 0
            while (start + from.size <= result.size) {
                if (result.subList(start, start + from.size) == from) {
                    result = result.subList(0, start) + to + result.subList(start + from.size, result.size)
                    start += to.size
                } else {
                    start++
                }
            }
        }
        return result
    }

    /**
     * Builds the index from item definitions, nicknames and the shorthand file.
     */
    private fun build(): Index {
        val keys = HashMap<String, IntArray>(8192)
        val nicknamed = HashMap<String, MutableSet<Int>>()
        for (definition in ItemNicknameDefinition.ALL) {
            for (nickname in definition.nicknames) {
                nicknamed.getOrPut(words(nickname).joinToString(" ")) { sortedSetOf() } += definition.id()
            }
        }
        nicknamed.forEach { (key, ids) -> keys[key] = ids.toIntArray() }

        val names = HashMap<String, Int>()
        val fullest = HashMap<String, Pair<Int, Int>>()
        val untradeable = HashMap<String, Int>()
        for (definition in ItemDefinition.ALL) {
            if (definition == null || definition.isNoted || definition.name == "null" ||
                definition.id() in RESTRICTED) {
                continue
            }
            val key = words(definition.name).joinToString(" ")
            if (!definition.isTradeable) {
                untradeable.putIfAbsent(key, definition.id())
                continue
            }
            names.putIfAbsent(key, definition.id())
            DOSE.matchEntire(definition.name)?.let {
                val base = words(it.groupValues[1]).joinToString(" ")
                val dose = it.groupValues[2].toInt()
                if ((fullest[base]?.first ?: -1) < dose) {
                    fullest[base] = dose to definition.id()
                }
            }
        }
        names.forEach { (key, id) -> keys.putIfAbsent(key, intArrayOf(id)) }
        fullest.forEach { (key, dose) -> keys.putIfAbsent(key, intArrayOf(dose.second)) }
        untradeable.forEach { (key, id) -> keys.putIfAbsent(key, intArrayOf(id)) }

        val shorthand = HashMap<String, List<List<String>>>()
        val rewrites = ArrayList<Pair<List<String>, List<String>>>()
        try {
            Files.newBufferedReader(SHORTHAND_PATH).use { reader ->
                val data = GsonUtils.GSON.fromJson(reader, JsonElement::class.java).asJsonObject
                for ((typed, meanings) in data.entrySet()) {
                    val from = words(typed)
                    val to = meanings.asJsonArray.map { words(it.asString) }.filter { it.isNotEmpty() }
                    if (from.isEmpty() || to.isEmpty()) {
                        continue
                    }
                    if (from.size > 1) rewrites += from to to.first() else shorthand[from.first()] = to
                }
            }
        } catch (e: Exception) {
            logger.catching(e)
        }
        return Index(keys, shorthand, rewrites)
    }
}
