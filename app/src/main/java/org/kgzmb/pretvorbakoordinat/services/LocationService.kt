package org.kgzmb.pretvorbakoordinat.services

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*

class LocationService : Service()
{
    //ponudnik lokacije
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    //povratni klic za lokacije
    private lateinit var locationCallback: LocationCallback

    companion object
    {
        const val ACTION_LOCATION_CHANGE = "ACTION_LOCATION_CHANGE"
        const val LATITUDE = "latitude"

        const val LONGITUDE = "longitude"
        const val TIMESTAMP = "timestamp"
    }

    override fun onBind(p0: Intent?): IBinder?
    {
        TODO("Not yet implemented")
    }

    override fun onCreate()
    {
        super.onCreate()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        Log.d(
            "LOKACIJSKA_STORITEV",
            "onCreate"
        )

        locationCallback = object : LocationCallback()
        {
            override fun onLocationResult(p0: LocationResult)
            {
                val latitude = p0.lastLocation?.latitude
                val longitude = p0.lastLocation?.longitude

                Log.d(
                    "SPREMEMBA_LOKACIJE",
                    "Lokacija: ${latitude}, $longitude"
                )

                val intent = Intent(ACTION_LOCATION_CHANGE)
                intent.putExtra(LATITUDE, latitude)

                intent.putExtra(LONGITUDE, longitude)
                intent.putExtra(TIMESTAMP, System.currentTimeMillis())

                sendBroadcast(intent)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    {
        Log.d("ZAČETEK_BELEŽENJA_LOKACIJE", "Začeli smo beležiti lokacijo!")
        Toast.makeText(applicationContext, "ZAGON LOKACIJSKE STORITVE", Toast.LENGTH_SHORT)
            .show()

        requestLocation()
        return START_STICKY
    }

    override fun onDestroy()
    {
        Toast.makeText(applicationContext, "Konec lokacijske storitce", Toast.LENGTH_SHORT)
            .show()
    }

    private fun requestLocation()
    {
        val locationRequest = LocationRequest.create()
        locationRequest.interval = 2000 //spremembo lokacije bomo dobili vsakih pol sekunde!

        //visoka natančnost
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        )
        {
            println("Ni lokacijskega dovoljenja!")
            return
        }
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()!!
        )
    }
}