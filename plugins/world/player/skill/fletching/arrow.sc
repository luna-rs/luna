import $file.bow
import bow.ARROW_SHAFT


val FEATHER = 314
val HEADLESS_ARROW = 53
val SET_AMOUNT = 15

final case class Arrow(level: Int,
                       exp: Double,
                       arrowTip: Int,
                       arrow: Int,
                       useWith: Int = HEADLESS_ARROW)

val ARROW_MAP = Map(
  'headless_arrow -> Arrow(level = 1,
    exp = 1.0,
    arrowTip = ARROW_SHAFT.unstrungId,
    useWith = FEATHER,
    arrow = HEADLESS_ARROW),

  'bronze_arrow -> Arrow(level = 1,
    exp = 1.3,
    arrowTip = 39,
    arrow = 882),

  'iron_arrow -> Arrow(level = 15,
    exp = 2.5,
    arrowTip = 40,
    arrow = 884),

  'steel_arrow -> Arrow(level = 30,
    exp = 5,
    arrowTip = 41,
    arrow = 886),

  'mithril_arrow -> Arrow(level = 45,
    exp = 7.5,
    arrowTip = 42,
    arrow = 888),

  'adamant_arrow -> Arrow(level = 60,
    exp = 10,
    arrowTip = 43,
    arrow = 890),

  'rune_arrow -> Arrow(level = 75,
    exp = 12.5,
    arrowTip = 44,
    arrow = 892),
)
val ARROWTIP_TO_ARROW = ARROW_MAP.values.
  map(arrow => arrow.arrowTip -> arrow).
  toMap