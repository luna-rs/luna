package engine.bot.gear;

 interface BotGearType {

    fun priority(): Int // make sure to keep in list when sorting since duplicate values may be possible
    fun containsId(id: Int): Boolean
    fun containsPurpose(purpose: BotGearPurpose): Boolean

}
