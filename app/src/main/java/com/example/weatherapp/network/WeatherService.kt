package com.example.weatherapp.network

import com.example.weatherapp.models.WeatherResponseModel
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {

    /** Get the data from: BASE_URL/2.5/weather
     *  Add @queries to the URL and pass the parameters as values */
    @GET("2.5/weather")
    fun getWeather(@Query("lat") lat: Double,
                   @Query("lon") lon: Double,
                   @Query("units") units: String?,
                   @Query("appid") appid: String?): Call<WeatherResponseModel>
}