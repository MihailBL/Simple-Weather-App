package com.example.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object Constants {

    const val APP_ID: String = "2c569e33da49a9073f2e99ae8acb90f9"
    const val BASE_URL: String = "https://api.openweathermap.org/data/"
    const val METRIC_UNIT: String = "metric"
    const val PREFERENCE_NAME = "WeatherAppPreference"
    const val WEATHER_RESPONSE_DATA = "weather_response_data"

    fun isNetworkAvailable(context: Context): Boolean{
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            /** Use the 'connectivityManager' to get the current active network first */
            val network = connectivityManager.activeNetwork ?: return false
            /** Use the active 'network' to get all capabilities (ex.: , NET_CAPABILITY_INTERNET, NET_CAPABILITY_NOT_RESTRICTED...) */
            val activeNetworkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when{
                /** Use 'activeNetworkCapabilities' to check presence of a transport*/
                activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        }
        else{
            // deprecated since android M but still works with newer androids
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnectedOrConnecting
        }
    }
}