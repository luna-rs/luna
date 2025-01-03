package world.player.login.init

import api.plugin.dsl.plugin

plugin {
    name = "Initialize player"
    description =
        """
        A plugin that prepares players for gameplay. This includes
        -> Sending login messages
        -> Loads gameframe tabs
        -> Loads the player context menu
        
        Amongst other things.
        """
    version = "1.0"
    corePlugin = true
    authors += "lare96"
}