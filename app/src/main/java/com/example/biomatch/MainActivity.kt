package com.example.biomatch

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        Handler().postDelayed({

//            AutoLogin()
            val intent = Intent(this@MainActivity, MainHome::class.java)
            startActivity(intent)
            finish()
        },4000)
    }

    private fun AutoLogin() {
        val sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE)
        val auto_phoneno = sharedPreferences.getString("phoneno", "")
        val auto_password = sharedPreferences.getString("password", "")
        Log.d("saved phoneno", auto_phoneno!!)
        Log.d("saved password", auto_password!!)
        val reference =
            FirebaseDatabase.getInstance("https://biomatch-96b5e-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("users")
        val checkUsername = reference.orderByChild("phoneno").equalTo(auto_phoneno)
        checkUsername.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val database_password = snapshot.child(auto_phoneno).child("password").getValue(
                        String::class.java
                    )
                    if (database_password == auto_password) {
                        //Intent intent = new Intent(MainActivity.this,testing_page.class);
                        val intent = Intent(this@MainActivity, MainHome::class.java)
                        intent.putExtra("phoneno", auto_phoneno)
                        intent.putExtra("startingpage", "1")
                        startActivity(intent)
                        finish()
                    } else {
                        val intent = Intent(this@MainActivity, Login::class.java)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    val intent = Intent(this@MainActivity, Login::class.java)
                    startActivity(intent)
                    finish()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}