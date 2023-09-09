package com.example.biomatch.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.biomatch.Login
import com.example.biomatch.R
import com.google.android.material.button.MaterialButton

class SettingFragment : Fragment() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view=  inflater.inflate(R.layout.fragment_setting, container, false)
        val logout = view.findViewById<MaterialButton>(R.id.logout)

        logout.setOnClickListener(View.OnClickListener { logout() })
        return view
    }

    fun logout() {
        val sharedPreferences =
            requireActivity().getSharedPreferences("shared preferences", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("phoneno", null)
        editor.putString("password", null)
        editor.apply()
        val intent = Intent(activity, Login::class.java)
        startActivity(intent)
    }

}