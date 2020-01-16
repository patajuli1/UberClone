package com.panda.uber

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast

import com.bumptech.glide.Glide
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.places.Place
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment
import com.google.android.gms.location.places.ui.PlaceSelectionListener
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.lang.NullPointerException

import java.util.ArrayList
import java.util.HashMap

class CustomerMapActivity : FragmentActivity(), OnMapReadyCallback {

    private var mMap: GoogleMap? = null
    internal lateinit var mLastLocation: Location
    internal lateinit var mLocationRequest: LocationRequest

    private var mFusedLocationClient: FusedLocationProviderClient? = null

    private var mLogout: Button? = null
    private var mRequest: Button? = null
    private var mSettings: Button? = null
    private var mHistory: Button? = null

    private var pickupLocation: LatLng? = null

    private var requestBol: Boolean? = false

    private var pickupMarker: Marker? = null

    private var mapFragment: SupportMapFragment? = null

    private var destination: String? = null
    private var requestService: String? = null

    private var destinationLatLng: LatLng? = null

    private var mDriverInfo: LinearLayout? = null

    private var mDriverProfileImage: ImageView? = null

    private var mDriverName: TextView? = null
    private var mDriverPhone: TextView? = null
    private var mDriverCar: TextView? = null

    private var mRadioGroup: RadioGroup? = null

    private var mRatingBar: RatingBar? = null
    private var radius = 1
    private var driverFound: Boolean? = false
    private var driverFoundID: String? = null

    internal lateinit var geoQuery: GeoQuery
    /*-------------------------------------------- Map specific functions -----
    |  Function(s) getDriverLocation
    |
    |  Purpose:  Get's most updated driver location and it's always checking for movements.
    |
    |  Note:
    |	   Even tho we used geofire to push the location of the driver we can use a normal
    |      Listener to get it's location with no problem.
    |
    |      0 -> Latitude
    |      1 -> Longitudde
    |
    *-------------------------------------------------------------------*/
    private var mDriverMarker: Marker? = null
    private var driverLocationRef: DatabaseReference? = null
    private var driverLocationRefListener: ValueEventListener? = null

    private var driveHasEndedRef: DatabaseReference? = null
    private var driveHasEndedRefListener: ValueEventListener? = null

    internal var mLocationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                if (applicationContext != null) {
                    mLastLocation = location

                    val latLng = LatLng(location.latitude, location.longitude)

                    //mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                    //mMap.animateCamera(CameraUpdateFactory.zoomTo(11));
                    if (!getDriversAroundStarted)
                        getDriversAround()
                }
            }
        }
    }


    internal var getDriversAroundStarted = false
    internal var markers: MutableList<Marker> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_costumer_map)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment!!.getMapAsync(this)

        destinationLatLng = LatLng(0.0, 0.0)

        mDriverInfo = findViewById<View>(R.id.driverInfo) as LinearLayout

        mDriverProfileImage = findViewById<View>(R.id.driverProfileImage) as ImageView

        mDriverName = findViewById<View>(R.id.driverName) as TextView
        mDriverPhone = findViewById<View>(R.id.driverPhone) as TextView
        mDriverCar = findViewById<View>(R.id.driverCar) as TextView

        mRatingBar = findViewById<View>(R.id.ratingBar) as RatingBar

        mRadioGroup = findViewById<View>(R.id.radioGroup) as RadioGroup
        mRadioGroup!!.check(R.id.UberX)

        mLogout = findViewById<View>(R.id.logout) as Button
        mRequest = findViewById<View>(R.id.request) as Button
        mSettings = findViewById<View>(R.id.settings) as Button
        mHistory = findViewById<View>(R.id.history) as Button

        mLogout!!.setOnClickListener(View.OnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this@CustomerMapActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
            return@OnClickListener
        })

        mRequest!!.setOnClickListener(View.OnClickListener {
            if (requestBol!!) {
                endRide()


            } else {
                val selectId = mRadioGroup!!.checkedRadioButtonId

                val radioButton = findViewById<View>(selectId) as RadioButton

                if (radioButton.text == null) {
                    return@OnClickListener
                }

                requestService = radioButton.text.toString()

                requestBol = true

                val userId = FirebaseAuth.getInstance().currentUser!!.uid

                val ref = FirebaseDatabase.getInstance().getReference("customerRequest")
                val geoFire = GeoFire(ref)
                geoFire.setLocation(userId, GeoLocation(mLastLocation.latitude, mLastLocation.longitude))

                pickupLocation = LatLng(mLastLocation.latitude, mLastLocation.longitude)
                pickupMarker = mMap!!.addMarker(MarkerOptions().position(pickupLocation!!).title("Pickup Here").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)))

                mRequest!!.text = "Getting your Driver...."

                getClosestDriver()
            }
        })
        mSettings!!.setOnClickListener(View.OnClickListener {
            val intent = Intent(this@CustomerMapActivity, CustomerSettingsActivity::class.java)
            startActivity(intent)
            return@OnClickListener
        })

        mHistory!!.setOnClickListener(View.OnClickListener {
            val intent = Intent(this@CustomerMapActivity, HistoryActivity::class.java)
            intent.putExtra("customerOrDriver", "Customers")
            startActivity(intent)
            return@OnClickListener
        })

        val autocompleteFragment = fragmentManager.findFragmentById(R.id.place_autocomplete_fragment) as PlaceAutocompleteFragment

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                // TODO: Get info about the selected place.
                destination = place.name.toString()
                destinationLatLng = place.latLng
            }

            override fun onError(status: Status) {
                // TODO: Handle the error.
            }
        })


    }

    private fun getClosestDriver() {
        val driverLocation = FirebaseDatabase.getInstance().reference.child("driversAvailable")

        val geoFire = GeoFire(driverLocation)
        geoQuery = geoFire.queryAtLocation(GeoLocation(pickupLocation!!.latitude, pickupLocation!!.longitude), radius.toDouble())
        geoQuery.removeAllListeners()

        geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
            override fun onKeyEntered(key: String, location: GeoLocation) {
                if ((!driverFound!!) && requestBol!!) {
                    val mCustomerDatabase = FirebaseDatabase.getInstance().reference.child("Users").child("Drivers").child(key)
                    mCustomerDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            if (dataSnapshot.exists() && dataSnapshot.childrenCount > 0) {
                                val driverMap = dataSnapshot.value as Map<String, Any>?
                                if (driverFound!!) {
                                    return
                                }

                                if (driverMap!!["service"] == requestService) {
                                    driverFound = true
                                    driverFoundID = dataSnapshot.key

                                    val driverRef = FirebaseDatabase.getInstance().reference.child("Users").child("Drivers").child(driverFoundID!!).child("customerRequest")
                                    val customerId = FirebaseAuth.getInstance().currentUser!!.uid
                                    val map = HashMap<String,Any?>()
                                    map.put("customerRideId", customerId)
                                    map.put("destination", destination)
                                    map.put("destinationLat", destinationLatLng!!.latitude)
                                    map.put("destinationLng", destinationLatLng!!.longitude)
                                    driverRef.updateChildren(map)

                                    getDriverLocation()
                                    getDriverInfo()
                                    getHasRideEnded()
                                    mRequest!!.text = "Looking for Driver Location...."
                                }
                            }
                        }

                        override fun onCancelled(databaseError: DatabaseError) {}
                    })
                }
            }

            override fun onKeyExited(key: String) {

            }

            override fun onKeyMoved(key: String, location: GeoLocation) {

            }

            override fun onGeoQueryReady() {
                if ((!driverFound!!)) {
                    radius++
                    getClosestDriver()
                }
            }

            override fun onGeoQueryError(error: DatabaseError) {

            }
        })
    }

    private fun getDriverLocation() {
        driverLocationRef = FirebaseDatabase.getInstance().reference.child("driversWorking").child(driverFoundID!!).child("l")
        driverLocationRefListener = driverLocationRef!!.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists() && requestBol!!) {
                    val map = dataSnapshot.value as List<Any>?
                    var locationLat = 0.0
                    var locationLng = 0.0
                    if (map!![0] != null) {
                        locationLat = java.lang.Double.parseDouble(map[0].toString())
                    }
                    if (map[1] != null) {
                        locationLng = java.lang.Double.parseDouble(map[1].toString())
                    }
                    val driverLatLng = LatLng(locationLat, locationLng)
                    if (mDriverMarker != null) {
                        mDriverMarker!!.remove()
                    }
                    val loc1 = Location("")
                    loc1.latitude = pickupLocation!!.latitude
                    loc1.longitude = pickupLocation!!.longitude

                    val loc2 = Location("")
                    loc2.latitude = driverLatLng.latitude
                    loc2.longitude = driverLatLng.longitude

                    val distance = loc1.distanceTo(loc2)

                    if (distance < 100) {
                        mRequest!!.text = "Driver's Here"
                    } else {
                        mRequest!!.text = "Driver Found: $distance"
                    }



                    mDriverMarker = mMap!!.addMarker(MarkerOptions().position(driverLatLng).title("your driver").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_car)))
                }

            }

            override fun onCancelled(databaseError: DatabaseError) {}
        })

    }

    /*-------------------------------------------- getDriverInfo -----
    |  Function(s) getDriverInfo
    |
    |  Purpose:  Get all the user information that we can get from the user's database.
    |
    |  Note: --
    |
    *-------------------------------------------------------------------*/
    private fun getDriverInfo() {
        mDriverInfo!!.visibility = View.VISIBLE
        val mCustomerDatabase = FirebaseDatabase.getInstance().reference.child("Users").child("Drivers").child(driverFoundID!!)
        mCustomerDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.childrenCount > 0) {
                    if (dataSnapshot.child("name") != null) {
                        mDriverName!!.text = dataSnapshot.child("name").value!!.toString()
                    }
                    if (dataSnapshot.child("phone") != null) {
                        mDriverPhone!!.text = dataSnapshot.child("phone").value!!.toString()
                    }
                    if (dataSnapshot.child("car") != null) {
                        mDriverCar!!.text = dataSnapshot.child("car").value!!.toString()
                    }
                    if (dataSnapshot.child("profileImageUrl").value != null) {
                        Glide.with(application).load(dataSnapshot.child("profileImageUrl").value!!.toString()).into(mDriverProfileImage!!)
                    }

                    var ratingSum = 0
                    var ratingsTotal = 0f
                    var ratingsAvg = 0f
                    for (child in dataSnapshot.child("rating").children) {
                        ratingSum = ratingSum + Integer.valueOf(child.value!!.toString())
                        ratingsTotal++
                    }
                    if (ratingsTotal != 0f) {
                        ratingsAvg = ratingSum / ratingsTotal
                        mRatingBar!!.rating = ratingsAvg
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {}
        })
    }

    private fun getHasRideEnded() {
        driveHasEndedRef = FirebaseDatabase.getInstance().reference.child("Users").child("Drivers").child(driverFoundID!!).child("customerRequest").child("customerRideId")
        driveHasEndedRefListener = driveHasEndedRef!!.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {

                } else {
                    endRide()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {}
        })
    }

    private fun endRide() {
        try {
            requestBol = false
            geoQuery.removeAllListeners()
            driverLocationRef!!.removeEventListener(driverLocationRefListener!!)
            driveHasEndedRef!!.removeEventListener(driveHasEndedRefListener!!)

            if (driverFoundID != null) {
                val driverRef = FirebaseDatabase.getInstance().reference.child("Users").child("Drivers").child(driverFoundID!!).child("customerRequest")
                driverRef.removeValue()
                driverFoundID = null

            }
            driverFound = false
            radius = 1
            val userId = FirebaseAuth.getInstance().currentUser!!.uid

            val ref = FirebaseDatabase.getInstance().getReference("customerRequest")
            val geoFire = GeoFire(ref)
            geoFire.removeLocation(userId)

            if (pickupMarker != null) {
                pickupMarker!!.remove()
            }
            if (mDriverMarker != null) {
                mDriverMarker!!.remove()
            }
            mRequest!!.text = "call Uber"

            mDriverInfo!!.visibility = View.GONE
            mDriverName!!.text = ""
            mDriverPhone!!.text = ""
            mDriverCar!!.text = "Destination: --"
            mDriverProfileImage!!.setImageResource(R.mipmap.ic_default_user)
        }catch (ex:NullPointerException){
            Log.d("nullpointer", ex.toString())
        }

    }

    /*-------------------------------------------- Map specific functions -----
    |  Function(s) onMapReady, buildGoogleApiClient, onLocationChanged, onConnected
    |
    |  Purpose:  Find and update user's location.
    |
    |  Note:
    |	   The update interval is set to 1000Ms and the accuracy is set to PRIORITY_HIGH_ACCURACY,
    |      If you're having trouble with battery draining too fast then change these to lower values
    |
    |
    *-------------------------------------------------------------------*/
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mLocationRequest = LocationRequest()
        mLocationRequest.interval = 1000
        mLocationRequest.fastestInterval = 1000
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            } else {
                checkLocationPermission()
            }
        }

        mFusedLocationClient!!.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())
        mMap!!.isMyLocationEnabled = true
    }

    /*-------------------------------------------- onRequestPermissionsResult -----
    |  Function onRequestPermissionsResult
    |
    |  Purpose:  Get permissions for our app if they didn't previously exist.
    |
    |  Note:
    |	requestCode: the nubmer assigned to the request that we've made. Each
    |                request has it's own unique request code.
    |
    *-------------------------------------------------------------------*/
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                android.app.AlertDialog.Builder(this)
                        .setTitle("give permission")
                        .setMessage("give permission message")
                        .setPositiveButton("OK") { dialogInterface, i -> ActivityCompat.requestPermissions(this@CustomerMapActivity, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1) }
                        .create()
                        .show()
            } else {
                ActivityCompat.requestPermissions(this@CustomerMapActivity, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1)
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

    private fun getDriversAround() {
        getDriversAroundStarted = true
        val driverLocation = FirebaseDatabase.getInstance().reference.child("driversAvailable")

        val geoFire = GeoFire(driverLocation)
        val geoQuery = geoFire.queryAtLocation(GeoLocation(mLastLocation.latitude, mLastLocation.longitude), 999999999.0)

        geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
            override fun onKeyEntered(key: String, location: GeoLocation) {

                for (markerIt in markers) {
                    if (markerIt.tag == key)
                        return
                }

                val driverLocation = LatLng(location.latitude, location.longitude)

                val mDriverMarker = mMap!!.addMarker(MarkerOptions().position(driverLocation).title(key).icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_car)))
                mDriverMarker.tag = key

                markers.add(mDriverMarker)


            }

            override fun onKeyExited(key: String) {
                for (markerIt in markers) {
                    if (markerIt.tag == key) {
                        markerIt.remove()
                    }
                }
            }

            override fun onKeyMoved(key: String, location: GeoLocation) {
                for (markerIt in markers) {
                    if (markerIt.tag == key) {
                        markerIt.position = LatLng(location.latitude, location.longitude)
                    }
                }
            }

            override fun onGeoQueryReady() {}

            override fun onGeoQueryError(error: DatabaseError) {

            }
        })
    }
}
