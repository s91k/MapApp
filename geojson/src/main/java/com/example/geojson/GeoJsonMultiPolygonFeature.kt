package com.example.geojson

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.contains
import org.json.JSONObject
import java.util.SortedMap
import kotlin.math.sqrt

class GeoJsonMultiPolygonFeature(polygons: MutableList<MutableList<MutableList<PointF>>>, strokeColor: Int, fillColor: Int, properties: JSONObject) {
    private val lodPolygons: SortedMap<Float, MutableList<MutableList<MutableList<PointF>>>> = mutableMapOf(Float.MAX_VALUE to polygons).toSortedMap()
    private val bounds: RectF = RectF(Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

    // Stored as ARBG-values
    var strokeColor: Int = strokeColor
    var fillColor: Int = fillColor

    val properties: JSONObject = properties

    init {
        calcBounds()
    }

    // Scale the feature to a map that's a 1.0f by 1.0f square (this makes it easier to manipulate later)
    internal fun normalize(mapBounds: RectF){
        for(polygons in lodPolygons){
            for (polygon in polygons.value) {
                for (ring in polygon) {
                    for (i in 0 until ring.size) {
                        ring[i].x = (ring[i].x - mapBounds.left) / mapBounds.width()
                        ring[i].y = (ring[i].y - mapBounds.top) / mapBounds.height()
                    }
                }
            }
        }

        calcBounds()
    }

    // Generate LOD-models based on the size of the screen and user-selected zoom levels
    internal fun generateLODModels(width: Int, aspectRatio: Float, zoomLevels: Set<Float>, cutOffDistance: Float = 1.0f){
        for(polygons in lodPolygons) {
            for (polygon in polygons.value) {
                for (ring in polygon) {
                    for (i in 0 until ring.size) {
                        ring[i].x *= width
                        ring[i].y *= aspectRatio * width
                    }
                }
            }

            Log.d("SwimWorld","Scale corrected to width, ${getNrOfPoints(polygons.value)} points updated.")
        }

        // Generate lower LOD versions
        for (scale in zoomLevels) {
            val simplifiedPolygons: MutableList<MutableList<MutableList<PointF>>> = mutableListOf()

            for (polygon in lodPolygons[Float.MAX_VALUE]!!) {
                val simplifiedPolygon: MutableList<MutableList<PointF>> = mutableListOf()

                for (ring in polygon) {
                    val simplifiedRing: MutableList<PointF> = mutableListOf()

                    simplifiedRing.add(ring[0])

                    for (i in 1 until ring.size) {
                        if(simplifiedRing.size < 3 && ring.size - i <= 3 - simplifiedRing.size){
                            simplifiedRing.add(ring[i])
                        } else {
                            val dst = pointFDistance(ring[i], simplifiedRing.last())

                            //Log.d("SwimWorld", "Dst: $dst")

                            if(dst > cutOffDistance / scale){
                                // Makes sure that longer lines are reasonably aligned
                                if(dst > cutOffDistance / scale * 2.0f && simplifiedRing.last() != ring[i - 1]){
                                    simplifiedRing.add(ring[i - 1])
                                }

                                simplifiedRing.add(ring[i])
                            }
                        }
                    }

                    simplifiedPolygon.add(simplifiedRing)
                }

                simplifiedPolygons.add(simplifiedPolygon)
            }

            lodPolygons[scale] = simplifiedPolygons

            Log.d("SwimWorld","LOD $scale generated, ${getNrOfPoints(simplifiedPolygons)} points created.")
        }

        // There no point in keeping the original polygon model since it will never be used
        if(lodPolygons.size > 1){
            lodPolygons.remove(Float.MAX_VALUE)
        }

        calcBounds()
    }

    private fun pointFDistance(p1: PointF, p2: PointF): Float {
        val vx: Double = ((p1.x - p2.x).toDouble())
        val vy: Double = ((p1.y - p2.y).toDouble())

        return sqrt(vx * vx + vy * vy).toFloat()
    }

    private fun getNrOfPoints(polygons: MutableList<MutableList<MutableList<PointF>>>): Int {
        return polygons.sumOf { it -> it.sumOf { it.size } }
    }

    private fun calcBounds(){
        var xMin = Float.MAX_VALUE
        var xMax = Float.MIN_VALUE
        var yMin = Float.MAX_VALUE
        var yMax = Float.MIN_VALUE

        for (polygon in lodPolygons[lodPolygons.firstKey()]!!) {
            for (ring in polygon) {
                for (i in 0 until ring.size) {
                    xMax = xMax.coerceAtLeast(ring[i].x)
                    xMin = xMin.coerceAtMost(ring[i].x)
                    yMax = yMax.coerceAtLeast(ring[i].y)
                    yMin = yMin.coerceAtMost(ring[i].y)
                }
            }
        }

        bounds.set(xMin, yMin, xMax, yMax)
    }

    internal fun draw(canvas: Canvas, scale: Float, offsetX: Float, offsetY: Float, strokePaint: Paint, fillPaint: Paint){
        strokePaint.color = strokeColor
        fillPaint.color = fillColor

        //Log.d("SwimWorld", "Using LOD-model for scale ${lodPolygons.entries.firstOrNull { it.key >= scale }?.key}")

        for (polygon in (lodPolygons.entries.firstOrNull { it.key >= scale } ?: lodPolygons.entries.last()).value ) {
            for (ring in polygon) {
                val path = Path()

                for (i in 0 until ring.size) {
                    val point = ring[i]

                    val pX = (point.x + offsetX) * scale
                    val pY = (point.y + offsetY) * scale

                    if (i == 0) {
                        path.moveTo(pX, pY)
                    } else {
                        path.lineTo(pX, pY)
                    }
                }

                path.close()

                canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, strokePaint)
            }
        }
    }

    fun contains(p: PointF): Boolean {
        if(bounds.contains(p)){
            for (polygon in lodPolygons.maxBy { it.key }.value) {
                for (ring in polygon) {
                    if (isPointInPolygon(p, ring)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    fun contains(r: RectF): Boolean{
        return bounds.intersect(r)
    }

    private fun isPointInPolygon(p: PointF, ring: List<PointF>): Boolean {
        var isInside = false
        var j = ring.size - 1

        for (i in ring.indices) {
            val xi = ring[i].x
            val yi = ring[i].y
            val xj = ring[j].x
            val yj = ring[j].y

            val intersect = (yi > p.y) != (yj > p.y) && (p.x < (xj - xi) * (p.y - yi) / (yj - yi) + xi)

            if (intersect) {
                isInside = !isInside
            }

            j = i
        }

        return isInside
    }
}