package game.content.barrows

import io.luna.game.model.mob.overlay.WalkableInterface

/**
 * The revision 377 Barrows kill-count and brother-head overlay.
 *
 * Widget 4535 is inter_98 in LostCityRS/Content's interface pack at
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50. Its kill-count component reads varbit 464.
 * The head models are set by the periodic prayer-drain effect.
 *
 * @author lare96
 */
class BarrowsOverlay : WalkableInterface(4535)
