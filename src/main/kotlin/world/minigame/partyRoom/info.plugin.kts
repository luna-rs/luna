package world.minigame.partyRoom

import api.plugin.dsl.plugin

plugin {
    name = "Party Room"
    description =
        """
        A plugin that enables the party room.
        -> Custom lever options as well as the traditional drop party, more can be added
        -> Saves/loads the drop party chest on graceful shutdown of the server
        -> ::party_room command for staff to lock the lever and remotely trigger options
        """
    version = "1.0"
    authors += "lare96"
}