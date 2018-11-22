import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent

/* A set of objects used for banking. */
private val BANK_OBJECTS = Set(3193, 2213, 3095)

/* Open the banking interface. */
on[ObjectFirstClickEvent].
  condition { msg => BANK_OBJECTS.contains(msg.id) }.
  run { _.plr.bank.open }