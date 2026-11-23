package game.item.weaponPoison

/**
 * The kinds of weapon that can be poisoned.
 *
 * @property ammunition Whether a stack is poisoned up to [WeaponPoison.AMMUNITION_PER_VIAL] at a time.
 * @property poisonMessage The message sent after poisoning this kind of weapon.
 * @property noun The word for this kind of weapon in other messages.
 * @author TheLining
 */
enum class WeaponType(val ammunition: Boolean, val poisonMessage: String, val noun: String) {
    ARROWS(true, "You poison some arrows.", "arrows"),
    BOLTS(true, "You poison some bolts.", "bolts"),
    DARTS(true, "You poison some darts.", "darts"),
    KNIVES(true, "You poison some knives.", "knives"),
    JAVELINS(true, "You poison the javelins.", "javelins"),
    DAGGER(false, "You poison the dagger.", "dagger"),
    SPEAR(false, "You poison a spear.", "spear")
}
