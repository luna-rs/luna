package api.bot.webwalk.walk

/**
 * Route failures eligible for recovery, and interruptions that must leave movement with its new owner.
 * Other failures, such as missing requirements or planner errors, have no recovery classification.
 *
 * @author lare96
 */
enum class WebWalkFailure {
    NO_ROUTE,
    EXECUTION,
    INTERRUPTED
}
