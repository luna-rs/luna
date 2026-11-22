package game.player.item.consume.food

import api.attr.Attr
import api.predef.*
import game.player.Sound
import io.luna.game.model.mob.Player

/**
 * An enum representing food that can be eaten.
 *
 * @author lare96
 */
enum class Food(val heal: Int,
                val delay: Int,
                val id: Int,
                val id2: Int? = null,
                val id3: Int? = null,
                val id4: Int? = null,
                val leftover: Int? = null,
                val sound: Sound = Sound.EAT) {

    BEER(heal = 1,
         delay = 3,
         id = 1917,
         leftover = 1919,
         sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.strength.boost(1 + plr.strength.staticLevel * 2 / 100)
            plr.attack.adjustLevel(-(1 + plr.attack.level * 6 / 100))
            plr.sendMessage("...and slightly dizzy too.")
        }

        override fun consumeMessage(name: String) = "You drink the $name.  You feel slightly reinvigorated..."
        override fun healMessage(name: String): String? = null
    },
    ASGARNIAN_ALE(heal = 2,
                  delay = 3,
                  id = 1905,
                  leftover = 1919,
                  sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.attack.adjustLevel(-(2 + plr.attack.level * 5 / 100))
            plr.strength.boost(2)
            plr.sendMessage("...and slightly dizzy too.")
        }

        override fun consumeMessage(name: String) = "You drink the ale. You feel slightly reinvigorated..."
        override fun healMessage(name: String): String? = null
    },
    WIZARDS_MIND_BOMB(heal = 1,
                      delay = 3,
                      id = 1907,
                      leftover = 1919,
                      sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.magic.boost(2 + plr.magic.staticLevel * 2 / 100)
            plr.attack.adjustLevel(-(1 + plr.attack.level * 5 / 100))
            plr.strength.adjustLevel(-(1 + plr.strength.level * 5 / 100))
            plr.defence.adjustLevel(-(1 + plr.defence.level * 5 / 100))
            plr.sendMessage("You feel very strange.")
        }

        override fun consumeMessage(name: String) = "You drink the Wizard's Mind Bomb."
        override fun healMessage(name: String): String? = null
    },
    GREENMANS_ALE(heal = 1,
                  delay = 3,
                  id = 1909,
                  leftover = 1919,
                  sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.herblore.boost(1)
            plr.attack.adjustLevel(-3)
            plr.strength.adjustLevel(-3)
            plr.defence.adjustLevel(-3)
            plr.sendMessage("It has a strange taste.")
        }

        override fun consumeMessage(name: String) = "You drink the Greenman's Ale."
        override fun healMessage(name: String): String? = null
    },
    DRAGON_BITTER(heal = 1,
                  delay = 3,
                  id = 1911,
                  leftover = 1919,
                  sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.strength.boost(2)
            plr.attack.adjustLevel(-(2 + plr.attack.level * 5 / 100))
            plr.sendMessage("...and slightly dizzy too.")
        }

        override fun consumeMessage(name: String) = "You drink the Dragon Bitter. You feel slightly reinvigorated..."
        override fun healMessage(name: String): String? = null
    },
    DWARVEN_STOUT(heal = 1,
                  delay = 3,
                  id = 1913,
                  leftover = 1919,
                  sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.mining.boost(1)
            plr.smithing.boost(1)
            plr.attack.adjustLevel(-(2 + plr.attack.level * 4 / 100))
            plr.strength.adjustLevel(-(2 + plr.strength.level * 4 / 100))
            plr.defence.adjustLevel(-(2 + plr.defence.level * 4 / 100))
            plr.sendMessage("It tastes pretty strong too.")
        }

        override fun consumeMessage(name: String) = "You drink the Dwarven Stout. It tastes foul."
        override fun healMessage(name: String): String? = null
    },
    GROG(heal = 3,
         delay = 3,
         id = 1915,
         leftover = 1919,
         sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.strength.boost(1 + plr.strength.staticLevel * 4 / 100)
            plr.attack.adjustLevel(-(5 + plr.attack.level * 3 / 100))
            plr.sendMessage("...and slightly dizzy too.")
        }

        override fun consumeMessage(name: String) = "You drink the Grog. You feel slightly reinvigorated..."
        override fun healMessage(name: String): String? = null
    },
    MOONLIGHT_MEAD(heal = 4,
                   delay = 3,
                   id = 2955,
                   leftover = 1919,
                   sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.sendMessage("It tastes like something just died in your mouth.")
        }

        override fun consumeMessage(name: String) = "You drink the foul smelling brew."
        override fun healMessage(name: String): String? = null
    },
    KEBAB(heal = 0,
          delay = 3,
          id = 1971) {
        override fun effect(plr: Player) = plr.onKebab()
        override fun healMessage(name: String): String? = null
    },
    WINE(heal = 11,
         delay = 3,
         id = 1993,
         leftover = 1935,
         sound = Sound.LIQUID) {
        override fun effect(plr: Player) = plr.onWine(2)
        override fun consumeMessage(name: String) = "You drink the wine."
        override fun healMessage(name: String) = "It makes you feel a bit dizzy, but it heals some health."
    },
    HALF_WINE(heal = 7,
              delay = 3,
              id = 1989,
              leftover = 1935,
              sound = Sound.LIQUID) {
        override fun effect(plr: Player) = plr.onWine(2)
        override fun consumeMessage(name: String) = "You drink the wine."
        override fun healMessage(name: String) = "It makes you feel a bit dizzy, but it heals some health."
    },
    BAD_WINE(heal = 0,
             delay = 3,
             id = 1991,
             leftover = 1935,
             sound = Sound.LIQUID) {
        override fun effect(plr: Player) = plr.onWine(3, false)
        override fun consumeMessage(name: String) = "You drink the wine."
        override fun healMessage(name: String): String? = null
    },

    BANANA(heal = 2,
           delay = 3,
           id = 1963),
    MEAT(heal = 2,
         delay = 3,
         id = 2142),
    CHICKEN(heal = 2,
            delay = 3,
            id = 2140),
    HERRING(heal = 2,
            delay = 3,
            id = 347),
    ANCHOVIES(heal = 2,
              delay = 3,
              id = 319),
    REDBERRY_PIE(heal = 2,
                 delay = 1,
                 id = 2325, id2 = 2333,
                 leftover = 2313),
    SHRIMP(heal = 3,
           delay = 3,
           id = 315),
    CAKE(heal = 4,
         delay = 3,
         id = 1891, id2 = 1893, id3 = 1895),
    COD(heal = 4,
        delay = 3,
        id = 339),
    PIKE(heal = 4,
         delay = 3,
         id = 351),
    CHOCOLATE_CAKE(heal = 5,
                   delay = 3,
                   id = 1897, id2 = 1899, id3 = 1901),
    MACKEREL(heal = 6,
             delay = 3,
             id = 355),
    MEAT_PIE(heal = 6,
             delay = 1,
             id = 2327, id2 = 2331,
             leftover = 2313),
    PLAIN_PIZZA(heal = 7,
                delay = 3,
                id = 2289, id2 = 2291),
    APPLE_PIE(heal = 7,
              delay = 1,
              id = 2323, id2 = 2335,
              leftover = 2313),
    TROUT(heal = 7,
          delay = 3,
          id = 333),
    PEACH(heal = 8,
          delay = 3,
          id = 6883),
    MEAT_PIZZA(heal = 8,
               delay = 3,
               id = 2293, id2 = 2295),
    ANCHOVY_PIZZA(heal = 9,
                  delay = 3,
                  id = 2297, id2 = 2299),
    SALMON(heal = 9,
           delay = 3,
           id = 329),
    BASS(heal = 9,
         delay = 3,
         id = 365),
    TUNA(heal = 10,
         delay = 3,
         id = 361),
    PINEAPPLE_PIZZA(heal = 11,
                    delay = 3,
                    id = 2301, id2 = 2303),
    SUMMER_PIE(heal = 11,
               delay = 1,
               id = 7218, id2 = 7220,
               leftover = 2313) {
        override fun effect(plr: Player) {
            plr.increaseRunEnergy(10.0)
            plr.agility.boost(5)
        }

        override fun consumeMessage(name: String, portion: Int) =
            if (portion == id) "You eat half the $name." else "You eat the remaining $name."

        override fun healMessage(name: String): String? = null
    },
    LOBSTER(heal = 12,
            delay = 3,
            id = 379),
    SWORDFISH(heal = 14,
              delay = 3,
              id = 373),
    MONKFISH(heal = 16,
             delay = 3,
             id = 7946),
    KARAMBWAN(heal = 18,
              delay = 1,
              id = 3144),
    SHARK(heal = 20,
          delay = 3,
          id = 385),
    MANTA_RAY(heal = 22,
              delay = 3,
              id = 391),
    SEA_TURTLE(heal = 22,
               delay = 3,
               id = 397),
    TUNA_POTATO(heal = 22,
                delay = 3,
                id = 7060),
    SARDINE(heal = 4,
            delay = 3,
            id = 325),
    BREAD(heal = 5,
          delay = 3,
          id = 2309),
    CHEESE(heal = 2,
           delay = 3,
           id = 1985),
    STEW(heal = 11,
         delay = 3,
         id = 2003,
         leftover = 1923),
    CURRY(heal = 19,
          delay = 3,
          id = 2011,
          leftover = 1923),
    KARAMBWANJI(heal = 3,
                delay = 3,
                id = 3151),
    COOKED_RABBIT(heal = 5,
                  delay = 3,
                  id = 3228),
    ROAST_RABBIT(heal = 7,
                 delay = 3,
                 id = 7223),
    THIN_SNAIL(heal = 5,
               delay = 3,
               id = 3369) {
        override fun effect(plr: Player) {
            plr.healUpTo(2)
        }
    },
    LEAN_SNAIL(heal = 6,
               delay = 3,
               id = 3371) {
        override fun effect(plr: Player) {
            plr.healUpTo(2)
        }
    },
    FAT_SNAIL(heal = 7,
              delay = 3,
              id = 3373) {
        override fun effect(plr: Player) {
            plr.healUpTo(2)
        }
    },
    SLIMY_EEL(heal = 6,
              delay = 3,
              id = 3381) {
        override fun effect(plr: Player) {
            plr.healUpTo(4)
        }
    },
    CAVE_EEL(heal = 7,
             delay = 3,
             id = 5003) {
        override fun effect(plr: Player) {
            plr.healUpTo(4)
        }
    },
    LAVA_EEL(heal = 11,
             delay = 3,
             id = 2149),
    CHOMPY(heal = 10,
           delay = 3,
           id = 2878),
    JUBBLY(heal = 15,
           delay = 3,
           id = 7568),
    FISHCAKE(heal = 11,
             delay = 3,
             id = 7530),
    CRAB_MEAT(heal = 2,
              delay = 3,
              id = 7521, id2 = 7523, id3 = 7524, id4 = 7525),
    LAST_CRAB_MEAT(heal = 2,
                   delay = 3,
                   id = 7526),
    OOMLIE_WRAP(heal = 14,
                delay = 3,
                id = 2343) {
        override fun consumeMessage(name: String) = "You eat the oomlie meat; it tastes very gamy."
    },
    UGTHANKI_KEBAB(heal = 19,
                   delay = 3,
                   id = 1885) {
        override fun effect(plr: Player) {
            plr.speak("Yum!")
        }

        override fun consumeMessage(name: String) = "You eat the ugthanki kebab."
    },
    BAKED_POTATO(heal = 4,
                 delay = 3,
                 id = 6701),
    POTATO_WITH_BUTTER(heal = 14,
                       delay = 3,
                       id = 6703),
    POTATO_WITH_CHEESE(heal = 16,
                       delay = 3,
                       id = 6705),
    CHILLI_POTATO(heal = 14,
                  delay = 3,
                  id = 7054),
    EGG_POTATO(heal = 16,
               delay = 3,
               id = 7056),
    MUSHROOM_POTATO(heal = 20,
                    delay = 3,
                    id = 7058),
    SPICY_SAUCE(heal = 2,
                delay = 3,
                id = 7072,
                leftover = 1923),
    CHILLI_CON_CARNE(heal = 5,
                     delay = 3,
                     id = 7062,
                     leftover = 1923),
    SCRAMBLED_EGG(heal = 5,
                  delay = 3,
                  id = 7078,
                  leftover = 1923),
    EGG_AND_TOMATO(heal = 8,
                   delay = 3,
                   id = 7064,
                   leftover = 1923),
    FRIED_ONIONS(heal = 5,
                 delay = 3,
                 id = 7084,
                 leftover = 1923),
    FRIED_MUSHROOMS(heal = 5,
                    delay = 3,
                    id = 7082,
                    leftover = 1923),
    MUSHROOM_AND_ONION(heal = 11,
                       delay = 3,
                       id = 7066,
                       leftover = 1923),
    TUNA_AND_CORN(heal = 13,
                  delay = 3,
                  id = 7068,
                  leftover = 1923),
    GARDEN_PIE(heal = 6,
               delay = 1,
               id = 7178, id2 = 7180,
               leftover = 2313) {
        override fun effect(plr: Player) {
            plr.farming.boost(3)
        }
    },
    FISH_PIE(heal = 6,
             delay = 1,
             id = 7188, id2 = 7190,
             leftover = 2313) {
        override fun effect(plr: Player) {
            plr.fishing.boost(3)
        }
    },
    ADMIRAL_PIE(heal = 8,
                delay = 1,
                id = 7198, id2 = 7200,
                leftover = 2313) {
        override fun effect(plr: Player) {
            plr.fishing.boost(5)
        }
    },
    WILD_PIE(heal = 11,
             delay = 1,
             id = 7208, id2 = 7210,
             leftover = 2313) {
        override fun effect(plr: Player) {
            plr.slayer.boost(5)
            plr.ranged.boost(4)
        }
    },
    NETTLE_TEA(heal = 3,
               delay = 3,
               id = 4239,
               leftover = 1923,
               sound = Sound.LIQUID) {
        override fun consumeMessage(name: String) = "You drink the nettle tea."
    },
    MILKY_NETTLE_TEA(heal = 3,
                     delay = 3,
                     id = 4240,
                     leftover = 1923,
                     sound = Sound.LIQUID) {
        override fun consumeMessage(name: String) = "You drink the nettle tea."
    },
    CUP_OF_NETTLE_TEA(heal = 3,
                      delay = 3,
                      id = 4242,
                      leftover = 1980,
                      sound = Sound.LIQUID) {
        override fun consumeMessage(name: String) = "You drink the nettle tea."
    },
    MILKY_CUP_OF_NETTLE_TEA(heal = 3,
                            delay = 3,
                            id = 4243,
                            leftover = 1980,
                            sound = Sound.LIQUID) {
        override fun consumeMessage(name: String) = "You drink the nettle tea."
    },
    EASTER_EGG(heal = 12,
               delay = 3,
               id = 1961) {
        override fun healMessage(name: String): String? = null
    },
    POTATO(heal = 1,
           delay = 3,
           id = 1942),
    ONION(heal = 1,
          delay = 3,
          id = 1957),
    CABBAGE(heal = 1,
            delay = 3,
            id = 1965) {
        override fun consumeMessage(name: String) = "You eat the cabbage. Yuck!"
        override fun healMessage(name: String) = "It heals some health anyway."
    },
    DRAYNOR_CABBAGE(heal = 1,
                    delay = 3,
                    id = 1967) {
        override fun effect(plr: Player) {
            plr.defence.boost(1 + plr.defence.staticLevel * 2 / 100)
            plr.whenFull("It seems to taste nicer than normal.")
        }
    },
    TOMATO(heal = 2,
           delay = 3,
           id = 1982),
    CHOCOLATE_BAR(heal = 3,
                  delay = 3,
                  id = 1973),
    SPINACH_ROLL(heal = 2,
                 delay = 3,
                 id = 1969) {
        override fun healMessage(name: String) = "It tastes a bit weird, but it fills you up."
    },
    PUMPKIN(heal = 14,
            delay = 3,
            id = 1959) {
        override fun healMessage(name: String): String? = null
    },
    UGTHANKI_MEAT(heal = 3,
                  delay = 3,
                  id = 1861),
    GIANT_CARP(heal = 6,
               delay = 3,
               id = 337),
    JANGERBERRIES(heal = 2,
                  delay = 3,
                  id = 247) {
        override fun effect(plr: Player) {
            plr.attack.boost(2)
            plr.strength.boost(1)
            plr.defence.adjustLevel(-1)
            plr.prayer.adjustLevel(1)
            plr.sendMessage("They taste very bitter.")
        }

        override fun healMessage(name: String): String? = null
    },
    LEMON(heal = 2,
          delay = 3,
          id = 2102) {
        override fun consumeMessage(name: String) = "You eat the lemon; it's very sour."
    },
    LEMON_CHUNKS(heal = 2,
                 delay = 3,
                 id = 2104) {
        override fun consumeMessage(name: String) = "You eat the lemon chunks; they're very sour."
        override fun healMessage(name: String) = "They heal some health."
    },
    LEMON_SLICES(heal = 2,
                 delay = 3,
                 id = 2106) {
        override fun consumeMessage(name: String) = "You eat the lemon slices; they're very sour."
        override fun healMessage(name: String) = "They heal some health."
    },
    ORANGE(heal = 2,
           delay = 3,
           id = 2108),
    ORANGE_CHUNKS(heal = 2,
                  delay = 3,
                  id = 2110) {
        override fun consumeMessage(name: String) = "You eat the orange chunks. Yum."
        override fun healMessage(name: String) = "They heal some health."
    },
    ORANGE_SLICES(heal = 2,
                  delay = 3,
                  id = 2112) {
        override fun consumeMessage(name: String) = "You eat the orange slices. Yum."
        override fun healMessage(name: String) = "They heal some health."
    },
    LIME(heal = 2,
         delay = 3,
         id = 2120) {
        override fun consumeMessage(name: String) = "You eat the lime; it's quite sour."
    },
    LIME_CHUNKS(heal = 2,
                delay = 3,
                id = 2122) {
        override fun consumeMessage(name: String) = "You eat the lime chunks; they're quite sour."
        override fun healMessage(name: String) = "They heal some health."
    },
    LIME_SLICES(heal = 2,
                delay = 3,
                id = 2124) {
        override fun consumeMessage(name: String) = "You eat the lime slices; they're quite sour."
        override fun healMessage(name: String) = "They heal some health."
    },
    PINEAPPLE_CHUNKS(heal = 2,
                     delay = 3,
                     id = 2116) {
        override fun consumeMessage(name: String) = "You eat the pineapple chunks. Yum."
        override fun healMessage(name: String) = "They heal some health."
    },
    PINEAPPLE_RING(heal = 2,
                   delay = 3,
                   id = 2118),
    DWELLBERRIES(heal = 2,
                 delay = 3,
                 id = 2126) {
        override fun consumeMessage(name: String) = "You eat the berries. They're quite tasty."
        override fun healMessage(name: String) = "They heal some health."
    },
    EQUA_LEAVES(heal = 1,
                delay = 3,
                id = 2128) {
        override fun consumeMessage(name: String) = "You eat the leaves; chewy but tasty."
        override fun healMessage(name: String) = "They heal some health."
    },
    POT_OF_CREAM(heal = 1,
                 delay = 3,
                 id = 2130) {
        override fun consumeMessage(name: String) = "You eat the cream. You get some on your nose."
    },
    TOADS_LEGS(heal = 3,
               delay = 3,
               id = 2152) {
        override fun consumeMessage(name: String) = "You eat the toad's legs. They're a bit chewy."
        override fun healMessage(name: String): String? = null
    },
    EQUA_TOADS_LEGS(heal = 3,
                    delay = 3,
                    id = 2154) {
        override fun consumeMessage(name: String) = "You eat the equa toad's legs. They're a bit chewy."
        override fun healMessage(name: String): String? = null
    },
    SPICY_TOADS_LEGS(heal = 3,
                     delay = 3,
                     id = 2156) {
        override fun consumeMessage(name: String) = "You eat the spicy toad's legs. They're a bit chewy."
        override fun healMessage(name: String): String? = null
    },
    SEASONED_LEGS(heal = 3,
                  delay = 3,
                  id = 2158) {
        override fun consumeMessage(name: String) = "You eat the seasoned toad's legs. They're a bit chewy."
        override fun healMessage(name: String): String? = null
    },
    SPICY_WORM(heal = 3,
               delay = 3,
               id = 2160) {
        override fun consumeMessage(name: String) = "You eat the spicy worm. They're a bit chewy."
        override fun healMessage(name: String): String? = null
    },
    KING_WORM(heal = 2,
              delay = 3,
              id = 2162) {
        override fun consumeMessage(name: String) = "You eat the king worm. They're a bit chewy."
        override fun healMessage(name: String): String? = null
    },
    CUP_OF_TEA(heal = 3,
               delay = 3,
               id = 1978,
               leftover = 1980,
               sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.attack.boost(2 + plr.attack.staticLevel * 2 / 100)
            plr.speak("Aaah, nothing like a nice cuppa tea!")
        }

        override fun consumeMessage(name: String) = "You drink the tea."
        override fun healMessage(name: String): String? = null
    },
    VODKA(heal = 5,
          delay = 3,
          id = 2015,
          sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.onSpirit()
        }

        override fun consumeMessage(name: String) = "You drink the $name. You feel slightly reinvigorated..."
        override fun healMessage(name: String): String? = null
    },
    WHISKY(heal = 5,
           delay = 3,
           id = 2017,
           sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.onSpirit()
        }

        override fun consumeMessage(name: String) = "You drink the $name. You feel slightly reinvigorated..."
        override fun healMessage(name: String): String? = null
    },
    GIN(heal = 5,
        delay = 3,
        id = 2019,
        sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.onSpirit()
        }

        override fun consumeMessage(name: String) = "You drink the $name. You feel slightly reinvigorated..."
        override fun healMessage(name: String): String? = null
    },
    BRANDY(heal = 5,
           delay = 3,
           id = 2021,
           sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.onSpirit()
        }

        override fun consumeMessage(name: String) = "You drink the $name. You feel slightly reinvigorated..."
        override fun healMessage(name: String): String? = null
    },
    CHOCCHIP_CRUNCHIES(heal = 7,
                       delay = 3,
                       id = 2209) {
        override fun effect(plr: Player) {
            plr.whenFull("They're very tasty.")
        }

        override fun consumeMessage(name: String) = "You eat the choc chip crunchies."
        override fun healMessage(name: String) = "They're very tasty. They heal some health."
    },
    PREMADE_CHOCCHIP_CRUNCHIES(heal = 7,
                               delay = 3,
                               id = 2239) {
        override fun effect(plr: Player) {
            plr.whenFull("They're very tasty.")
        }

        override fun consumeMessage(name: String) = "You eat the choc chip crunchies."
        override fun healMessage(name: String) = "They're very tasty. They heal some health."
    },
    SPICY_CRUNCHIES(heal = 7,
                    delay = 3,
                    id = 2213) {
        override fun effect(plr: Player) {
            plr.whenFull("They taste great.")
        }

        override fun consumeMessage(name: String) = "You eat the spicy crunchies."
        override fun healMessage(name: String) = "They taste great, you feel much better."
    },
    PREMADE_SPICY_CRUNCHIES(heal = 7,
                            delay = 3,
                            id = 2241) {
        override fun effect(plr: Player) {
            plr.whenFull("They taste great.")
        }

        override fun consumeMessage(name: String) = "You eat the spicy crunchies."
        override fun healMessage(name: String) = "They taste great, you feel much better."
    },
    WORM_CRUNCHIES(heal = 8,
                   delay = 3,
                   id = 2205) {
        override fun consumeMessage(name: String) =
            "You eat the worm crunchies. They're somehow both very crunchy and very chewy."
        override fun healMessage(name: String): String? = null
    },
    PREMADE_WORM_CRUNCHIES(heal = 8,
                           delay = 3,
                           id = 2237) {
        override fun consumeMessage(name: String) =
            "You eat the worm crunchies. They're somehow both very crunchy and very chewy."
        override fun healMessage(name: String): String? = null
    },
    TOAD_CRUNCHIES(heal = 8,
                   delay = 3,
                   id = 2217) {
        override fun effect(plr: Player) {
            plr.whenFull("They certainly taste... unique.")
        }

        override fun consumeMessage(name: String) = "You eat the toad crunchies."
        override fun healMessage(name: String) = "They certainly taste... unique. They heal some health."
    },
    PREMADE_TOAD_CRUNCHIES(heal = 8,
                           delay = 3,
                           id = 2243) {
        override fun effect(plr: Player) {
            plr.whenFull("They certainly taste... unique.")
        }

        override fun consumeMessage(name: String) = "You eat the toad crunchies."
        override fun healMessage(name: String) = "They certainly taste... unique. They heal some health."
    },
    VEGETABLE_BATTA(heal = 10,
                    delay = 3,
                    id = 2281) {
        override fun effect(plr: Player) {
            plr.whenFull("It tastes pretty good.")
        }

        override fun healMessage(name: String) = "It tastes pretty good and makes you feel a bit better."
    },
    PREMADE_VEGETABLE_BATTA(heal = 10,
                            delay = 3,
                            id = 2227) {
        override fun effect(plr: Player) {
            plr.whenFull("It tastes pretty good.")
        }

        override fun healMessage(name: String) = "It tastes pretty good and makes you feel a bit better."
    },
    FRUIT_BATTA(heal = 11,
                delay = 3,
                id = 2277) {
        override fun effect(plr: Player) {
            plr.whenFull("It tastes pretty good.")
        }

        override fun healMessage(name: String) = "It tastes pretty good and makes you feel a bit better."
    },
    PREMADE_FRUIT_BATTA(heal = 11,
                        delay = 3,
                        id = 2225) {
        override fun effect(plr: Player) {
            plr.whenFull("It tastes pretty good.")
        }

        override fun healMessage(name: String) = "It tastes pretty good and makes you feel a bit better."
    },
    WORM_BATTA(heal = 11,
               delay = 3,
               id = 2253) {
        override fun effect(plr: Player) {
            plr.whenFull("It's a bit chewy.")
        }

        override fun healMessage(name: String) = "It's a bit chewy, but it heals some health."
    },
    PREMADE_WORM_BATTA(heal = 11,
                       delay = 3,
                       id = 2219) {
        override fun effect(plr: Player) {
            plr.whenFull("It's a bit chewy.")
        }

        override fun healMessage(name: String) = "It's a bit chewy, but it heals some health."
    },
    TOAD_BATTA(heal = 11,
               delay = 3,
               id = 2255) {
        override fun effect(plr: Player) {
            plr.whenFull("It's a bit chewy.")
        }

        override fun healMessage(name: String) = "It's a bit chewy, but it heals some health."
    },
    PREMADE_TOAD_BATTA(heal = 11,
                       delay = 3,
                       id = 2221) {
        override fun effect(plr: Player) {
            plr.whenFull("It's a bit chewy.")
        }

        override fun healMessage(name: String) = "It's a bit chewy, but it heals some health."
    },
    CHEESE_AND_TOMATO_BATTA(heal = 11,
                            delay = 3,
                            id = 2259) {
        override fun effect(plr: Player) {
            plr.whenFull("It tastes quite good.")
        }

        override fun consumeMessage(name: String) = "You eat the cheese and tomato batta."
        override fun healMessage(name: String) = "It tastes quite good and makes you feel a bit better."
    },
    PREMADE_CHEESE_AND_TOMATO_BATTA(heal = 11,
                                    delay = 3,
                                    id = 2223) {
        override fun effect(plr: Player) {
            plr.whenFull("It tastes quite good.")
        }

        override fun consumeMessage(name: String) = "You eat the cheese and tomato batta."
        override fun healMessage(name: String) = "It tastes quite good and makes you feel a bit better."
    },
    WORM_HOLE(heal = 12,
              delay = 3,
              id = 2191) {
        override fun effect(plr: Player) {
            plr.whenFull("It tastes awful.")
        }

        override fun consumeMessage(name: String) = "You eat the worm hole."
        override fun healMessage(name: String) = "It tastes awful, but it heals little health."
    },
    PREMADE_WORM_HOLE(heal = 12,
                      delay = 3,
                      id = 2233) {
        override fun effect(plr: Player) {
            plr.whenFull("It tastes awful.")
        }

        override fun consumeMessage(name: String) = "You eat the worm hole."
        override fun healMessage(name: String) = "It tastes awful, but it heals little health."
    },
    VEG_BALL(heal = 12,
             delay = 3,
             id = 2195) {
        override fun effect(plr: Player) {
            plr.whenFull("It tastes quite good.")
        }

        override fun consumeMessage(name: String) = "You eat the veg ball."
        override fun healMessage(name: String) = "It tastes quite good and makes you feel a bit better."
    },
    PREMADE_VEG_BALL(heal = 12,
                     delay = 3,
                     id = 2235) {
        override fun effect(plr: Player) {
            plr.whenFull("It tastes quite good.")
        }

        override fun consumeMessage(name: String) = "You eat the veg ball."
        override fun healMessage(name: String) = "It tastes quite good and makes you feel a bit better."
    },
    CHOCOLATE_BOMB(heal = 15,
                   delay = 3,
                   id = 2185) {
        override fun effect(plr: Player) {
            plr.whenFull("It tastes great.")
        }

        override fun consumeMessage(name: String) = "You eat the chocolate bomb."
        override fun healMessage(name: String) = "It tastes great and makes you feel much better."
    },
    PREMADE_CHOCOLATE_BOMB(heal = 15,
                           delay = 3,
                           id = 2229) {
        override fun effect(plr: Player) {
            plr.whenFull("It tastes great.")
        }

        override fun consumeMessage(name: String) = "You eat the chocolate bomb."
        override fun healMessage(name: String) = "It tastes great and makes you feel much better."
    },
    TANGLED_TOADS_LEGS(heal = 15,
                       delay = 3,
                       id = 2187) {
        override fun effect(plr: Player) {
            plr.whenFull("It tastes... slimy.")
        }

        override fun consumeMessage(name: String) = "You eat the tangled toads legs."
        override fun healMessage(name: String) = "It tastes... slimy, but it heals some health."
    },
    PREMADE_TANGLED_TOADS_LEGS(heal = 15,
                               delay = 3,
                               id = 2231) {
        override fun effect(plr: Player) {
            plr.whenFull("It tastes... slimy.")
        }

        override fun consumeMessage(name: String) = "You eat the tangled toads legs."
        override fun healMessage(name: String) = "It tastes... slimy, but it heals some health."
    },
    FRUIT_BLAST(heal = 9,
                delay = 3,
                id = 2084,
                leftover = 2026,
                sound = Sound.LIQUID) {
        override fun consumeMessage(name: String) = "You drink the cocktail. It tastes great."
        override fun healMessage(name: String) = "You feel slightly reinvigorated."
    },
    PREMADE_FRUIT_BLAST(heal = 9,
                        delay = 3,
                        id = 2034,
                        leftover = 2026,
                        sound = Sound.LIQUID) {
        override fun consumeMessage(name: String) = "You drink the cocktail. It tastes great."
        override fun healMessage(name: String) = "You feel slightly reinvigorated."
    },
    PINEAPPLE_PUNCH(heal = 9,
                    delay = 3,
                    id = 2048,
                    leftover = 2026,
                    sound = Sound.LIQUID) {
        override fun consumeMessage(name: String) = "You drink the cocktail. It tastes great."
        override fun healMessage(name: String) = "You feel slightly reinvigorated."
    },
    PREMADE_PINEAPPLE_PUNCH(heal = 9,
                            delay = 3,
                            id = 2036,
                            leftover = 2026,
                            sound = Sound.LIQUID) {
        override fun consumeMessage(name: String) = "You drink the cocktail. It tastes great."
        override fun healMessage(name: String) = "You feel slightly reinvigorated."
    },
    BLURBERRY_SPECIAL(heal = 7,
                      delay = 3,
                      id = 2064,
                      leftover = 2026,
                      sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.onCocktail(2, 5)
        }

        override fun consumeMessage(name: String) = "You drink the cocktail. It tastes great,"
        override fun healMessage(name: String): String? = null
    },
    PREMADE_BLURBERRY_SPECIAL(heal = 7,
                              delay = 3,
                              id = 2028,
                              leftover = 2026,
                              sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.onCocktail(2, 5)
        }

        override fun consumeMessage(name: String) = "You drink the cocktail. It tastes great,"
        override fun healMessage(name: String): String? = null
    },
    CHOC_SATURDAY(heal = 5,
                  delay = 3,
                  id = 2074,
                  leftover = 2026,
                  sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.onCocktail(2, 5)
        }

        override fun consumeMessage(name: String) = "You drink the cocktail. It tastes great,"
        override fun healMessage(name: String): String? = null
    },
    PREMADE_CHOC_SATURDAY(heal = 5,
                          delay = 3,
                          id = 2030,
                          leftover = 2026,
                          sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.onCocktail(2, 5)
        }

        override fun consumeMessage(name: String) = "You drink the cocktail. It tastes great,"
        override fun healMessage(name: String): String? = null
    },
    DRUNK_DRAGON(heal = 5,
                 delay = 3,
                 id = 2092,
                 leftover = 2026,
                 sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.onCocktail(2, 5)
        }

        override fun consumeMessage(name: String) = "You drink the cocktail. It tastes great,"
        override fun healMessage(name: String): String? = null
    },
    PREMADE_DRUNK_DRAGON(heal = 5,
                         delay = 3,
                         id = 2032,
                         leftover = 2026,
                         sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.onCocktail(2, 5)
        }

        override fun consumeMessage(name: String) = "You drink the cocktail. It tastes great,"
        override fun healMessage(name: String): String? = null
    },
    WIZARD_BLIZZARD(heal = 5,
                    delay = 3,
                    id = 2054,
                    leftover = 2026,
                    sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.onCocktail(1, 6)
        }

        override fun consumeMessage(name: String) = "You drink the cocktail. It tastes great,"
        override fun healMessage(name: String): String? = null
    },
    PREMADE_WIZARD_BLIZZARD(heal = 5,
                            delay = 3,
                            id = 2040,
                            leftover = 2026,
                            sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.onCocktail(1, 6)
        }

        override fun consumeMessage(name: String) = "You drink the cocktail. It tastes great,"
        override fun healMessage(name: String): String? = null
    },
    SHORT_GREEN_GUY(heal = 5,
                    delay = 3,
                    id = 2080,
                    leftover = 2026,
                    sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.onCocktail(1, 6)
        }

        override fun consumeMessage(name: String) = "You drink the cocktail. It tastes great,"
        override fun healMessage(name: String): String? = null
    },
    PREMADE_SHORT_GREEN_GUY(heal = 5,
                            delay = 3,
                            id = 2038,
                            leftover = 2026,
                            sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.onCocktail(1, 6)
        }

        override fun consumeMessage(name: String) = "You drink the cocktail. It tastes great,"
        override fun healMessage(name: String): String? = null
    },
    PURPLE_SWEETS(heal = 3,
                  delay = 3,
                  id = 4561) {
        override fun effect(plr: Player) = plr.increaseRunEnergy(10.0)
        override fun consumeMessage(name: String) = "You eat the sweets."
        override fun healMessage(name: String) = "The sugary goodness heals some energy."
    };

    companion object {

        /**
         * The highest-healing food currently defined.
         *
         * This is used as the fallback upper bound when a bot needs to know the best available food type without
         * caring about cost, availability, or combat level.
         */
        val BEST_FOOD = Food.values()
            .maxByOrNull { it.heal }
            ?.let { it to it.heal }
            ?: (MEAT to MEAT.heal)

        /**
         * Mappings of [Food.ids] to [Food].
         */
        val ID_TO_FOOD = HashMap<Int, Food>().apply {
            for (food in Food.values()) {
                for (id in food.ids) {
                    this[id] = food
                }
            }
        }

        /**
         * Throttles how often the player can eat.
         */
        val Player.lastEat by Attr.timeSource()

        /**
         * Applies wine's Attack drain and the message used when it cannot restore health.
         */
        private fun Player.onWine(drain: Int, heals: Boolean = true) {
            attack.adjustLevel(-drain)
            if (!heals || hitpoints.level >= hitpoints.staticLevel) {
                sendMessage("It makes you feel a bit dizzy.")
            }
        }

        /**
         * Heals up to [extra] more hitpoints, for food whose healing varies from one bite to the next.
         */
        private fun Player.healUpTo(extra: Int) = hitpoints.adjustLevel(rand(extra))

        /**
         * Sends [message] when the player has no health to restore, in place of the food's heal message.
         */
        private fun Player.whenFull(message: String) {
            if (hitpoints.level >= hitpoints.staticLevel) {
                sendMessage(message)
            }
        }

        /**
         * Applies the Strength boost and Attack drain shared by vodka, whisky, gin and brandy.
         */
        private fun Player.onSpirit() {
            strength.boost(1 + strength.staticLevel * 5 / 100)
            attack.adjustLevel(-(3 + attack.level * 2 / 100))
            sendMessage("...and slightly dizzy too.")
        }

        /**
         * Applies an alcoholic gnome cocktail's Strength boost of [boost] plus [percent] percent, and its Attack drain.
         */
        private fun Player.onCocktail(boost: Int, percent: Int) {
            strength.boost(boost + strength.staticLevel * percent / 100)
            attack.adjustLevel(-(3 + attack.level * 2 / 100))
            sendMessage("although you feel slightly dizzy.")
        }

        /**
         * Applies a kebab's weighted effects, including the Hitpoints penalties used before July 2007.
         * Healing is handled here because each kebab rolls one outcome instead of restoring a fixed amount.
         */
        private fun Player.onKebab() {
            val hp = hitpoints
            val heal = when (rand(31)) {
                0 -> {
                    sendMessage("Wow, that was an amazing kebab! You feel really invigorated.")
                    attack.boost(2)
                    strength.boost(2)
                    defence.boost(2)
                    7 + hp.staticLevel * 24 / 100
                }
                in 1..8 -> {
                    sendMessage("That was a good kebab. You feel a lot better.")
                    6 + hp.staticLevel * 14 / 100
                }
                in 9..28 -> {
                    if (hp.level < hp.staticLevel) {
                        sendMessage("It heals some health.")
                    }
                    3 + hp.staticLevel * 7 / 100
                }
                29 -> {
                    sendMessage("That tasted very dodgy. You feel very ill.")
                    attack.adjustLevel(-3)
                    strength.adjustLevel(-3)
                    defence.adjustLevel(-3)
                    if (hp.level > 2) {
                        damage(minOf(4, hp.level - 2))
                    }
                    0
                }
                30 -> {
                    if (hp.level > 4) {
                        sendMessage("That tasted a bit dodgy. You feel a bit ill.")
                        damage(3)
                    } else {
                        sendMessage("The kebab didn't seem to do a lot.")
                    }
                    0
                }
                else -> {
                    sendMessage("The kebab didn't seem to do a lot.")
                    0
                }
            }
            hp.adjustLevel(heal)
        }
    }

    /**
     * The identifier set.
     */
    val ids = newIdSet()

    /**
     * The mappings of formatted names.
     */
    val formattedName = itemName(id).lowercase()

    /**
     * Invoked when the food is eaten.
     */
    open fun effect(plr: Player) {}

    /**
     * The message sent when the food is eaten.
     */
    open fun consumeMessage(name: String) = "You eat the $name."

    /**
     * The message sent when the [portion] identifier of this food is eaten.
     */
    open fun consumeMessage(name: String, portion: Int) = consumeMessage(name)

    /**
     * The message sent when the food heals health, or `null` for none.
     */
    open fun healMessage(name: String): String? = "It heals some health."

    /**
     * Computes and returns the next food portion identifier.
     */
    fun getNextId(current: Int) =
        when (current) {
            id -> id2
            id2 -> id3
            id3 -> id4
            else -> null
        }

    /**
     * Computes and returns a new set of this food's identifiers.
     */
    private fun newIdSet(): Set<Int> {
        val ids = HashSet<Int>(4)
        ids += id
        if (id2 != null) {
            ids += id2
        }
        if (id3 != null) {
            ids += id3
        }
        if (id4 != null) {
            ids += id4
        }
        return ids
    }
}
