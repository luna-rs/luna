package game.obj.entrances

import api.predef.*

object1(PortPhasmatysBarrier.ID, { _, _ -> PortPhasmatysBarrier.POLICY }) {
    PortPhasmatysBarrier.use(plr, gameObject)
}
