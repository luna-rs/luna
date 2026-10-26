package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Hitpoints guide. Hitpoints unlocks nothing, so it explains the skill instead.
 */
val hitpointsGuide = skillGuide("Hitpoints") {
    category("Hitpoints") {
        text("Your Hitpoints level is how much damage")
        text("you can take before you die.")
        text("")
        text("When you are hit, your Hitpoints go down.")
        text("They come back slowly over time, and")
        text("eating food heals them faster.")
        text("")
        text("You start with 10 Hitpoints. Dealing")
        text("damage in combat gives you Hitpoints")
        text("experience as well as experience in")
        text("the skill you fight with.")
        text("")
        text("Your Hitpoints level counts towards")
        text("your combat level.")
    }
}
