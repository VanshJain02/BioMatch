package com.example.biomatch

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.*

class Login : AppCompatActivity() {

    lateinit var phoneno: TextInputLayout
    lateinit var password:TextInputLayout
    lateinit var login: Button
    lateinit var login_forgetpassword:Button
    lateinit var login_signup: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)


        //HOOKS
        phoneno = findViewById(R.id.login_phoneno)
        password = findViewById(R.id.login_password)
        login = findViewById(R.id.login_login)
        login_forgetpassword = findViewById(R.id.login_forgetpassword)
        login_signup = findViewById(R.id.login_signup)


        login.setOnClickListener { v -> loginUser(v) }
        login_signup.setOnClickListener {
            val intent = Intent(this@Login, SignUp::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun AutoSave(save_phoneno: String, save_password: String) {
        val sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("phoneno", save_phoneno)
        editor.putString("password", save_password)
        editor.apply()
    }

    fun validatePhoneNo(): Boolean? {
        val `val` = phoneno.editText!!.text.toString()
        return if (`val`.isEmpty()) {
            phoneno.error = "Field cannot be empty"
            false
        } else if (`val`.length != 10) {
            //Log.d("LENGTH",Integer.toString(val.length()));
            phoneno.error = "Incorrect Phone No"
            false
        } else {
            phoneno.error = null
            phoneno.isErrorEnabled = false
            true
        }
    }

    fun validatePassword(): Boolean? {
        val `val` = password.editText!!.text.toString()
        val passwordVal = Regex("^" +  //  "(?=.*[0-9])" +         //at least 1 digit
                "(?=.*[a-z])" +         //at least 1 lower case letter
                 "(?=.*[A-Z])" +         //at least 1 upper case letter
                 "(?=.*[a-zA-Z])" +      //any letter
                 "(?=.*[@#$%^&+=])" +    //at least 1 special character
                "(?=\\S+$)" +  //no white spaces
                ".{4,}" +  //at least 4 characters
                "$")
        return if (`val`.isEmpty()) {
            password.error = "Field cannot be empty"
            false
        } else if (!`val`.matches(passwordVal)) {
            password.error = "Password should be more than 4 characters"
            //password.setError("Password is too weak\n•at least 1 upper case letter\n•at least 1 lower case letter\n•at least 1 special character\n•at least 1 digit");
            false
        } else {
            password.error = null
            password.isErrorEnabled = false
            true
        }
    }

    fun loginUser(view: View?) {
        if (!validatePhoneNo()!! or !validatePassword()!!) {
            return
        } else {
            val login_phoneno = phoneno.editText!!.text.toString().trim { it <= ' ' }
            val login_password = password.editText!!.text.toString().trim { it <= ' ' }
            checkUser(login_phoneno, login_password)
        }
    }

    private fun checkUser(login_phoneno: String, login_password: String) {
        val reference =
            FirebaseDatabase.getInstance("https://biomatch-96b5e-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("users")
        val checkUsername = reference.orderByChild("phoneno").equalTo(login_phoneno)
        checkUsername.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    phoneno.error = null
                    phoneno.isErrorEnabled = false
                    val database_password =
                        snapshot.child(login_phoneno).child("password").getValue(
                            String::class.java
                        )
                    if (database_password == login_password) {
                        password.error = null
                        password.isErrorEnabled = false
                        AutoSave(login_phoneno, login_password)
                        val intent = Intent(this@Login, MainHome::class.java)
                        intent.putExtra("phoneno", login_phoneno)
                        intent.putExtra("startingpage", "1")
                        startActivity(intent)
                        finish()
                    } else {
                        password.error = "Incorrect Password"
                        password.requestFocus()
                    }
                } else {
                    phoneno.error = "User does not exists"
                    phoneno.requestFocus()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}