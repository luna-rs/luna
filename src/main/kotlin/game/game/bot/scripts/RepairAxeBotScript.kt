package game.bot.scripts

/**
 * Retains the historical serialized data-class name; all maintenance executes in [RepairGatheringToolBotScript].
 * Central script registration maps the old runner name to that shared implementation.
 *
 * @author lare96
 */
class RepairAxeBotScript private constructor() {
    companion object {
        /**
         * Legacy axe data loads through the shared schema, whose missing family defaults to axes.
         *
         * @author lare96
         */
        class RepairData : RepairGatheringToolBotScript.Companion.RepairData()
    }
}
