package com.panda.uber

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView

import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.HashMap
import java.util.Objects

class CustomerSettingsActivity : AppCompatActivity() {

    private var mNameField: EditText? = null
    private var mPhoneField: EditText? = null

    private var mBack: Button? = null
    private var mConfirm: Button? = null

    private var mProfileImage: ImageView? = null

    private var mAuth: FirebaseAuth? = null
    private var mCustomerDatabase: DatabaseReference? = null

    private var userID: String? = null
    private var mName: String? = null
    private var mPhone: String? = null
    private var mProfileImageUrl: String? = null

    private var resultUri: Uri? = null


    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_settings)

        mNameField = findViewById<View>(R.id.name) as EditText
        mPhoneField = findViewById<View>(R.id.phone) as EditText

        mProfileImage = findViewById<View>(R.id.profileImage) as ImageView

        mBack = findViewById<View>(R.id.back) as Button
        mConfirm = findViewById<View>(R.id.confirm) as Button

        mAuth = FirebaseAuth.getInstance()
        userID = mAuth!!.currentUser!!.uid
        mCustomerDatabase = FirebaseDatabase.getInstance().reference.child("Users").child("Customers").child(userID!!)

        getUserInfo()

        mProfileImage!!.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, 1)
        }

        mConfirm!!.setOnClickListener { saveUserInformation() }

        mBack!!.setOnClickListener(View.OnClickListener {
            finish()
            return@OnClickListener
        })
    }

    private fun getUserInfo() {
        mCustomerDatabase!!.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.childrenCount > 0) {
                    val map = dataSnapshot.value as Map<String, Any>?
                    if (map!!["name"] != null) {
                        mName = map["name"]!!.toString()
                        mNameField!!.setText(mName)
                    }
                    if (map["phone"] != null) {
                        mPhone = map["phone"]!!.toString()
                        mPhoneField!!.setText(mPhone)
                    }
                    if (map["profileImageUrl"] != null) {
                        mProfileImageUrl = map["profileImageUrl"]!!.toString()
                        Glide.with(application).load(mProfileImageUrl).into(mProfileImage!!)
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {}
        })
    }


    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun saveUserInformation() {
        mName = mNameField!!.text.toString()
        mPhone = mPhoneField!!.text.toString()

        val userInfo = HashMap<String,Any?>()
        userInfo.put("name", mName)
        userInfo.put("phone", mPhone)
        mCustomerDatabase!!.updateChildren(userInfo)

        if (resultUri != null) {

            val filePath = FirebaseStorage.getInstance().reference.child("profile_images").child(userID!!)
            var bitmap: Bitmap? = null
            try {
                bitmap = MediaStore.Images.Media.getBitmap(application.contentResolver, resultUri)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            val baos = ByteArrayOutputStream()
            bitmap!!.compress(Bitmap.CompressFormat.JPEG, 20, baos)
            val data = baos.toByteArray()
            val uploadTask = filePath.putBytes(data)


            val urlTask = getUrlTask(uploadTask)
            urlTask.result

        } else {
            finish()
        }

    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun getUrlTask(uploadTask: UploadTask): Task<Uri> {
        return uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) {
                throw Objects.requireNonNull<Exception>(task.exception)
            }

            // Continue with the task to get the download URL
            uploadTask.result.storage.downloadUrl
        }.addOnCompleteListener(OnCompleteListener { task ->
            if (task.isSuccessful) {
                val downloadUrl = task.result

                val newImage = HashMap<String,Any>()
                newImage.put("profileImageUrl", downloadUrl!!.toString())
                mCustomerDatabase!!.updateChildren(newImage)

                finish()
                return@OnCompleteListener
            } else {
                // Handle failures
                // ...
            }
        }).addOnFailureListener { }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            val imageUri = data!!.data
            resultUri = imageUri
            mProfileImage!!.setImageURI(resultUri)
        }
    }
}
