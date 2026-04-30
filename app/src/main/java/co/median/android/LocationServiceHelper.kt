package co.median.android

import android.Manifest
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*

class LocationServiceHelper(private val activity: Activity) {

    private val defaultRequestLocationInterval = 1000L // 1 sec
    var callback: Callback? = null

    private val requestLocationPermissionsLauncher =
        (activity as ComponentActivity).registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val fineLocationGranted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted =
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineLocationGranted && coarseLocationGranted) {
                promptLocationService()
            } else {
                callback?.onResult(false)
            }
        }

    private val requestEnableLocationLauncher =
        (activity as ComponentActivity).registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->

            if (result.resultCode == RESULT_OK) {
                callback?.onResult(true)
            } else {
                callback?.onResult(false)
            }
        }

    fun promptLocationService(callback: Callback) {
        this.callback = callback
        promptLocationService()
    }

    fun promptLocationService() {

        // 1. Permission check
        if (!isLocationPermissionGranted()) {
            showRequestPermissionRationale()

            requestLocationPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        // 2. Build LocationRequest (CI-safe modern API)
        val locationRequest = LocationRequest.Builder(1000L)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(5000)
            .build()

        val locationSettingsRequest =
            LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .build()

        val client = LocationServices.getSettingsClient(activity)
        val task = client.checkLocationSettings(locationSettingsRequest)

        task.addOnSuccessListener {
            callback?.onResult(true)
        }

        task.addOnFailureListener { e ->
            if (e is ResolvableApiException) {

                val intentSenderRequest =
                    IntentSenderRequest.Builder(e.resolution).build()

                requestEnableLocationLauncher.launch(intentSenderRequest)

            } else {
                callback?.onResult(false)
            }
        }
    }

    fun isLocationServiceEnabled(): Boolean {

        if (!isLocationPermissionGranted()) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lm = activity.getSystemService(LocationManager::class.java)
            lm.isLocationEnabled
        } else {
            val mode = Settings.Secure.getInt(
                activity.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )
            mode != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    private fun isLocationPermissionGranted(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val coarse = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        return fine == PackageManager.PERMISSION_GRANTED &&
                coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun showRequestPermissionRationale() {
        if (
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ||
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        ) {
            Toast.makeText(
                activity,
                R.string.request_permission_explanation_geolocation,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    interface Callback {
        fun onResult(enabled: Boolean)
    }
}
