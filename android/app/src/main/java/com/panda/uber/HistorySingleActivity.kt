package com.panda.uber

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast

import com.bumptech.glide.Glide
import com.directions.route.AbstractRouting
import com.directions.route.Route
import com.directions.route.RouteException
import com.directions.route.Routing
import com.directions.route.RoutingListener
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.paypal.android.sdk.payments.PayPalConfiguration
import com.paypal.android.sdk.payments.PayPalPayment
import com.paypal.android.sdk.payments.PayPalService
import com.paypal.android.sdk.payments.PaymentActivity
import com.paypal.android.sdk.payments.PaymentConfirmation

import org.json.JSONException
import org.json.JSONObject

import java.math.BigDecimal
import java.util.ArrayList
import java.util.Calendar
import java.util.Locale

class HistorySingleActivity : AppCompatActivity(), OnMapReadyCallback, RoutingListener {
    private var rideId: String? = null
    private var currentUserId: String? = null
    private var customerId: String? = null
    private var driverId: String? = null
    private var userDriverOrCustomer: String? = null

    private var rideLocation: TextView? = null
    private var rideDistance: TextView? = null
    private var rideDate: TextView? = null
    private var userName: TextView? = null
    private var userPhone: TextView? = null

    private var userImage: ImageView? = null

    private var mRatingBar: RatingBar? = null

    private var mPay: Button? = null

    private var historyRideInfoDb: DatabaseReference? = null

    private var destinationLatLng: LatLng? = null
    private var pickupLatLng: LatLng? = null
    private var distance: String? = null
    private var ridePrice: Double? = null
    private var customerPaid: Boolean? = false

    private var mMap: GoogleMap? = null
    private var mMapFragment: SupportMapFragment? = null

    private val PAYPAL_REQUEST_CODE = 1


    private var polylines: MutableList<Polyline>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_single)

        val intent = Intent(this, PayPalService::class.java)
        intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION, config)
        startService(intent)

        polylines = ArrayList()

        rideId = getIntent().extras!!.getString("rideId")

        mMapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mMapFragment!!.getMapAsync(this)


        rideLocation = findViewById<View>(R.id.rideLocation) as TextView
        rideDistance = findViewById<View>(R.id.rideDistance) as TextView
        rideDate = findViewById<View>(R.id.rideDate) as TextView
        userName = findViewById<View>(R.id.userName) as TextView
        userPhone = findViewById<View>(R.id.userPhone) as TextView

        userImage = findViewById<View>(R.id.userImage) as ImageView

        mRatingBar = findViewById<View>(R.id.ratingBar) as RatingBar

        mPay = findViewById(R.id.pay)

        currentUserId = FirebaseAuth.getInstance().currentUser!!.uid

        historyRideInfoDb = FirebaseDatabase.getInstance().reference.child("history").child(rideId!!)
        getRideInformation()

    }

    private fun getRideInformation() {
        historyRideInfoDb!!.addListenerForSingleValueEvent(object : ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (child in dataSnapshot.children) {
                        if (child.key == "customer") {
                            customerId = child.value!!.toString()
                            if (customerId != currentUserId) {
                                userDriverOrCustomer = "Drivers"
                                getUserInformation("Customers", customerId)
                            }
                        }
                        if (child.key == "driver") {
                            driverId = child.value!!.toString()
                            if (driverId != currentUserId) {
                                userDriverOrCustomer = "Customers"
                                getUserInformation("Drivers", driverId)
                                displayCustomerRelatedObjects()
                            }
                        }
                        if (child.key == "timestamp") {
                            rideDate!!.text = getDate(java.lang.Long.valueOf(child.value!!.toString()))
                        }
                        if (child.key == "rating") {
                            mRatingBar!!.rating = Integer.valueOf(child.value!!.toString()).toFloat()

                        }
                        if (child.key == "customerPaid") {
                            customerPaid = true
                        }
                        if (child.key == "distance") {
                            distance = child.value!!.toString()
                            rideDistance!!.text = distance!!.substring(0, Math.min(distance!!.length, 5)) + " km"
                            ridePrice = java.lang.Double.valueOf(distance!!) * 0.5

                        }
                        if (child.key == "destination") {
                            rideLocation!!.text = child.value!!.toString()
                        }
                        if (child.key == "location") {
                            pickupLatLng = LatLng(java.lang.Double.valueOf(child.child("from").child("lat").value!!.toString()), java.lang.Double.valueOf(child.child("from").child("lng").value!!.toString()))
                            destinationLatLng = LatLng(java.lang.Double.valueOf(child.child("to").child("lat").value!!.toString()), java.lang.Double.valueOf(child.child("to").child("lng").value!!.toString()))
                            if (destinationLatLng !== LatLng(0.0, 0.0)) {
                                getRouteToMarker()
                            }
                        }
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {}
        })
    }

    private fun displayCustomerRelatedObjects() {
        mRatingBar!!.visibility = View.VISIBLE
        mPay!!.visibility = View.VISIBLE
        mRatingBar!!.onRatingBarChangeListener = RatingBar.OnRatingBarChangeListener { ratingBar, rating, fromUser ->
            historyRideInfoDb!!.child("rating").setValue(rating)
            val mDriverRatingDb = FirebaseDatabase.getInstance().reference.child("Users").child("Drivers").child(driverId!!).child("rating")
            mDriverRatingDb.child(rideId!!).setValue(rating)
        }
        if (customerPaid!!) {
            mPay!!.isEnabled = false
        } else {
            mPay!!.isEnabled = true
        }
        mPay!!.setOnClickListener { payPalPayment() }
    }

    private fun payPalPayment() {
        val payment = PayPalPayment(BigDecimal(ridePrice!!), "USD", "Uber Ride",
                PayPalPayment.PAYMENT_INTENT_SALE)

        val intent = Intent(this, PaymentActivity::class.java)

        intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION, config)
        intent.putExtra(PaymentActivity.EXTRA_PAYMENT, payment)

        startActivityForResult(intent, PAYPAL_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PAYPAL_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val confirm = data!!.getParcelableExtra<PaymentConfirmation>(PaymentActivity.EXTRA_RESULT_CONFIRMATION)
                if (confirm != null) {
                    try {
                        val jsonObj = JSONObject(confirm.toJSONObject().toString())

                        val paymentResponse = jsonObj.getJSONObject("response").getString("state")

                        if (paymentResponse == "approved") {
                            Toast.makeText(applicationContext, "Payment successful", Toast.LENGTH_LONG).show()
                            historyRideInfoDb!!.child("customerPaid").setValue(true)
                            mPay!!.isEnabled = false
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }

                }

            } else {
                Toast.makeText(applicationContext, "Payment unsuccessful", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        stopService(Intent(this, PayPalService::class.java))
        super.onDestroy()
    }


    private fun getUserInformation(otherUserDriverOrCustomer: String, otherUserId: String?) {
        val mOtherUserDB = FirebaseDatabase.getInstance().reference.child("Users").child(otherUserDriverOrCustomer).child(otherUserId!!)
        mOtherUserDB.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val map = dataSnapshot.value as Map<String, Any>?
                    if (map!!["name"] != null) {
                        userName!!.text = map["name"]!!.toString()
                    }
                    if (map["phone"] != null) {
                        userPhone!!.text = map["phone"]!!.toString()
                    }
                    if (map["profileImageUrl"] != null) {
                        Glide.with(application).load(map["profileImageUrl"]!!.toString()).into(userImage!!)
                    }
                }

            }

            override fun onCancelled(databaseError: DatabaseError) {}
        })
    }

    private fun getDate(time: Long?): String {
        val cal = Calendar.getInstance(Locale.getDefault())
        cal.timeInMillis = time!! * 1000
        return DateFormat.format("MM-dd-yyyy hh:mm", cal).toString()
    }

    private fun getRouteToMarker() {
        val routing = Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(pickupLatLng, destinationLatLng)
                .build()
        routing.execute()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
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

        val builder = LatLngBounds.Builder()
        builder.include(pickupLatLng!!)
        builder.include(destinationLatLng!!)
        val bounds = builder.build()

        val width = resources.displayMetrics.widthPixels
        val padding = (width * 0.2).toInt()

        val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)

        mMap!!.animateCamera(cameraUpdate)

        mMap!!.addMarker(MarkerOptions().position(pickupLatLng!!).title("pickup location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)))
        mMap!!.addMarker(MarkerOptions().position(destinationLatLng!!).title("destination"))

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
        private val config = PayPalConfiguration()
                .environment(PayPalConfiguration.ENVIRONMENT_SANDBOX)
                //            .clientId(PayPalConfig.PAYPAL_CLIENT_ID);
                .clientId("")
        private val COLORS = intArrayOf(R.color.primary_dark_material_light)
    }

}
