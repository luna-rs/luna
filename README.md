# Luna
Luna is a Runescape emulator for the #317 protocol. It aims to correct all of the issues accumulated over the various releases of it's predecessor [Asteria](https://github.com/lare96/asteria-3.0), and currently runs on Java 8.

# Plugins
Plugins are used to develop all game related content, in order to decouple game logic from the other various abstraction models. The plugins are written in Scala and are then subsequently compiled to Java bytecode. Scala was chosen as the language because of its rich typing system and its ability to enable the user to write exceptionally modular code. A pipeline-like publish-subscribe-style model is used to manage all plugins.


# Thanks (In no specific order)
Ryley Kimmel (AtomicInt_)
