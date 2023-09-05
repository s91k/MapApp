package com.example.geojson

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import androidx.core.content.res.getResourceIdOrThrow
import org.json.JSONObject
import java.io.InputStream
import java.nio.charset.Charset

class GeoJsonView(context: Context, attrs: AttributeSet) : TextureView(context, attrs), TextureView.SurfaceTextureListener {
    private val thread: RenderingThread

    private val gestureDetector: GestureDetector
    private val scaleGestureDetector: ScaleGestureDetector

    private var featurePressListener: ((feature: GeoJsonMultiPolygonFeature) -> Unit)? = null

    val defaultStrokeColor: Int
        get() = thread.strokePaint.color

    val defaultFillColor: Int
        get() = thread.fillPaint.color

    init {
        surfaceTextureListener = this

        val attributeArray: TypedArray = context.theme.obtainStyledAttributes(attrs,
            R.styleable.GeoJsonView, 0, 0)

        val fillPaint = Paint().apply {
            color = attributeArray.getColor(R.styleable.GeoJsonView_fill_color, Color.BLUE)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val strokePaint = Paint().apply {
            color = attributeArray.getColor(R.styleable.GeoJsonView_stroke_color, Color.BLACK)
            style = Paint.Style.STROKE
            strokeWidth = attributeArray.getFloat(R.styleable.GeoJsonView_stroke_width, 1.0f)
            isAntiAlias = true
        }

        thread = RenderingThread(this, strokePaint, fillPaint)

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                // Handle down event
                return true
            }

            override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                // Handle dragging
                thread.scroll(distanceX, distanceY)
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val f = thread.getFeatureAtPoint(PointF(e.x, e.y))

                if(f != null && featurePressListener != null){
                    featurePressListener!!.invoke(f)
                    thread.forceRedraw()
                }

                return true
            }
        })

        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.OnScaleGestureListener {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                thread.scale(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                // Handle scale end
            }
        })

        try{
            val fileId = attributeArray.getResourceIdOrThrow(R.styleable.GeoJsonView_file_id)
            loadGeoJsonFile(fileId)
        } catch(e: IllegalArgumentException){
            Log.d("SwimWorld", "No file in attributes")
        }
    }

    // Function to load GeoJson data from the file
    private fun loadGeoJsonFile(fileId: Int) {
        try {
            val inputStream: InputStream = context.resources.openRawResource(fileId)
            val jsonString = inputStream.bufferedReader(Charset.defaultCharset()).use { it.readText() }
            val jsonObject = JSONObject(jsonString)

            thread.loadGeoJsonFile(jsonObject)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setFeaturePressListener(listener: (feature: GeoJsonMultiPolygonFeature) -> Unit){
        featurePressListener = listener
    }

    override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
        thread.start()
    }

    override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
        TODO("Not yet implemented")
    }

    override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
        thread.stopRunning()
        thread.join()

        return true
    }

    override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
        // Ignored
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleGestureDetector.onTouchEvent(event)

        return true
    }

    private class RenderingThread(surface: TextureView, strokePaint: Paint, fillPaint: Paint): Thread(){
        private val surface = surface

        private var multiPolygons: MutableList<GeoJsonMultiPolygonFeature> = mutableListOf()

        private var mapBounds: RectF = RectF()

        private var x = 0.0f
        private var y = 0.0f

        private var scale = 1.0f

        private var redraw: Boolean = true

        var strokePaint: Paint = strokePaint
            private set

        var fillPaint: Paint = fillPaint
            private set

        @Volatile
        private var running: Boolean = false

        override fun run() {
            running = true

            // Update the coordinates based on map size
            val aspectRatio = mapBounds.height() / mapBounds.width()
            val width = surface.measuredWidth

            for (polygons in multiPolygons) {
                polygons.generateLODModels(width, aspectRatio, setOf(1.0f, 3.0f, 5.0f), 3.0f)
            }

            while(running && !interrupted()){
                if(redraw){
                    val drawStartTime = System.currentTimeMillis()

                    val canvas: Canvas? = surface.lockCanvas()

                    if(canvas != null){
                        canvas.drawColor(Color.WHITE)

                        val x = this.x
                        val y = this.y

                        val scale = this.scale

                        for (polygons in multiPolygons) {
                            polygons.draw(canvas, scale, x, y, strokePaint, fillPaint)
                        }

                        surface.unlockCanvasAndPost(canvas)
                    }

                    Log.d("SwimWorld", "Render time: ${System.currentTimeMillis() - drawStartTime}ms")

                    redraw = false
                }

                try {
                    sleep(15)
                } catch (e: InterruptedException) {
                    // Interrupted
                }
            }
        }

        fun stopRunning(){
            interrupt()
            running = false
        }

        fun loadGeoJsonFile(jsonObject: JSONObject){
            if(running){
                throw(Exception("Rendering thread already running."))
            }

            var mapXMin = Float.MAX_VALUE
            var mapXMax = Float.MIN_VALUE
            var mapYMin = Float.MAX_VALUE
            var mapYMax = Float.MIN_VALUE

            this.multiPolygons.clear()

            // Load map from the file
            if (jsonObject.has("type") && jsonObject.getString("type") == "FeatureCollection") {
                val featuresArray = jsonObject.getJSONArray("features")
                for (i in 0 until featuresArray.length()) {
                    val featureObject = featuresArray.getJSONObject(i)

                    if (featureObject.has("geometry") && featureObject.getJSONObject("geometry").has("type")) {
                        val geometryType = featureObject.getJSONObject("geometry").getString("type")

                        if (geometryType == "MultiPolygon") {
                            val coordinatesArray =
                                featureObject.getJSONObject("geometry").getJSONArray("coordinates")
                            val polygons: MutableList<MutableList<MutableList<PointF>>> =
                                mutableListOf()
                            for (j in 0 until coordinatesArray.length()) {
                                val polygon: MutableList<MutableList<PointF>> = mutableListOf()
                                val polygonCoordinatesArray = coordinatesArray.getJSONArray(j)
                                for (k in 0 until polygonCoordinatesArray.length()) {
                                    val ring: MutableList<PointF> = mutableListOf()
                                    val ringCoordinatesArray = polygonCoordinatesArray.getJSONArray(k)
                                    for (l in 0 until ringCoordinatesArray.length()) {
                                        val pointCoordinatesArray = ringCoordinatesArray.getJSONArray(l)
                                        if (pointCoordinatesArray.length() == 2) {
                                            val x = pointCoordinatesArray.getDouble(0)
                                            val y = pointCoordinatesArray.getDouble(1) * -1.0 // Y-axis is flipped in GeoJson
                                            val point = PointF(x.toFloat(), y.toFloat())

                                            ring.add(point)

                                            mapXMax = mapXMax.coerceAtLeast(point.x)
                                            mapXMin = mapXMin.coerceAtMost(point.x)
                                            mapYMax = mapYMax.coerceAtLeast(point.y)
                                            mapYMin = mapYMin.coerceAtMost(point.y)
                                        }
                                    }
                                    polygon.add(ring)
                                }
                                polygons.add(polygon)
                            }

                            val properties = if(featureObject.has("properties")) featureObject.getJSONObject("properties") else JSONObject()

                            multiPolygons.add(GeoJsonMultiPolygonFeature(polygons, strokePaint.color, fillPaint.color, properties))
                        }
                    }
                }
            }

            mapBounds.set(mapXMin, mapYMin, mapXMax, mapYMax)

            for(polygons in multiPolygons){
                polygons.normalize(mapBounds)
            }
        }

        fun scroll(dx: Float, dy: Float){
            x -= dx * (1 / scale)
            y -= dy * (1 / scale)

            redraw = true
        }

        fun scale(s: Float, sx: Float, sy: Float){
            val oldRWidth = 1.0f / scale
            val oldRHeight = 1.0f / scale

            scale *= s
            scale = scale.coerceIn(1.0f, 5.0f)

            val newRWidth = 1.0f / scale
            val newRHeight = 1.0f / scale

            x -= (oldRWidth - newRWidth) * sx
            y -= (oldRHeight - newRHeight) * sy

            redraw = true
        }

        fun getFeatureAtPoint(p: PointF): GeoJsonMultiPolygonFeature?{
            val pCorrected = PointF(p.x * (1.0f / scale) - x, p.y * (1.0f / scale) - y)

            for(f in multiPolygons){
                if(f.contains(pCorrected)){
                    return f
                }
            }

            return null
        }

        fun forceRedraw(){
            redraw = true
        }
    }
}
