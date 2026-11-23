package game.skill.cooking.gnomeCooking

/**
 * The three kinds of gnome dish. Each starts as Gianne dough pressed into its [tin], is half baked, filled, baked
 * again and then topped.
 *
 * @property spoilt What a wrong filling or topping leaves.
 * @property noun What topping messages call the dish.
 */
enum class GnomeDish(val tin: Int,
                     val raw: Int,
                     val halfBaked: Int,
                     val burnt: Int,
                     val spoilt: Int,
                     val noun: String,
                     val shapeMessage: String,
                     val halfBakeMessage: String,
                     val burnMessage: String,
                     val bakeMessage: String,
                     val fullMessage: String,
                     val inspectMessage: String,
                     val plainMessage: String,
                     val contentsMessage: String,
                     private val addMessage: String) {
    BATTA(tin = 2164,
          raw = 2250,
          halfBaked = 2249,
          burnt = 2247,
          spoilt = 2245,
          noun = "gnome batta",
          shapeMessage = "You put the Gianne dough into the batta tin.",
          halfBakeMessage = "You half-cook the batta.",
          burnMessage = "You accidentally burn the batta.",
          bakeMessage = "You put the batta in the oven.",
          fullMessage = "The half baked batta already has enough ingredients.",
          inspectMessage = "You inspect the half baked batta...",
          plainMessage = "The half baked batta is completely plain.",
          contentsMessage = "The half baked batta contains:",
          addMessage = "You add the %s to the gnome batta."),
    CRUNCHIES(tin = 2165,
              raw = 2202,
              halfBaked = 2201,
              burnt = 2199,
              spoilt = 2197,
              noun = "gnome crunchies",
              shapeMessage = "You put the Gianne dough into the crunchy tray.",
              halfBakeMessage = "You half-cook the crunchies.",
              burnMessage = "You accidentally burn the crunchies.",
              bakeMessage = "You put the crunchies in the oven.",
              fullMessage = "The half baked crunchies already have enough ingredients.",
              inspectMessage = "You inspect the half baked crunchies...",
              plainMessage = "The half baked crunchies are completely plain.",
              contentsMessage = "The half baked crunchies contain:",
              addMessage = "You mix the %s into the dough."),
    BOWL(tin = 2166,
         raw = 2178,
         halfBaked = 2177,
         burnt = 2175,
         spoilt = 2173,
         noun = "gnome bowl",
         shapeMessage = "You use the mould to shape the dough into a rough bowl.",
         halfBakeMessage = "You half-cook the gnome bowl.",
         burnMessage = "You accidentally burn the gnome bowl.",
         bakeMessage = "You put the gnome bowl in the oven.",
         fullMessage = "The half baked bowl already has enough ingredients.",
         inspectMessage = "You look into the half baked bowl...",
         plainMessage = "The half baked bowl is empty.",
         contentsMessage = "The half baked bowl contains:",
         addMessage = "You add the %s to the dough bowl.");

    /**
     * The message for adding [ingredient] to this dish's filling.
     */
    fun addMessage(ingredient: Int, name: String) = when (this) {
        BATTA -> when (ingredient) {
            GnomeCooking.DWELLBERRIES -> "You add the dwell berries to the gnome batta."
            GnomeCooking.EQUA_LEAVES -> "You sprinkle the equa leaves over the gnome batta."
            GnomeCooking.SEASONED_LEGS -> "You add the seasoned toad's legs to the gnome batta."
            GnomeCooking.SPICY_WORM -> "You add the spicy king worm to the gnome batta."
            else -> addMessage.format(name)
        }
        CRUNCHIES -> when (ingredient) {
            GnomeCooking.GNOME_SPICE -> "You sprinkle the spice into the dough."
            else -> addMessage.format(name)
        }
        BOWL -> when (ingredient) {
            GnomeCooking.GNOME_SPICE -> "You sprinkle some gnome spice over the dough bowl."
            else -> addMessage.format(name)
        }
    }
}
