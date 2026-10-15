package com.bapi.ipcamera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemBarInsets.apply(findViewById(android.R.id.content))
        supportFragmentManager.commit {
            replace(android.R.id.content, SettingsFragment())
        }
    }
}
