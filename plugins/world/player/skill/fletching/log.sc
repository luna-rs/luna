import $file.bow
import bow._

final case class Log(id: Int, private val bows: Bow*) {
  val bowArray = bows.toArray
  val unstrungIds = bows.map(_.unstrungId).toArray
}

val LOG_MAP = Map(
  'log ->
    Log(id = 1151,
      bows = ARROW_SHAFT, SHORTBOW, LONGBOW),

  'oak_log ->
    Log(id = 1521,
      bows = OAK_SHORTBOW, OAK_LONGBOW),

  'willow_log ->
    Log(id = 1519,
      bows = WILLOW_SHORTBOW, WILLOW_LONGBOW),

  'maple_log ->
    Log(id = 1517,
      bows = MAPLE_SHORTBOW, MAPLE_LONGBOW),

  'yew_log ->
    Log(id = 1515,
      bows = YEW_SHORTBOW, YEW_LONGBOW),

  'magic_log ->
    Log(id = 1513,
      bows = MAGIC_SHORTBOW, MAGIC_LONGBOW),
)

val ID_TO_LOG = LOG_MAP.values.
  map(log => log.id -> log).
  toMap

val UNSTRUNG_TO_BOW = LOG_MAP.values.
  flatMap(_.bowArray).
  map(bow => bow.unstrungId -> bow).
  toMap