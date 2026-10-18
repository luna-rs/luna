package api.bot.webwalk.plan

/**
 * How much the planner dislikes things, as numbers of ticks. The weights are tuned by hand, so they are all here.
 *
 * Most edges already say how long they take, in ticks while running. What is added to that, to steer bots away from things
 * that they would rather not do, are penalties.
 *
 * @property doorTicks The time to open a door, gate or curtain and step through, if the edge doesn't say.
 * @property ladderTicks The time to climb a ladder or staircase, if the edge doesn't say.
 * @property trapdoorTicks The time to open a trapdoor and climb down, if the edge doesn't say.
 * @property crossingTicks The time of a crossing that has a handler, such as a toll gate, if the edge doesn't say.
 * @property shipTicks The time of a ship trip, if the edge doesn't say.
 * @property fairyRingTicks The time of a fairy ring trip.
 * @property coinWeight The penalty for each coin that an edge needs, so that bots don't pay fares and tolls for short trips.
 * Coins are spent, so one coin is worth this many ticks: a toll of 10 coins is worth walking about 10 ticks to avoid.
 * @property runePenalty The penalty for casting a teleport spell, for its runes.
 * @property chargePenalty The penalty for using the charge of teleport jewellery.
 * @property wildernessEntry The penalty for walking into the wilderness, or arriving in it.
 * @property wildernessWalkFactor The penalty for each tick walked in the wilderness, as a multiple of those ticks.
 * @property wildernessLevelScale How much each level of the wilderness adds to that multiple, as a fraction of it.
 * @property linkRadius How far, in tiles, nodes are looked for to link the start and the destination to. This is the range
 * of the route pathfinder, so that the estimates are made without the long range search.
 * @property linkCandidates How many of the nearest nodes are tried to link the start and the destination to.
 * @property shortcutRadius How far, in tiles, a walk may go straight to a later node of the trip instead of through the nodes
 * on the way. It is short of the range of the route pathfinder, because a bot is let go a few tiles before the end of a walk,
 * so it starts the next one a little way off the node.
 * @property scatterRadius How far, in tiles, a waypoint between two walks may be moved, so that bots don't all step on the
 * same tiles. `0` keeps every waypoint where it is.
 * @property scatterAttempts How many tiles are tried for each waypoint that is moved, before it is left where it is.
 * @property alternatives How many other trips are tried to vary the route of a bot.
 * @property jitter How much the cost of each edge may be changed, as a fraction, when trying other trips.
 * @property toleranceFraction How much more than the best trip a bot may take, as a fraction, at the lowest intelligence.
 * @property toleranceMinimum The least ticks that a bot may take more than the best trip, at the lowest intelligence.
 * @property legPenalty A tiny cost for each leg that is only used to choose between trips that cost the same, so that the one
 * with fewer legs wins. It is not part of the cost that a trip is reported to have.
 * @property abundantUses How many times a bot must be able to pay the largest spend of a coin, rune, item or jewellery charge
 * for the planner to stop counting what is left of it on a trip. A trip never spends that much, and counting less is faster.
 * @property maxLabels How many ways of arriving at a node, with different amounts left to spend, the planner keeps at most.
 * @property bankTicks The time to open a bank and withdraw what a trip needs, which is added to a trip by the bank when it is
 * compared with one without it (see [api.bot.webwalk.walk.WebWalker.planTrip]).
 *
 * @author Hydrozoa
 */
data class PlannerCosts(val doorTicks: Double = 3.0,
                        val ladderTicks: Double = 4.0,
                        val trapdoorTicks: Double = 6.0,
                        val crossingTicks: Double = 8.0,
                        val shipTicks: Double = 40.0,
                        val fairyRingTicks: Double = 12.0,
                        val coinWeight: Double = 1.0,
                        val runePenalty: Double = 6.0,
                        val chargePenalty: Double = 12.0,
                        val wildernessEntry: Double = 250.0,
                        val wildernessWalkFactor: Double = 4.0,
                        val wildernessLevelScale: Double = 0.1,
                        val linkRadius: Int = 56,
                        val linkCandidates: Int = 6,
                        val shortcutRadius: Int = 48,
                        val scatterRadius: Int = 2,
                        val scatterAttempts: Int = 4,
                        val alternatives: Int = 8,
                        val jitter: Double = 0.3,
                        val toleranceFraction: Double = 0.2,
                        val toleranceMinimum: Double = 8.0,
                        val legPenalty: Double = 0.01,
                        val abundantUses: Int = 10,
                        val maxLabels: Int = 6,
                        val bankTicks: Double = 10.0)
