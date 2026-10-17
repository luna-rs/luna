package api.bot.webwalk.visualize

import io.luna.game.cache.Archive
import io.luna.game.cache.Cache
import io.luna.game.cache.map.MapIndexTable
import io.luna.game.cache.map.MapTile
import io.luna.game.model.Region
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt

/**
 * Paints the terrain of the cache the way the client builds it, for [WebWalkMapRenderer].
 *
 * The client does not paint whole tiles. The colour of the underlay of a tile is the average of the underlays of the tiles
 * around it (11 by 11 tiles), so that grounds blend into each other, and an overlay is only painted where the shape of its
 * tile says it is, so that a path or a shore takes the shape of a curve or a diagonal instead of a square. The shapes are
 * not in the cache but in the client, so they are in [SHAPE_POINTS] and [SHAPE_FACES], which are the ones of the client.
 * Lighting, shadows and textures are not painted.
 *
 * @author Hydrozoa
 */
object WebWalkTerrain {

    /**
     * A floor from `flo.dat`, which is both what an underlay and an overlay of the map data is.
     *
     * @property rgb The colour, as `0xRRGGBB`.
     * @property texture The id of the texture, or `-1` if there is none.
     * @property hue The hue, from 0 to 255.
     * @property saturation The saturation, from 0 to 255.
     * @property lightness The lightness, from 0 to 255.
     * @property luminance The weight that the floor has when colours are averaged.
     * @property chroma The hue, weighted by [luminance].
     */
    class Floor(val rgb: Int, val texture: Int, val hue: Int, val saturation: Int, val lightness: Int, val luminance: Int,
                val chroma: Int) {

        /**
         * @return `true` if the floor is not painted at all, which the client marks with the colour magenta.
         */
        val isInvisible: Boolean get() = rgb == INVISIBLE
    }

    /**
     * The colour that the cache gives to a floor that is not painted.
     */
    private const val INVISIBLE = 0xff00ff

    /**
     * The colour of a floor with no colour of its own, such as a floor that only has a texture.
     */
    private const val UNKNOWN_FLOOR = 0x6b5b3a

    /**
     * The colour of water, which is a texture in the client.
     */
    private const val WATER = 0x2a4f9e

    /**
     * The id of the overlay that is water.
     */
    private const val WATER_OVERLAY = 6

    /**
     * The colour of lava, which is a texture in the client.
     */
    private const val LAVA = 0xc2410c

    /**
     * The ids of the overlays that are lava. The first is the lava floor, which is a texture, and the second is the invisible
     * floor that the lava of the Wilderness is laid over with objects.
     */
    private val LAVA_OVERLAYS = setOf(19, 152)

    /**
     * How much the colours are darkened by, so that what is drawn over them can be seen.
     */
    private const val DIM = 0.85

    /**
     * The number of tiles that the underlay colours are averaged over on each side of a tile.
     */
    private const val BLEND = 5

    /**
     * The points that make up each shape, which are numbered like this, with a tile of 128 by 128 and the south-west corner
     * at the origin: 1, 3, 5 and 7 are the corners (south-west, south-east, north-east and north-west) and 2, 4, 6 and 8 are
     * the middles of the sides after each (south, east, north and west), and 9 to 16 are inside the tile.
     */
    private val SHAPE_POINTS = arrayOf(intArrayOf(1, 3, 5, 7), intArrayOf(1, 3, 5, 7), intArrayOf(1, 3, 5, 7),
                                       intArrayOf(1, 3, 5, 7, 6), intArrayOf(1, 3, 5, 7, 6), intArrayOf(1, 3, 5, 7, 6),
                                       intArrayOf(1, 3, 5, 7, 6), intArrayOf(1, 3, 5, 7, 2, 6),
                                       intArrayOf(1, 3, 5, 7, 2, 8), intArrayOf(1, 3, 5, 7, 2, 8),
                                       intArrayOf(1, 3, 5, 7, 11, 12), intArrayOf(1, 3, 5, 7, 11, 12),
                                       intArrayOf(1, 3, 5, 7, 13, 14))

    /**
     * The triangles of each shape, as groups of four numbers: `0` for a triangle of the underlay or `1` for one of the
     * overlay, then the indices in [SHAPE_POINTS] of its three points. The first shape is only underlay. The shape in the
     * map data is the index here minus one, so that the overlay shape 0 is the one that is only overlay.
     */
    private val SHAPE_FACES = arrayOf(intArrayOf(0, 1, 2, 3, 0, 0, 1, 3),
                                      intArrayOf(1, 1, 2, 3, 1, 0, 1, 3),
                                      intArrayOf(0, 1, 2, 3, 1, 0, 1, 3),
                                      intArrayOf(0, 0, 1, 2, 0, 0, 2, 4, 1, 0, 4, 3),
                                      intArrayOf(0, 0, 1, 4, 0, 0, 4, 3, 1, 1, 2, 4),
                                      intArrayOf(0, 0, 4, 3, 1, 0, 1, 2, 1, 0, 2, 4),
                                      intArrayOf(0, 1, 2, 4, 1, 0, 1, 4, 1, 0, 4, 3),
                                      intArrayOf(0, 4, 1, 2, 0, 4, 2, 5, 1, 0, 4, 5, 1, 0, 5, 3),
                                      intArrayOf(0, 4, 1, 2, 0, 4, 2, 3, 0, 4, 3, 5, 1, 0, 4, 5),
                                      intArrayOf(0, 0, 4, 5, 1, 4, 1, 2, 1, 4, 2, 3, 1, 4, 3, 5),
                                      intArrayOf(0, 0, 1, 5, 0, 1, 4, 5, 0, 1, 2, 4, 1, 0, 5, 3, 1, 5, 4, 3, 1, 4, 2, 3),
                                      intArrayOf(1, 0, 1, 5, 1, 1, 4, 5, 1, 1, 2, 4, 0, 0, 5, 3, 0, 5, 4, 3, 0, 4, 2, 3),
                                      intArrayOf(1, 0, 5, 4, 1, 0, 1, 5, 0, 0, 4, 3, 0, 4, 5, 3, 0, 5, 2, 3, 0, 1, 2, 5))

    /**
     * Reads every floor, which are indexed by their id minus one, as the overlays and underlays of the map data are.
     *
     * @param cache An open cache.
     * @return The floors.
     */
    fun readFloors(cache: Cache): List<Floor> {
        val buf = Archive.decode(cache.getFile(0, 2)).getFileData("flo.dat")
        try {
            val floors = ArrayList<Floor>()
            repeat(buf.readUnsignedShort()) {
                var rgb = 0
                var texture = -1
                while (true) {
                    when (buf.readUnsignedByte().toInt()) {
                        0 -> break
                        1 -> rgb = buf.readUnsignedMedium()
                        2 -> texture = buf.readUnsignedByte().toInt()
                        6 -> {
                            while (buf.readByte().toInt() != 10) {
                                // The name of the floor.
                            }
                        }
                        7 -> buf.readUnsignedMedium()
                        else -> {}
                    }
                }
                floors += floorOf(rgb, texture)
            }
            return floors
        } finally {
            buf.release()
        }
    }

    /**
     * Makes a floor of its colour, working out its hue, saturation and lightness as the client does.
     */
    private fun floorOf(rgb: Int, texture: Int): Floor {
        val red = (rgb shr 16 and 0xff) / 256.0
        val green = (rgb shr 8 and 0xff) / 256.0
        val blue = (rgb and 0xff) / 256.0
        val min = minOf(red, green, blue)
        val max = maxOf(red, green, blue)
        var h = 0.0
        var s = 0.0
        val l = (min + max) / 2.0
        if (min != max) {
            s = if (l < 0.5) (max - min) / (min + max) else (max - min) / (2.0 - max - min)
            h = when (max) {
                red -> (green - blue) / (max - min)
                green -> (blue - red) / (max - min) + 2.0
                else -> (red - green) / (max - min) + 4.0
            }
        }
        h /= 6.0
        val saturation = (s * 256.0).toInt().coerceIn(0, 255)
        val lightness = (l * 256.0).toInt().coerceIn(0, 255)
        var luminance = if (l > 0.5) ((1.0 - l) * s * 512.0).toInt() else (s * l * 512.0).toInt()
        if (luminance < 1) {
            luminance = 1
        }
        return Floor(rgb, texture, (h * 256.0).toInt(), saturation, lightness, luminance, (luminance * h).toInt())
    }

    /**
     * Converts a colour from hue, saturation and lightness, each from 0 to 255.
     */
    private fun hslToRgb(hue: Int, saturation: Int, lightness: Int): Int {
        val h = (hue and 0xff) / 256.0
        val s = saturation.coerceIn(0, 255) / 256.0
        val l = lightness.coerceIn(0, 255) / 256.0
        if (s == 0.0) {
            val grey = (l * 255).toInt()
            return (grey shl 16) or (grey shl 8) or grey
        }
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        fun channel(t0: Double): Int {
            var t = t0
            if (t < 0) t += 1
            if (t > 1) t -= 1
            val c = when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 1.0 / 2 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
            return (c * 255).toInt().coerceIn(0, 255)
        }
        return (channel(h + 1.0 / 3) shl 16) or (channel(h) shl 8) or channel(h - 1.0 / 3)
    }

    /**
     * Darkens a colour.
     */
    private fun dim(rgb: Int): Int {
        val r = ((rgb shr 16 and 0xff) * DIM).toInt()
        val g = ((rgb shr 8 and 0xff) * DIM).toInt()
        val b = ((rgb and 0xff) * DIM).toInt()
        return (r shl 16) or (g shl 8) or b
    }

    /**
     * The position in a tile of a point of a shape, with the tile 128 by 128 and its south-west corner at the origin.
     */
    private fun pointOf(point: Int): Pair<Int, Int> = when (point) {
        1 -> 0 to 0
        2 -> 64 to 0
        3 -> 128 to 0
        4 -> 128 to 64
        5 -> 128 to 128
        6 -> 64 to 128
        7 -> 0 to 128
        8 -> 0 to 64
        9 -> 64 to 32
        10 -> 96 to 64
        11 -> 64 to 96
        12 -> 32 to 64
        13 -> 32 to 32
        14 -> 96 to 32
        15 -> 96 to 96
        else -> 32 to 96
    }

    /**
     * Makes a mask of the pixels of a tile that the overlay is painted on.
     *
     * @param shape The shape of the overlay in the map data.
     * @param angle The rotation of the shape, from 0 to 3.
     * @param scale The number of pixels along the side of a tile.
     * @return The pixels, row by row from the north, which are `true` where the overlay is.
     */
    private fun overlayMask(shape: Int, angle: Int, scale: Int): BooleanArray {
        val index = shape + 1
        val points = SHAPE_POINTS[index].map { original ->
            var point = original
            // The points are turned by the angle, the corners and middles of the sides by 90 degrees at a time.
            if (point and 1 == 0 && point <= 8) {
                point = ((point - angle - angle - 1) and 7) + 1
            }
            if (point in 9..12) {
                point = ((point - 9 - angle) and 3) + 9
            }
            if (point in 13..16) {
                point = ((point - 13 - angle) and 3) + 13
            }
            pointOf(point)
        }
        val mask = BooleanArray(scale * scale)
        val faces = SHAPE_FACES[index]
        for (face in faces.indices step 4) {
            if (faces[face] == 0) {
                continue
            }
            val corners = (1..3).map { i ->
                var vertex = faces[face + i]
                if (vertex < 4) {
                    vertex = (vertex - angle) and 3
                }
                points[vertex]
            }
            fillTriangle(mask, scale, corners)
        }
        return mask
    }

    /**
     * Sets the pixels of a mask whose centre is within a triangle.
     */
    private fun fillTriangle(mask: BooleanArray, scale: Int, corners: List<Pair<Int, Int>>) {
        val (ax, ay) = corners[0]
        val (bx, by) = corners[1]
        val (cx, cy) = corners[2]
        val area = (bx - ax).toDouble() * (cy - ay) - (cx - ax).toDouble() * (by - ay)
        if (area == 0.0) {
            return
        }
        for (row in 0 until scale) {
            // A row is counted from the north and a tile is counted from the south.
            val z = (scale - (row + 0.5)) / scale * 128
            for (column in 0 until scale) {
                val x = (column + 0.5) / scale * 128
                val w0 = ((bx - x) * (cy - z) - (cx - x) * (by - z)) / area
                val w1 = ((cx - x) * (ay - z) - (ax - x) * (cy - z)) / area
                val w2 = 1 - w0 - w1
                if (w0 >= -1e-9 && w1 >= -1e-9 && w2 >= -1e-9) {
                    mask[row * scale + column] = true
                }
            }
        }
    }

    /**
     * Paints the terrain of the map data onto an image.
     *
     * @param image The image, which is [bounds] times [scale] big with north at the top.
     * @param table The map data.
     * @param floors The floors.
     * @param plane The plane to paint.
     * @param bounds The tiles to paint.
     * @param scale The number of pixels along the side of a tile.
     */
    fun paint(image: BufferedImage, table: MapIndexTable, floors: List<Floor>, plane: Int,
              bounds: WebWalkMapRenderer.Bounds, scale: Int) {
        // The tiles, with room around them to average the underlays of the tiles on the edge.
        val minX = bounds.minX - BLEND
        val minY = bounds.minY - BLEND
        val width = bounds.width + BLEND * 2
        val height = bounds.height + BLEND * 2
        val tiles = arrayOfNulls<MapTile>(width * height)
        val regions = HashMap<Region, io.luna.game.cache.map.MapTileGrid>()
        for (region in table.allRegions) {
            val origin = region.absPosition
            if (origin.x + 63 < minX || origin.x >= minX + width || origin.y + 63 < minY || origin.y >= minY + height) {
                continue
            }
            regions[region] = table.tileSet.getGrid(table.indexTable[region])
        }
        for ((region, grid) in regions) {
            val origin = region.absPosition
            for (dx in 0 until 64) {
                for (dy in 0 until 64) {
                    val x = origin.x + dx - minX
                    val y = origin.y + dy - minY
                    if (x !in 0 until width || y !in 0 until height) {
                        continue
                    }
                    // The floor of a bridge is on the plane above it in the map data.
                    val bridged = plane < 3 && grid.getTile(dx, dy, plane + 1).isBridge
                    tiles[y * width + x] = grid.getTile(dx, dy, if (bridged) plane + 1 else plane)
                }
            }
        }

        // Sums of the underlays over all the tiles above and to the left of each corner, to average any area quickly.
        val stride = width + 1
        val sums = Array(5) { LongArray(stride * (height + 1)) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val floor = tiles[y * width + x]?.underlay?.takeIf { it > 0 }?.let { floors.getOrNull(it - 1) }
                val values = longArrayOf(floor?.chroma?.toLong() ?: 0, floor?.saturation?.toLong() ?: 0,
                                         floor?.lightness?.toLong() ?: 0, floor?.luminance?.toLong() ?: 0,
                                         if (floor == null) 0 else 1)
                for (kind in 0 until 5) {
                    val sum = sums[kind]
                    sum[(y + 1) * stride + x + 1] =
                        values[kind] + sum[y * stride + x + 1] + sum[(y + 1) * stride + x] - sum[y * stride + x]
                }
            }
        }
        fun areaSum(kind: Int, x: Int, y: Int): Long {
            val sum = sums[kind]
            val x0 = x - BLEND
            val y0 = y - BLEND
            val x1 = x + BLEND + 1
            val y1 = y + BLEND + 1
            return sum[y1 * stride + x1] - sum[y0 * stride + x1] - sum[y1 * stride + x0] + sum[y0 * stride + x0]
        }

        val masks = HashMap<Int, BooleanArray>()
        val pixels = (image.raster.dataBuffer as DataBufferInt).data
        val imageWidth = image.width
        for (ty in BLEND until height - BLEND) {
            for (tx in BLEND until width - BLEND) {
                val tile = tiles[ty * width + tx] ?: continue
                val underlay = tile.underlay
                // The map data reads the id of an overlay as a signed byte, but the ids go up to 255.
                val overlay = tile.overlay and 0xff
                if (underlay <= 0 && overlay <= 0) {
                    continue
                }
                val left = (tx - BLEND) * scale
                val top = (height - BLEND - 1 - ty) * scale

                var underlayColour = -1
                if (underlay > 0) {
                    val luminance = areaSum(3, tx, ty)
                    val count = areaSum(4, tx, ty)
                    if (luminance > 0 && count > 0) {
                        val hue = (areaSum(0, tx, ty) * 256 / luminance).toInt()
                        underlayColour = dim(hslToRgb(hue, (areaSum(1, tx, ty) / count).toInt(),
                                                      (areaSum(2, tx, ty) / count).toInt()))
                        for (row in top until top + scale) {
                            java.util.Arrays.fill(pixels, row * imageWidth + left, row * imageWidth + left + scale,
                                                  underlayColour)
                        }
                    }
                }
                if (overlay <= 0) {
                    continue
                }
                val floor = floors.getOrNull(overlay - 1)
                if (floor != null && floor.isInvisible && overlay !in LAVA_OVERLAYS) {
                    continue
                }
                val colour = dim(when {
                    overlay == WATER_OVERLAY -> WATER
                    overlay in LAVA_OVERLAYS -> LAVA
                    floor == null || (floor.texture >= 0 && floor.rgb == 0) -> UNKNOWN_FLOOR
                    else -> floor.rgb
                })
                val mask = masks.getOrPut(tile.overlayType * 4 + tile.overlayOrientation) {
                    overlayMask(tile.overlayType, tile.overlayOrientation, scale)
                }
                for (row in 0 until scale) {
                    val start = (top + row) * imageWidth + left
                    for (column in 0 until scale) {
                        if (mask[row * scale + column]) {
                            pixels[start + column] = colour
                        }
                    }
                }
            }
        }
    }
}
