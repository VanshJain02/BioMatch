package com.example.biomatch

import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.IdRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.biomatch.fragment.CaptureFragment
import com.example.biomatch.fragment.HomeFragment
import com.example.biomatch.fragment.HomeFragment.Companion.identifyprogressBar
import com.example.biomatch.fragment.RegisterFragment
import com.example.biomatch.fragment.SettingFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainHome : AppCompatActivity() {

    private val rotateOpen: Animation by lazy {
        AnimationUtils.loadAnimation(
            this,
            R.anim.rotate_open_anim
        )
    }
    private val rotateClose: Animation by lazy {
        AnimationUtils.loadAnimation(
            this,
            R.anim.rotate_close_anim
        )
    }
    private val fromBottom: Animation by lazy {
        AnimationUtils.loadAnimation(
            this,
            R.anim.from_bottom_anim
        )
    }
    private val toBottom: Animation by lazy {
        AnimationUtils.loadAnimation(
            this,
            R.anim.to_bottom_anim
        )
    }
    private var clicked = false

    private var currentSelectItemId = R.id.miHome
    private var savedStateSparseArray = SparseArray<Fragment.SavedState>()

    companion object {
        const val SAVED_STATE_CONTAINER_KEY = "ContainerKey"
        const val SAVED_STATE_CURRENT_TAB_KEY = "CurrentTabKey"
        var homeFragment = HomeFragment()

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_home)
        supportActionBar?.hide()
        val bottom_navigation = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        val add_btn = findViewById<FloatingActionButton>(R.id.fab)
        val register_btn = findViewById<FloatingActionButton>(R.id.btn_register)
        val identify_btn = findViewById<FloatingActionButton>(R.id.btn_identify)

        bottom_navigation.background = null

        if(!Python.isStarted()){
            Python.start(AndroidPlatform(this))
        }

        var settingFragment = SettingFragment()
        var register = RegisterFragment()

        if (savedInstanceState != null) {
            Log.d("SAVE","SAVESTATEUSED")
            savedStateSparseArray = savedInstanceState.getSparseParcelableArray(SAVED_STATE_CONTAINER_KEY)
                ?: savedStateSparseArray
            currentSelectItemId = savedInstanceState.getInt(SAVED_STATE_CURRENT_TAB_KEY)
        } else {
            makeCurrentFragment(register,R.id.miRegister)
        }

        if(allPermissionGranted()){
            bottom_navigation.setOnNavigationItemSelectedListener {
                when(it.itemId){
                    R.id.miHome -> {
                        if(currentSelectItemId!=R.id.miHome) {
                            currentSelectItemId=R.id.miHome
                            makeCurrentFragment(homeFragment, R.id.miHome)
                        }
                    }
                    R.id.miSetting ->{
                        if(currentSelectItemId!=R.id.miSetting){
                            currentSelectItemId=R.id.miSetting
                            makeCurrentFragment(settingFragment,R.id.miSetting)}
                    }
                }
                true
            }


        }
        else{
            ActivityCompat.requestPermissions(
                this, Constants.REQUIRED_PERMISSIONS,
                Constants.REQUEST_CODE_PERMISSIONS)
        }

        add_btn.setOnClickListener {
            onCameraFabClicked()
        }

        register_btn.setOnClickListener{
            currentSelectItemId=R.id.miRegister
            makeCurrentFragment(register,R.id.miRegister)
//            homeFragment.identify_finger()
            onCameraFabClicked()
//            onCameraFabClicked()
//            val intent = Intent(this@MainHome, RegisterFingerprint::class.java)
//            startActivity(intent)
        }
        identify_btn.setOnClickListener{
            currentSelectItemId=R.id.miCustomCapture
            var customCapture = CaptureFragment()
            makeCurrentFragment(customCapture,R.id.miCustomCapture)
//            homeFragment.identify_finger()
            onCameraFabClicked()
//
//            val intent = Intent(this@MainHome, IdentifyFingerprint::class.java)
//            startActivity(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSparseParcelableArray(SAVED_STATE_CONTAINER_KEY, savedStateSparseArray)
        outState.putInt(SAVED_STATE_CURRENT_TAB_KEY, currentSelectItemId)
    }
    private fun savedFragmentState(actionId: Int) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fl_wrapper)
        if (currentFragment != null) {
            savedStateSparseArray.put(currentSelectItemId,
                supportFragmentManager.saveFragmentInstanceState(currentFragment)
            )
        }
        currentSelectItemId = actionId
    }
    private fun makeCurrentFragment(Fragment: Fragment, @IdRes actionId: Int) {
        savedFragmentState(actionId)
//        createFragment(Fragment,actionId)
        Fragment.setInitialSavedState(savedStateSparseArray[actionId])

        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fl_wrapper,Fragment)
            commit()
        }
    }

    //Floating Action Button Animations
    private fun onCameraFabClicked() {
        setVisibility(clicked)
        setAnimation(clicked)
        clicked = !clicked
    }
    private fun setVisibility(clicked: Boolean) {
        val edit_btn = findViewById<FloatingActionButton>(R.id.btn_register)
        val image_btn = findViewById<FloatingActionButton>(R.id.btn_identify)

        if (!clicked) {
            edit_btn.visibility = View.VISIBLE
            image_btn.visibility = View.VISIBLE
        } else {
            edit_btn.visibility = View.GONE
            image_btn.visibility = View.GONE
        }
    }
    private fun setAnimation(clicked: Boolean) {
        val edit_btn = findViewById<FloatingActionButton>(R.id.btn_register)
        val image_btn = findViewById<FloatingActionButton>(R.id.btn_identify)
        val add_btn = findViewById<FloatingActionButton>(R.id.fab)

        if(!clicked) {
            edit_btn.startAnimation(fromBottom)
            image_btn.startAnimation(fromBottom)
            add_btn.startAnimation(rotateOpen)
        }else {
            edit_btn.startAnimation(toBottom)
            image_btn.startAnimation(toBottom)
            add_btn.startAnimation(rotateClose)
        }
    }
    private fun allPermissionGranted() =
        Constants.REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                baseContext, it
            ) == PackageManager.PERMISSION_GRANTED
        }
}