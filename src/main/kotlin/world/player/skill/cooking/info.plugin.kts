package world.player.skill.cooking

import api.plugin.dsl.plugin

plugin {
    name = "Cooking"
    description =
        """
        A plugin that enables the Cooking skill. This includes
        -> All "simple" cooking items
        -> Stopping burns after a certain cooking level is reached
        -> Fires burn more frequently than ranges
        
        TODO
        -> bread, pies, stews, pizzas, cakes, wines, hto drinks, veggies, dairy? kebabs?
        """
    version = "1.0"
    authors += "lare96"
}