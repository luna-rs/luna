package game.skill.firemaking

/**
 * Logs coated with a treasure trail firelighter. They light like normal logs and burn as a coloured fire.
 */
enum class ColouredLog(val id: Int, val firelighter: Int, val fireId: Int) {
    RED(id = 7404, firelighter = 7329, fireId = 11404),
    GREEN(id = 7405, firelighter = 7330, fireId = 11405),
    BLUE(id = 7406, firelighter = 7331, fireId = 11406);

    companion object {

        /**
         * Experience for lighting any coloured log.
         */
        const val EXP = 50.0
    }
}
