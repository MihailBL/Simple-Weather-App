package com.example.weatherapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.databinding.DialogCustomProgressBinding
import com.example.weatherapp.databinding.PermissionRationaleCustomDialogBinding
import com.example.weatherapp.models.WeatherResponseModel
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // View Binding Object
    private var binding: ActivityMainBinding? = null
    // Fused Location object for holding the current location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    // Custom progressbar dialog property
    private var progressDialog: Dialog? = null
    // Shared preferences
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(this.binding?.root)

        // Initialize fusedLocationClient
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize sharedPreferences
        this.sharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        // Check for the permissions
        if (!checkLocationPermissions()){
            requestLocationPermissions()
        }

        // If the permissions are denied more times click this btn to show rationale dialog
        this.binding?.btnCheckPermissions?.setOnClickListener {
            if(!checkLocationPermissions()){
                showRequestPermissionRationaleDialog()
            }
            else{
                Toast.makeText(this, "Location Permissions Already Granted!", Toast.LENGTH_LONG).show()
            }
        }

        // Check for Location Service
        this.binding?.btnCheckLocationService?.setOnClickListener {
            if (!isLocationServicesEnabled()){
                Toast.makeText(this, "Please Turn On Your Location Provider On The Device", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
            else{
                Toast.makeText(this, "Location Provider Already Turned On The Device", Toast.LENGTH_LONG).show()
            }
        }

        // Get Location Data
        this.binding?.btnGetLocationData?.setOnClickListener {
            requestLocationData()
        }
    }

    /** Check for Location permissions */
    private fun checkLocationPermissions(): Boolean{
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED){
            return true
        }
        return false
    }

    /** Method for creating permission requests */
    private fun requestLocationPermissions(){
        Dexter.withActivity(this)
            .withPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
            .withListener(object : MultiplePermissionsListener{
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report?.areAllPermissionsGranted() == true && isLocationServicesEnabled()){
                        requestLocationData()
                    }
                    else if (!isLocationServicesEnabled() && report?.areAllPermissionsGranted() == true){
                        Toast.makeText(this@MainActivity, "Turn On Location Provider On The Device", Toast.LENGTH_LONG).show()
                    }
                    if (report?.areAllPermissionsGranted() == false){
                        Toast.makeText(this@MainActivity, "Permissions Denied!", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>?, token: PermissionToken?) {
                    showRequestPermissionRationaleDialog()
                }
            }).onSameThread().check()
    }

    /** Check if the Location Services are enabled on the device */
    private fun isLocationServicesEnabled(): Boolean{
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /** Method for getting Current Location Data */
    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val locationRequest = com.google.android.gms.location.LocationRequest()
        locationRequest.priority = LocationRequest.QUALITY_HIGH_ACCURACY
        locationRequest.numUpdates = 2
        this.fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
    }

    /** LOCATION CALLBACK OVERRIDE */
    private val locationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            val lastLocation = locationResult.lastLocation
            val latitude = lastLocation?.latitude
            val longitude = lastLocation?.longitude

            fusedLocationClient.flushLocations()

            getLocationWeatherDetails(latitude, longitude)
        }
    }

    /** Function to connect to 'www.openwathermap.org' and get weather details */
    private fun getLocationWeatherDetails(latitude: Double?, longitude: Double?){
        if (Constants.isNetworkAvailable(this)){
            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())          // Property to Convert to Gson Automatically
                .build()
            /** Map the service interface in which we declare the API endpoint (IN MY CASE @GET REQUEST)
             * '.create()' Creates implementation of the API endpoint defined by the specified service */
            val service: WeatherService = retrofit.create(WeatherService::class.java)
            val listCall: Call<WeatherResponseModel> = service.getWeather(latitude!!, longitude!!, Constants.METRIC_UNIT, Constants.APP_ID)
            showCustomProgressDialog()
            listCall.enqueue(object : Callback<WeatherResponseModel>{
                @SuppressLint("CommitPrefEdits")
                override fun onResponse(call: Call<WeatherResponseModel>, response: Response<WeatherResponseModel>) {
                    if (response.isSuccessful){
                        hideCustomProgressDialog()
                        val weatherList: WeatherResponseModel = response.body()!!
                        Log.i("RESPONSE RESULT: ", "$weatherList")

                        val weatherResponseJsonString = Gson().toJson(weatherList)         // Using Gson() create Json() and convert it to string
                        val editor = sharedPreferences.edit()                             // Create new Editor with these preferences
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)  // Store the data under the given constant
                        editor.apply()
                        setUpUI()
                    }else{
                        hideCustomProgressDialog()
                        when(response.code()){
                            400 -> Log.e("Error 400", "Bad connection")
                            404 -> Log.e("Error 404", "Not found")
                            else -> Log.e("Error", "Other Type OF 400 Error")
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponseModel>, t: Throwable) {
                    Log.e("---------ERROR---------", t.message!!.toString())
                    hideCustomProgressDialog()
                }
            })
        }
        else{
            Toast.makeText(this, "No Internet Connection", Toast.LENGTH_LONG).show()
        }
    }

    /** Method for showing Rationale Permission Request Dialog */
    private fun showRequestPermissionRationaleDialog(){
        val dialog = Dialog(this)
        val dialogBinding = PermissionRationaleCustomDialogBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.setTitle("Permission Rationale Dialog")
        dialog.setCancelable(false)

        // Set the listeners for the buttons
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnGoToSettings.setOnClickListener {
            /* GO TO THE APPLICATIONS SETTINGS TO ENABLE THE LOCATION PERMISSION */
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val appUri = Uri.fromParts("package", packageName, null) // Builds uri: scheme:packageName#fragment
                intent.data = appUri
                startActivity(intent)
            }catch (e: Exception){
                e.printStackTrace()
            }
        }
        dialog.show()
    }

    /** Custom Progressbar Dialog */
    private fun showCustomProgressDialog(){
        this.progressDialog = Dialog(this)
        val bindingProgressDialog = DialogCustomProgressBinding.inflate(layoutInflater)
        this.progressDialog?.setContentView(bindingProgressDialog.root)
        this.progressDialog?.setCancelable(false)
        this.progressDialog?.show()
    }

    /** Hide custom progressbar dialog */
    private fun hideCustomProgressDialog(){
        if (this.progressDialog != null){
            this.progressDialog?.dismiss()
        }
    }

    /** Set up the UI with the information about the weather(got on our current location) */
    @SuppressLint("SetTextI18n")
    private fun setUpUI(){
        // Get the data from 'sharedPreferences' which was stored by the editor(create via .edit() of sharedPreferences)
        val currWeatherJsonString = this.sharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")
        if (!currWeatherJsonString.isNullOrEmpty()){
            val currWeather = Gson().fromJson(currWeatherJsonString, WeatherResponseModel::class.java)

            for(i in currWeather.weather.indices){
                Log.i("CURRENT WEATHER: ", currWeather.weather.toString())
                // 1st card view (Weather)
                this.binding?.tvMain?.text = currWeather.weather[i].main
                this.binding?.tvMainDescription?.text = currWeather.weather[i].description

                // 2nd card view (Temperature)
                this.binding?.tvTemperature?.text = currWeather.main.temp.toString() +  getUnit(application.resources.configuration.locales.toString())
                this.binding?.tvHumidity?.text = currWeather.main.humidity.toString() + " per cent"

                // 3rd card view (temperature-max/min)
                this.binding?.tvTemperatureMax?.text = currWeather.main.temp_max.toString() + " max"
                this.binding?.tvTemperatureMin?.text = currWeather.main.temp_min.toString() + " min"

                //4th card view (wind)
                this.binding?.tvWind?.text = currWeather.wind.deg.toString()
                this.binding?.tvWindDescription?.text = "miles/hour"

                // 5th card view (Location)
                this.binding?.tvLocation?.text = currWeather.name
                this.binding?.tvLocationDescription?.text = currWeather.sys.country

                // 6th card view (sunrise/sunset)
                this.binding?.tvSunrise?.text = unixTime(currWeather.sys.sunrise)
                this.binding?.tvSunset?.text = unixTime(currWeather.sys.sunset)

                // Change icon depending on the weather
                when(currWeather.weather[i].icon){
                    "01d" -> this.binding?.ivMain?.setImageResource(R.drawable.sunny)
                    "02d" -> this.binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03d" -> this.binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04d" -> this.binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04n" -> this.binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10d" -> this.binding?.ivMain?.setImageResource(R.drawable.rain)
                    "11d" -> this.binding?.ivMain?.setImageResource(R.drawable.storm)
                    "13d" -> this.binding?.ivMain?.setImageResource(R.drawable.snowflake)
                    "01n" -> this.binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "02n" -> this.binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03n" -> this.binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10n" -> this.binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "11n" -> this.binding?.ivMain?.setImageResource(R.drawable.rain)
                    "13n" -> this.binding?.ivMain?.setImageResource(R.drawable.snowflake)
                }
            }
        }
    }

    /** Method for determining the specific units via current location */
    private fun getUnit(value: String): String {
        var resultValue = "℃"
        if ("US" == value || "LR" == value || "MM" == value){
            resultValue = "F"
        }
        return resultValue
    }

    /** Get the current time based on current location
     * @param timex parameter holding the time in long which can be converted to date */
    @SuppressLint("SimpleDateFormat")
    private fun unixTime(timex: Long): String{
        val date = Date(timex * 1000L)              // Must multiply by 1000L cuz timestamp is in millis
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    /** What should do on creating Options Menu */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    /** What should do on selected option item */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.btn_refresh -> {
                requestLocationData()
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }
}