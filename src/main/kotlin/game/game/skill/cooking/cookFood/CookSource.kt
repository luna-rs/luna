package game.skill.cooking.cookFood

/**
 * Where a [Food] can be cooked. [refusal] is shown when the food is used on the wrong kind of source.
 */
enum class CookSource(val refusal: String) {
    ANY(""),
    RANGE_ONLY("You need a proper oven to cook that."),
    FIRE_ONLY("You need to roast that over an open fire.")
}
