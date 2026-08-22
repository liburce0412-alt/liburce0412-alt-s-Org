package com.campusai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.campusai.app.CampusApp
import com.campusai.core.database.CampusDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dao = CampusDatabase.getDatabase(applicationContext).campusDao()
        setContent { CampusApp(dao = dao) }
    }
}
