package engine.test

enum class GameTestType {
    COMBAT, // logs in <x> bots that will spawn with random items and gear and start fighting each other
    SMART_WANDERING, // logs in <x> bots that will smart wander <x> radius
    IDLE // logs in <x> bots, optional to scatter them around the world, they will do nothing but stay online

}