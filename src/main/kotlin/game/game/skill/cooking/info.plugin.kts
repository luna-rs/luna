package game.skill.cooking

import api.plugin.dsl.plugin

plugin {
    name = "Cooking"
    description =
        """
        A plugin that enables the Cooking skill. This includes
        -> All "simple" cooking items
        -> Stopping burns after a certain cooking level is reached
        -> Fires burn more frequently than ranges
        -> Fermenting wines
        -> Preparing raw food like bread, pizzas, cakes, etc.
        """
    version = "1.0"
    authors += "lare96"
}