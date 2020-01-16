package com.panda.uber

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button

class MainActivity : AppCompatActivity() {
    private var mDriver: Button? = null
    private var mCustomer: Button? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mDriver = findViewById<View>(R.id.driver) as Button
        mCustomer = findViewById<View>(R.id.customer) as Button

        startService(Intent(this@MainActivity, onAppKilled::class.java))
        mDriver!!.setOnClickListener(View.OnClickListener {
            val intent = Intent(this@MainActivity, DriverLoginActivity::class.java)
            startActivity(intent)
            finish()
            return@OnClickListener
        })

        mCustomer!!.setOnClickListener(View.OnClickListener {
            val intent = Intent(this@MainActivity, CustomerLoginActivity::class.java)
            startActivity(intent)
            finish()
            return@OnClickListener
        })
    }
}
