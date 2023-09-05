package com.example.mapapp

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    var selectedFeature: com.example.geojson.GeoJsonMultiPolygonFeature? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val geoJsonView = findViewById<com.example.geojson.GeoJsonView>(R.id.geoJsonView)

        val featureTitle = findViewById<TextView>(R.id.featureTitle)

        geoJsonView.setFeaturePressListener{
            if(selectedFeature != null){
                selectedFeature!!.fillColor = geoJsonView.defaultFillColor
            } else {
                val featureView = findViewById<View>(R.id.featureDescription)
                featureView.visibility = View.VISIBLE
            }

            it.fillColor = Color.BLUE
            selectedFeature = it

            featureTitle.text = it.properties.getString("NAME")

            Log.d("SwimWorld", "Clicked on ${it.properties.getString("NAME")}")
        }
    }
}


