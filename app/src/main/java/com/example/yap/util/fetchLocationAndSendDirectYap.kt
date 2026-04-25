package com.example.yap.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices

@SuppressLint("MissingPermission")
fun fetchLocationAndSendDirectYap(
    context: Context,
    isLocationEnabled: Boolean,
    onLocationReady: (Double?, Double?) -> Unit
) {
    if (!isLocationEnabled) {
        Log.d("API1", "Тумблер ВЫКЛЮЧЕН. Координаты: null")
        onLocationReady(null, null)
        return
    }

    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
        if (location != null) {
            Log.d("API1", "Тумблер ВКЛЮЧЕН. Координаты: ${location.latitude}")
            onLocationReady(location.latitude, location.longitude)
        } else {
            onLocationReady(null, null)
        }
    }.addOnFailureListener {
        onLocationReady(null, null)
    }
}