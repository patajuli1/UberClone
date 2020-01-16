package com.panda.uber

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

import com.bumptech.glide.Glide
import com.directions.route.AbstractRouting
import com.directions.route.Route
import com.directions.route.RouteException
import com.directions.route.Routing
import com.directions.route.RoutingListener
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

import java.util.ArrayList
import java.util.HashMap

class DriverMapActivity : FragmentActivity(), OnMapReadyCallback, RoutingListener {

    private var mMap: GoogleMap? = null
    internal var mLastLocation: Location? = null
    lateinit var mLocationRequest: LocationRequest

    private var mFusedLocationClient: FusedLocationProviderClient? = null


    private var mLogout: Button? = null
    private var mSettings: Button? = null
    private var mRideStatus: Button? = null
    private var mHistory: Button? = null

    private var mWorkingSwitch: Switch? = null

    private var status = 0

    private var customerId = ""
    private var destination: String? = null
    private var destinationLatLng: LatLng? = null
    private var pickupLatLng: LatLng? = null
    private var rideDistance: Float = 0.toFloat()

    private var isLoggingOut: Boolean? = false

    private var mapFragment: SupportMapFragment? = null

    private var mCustomerInfo: LinearLayout? = null

    private var mCustomerProfileImage: ImageView? = null

    private var mCustomerName: TextView? = null
    private var mCustomerPhone: TextView? = null
    private var mCustomerDestination: TextView? = null

    internal var pickupMarker: Marker? = null
    private var assignedCustomerPickupLocationRef: DatabaseReference? = null
    private var assignedCustomerPickupLocationRefListener: ValueEventListener? = null

    private val currentTimestamp: Long?
        get() = System.currentTimeMillis() / 1000


    internal var mLocationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                if (applicationContext != null) {

                    if (customerId != "" && mLastLocation != null && location != null) {
                        rideDistance += mLastLocation!!.distanceTo(location) / 1000
                    }
                    mLastLocation = location


                    val latLng = LatLng(location!!.latitude, location.longitude)
                    mMap!!.moveCamera(CameraUpdateFactory.newLatLng(latLng))
                    mMap!!.animateCamera(CameraUpdateFactory.zoomTo(11f))

                    val userId = FirebaseAuth.getInstance().currentUser!!.uid
                    val refAvailable = FirebaseDatabase.getInstance().getReference("driversAvailable")
                    val refWorking = FirebaseDatabase.getInstance().getReference("driversWorking")
                    val geoFireAvailable = GeoFire(refAvailable)
                    val geoFireWorking = GeoFire(refWorking)

                    when (customerId) {
                        "" -> {
                            geoFireWorking.removeLocation(userId)
                            geoFireAvailable.setLocation(userId, GeoLocation(location.latitude, location.longitude))
                        }

                        else -> {
                            geoFireAvailable.removeLocation(userId)
                            geoFireWorking.setLocation(userId, GeoLocation(location.latitude, location.longitude))
                        }
                    }
                }
            }
        }
    }


    private var polylines: MutableList<Polyline>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_map)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        polylines = ArrayList()


        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment!!.getMapAsync(this)



        mCustomerInfo = findViewById<View>(R.id.customerInfo) as LinearLayout

        mCustomerProfileImage = findViewById<View>(R.id.customerProfileImage) as ImageView

        mCustomerName = findViewById<View>(R.id.customerName) as TextView
        mCustomerPhone = findViewById<View>(R.id.customerPhone) as TextView
        mCustomerDestination = findViewById<View>(R.id.customerDestination) as TextView

        mWorkingSwitch = findViewById<View>(R.id.workingSwitch) as Switch
        mWorkingSwitch!!.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                connectDriver()
            } else {
                disconnectDriver()
            }
        }

        mSettings = findViewById<View>(R.id.settings) as Button
        mLogout = findViewById<View>(R.id.logout) as Button
        mRideStatus = findViewById<View>(R.id.rideStatus) as Button
        mHistory = findViewById<View>(R.id.history) as Button
        mRideStatus!!.setOnClickListener {
            when (status) {
                1 -> {
                    status = 2
                    erasePolylines()
                    if (destinationLatLng!!.latitude != 0.0 && destinationLatLng!!.longitude != 0.0) {
                        getRouteToMarker(destinationLatLng)
                    }
                    mRideStatus!!.text = "drive completed"
                }
                2 -> {
                    recordRide()
                    endRide()
                }
            }
        }

        mLogout!!.setOnClickListener(View.OnClickListener {
            isLoggingOut = true

            disconnectDriver()

            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this@DriverMapActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
            return@OnClickListener
        })
        mSettings!!.setOnClickListener(View.OnClickListener {
            val intent = Intent(this@DriverMapActivity, DriverSettingsActivity::class.java)
            startActivity(intent)
            return@OnClickListener
        })
        mHistory!!.setOnClickListener(View.OnClickListener {
            val intent = Intent(this@DriverMapActivity, HistoryActivity::class.java)
            intent.putExtra("customerOrDriver", "Drivers")
            startActivity(intent)
            return@OnClickListener
        })
        getAssignedCustomer()
    }

    private fun getAssignedCustomer() {
        val driverId = FirebaseAuth.getInstance().currentUser!!.uid
        val assignedCustomerRef = FirebaseDatabase.getInstance().reference.child("Users").child("Drivers").child(driverId).child("customerRequest").child("customerRideId")
        assignedCustomerRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    status = 1
                    customerId = dataSnapshot.value!!.toString()
                    getAssignedCustomerPickupLocation()
                    getAssignedCustomerDestination()
                    getAssignedCustomerInfo()
                } else {
                    endRide()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {}
        })
    }

    private fun getAssignedCustomerPickupLocation() {
        assignedCustomerPickupLocationRef = FirebaseDatabase.getInstance().reference.child("customerRequest").child(customerId).child("l")
        assignedCustomerPickupLocationRefListener = assignedCustomerPickupLocationRef!!.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists() && customerId != "") {
                    val map = dataSnapshot.value as List<Any>?
                    var locationLat = 0.0
                    var locationLng = 0.0
                    if (map!![0] != null) {
                        locationLat = java.lang.Double.parseDouble(map[0].toString())
                    }
                    if (map[1] != null) {
                        locationLng = java.lang.Double.parseDouble(map[1].toString())
                    }
                    pickupLatLng = LatLng(locationLat, locationLng)
                    pickupMarker = mMap!!.addMarker(MarkerOptions().position(pickupLatLng!!).title("pickup location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)))
                    getRouteToMarker(pickupLatLng)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {}
        })
    }

    private fun getRouteToMarker(pickupLatLng: LatLng?) {
        if (pickupLatLng != null && mLastLocation != null) {
            val routing = Routing.Builder()
                    .travelMode(AbstractRouting.TravelMode.DRIVING)
                    .withListener(this)
                    .alternativeRoutes(false)
                    .waypoints(LatLng(mLastLocation!!.latitude, mLastLocation!!.longitude), pickupLatLng)
                    .build()
            routing.execute()
        }
    }

    private fun getAssignedCustomerDestination() {
        val driverId = FirebaseAuth.getInstance().currentUser!!.uid
        val assignedCustomerRef = FirebaseDatabase.getInstance().reference.child("Users").child("Drivers").child(driverId).child("customerRequest")
        assignedCustomerRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val map = dataSnapshot.value as Map<String, Any>?
                    if (map!!["destination"] != null) {
                        destination = map["destination"]!!.toString()
                        mCustomerDestination!!.text = "Destination: " + destination!!
                    } else {
                        mCustomerDestination!!.text = "Destination: --"
                    }

                    var destinationLat: Double? = 0.0
                    var destinationLng: Double? = 0.0
                    if (map["destinationLat"] != null) {
                        destinationLat = java.lang.Double.valueOf(map["destinationLat"]!!.toString())
                    }
                    if (map["destinationLng"] != null) {
                        destinationLng = java.lang.Double.valueOf(map["destinationLng"]!!.toString())
                        destinationLatLng = LatLng(destinationLat!!, destinationLng)
                    }

                }
            }

            override fun onCancelled(databaseError: DatabaseError) {}
        })
    }


    private fun getAssignedCustomerInfo() {
        mCustomerInfo!!.visibility = View.VISIBLE
        val mCustomerDatabase = FirebaseDatabase.getInstance().reference.child("Users").child("Customers").child(customerId)
        mCustomerDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.childrenCount > 0) {
                    val map = dataSnapshot.value as Map<String, Any>?
                    if (map!!["name"] != null) {
                        mCustomerName!!.text = map["name"]!!.toString()
                    }
                    if (map["phone"] != null) {
                        mCustomerPhone!!.text = map["phone"]!!.toString()
                    }
                    if (map["profileImageUrl"] != null) {
                        Glide.with(application).load(map["profileImageUrl"]!!.toString()).into(mCustomerProfileImage!!)
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {}
        })
    }


    private fun endRide() {
        mRideStatus!!.text = "picked customer"
        erasePolylines()

        val userId = FirebaseAuth.getInstance().currentUser!!.uid
        val driverRef = FirebaseDatabase.getInstance().reference.child("Users").child("Drivers").child(userId).child("customerRequest")
        driverRef.removeValue()

        val ref = FirebaseDatabase.getInstance().getReference("customerRequest")
        val geoFire = GeoFire(ref)
        geoFire.removeLocation(customerId)
        customerId = ""
        rideDistance = 0f

        if (pickupMarker != null) {
            pickupMarker!!.remove()
        }
        if (assignedCustomerPickupLocationRefListener != null) {
            assignedCustomerPickupLocationRef!!.removeEventListener(assignedCustomerPickupLocationRefListener!!)
        }
        mCustomerInfo!!.visibility = View.GONE
        mCustomerName!!.text = ""
        mCustomerPhone!!.text = ""
        mCustomerDestination!!.text = "Destination: --"
        mCustomerProfileImage!!.setImageResource(R.mipmap.ic_default_user)
    }

    private fun recordRide() {
        val userId = FirebaseAuth.getInstance().currentUser!!.uid
        val driverRef = FirebaseDatabase.getInstance().reference.child("Users").child("Drivers").child(userId).child("history")
        val customerRef = FirebaseDatabase.getInstance().reference.child("Users").child("Customers").child(customerId).child("history")
        val historyRef = FirebaseDatabase.getInstance().reference.child("history")
        val requestId = historyRef.push().key
        driverRef.child(requestId!!).setValue(true)
        customerRef.child(requestId).setValue(true)

        val map = HashMap<String,Any>()
        map.put("driver", userId)
        map.put("customer", customerId)
        map.put("rating", 0)
        currentTimestamp?.let { map.put("timestamp", it) }
        destination?.let { map.put("destination", it) }
        map.put("location/from/lat", pickupLatLng!!.latitude)
        map.put("location/from/lng", pickupLatLng!!.longitude)
        map.put("location/to/lat", destinationLatLng!!.latitude)
        map.put("location/to/lng", destinationLatLng!!.longitude)
        map.put("distance", rideDistance)
        historyRef.child(requestId).updateChildren(map)
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mLocationRequest = LocationRequest()
        mLocationRequest.interval = 1000
        mLocationRequest.fastestInterval = 1000
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            } else {
                checkLocationPermission()
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                AlertDialog.Builder(this)
                        .setTitle("give permission")
                        .setMessage("give permission message")
                        .setPositiveButton("OK") { dialogInterface, i -> ActivityCompat.requestPermissions(this@DriverMapActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1) }
                        .create()
                        .show()
            } else {
                ActivityCompat.requestPermissions(this@DriverMapActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        mFusedLocationClient!!.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())
                        mMap!!.isMyLocationEnabled = true
                    }
                } else {
                    Toast.makeText(applicationContext, "Please provide the permission", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private fun connectDriver() {
        checkLocationPermission()
        mFusedLocationClient!!.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())
        mMap!!.isMyLocationEnabled = true
    }

    private fun disconnectDriver() {
        if (mFusedLocationClient != null) {
            mFusedLocationClient!!.removeLocationUpdates(mLocationCallback)
        }
        val userId = FirebaseAuth.getInstance().currentUser!!.uid
        val ref = FirebaseDatabase.getInstance().getReference("driversAvailable")

        val geoFire = GeoFire(ref)
        geoFire.removeLocation(userId)
    }

    override fun onRoutingFailure(e: RouteException?) {
        if (e != null) {
            Toast.makeText(this, "Error: " + e.message, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRoutingStart() {}
    override fun onRoutingSuccess(route: ArrayList<Route>, shortestRouteIndex: Int) {
        if (polylines!!.size > 0) {
            for (poly in polylines!!) {
                poly.remove()
            }
        }

        polylines = ArrayList()
        //add route(s) to the map.
        for (i in route.indices) {

            //In case of more than 5 alternative routes
            val colorIndex = i % COLORS.size

            val polyOptions = PolylineOptions()
            polyOptions.color(resources.getColor(COLORS[colorIndex]))
            polyOptions.width((10 + i * 3).toFloat())
            polyOptions.addAll(route[i].points)
            val polyline = mMap!!.addPolyline(polyOptions)
            polylines!!.add(polyline)

            Toast.makeText(applicationContext, "Route " + (i + 1) + ": distance - " + route[i].distanceValue + ": duration - " + route[i].durationValue, Toast.LENGTH_SHORT).show()
        }

    }

    override fun onRoutingCancelled() {}
    private fun erasePolylines() {
        for (line in polylines!!) {
            line.remove()
        }
        polylines!!.clear()
    }

    companion object {
        private val COLORS = intArrayOf(R.color.primary_dark_material_light)
    }

}