package game.skill.guide

/**
 * The content of one skill guide: the guide that opens when a skill is clicked in the stats tab.
 *
 * @property name The name shown as the guide's title.
 * @property categories The categories, one per tab down the right side of the guide.
 * @author Hydrozoa
 */
class SkillGuide(val name: String, val categories: List<SkillGuideCategory>) {

    companion object {

        /**
         * The most categories a guide can have, one per tab on the interface.
         */
        const val MAX_CATEGORIES = 13
    }

    init {
        require(categories.size in 1..MAX_CATEGORIES) {
            "Guide '$name' has ${categories.size} categories, it must have 1 to $MAX_CATEGORIES."
        }
    }
}

/**
 * One category (tab) of a [SkillGuide].
 *
 * @property name The label on the category's tab.
 * @property entries The rows shown when the category is selected.
 * @author Hydrozoa
 */
class SkillGuideCategory(val name: String, val entries: List<SkillGuideEntry>) {

    companion object {

        /**
         * The most rows a category can have, one per row on the interface.
         */
        const val MAX_ENTRIES = 40
    }

    init {
        require(entries.size in 1..MAX_ENTRIES) {
            "Category '$name' has ${entries.size} rows, it must have 1 to $MAX_ENTRIES."
        }
    }
}

/**
 * One row of a [SkillGuideCategory]: the level something is unlocked at, an item icon and a description. A row
 * without a level is plain text, for guides that explain a skill instead of listing unlocks.
 *
 * @property level The level shown in the left column, or `null` for none.
 * @property item The item shown next to the level, or `null` for no icon.
 * @property text The description.
 * @author Hydrozoa
 */
class SkillGuideEntry(val level: Int?, val item: Int?, val text: String)

/**
 * Builds a [SkillGuide] named [name].
 */
fun skillGuide(name: String, block: SkillGuideBuilder.() -> Unit): SkillGuide =
    SkillGuideBuilder(name).apply(block).build()

/**
 * The receiver for [skillGuide].
 *
 * @author lare96
 */
class SkillGuideBuilder(private val name: String) {

    /**
     * The categories added so far.
     */
    private val categories = ArrayList<SkillGuideCategory>()

    /**
     * Adds a category labelled [name], with the rows added in [block].
     */
    fun category(name: String, block: SkillGuideCategoryBuilder.() -> Unit) {
        categories += SkillGuideCategoryBuilder(name).apply(block).build()
    }

    /**
     * Builds the guide.
     */
    fun build() = SkillGuide(name, categories)
}

/**
 * The receiver for [SkillGuideBuilder.category].
 *
 * @author Hydrozoa
 */
class SkillGuideCategoryBuilder(private val name: String) {

    /**
     * The rows added so far.
     */
    private val entries = ArrayList<SkillGuideEntry>()

    /**
     * Adds a row at [level] showing [item] (or no icon when `null`) and [text].
     */
    fun row(level: Int, item: Int?, text: String) {
        entries += SkillGuideEntry(level, item, text)
    }

    /**
     * Adds a row of plain [text], with no level or icon.
     */
    fun text(text: String) {
        entries += SkillGuideEntry(null, null, text)
    }

    /**
     * Builds the category.
     */
    fun build() = SkillGuideCategory(name, entries)
}
