package world.player.skill.thieving

import api.plugin.dsl.plugin

plugin {
    name = "Thieving"
    description =
        """
        A plugin that enables the Thieving skill. This includes
        -> Pickpocketing NPCs
            -> Getting stunned
        -> Stealing from market stalls
            -> Guards being alerted by the player
            -> Choice between stalls being refreshed "locally" and "globally"
        -> Picking locked chests
        -> Rogue equipment
            -> As a random drop from pickpocketing, stealing, and picking locked chests
            -> Chance of double loot depending on amount of pieces worn
        """
    version = "1.0"
    authors += "lare96"
}