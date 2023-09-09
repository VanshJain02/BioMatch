package com.example.biomatch

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.biomatch.DataClass.user_signup
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.*

class SignUp : AppCompatActivity() {

    var fullname: TextInputLayout? = null
    var username:TextInputLayout? = null
    var phoneno:TextInputLayout? = null
    var password:TextInputLayout? = null
    lateinit var signup: Button
    lateinit var signup_login: Button

    var rootNode: FirebaseDatabase? = null
    var reference: DatabaseReference? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        fullname = findViewById<TextInputLayout>(R.id.signup_fullname)
        username = findViewById<TextInputLayout>(R.id.signup_username)
        phoneno = findViewById<TextInputLayout>(R.id.signup_phone)
        password = findViewById<TextInputLayout>(R.id.signup_password)
        signup = findViewById<Button>(R.id.signup_signup)
        signup_login = findViewById<Button>(R.id.signup_login)

        signup.setOnClickListener(View.OnClickListener { v ->
            rootNode = FirebaseDatabase.getInstance("https://biomatch-96b5e-default-rtdb.asia-southeast1.firebasedatabase.app")
            reference = rootNode!!.getReference("users")
            registerUser(v)
        })

        signup_login.setOnClickListener(View.OnClickListener {
            val intent = Intent(this@SignUp, Login::class.java)
            startActivity(intent)
            finish()
        })
    }

    fun validateName(): Boolean? {
        val `val` = fullname!!.editText!!.text.toString()
        return if (`val`.isEmpty()) {
            fullname!!.error = "Field cannot be empty"
            false
        } else {
            fullname!!.error = null
            true
        }
    }

    fun validateUsername(): Boolean? {
        val vali = username!!.editText!!.text.toString()
        val noWhiteSpace: Regex = Regex("\\A\\w{4,20}\\z")

        return if (vali.isEmpty()) {
            username!!.error = "Field cannot be empty"
            false
        } else if (vali.length >= 15) {
            username!!.error = "Username too long"
            false
        } else if (!vali.matches(noWhiteSpace)) {
            username!!.error = "White Spaces are not allowed"
            false
        } else {
            username!!.error = null
            username!!.isErrorEnabled = false
            true
        }
    }

    fun validatePhoneNo(): Boolean? {
        val `val` = phoneno!!.editText!!.text.toString()
        return if (`val`.isEmpty()) {
            phoneno!!.error = "Field cannot be empty"
            false
        } else if (`val`.length != 10) {
            //Log.d("LENGTH",Integer.toString(val.length()));
            phoneno!!.error = "Incorrect Phone No"
            false
        } else {
            phoneno!!.error = null
            phoneno!!.isErrorEnabled = false
            true
        }
    }

    fun validatePassword(): Boolean? {
        val `val` = password!!.editText!!.text.toString()
        val passwordVal = Regex("^" +  //"(?=.*[0-9])" +         //at least 1 digit
                "(?=.*[a-z])" +         //at least 1 lower case letter
                "(?=.*[A-Z])" +         //at least 1 upper case letter
                "(?=.*[a-zA-Z])" +      //any letter
                "(?=.*[@#$%^&+=])" +    //at least 1 special character
                "(?=\\S+$)" +  //no white spaces
                ".{4,}" +  //at least 4 characters
                "$")
        return if (`val`.isEmpty()) {
            password!!.error = "Field cannot be empty"
            false
        } else if (!`val`.matches(passwordVal)) {
            password!!.error = "Password should be more than 4 characters"
            // password.setError("Password is too weak\n•at least 1 upper case letter\n•at least 1 lower case letter\n•at least 1 special character\n•at least 1 digit");
            false
        } else {
            password!!.error = null
            password!!.isErrorEnabled = false
            true
        }
    }

    fun registerUser(view: View?) {
        val regname = fullname!!.editText!!.text.toString()
        val regusername = username!!.editText!!.text.toString().lowercase()
        val regphoneno = phoneno!!.editText!!.text.toString().lowercase()
        val regpassword = password!!.editText!!.text.toString()
        if (!validateName()!! or !validatePassword()!! or !validatePhoneNo()!! or !validateUsername()!!) {
            return
        }
        val checkPhoneno = reference!!.orderByChild("phoneno").equalTo(regphoneno)
        checkPhoneno.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    phoneno!!.error = "Phone No already in use"
                    phoneno!!.requestFocus()
                    username!!.error = null
                    username!!.isErrorEnabled = false
                } else {
                    phoneno!!.error = null
                    phoneno!!.isErrorEnabled = false
                    val checkUsername = reference!!.orderByChild("username").equalTo(regusername)
                    checkUsername.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                username!!.error = "Username already exists"
                                username!!.requestFocus()
                            } else {
                                username!!.error = null
                                username!!.isErrorEnabled = false
                                val user_signup = user_signup(regname, regpassword, regusername, regphoneno)
                                reference!!.child(regphoneno).setValue(user_signup)
                                val intent = Intent(this@SignUp, MainHome::class.java)
                                intent.putExtra("phoneno", regphoneno)
                                intent.putExtra("startingpage", "1")
                                startActivity(intent)
                                finish()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

}