package com.example.mapapp

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.geojson.GeoJsonMultiPolygonFeature

class MainActivity : AppCompatActivity() {

    private val seasSwumIn: MutableSet<String> = mutableSetOf()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val geoJsonView = findViewById<com.example.geojson.GeoJsonView>(R.id.geo_json_view)
        val featureTitle = findViewById<TextView>(R.id.feature_title)
        val swimButton = findViewById<Button>(R.id.swim_button)

        getPreferences(MODE_PRIVATE).getStringSet("SwimWorld_Seas_Swum_In", seasSwumIn)
            ?.let { seasSwumIn.addAll(it) }

        geoJsonView.getAllFeatures().forEach {
            if(seasSwumIn.contains(it.properties.getString("ID"))){
                it.fillColor = Color.CYAN
            }
        }

        var selectedFeature: GeoJsonMultiPolygonFeature? = null
        var selectedFeatureColor: Int = geoJsonView.defaultFillColor

        geoJsonView.setFeaturePressListener{
            if(selectedFeature != null){
                selectedFeature!!.fillColor = selectedFeatureColor
            } else {
                val featureView = findViewById<View>(R.id.feature_description)
                featureView.visibility = View.VISIBLE
            }

            selectedFeatureColor = it.fillColor
            it.fillColor = Color.BLUE

            selectedFeature = it

            featureTitle.text = it.properties.getString("NAME")

            if(seasSwumIn.contains(it.properties.getString("ID"))){
                swimButton.text = getString(R.string.swim_no)
            } else {
                swimButton.text = getString(R.string.swim_yes)
            }

            Log.d("SwimWorld", "Clicked on ${it.properties.getString("NAME")}")
        }

        swimButton.setOnClickListener {
            if(selectedFeature != null){
                if(seasSwumIn.contains(selectedFeature!!.properties.getString("ID"))){
                    selectedFeatureColor = geoJsonView.defaultFillColor
                    selectedFeature!!.fillColor = geoJsonView.defaultFillColor
                    seasSwumIn.remove(selectedFeature!!.properties.getString("ID"))

                    swimButton.text = getString(R.string.swim_yes)
               } else {
                    selectedFeatureColor = Color.CYAN
                    selectedFeature!!.fillColor = Color.CYAN
                    seasSwumIn.add(selectedFeature!!.properties.getString("ID"))

                    swimButton.text = getString(R.string.swim_no)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return

        with(sharedPref.edit()){
            putStringSet("SwimWorld_Seas_Swum_In", seasSwumIn)
            apply()
        }
    }
}


