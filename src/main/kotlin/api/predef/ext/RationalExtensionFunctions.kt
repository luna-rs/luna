package api.predef.ext

import io.luna.util.Rational

/**
 * Creates a [Rational] instance.
 */
infix fun Int.of(d: Int) = Rational(this.toLong(), d.toLong())