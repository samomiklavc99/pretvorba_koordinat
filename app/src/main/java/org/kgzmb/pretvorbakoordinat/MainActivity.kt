package org.kgzmb.pretvorbakoordinat

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.kgzmb.pretvorbakoordinat.services.LocationService
import kotlin.math.abs


class MainActivity : AppCompatActivity()
{
    private var writePermissionGranted = false
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var tvWGS84Lon: TextView
    private lateinit var tvWGS84Lat: TextView

    private lateinit var tvD96X: TextView
    private lateinit var tvD96Y: TextView

    private var latitude = 0.0
    private var longitude = 0.0

    private val client = HttpClient(OkHttp)
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        init()
    }

    private fun init()
    {
        tvWGS84Lat = findViewById(R.id.tvWgs84Lat)
        tvWGS84Lon = findViewById(R.id.tvWgs84Lon)

        tvD96X = findViewById(R.id.tvD96X)
        tvD96Y = findViewById(R.id.tvD96Y)

        permissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val granted = permissions.entries.all {
                    it.value
                }
                if (granted)
                {
                    // navigate to respective screen
                } else
                {
                    // show custom alert
                    //Previously Permission Request was cancelled with 'Dont Ask Again',
                    // Redirect to Settings after showing Information about why you need the permission
                    showPermissionDialog()
                }
            }

        updateOrRequestPermission()
        initLocationTracking()

        startLocationService()
    }

    private fun showPermissionDialog()
    {
        val builder = AlertDialog.Builder(baseContext)
        builder.setTitle("Permission required")
        builder.setMessage("Some permissions are needed to be allowed to use this app without any problems.")
        builder.setPositiveButton("Grant") { dialog, _ ->
            dialog.cancel()
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val uri = Uri.fromParts("package", this.packageName, null)
            intent.data = uri
            startActivity(intent)
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun startLocationService()
    {
        val locationBroadcastReceiver = object : BroadcastReceiver()
        {
            override fun onReceive(p0: Context?, p1: Intent?)
            {
                latitude = p1?.getDoubleExtra(LocationService.LATITUDE, 0.0)!!
                longitude = p1.getDoubleExtra(LocationService.LONGITUDE, 0.0)!!


                tvWGS84Lat.text = decimalToDMS(latitude) + "N"
                tvWGS84Lon.text = decimalToDMS(longitude) + "E"

                Log.d("TRENUTNA_LOKACIJA", "Lat: $latitude Lon: $longitude, ")
                getD96Coordinates()
            }
        }

        val intentFilter = IntentFilter(LocationService.ACTION_LOCATION_CHANGE)
        this.registerReceiver(locationBroadcastReceiver, intentFilter)

        val intent = Intent(this.applicationContext, LocationService::class.java)
        this.startService(intent)

    }

    private fun decimalToDMS(recivedCoord: Double): String?
    {
        var coord = recivedCoord
        var mod = coord % 1
        var intPart = coord.toInt()
        val degrees = intPart.toString()
        coord = mod * 60
        mod = coord % 1
        intPart = coord.toInt()
        if (intPart < 0) intPart *= -1
        val minutes = intPart.toString()
        coord = mod * 60
        intPart = coord.toInt()
        if (intPart < 0) intPart *= -1
        val seconds = intPart.toString()
        return abs(degrees.toInt()).toString() + "°" + minutes + "'" + seconds + "\""
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun getD96Coordinates()
    {
        GlobalScope.launch(Dispatchers.IO) {
            Log.d("KLIC", "Klic")

            var response: HttpResponse? = null

            try
            {
                response = client.submitForm(
                    url = LINK,
                    formParameters = Parameters.build {
                        append("DMS", "dms")
                        append("SIY", "")
                        append("SIX", "")
                        append("SIZ", "")
                        append("KOSY", "")
                        append("KOSX", "")
                        append("KOSZ", "")
                        append("UTMZONE", "33")
                        append("UTY", "")
                        append("UTX", "")
                        append("UTZ", "")
                        append("MGRS_UTM", "")
                        append("MGRS_ZONE", "")
                        append("MGRSY", "")
                        append("MGRSX", "")
                        append("MGRSZ", "")
                        append("LON", "$longitude")
                        append("LAT", "$latitude")
                        append("NMV", "")
                        append("fromWGS", "pretvorba+iz+WGS84")
                        append("GKY", "")
                        append("GKX", "")
                        append("GKZ", "")
                        append("B_LON", "")
                        append("B_LAT", "")
                        append("B_NMV", "")
                        append("UL", "")
                    }
                )

            } catch (e: IOException)
            {
                Toast.makeText(
                    applicationContext,
                    "Napaka pri pridobivanju D96 koordinat!\nImate internetno povezavo?",
                    Toast.LENGTH_LONG
                ).show()
            }

            try
            {
                val htmlText = response?.bodyAsText()
                val html = Jsoup.parse(htmlText!!)

                /**
                 * child(0) je tbody
                 * child(6) je ustrezna vrstica tr
                 * child(1) je stoplpec td
                 * child(0) je <b></b>
                 * */
                val tr = html.getElementsByClass("center")[0].child(0).child(3)
                Log.d("ODZIV", "${tr.childrenSize()}")

                withContext(Dispatchers.Main) {
                    tvD96X.text = "E " + tr.child(2).child(0).attr("value")
                    tvD96Y.text = "N " + tr.child(4).child(0).attr("value")
                }

//                tvD96Z.text = tr.child(6).child(0).attr("value")
            } catch (e: IndexOutOfBoundsException)
            {
                Toast.makeText(
                    applicationContext,
                    "Napaka pri branju podatkov!\nStruktura spletne strani se je spremenila." +
                            "\nKontaktirajte razvijalca aplikacije!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    }

    private fun initLocationTracking()
    {
        //android marshmallow (6.0) ali višje
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        )
        {
            Log.d("DOVOLJENJA_NE", "Nimamo dovoljenja za zagon storitve!")

            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                1
            )
        } else
        {
            Log.d("DOVOLJENJA_DA", "Imamo dovoljenja za zagon lokacijske storitve!")
        }
    }

    private fun updateOrRequestPermission()
    {
        val hasWritePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val minSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        writePermissionGranted = hasWritePermission || minSdk29

        val permissionsToRequest = mutableListOf<String>()
        if (!writePermissionGranted)
        {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (permissionsToRequest.isNotEmpty())
        {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    )
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode)
        {
            1 ->
            {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.d("LOKACIJA", "dobili dovoljenje od uporabnika!")
                } else
                {
                    Toast.makeText(
                        this,
                        "Za uporabo aplikacije, potrebujemo dovoljenje za lokacijo",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    companion object
    {
        const val LINK = "https://www.mkx.si/geoconv/"
    }

}