package com.womensafety.app.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OTPVerificationScreen(
    phoneNumber: String,
    @Suppress("UNUSED_PARAMETER") expectedOTP: String = "", // For debugging
    onOTPVerified: (String) -> Unit,
    onResendOTP: () -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    timeRemaining: Int = 0
) {
    var otpCode by remember { mutableStateOf("") }
    var isCodeComplete by remember { mutableStateOf(false) }
    
    LaunchedEffect(otpCode.length) {
        isCodeComplete = otpCode.length == 6
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify Phone Number") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Icon
            Icon(
                Icons.Default.Phone,
                contentDescription = "Phone verification",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Title
            Text(
                text = "Verify Your Phone",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Phone number info
            Text(
                text = "We've sent a 6-digit code to",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = phoneNumber,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Debug info - remove in production
            
            // OTP Input
            OutlinedTextField(
                value = otpCode,
                onValueChange = { 
                    if (it.length <= 6) {
                        otpCode = it.filter { char -> char.isDigit() }
                    }
                },
                label = { Text("Enter 6-digit code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = 4.sp
                ),
                placeholder = { Text("000000", textAlign = TextAlign.Center) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Error message
            errorMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Verify button
            Button(
                onClick = { 
                    if (isCodeComplete) {
                        onOTPVerified(otpCode)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isCodeComplete && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Verify", fontSize = 16.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Resend section
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (timeRemaining > 0) {
                        "Resend code in ${timeRemaining}s"
                    } else {
                        "Didn't receive the code?"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (timeRemaining == 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onResendOTP,
                        enabled = !isLoading
                    ) {
                        Text("Resend Code")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Instructions
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Important:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "• The code will expire in 5 minutes\n" +
                               "• You have 3 attempts to enter the correct code\n" +
                               "• Make sure you have good network reception",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
