package world.player.skill.magic

interface SpellRequirement
class ItemRequirement(val id: Int, val amount: Int = 1) : SpellRequirement
class RuneRequirement(val rune: Rune, val amount: Int) : SpellRequirement