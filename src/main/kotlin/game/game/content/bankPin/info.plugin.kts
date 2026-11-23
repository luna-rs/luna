package game.content.bankPin

import api.plugin.dsl.plugin

plugin {
    name = "Bank PIN"
    description =
        """
        A plugin that lets players lock their bank with a four digit PIN, set from the bankers, and asks for it the
        first time they open their bank after logging in.
        """
    version = "1.0"
    authors += "TheLining"
}
