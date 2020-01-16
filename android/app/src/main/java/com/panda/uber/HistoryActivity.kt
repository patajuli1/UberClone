package com.panda.uber

import android.annotation.SuppressLint
import android.app.ProgressDialog
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.panda.uber.historyRecyclerView.HistoryAdapter
import com.panda.uber.historyRecyclerView.HistoryObject

import org.json.JSONException
import org.json.JSONObject

import java.io.IOException
import java.util.ArrayList
import java.util.Calendar
import java.util.Locale

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response


class HistoryActivity : AppCompatActivity() {
    private var customerOrDriver: String? = null
    private var userId: String? = null

    private var mHistoryRecyclerView: RecyclerView? = null
    private var mHistoryAdapter: RecyclerView.Adapter<*>? = null
    private var mHistoryLayoutManager: RecyclerView.LayoutManager? = null

    private var mBalance: TextView? = null

    private var Balance: Double? = 0.0

    private var mPayout: Button? = null

    private var mPayoutEmail: EditText? = null

    private val resultsHistory = ArrayList<HistoryObject>()
    private val dataSetHistory: ArrayList<HistoryObject>
        get() = resultsHistory
    internal lateinit var progress: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        mBalance = findViewById(R.id.balance)
        mPayout = findViewById(R.id.payout)
        mPayoutEmail = findViewById(R.id.payoutEmail)

        mHistoryRecyclerView = findViewById<View>(R.id.historyRecyclerView) as RecyclerView
        mHistoryRecyclerView!!.isNestedScrollingEnabled = false
        mHistoryRecyclerView!!.setHasFixedSize(true)
        mHistoryLayoutManager = LinearLayoutManager(this@HistoryActivity)
        mHistoryRecyclerView!!.layoutManager = mHistoryLayoutManager
        mHistoryAdapter = HistoryAdapter(dataSetHistory, this@HistoryActivity)
        mHistoryRecyclerView!!.adapter = mHistoryAdapter


        customerOrDriver = intent.extras!!.getString("customerOrDriver")
        userId = FirebaseAuth.getInstance().currentUser!!.uid
        getUserHistoryIds()

        if (customerOrDriver == "Drivers") {
            mBalance!!.visibility = View.VISIBLE
            mPayout!!.visibility = View.VISIBLE
            mPayoutEmail!!.visibility = View.VISIBLE
        }

        mPayout!!.setOnClickListener { payoutRequest() }
    }

    private fun getUserHistoryIds() {
        val userHistoryDatabase = FirebaseDatabase.getInstance().reference.child("Users").child(customerOrDriver!!).child(userId!!).child("history")
        userHistoryDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (history in dataSnapshot.children) {
                        FetchRideInformation(history.key)
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {}
        })
    }

    private fun FetchRideInformation(rideKey: String?) {
        val historyDatabase = FirebaseDatabase.getInstance().reference.child("history").child(rideKey!!)
        historyDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val rideId = dataSnapshot.key
                    var timestamp: Long? = 0L
                    val distance = ""
                    var ridePrice: Double? = 0.0

                    if (dataSnapshot.child("timestamp").value != null) {
                        timestamp = java.lang.Long.valueOf(dataSnapshot.child("timestamp").value!!.toString())
                    }

                    if (dataSnapshot.child("customerPaid").value != null && dataSnapshot.child("driverPaidOut").value == null) {
                        if (dataSnapshot.child("distance").value != null) {
                            ridePrice = java.lang.Double.valueOf(dataSnapshot.child("price").value!!.toString())
                            Balance = Balance?.plus(ridePrice)
                            mBalance!!.text = "Balance: " + Balance.toString() + " $"
                        }
                    }


                    val obj = HistoryObject(rideId, getDate(timestamp))
                    resultsHistory.add(obj)
                    mHistoryAdapter!!.notifyDataSetChanged()
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

    private fun payoutRequest() {
        progress = ProgressDialog(this)
        progress.setTitle("Processing your payout")
        progress.setMessage("Please Wait...")
        progress.setCancelable(false) // disable dismiss by tapping outside of the dialog
        progress.show()

        val client = OkHttpClient()
        val postData = JSONObject()
        try {
            postData.put("uid", FirebaseAuth.getInstance().currentUser!!.uid)
            postData.put("email", mPayoutEmail!!.text)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        val body = RequestBody.create(MEDIA_TYPE,
                postData.toString())

        val request = Request.Builder()
                .url("https://us-central1-uberapp-408c8.cloudfunctions.net/payout")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Your Token")
                .addHeader("cache-control", "no-cache")
                .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val mMessage = e.message.toString()
                Log.w("failure Response", mMessage)
                progress.dismiss()
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {

                val responseCode = response.code()


                if (response.isSuccessful)
                    when (responseCode) {
                        200 -> Snackbar.make(findViewById(R.id.layout), "Payout Successful!", Snackbar.LENGTH_LONG).show()
                        501 -> Snackbar.make(findViewById(R.id.layout), "Error: no payout available", Snackbar.LENGTH_LONG).show()
                        else -> Snackbar.make(findViewById(R.id.layout), "Error: couldn't complete the transaction", Snackbar.LENGTH_LONG).show()
                    }
                else
                    Snackbar.make(findViewById(R.id.layout), "Error: couldn't complete the transaction", Snackbar.LENGTH_LONG).show()

                progress.dismiss()
            }
        })
    }

    companion object {


        val MEDIA_TYPE = MediaType.parse("application/json")
    }
}
