final case class Bow(level: Int, exp: Double, unstrungId: Int, strungId: Int)

val ARROW_SHAFT = Bow(level = 1,
  exp = 5.0,
  unstrungId = 52,
  strungId = -1 // Unused.
)
val SHORTBOW = Bow(level = 5,
  exp = 5.0,
  unstrungId = 50,
  strungId = 841
)
val LONGBOW = Bow(level = 10,
  exp = 10.0,
  unstrungId = 48,
  strungId = 839
)
val OAK_SHORTBOW = Bow(level = 20,
  exp = 16.5,
  unstrungId = 54,
  strungId = 843
)
val OAK_LONGBOW = Bow(level = 25,
  exp = 25.0,
  unstrungId = 56,
  strungId = 845
)
val WILLOW_SHORTBOW = Bow(level = 35,
  exp = 33.3,
  unstrungId = 60,
  strungId = 847
)
val WILLOW_LONGBOW = Bow(level = 40,
  exp = 41.5,
  unstrungId = 58,
  strungId = 849
)
val MAPLE_SHORTBOW = Bow(level = 50,
  exp = 50.0,
  unstrungId = 64,
  strungId = 853
)
val MAPLE_LONGBOW = Bow(level = 55,
  exp = 58.3,
  unstrungId = 62,
  strungId = 851
)
val YEW_SHORTBOW = Bow(level = 65,
  exp = 67.5,
  unstrungId = 68,
  strungId = 857
)
val YEW_LONGBOW = Bow(level = 70,
  exp = 75.0,
  unstrungId = 66,
  strungId = 855
)
val MAGIC_SHORTBOW = Bow(level = 80,
  exp = 83.3,
  unstrungId = 70,
  strungId = 861
)
val MAGIC_LONGBOW = Bow(level = 85,
  exp = 91.5,
  unstrungId = 68,
  strungId = 859
)