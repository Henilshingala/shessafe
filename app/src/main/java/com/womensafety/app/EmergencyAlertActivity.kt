package com.womensafety.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.app.receiver.SOSAlertService

class EmergencyAlertActivity : ComponentActivity() {

    private lateinit var alertManager: SOSAlertManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        alertManager = SOSAlertManager(this)
        alertManager.playEmergencyAlert(enableSound = true, enableVibration = true)

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Red) {
                EmergencyAlertScreen(onStop = { stopAlertAndFinish() })
            }
        }
    }

    private fun stopAlertAndFinish() {
        try {
            alertManager.stopAlert()
            alertManager.cleanup()
        } catch (e: Exception) {
            android.util.Log.e("EmergencyAlertActivity", "Error stopping alert", e)
        }
        
        try {
            val stopIntent = android.content.Intent(this, SOSAlertService::class.java).apply {
                action = SOSAlertService.ACTION_STOP
            }
            startService(stopIntent)
        } catch (e: Exception) {
            android.util.Log.w("EmergencyAlertActivity", "Failed to stop service", e)
        }
        
        finishAndRemoveTask()
    }
}

@Composable
private fun EmergencyAlertScreen(onStop: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB00020)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "EMERGENCY ALERT",
                fontSize = 28.sp,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(16.dp)
            )
            Text(
                text = "SOS in progress. Buzzer and vibration active.",
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Red),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp)
            ) {
                Text("STOP", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
