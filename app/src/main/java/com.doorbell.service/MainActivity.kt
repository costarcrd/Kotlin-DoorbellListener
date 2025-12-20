package com.doorbell.service

import android.content.Intent
import android.Manifest
import android.os.Bundle
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.content.pm.PackageManager
import android.widget.Toast
import android.util.Log
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("MAIN", "MainActivity onCreate")
        if (Build.VERSION.SDK_INT >= 23) { // API 23 = Android 6
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            } else {
                startDoorbellService()
            }
        } else {
            // Android < 6 já concede permissão pelo Manifest
            startDoorbellService()
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startDoorbellService()
        } else {
            Toast.makeText(this, "Permissão necessária para criar config.ini", Toast.LENGTH_LONG).show()
        }
    }

    private fun startDoorbellService() {
        startService(Intent(this, PiListenerService::class.java))
        Log.i("MAIN", "A iniciar o serviço...")
        finish()
    }

}
