package com.womensafety.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.app.data.SecurePreferences
import com.womensafety.app.data.models.EmergencyContact
import com.womensafety.app.service.ProtectionService
import com.womensafety.app.ui.screens.GuardiansScreen
import com.womensafety.app.ui.screens.OemSetupScreen
import com.womensafety.app.ui.screens.PermissionsChecklistScreen
import com.womensafety.app.ui.screens.SettingsScreen
import com.womensafety.app.ui.theme.WomenSafetyAppTheme
import com.womensafety.app.util.PhoneUtil
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: SimpleMainViewModel by viewModels {
        SimpleMainViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WomenSafetyAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NirbhayaApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun NirbhayaApp(viewModel: SimpleMainViewModel) {
    val context = LocalContext.current
    val loginState by viewModel.loginState.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()

    var isLoggedIn by remember {
        mutableStateOf(SecurePreferences.isLoggedIn(context))
    }

    var showPermissionsChecklist by remember {
        mutableStateOf(!SecurePreferences.isPermissionsChecklistComplete(context))
    }

    var showOemSetup by remember {
        mutableStateOf(false)
    }

    // Start persistent ProtectionService when user is logged in
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            ProtectionService.start(context)
        }
    }

    // React to successful login
    LaunchedEffect(loginState.step) {
        if (loginState.step == OtpStep.DONE) {
            isLoggedIn = true
            ProtectionService.start(context)
        }
    }

    when {
        showPermissionsChecklist -> {
            PermissionsChecklistScreen(
                onAllCriticalGranted = {
                    SecurePreferences.setPermissionsChecklistComplete(context, true)
                    showPermissionsChecklist = false
                    // If OEM setup not shown yet, show it right after checklist
                    if (!SecurePreferences.isOemSetupShown(context)) {
                        showOemSetup = true
                    }
                },
                onOpenOemSetup = {
                    showOemSetup = true
                }
            )
        }
        showOemSetup -> {
            OemSetupScreen(
                onComplete = {
                    SecurePreferences.setOemSetupShown(context, true)
                    showOemSetup = false
                }
            )
        }
        !isLoggedIn -> {
            LoginFlow(
                viewModel = viewModel,
                loginState = loginState
            )
        }
        else -> {
            AppContent(
                viewModel = viewModel,
                contacts = contacts,
                settingsState = settingsState,
                onLogout = {
                    ProtectionService.stop(context)
                    viewModel.logout()
                    isLoggedIn = false
                },
                onRecheckSetup = {
                    showPermissionsChecklist = true
                }
            )
        }
    }
}

@Composable
fun LoginFlow(
    viewModel: SimpleMainViewModel,
    loginState: LoginState
) {
    val region = remember { PhoneUtil.defaultRegion() }
    val callingCode = remember { PhoneUtil.callingCodeForRegion(region) }

    when (loginState.step) {
        OtpStep.ENTER_PHONE, OtpStep.DONE -> {
            PhoneEntryScreen(
                callingCode = callingCode,
                isLoading = loginState.isLoading,
                errorMessage = loginState.errorMessage,
                onSendOtp = { number -> viewModel.sendOtp(number) }
            )
        }
        OtpStep.ENTER_OTP -> {
            BackHandler { /* Go back */ }
            OtpEntryScreen(
                phoneNumber = loginState.phoneNumber,
                isLoading = loginState.isLoading,
                errorMessage = loginState.errorMessage,
                resendCountdownSeconds = loginState.resendCountdownSeconds,
                onVerify = { otp -> viewModel.verifyOtp(otp) },
                onResend = { viewModel.resendOtp() }
            )
        }
    }
}

@Composable
fun PhoneEntryScreen(
    callingCode: String,
    isLoading: Boolean,
    errorMessage: String?,
    onSendOtp: (String) -> Unit
) {
    var localNumber by remember { mutableStateOf("") }
    val fullNumber = remember(localNumber, callingCode) {
        val digits = localNumber.filter { it.isDigit() }
        if (localNumber.trimStart().startsWith("+")) localNumber.trim() else "$callingCode$digits"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(Icons.Default.Security, null, modifier = Modifier.size(64.dp),
             tint = MaterialTheme.colorScheme.primary)
        Text("Nirbhaya-Who Safe", style = MaterialTheme.typography.headlineMedium,
             fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Enter your mobile number to get started",
             style = MaterialTheme.typography.bodyLarge,
             color = MaterialTheme.colorScheme.onSurfaceVariant,
             textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = localNumber,
            onValueChange = { localNumber = it },
            label = { Text("Mobile number") },
            placeholder = { Text("e.g. $callingCode XXXXXX") },
            prefix = { Text(if (localNumber.trimStart().startsWith("+")) "" else callingCode) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null
        )

        if (errorMessage != null) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error,
                 style = MaterialTheme.typography.bodySmall,
                 modifier = Modifier.fillMaxWidth())
        }

        Button(
            onClick = { onSendOtp(fullNumber) },
            enabled = !isLoading && localNumber.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Send OTP")
        }

        Text(
            "An OTP will be sent to verify your number. Internet is required for this step only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OtpEntryScreen(
    phoneNumber: String,
    isLoading: Boolean,
    errorMessage: String?,
    resendCountdownSeconds: Int,
    onVerify: (String) -> Unit,
    onResend: () -> Unit
) {
    var otp by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(Icons.Default.Sms, null, modifier = Modifier.size(64.dp),
             tint = MaterialTheme.colorScheme.primary)
        Text("Enter OTP", style = MaterialTheme.typography.headlineMedium,
             fontWeight = FontWeight.Bold)
        Text("OTP sent to $phoneNumber",
             style = MaterialTheme.typography.bodyLarge,
             color = MaterialTheme.colorScheme.onSurfaceVariant,
             textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = otp,
            onValueChange = { if (it.length <= 6) otp = it.filter { c -> c.isDigit() } },
            label = { Text("6-digit OTP") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null
        )

        if (errorMessage != null) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error,
                 style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = { onVerify(otp) },
            enabled = !isLoading && otp.length == 6,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Verify OTP")
        }

        TextButton(
            onClick = onResend,
            enabled = resendCountdownSeconds == 0
        ) {
            val text = if (resendCountdownSeconds > 0) "Resend in ${resendCountdownSeconds}s"
                       else "Resend OTP"
            Text(text)
        }
    }
}

@Composable
fun AppHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Security, null,
                 tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Nirbhaya-Who Safe", style = MaterialTheme.typography.titleLarge,
                 color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold,
                 letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Icon(Icons.Default.Security, null,
                 tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun AppContent(
    viewModel: SimpleMainViewModel,
    contacts: List<EmergencyContact>,
    settingsState: SettingsState,
    onLogout: () -> Unit,
    onRecheckSetup: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sosStatus by viewModel.sosStatus.collectAsState()

    var showGuardians by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(topBar = { AppHeader() }) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                showGuardians -> {
                    BackHandler { showGuardians = false }
                    GuardiansScreen(
                        contacts = contacts,
                        userPhoneNumber = settingsState.userPhoneNumber,
                        onAddGuardian = { name, phone ->
                            coroutineScope.launch {
                                val error = viewModel.addGuardian(name, phone)
                                if (error != null) {
                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onDeleteContact = { viewModel.deleteContact(it) },
                        onBack = { showGuardians = false }
                    )
                }
                showSettings -> {
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        onNavigateBack = { showSettings = false },
                        manualSosDelay = settingsState.manualSosDelay,
                        onManualSosDelayChange = { viewModel.updateManualSosDelay(it) },
                        iAmSafeEnabled = settingsState.iAmSafeEnabled,
                        onIAmSafeEnabledChange = { viewModel.updateIAmSafeEnabled(it) },
                        onRecheckSetup = {
                            showSettings = false
                            onRecheckSetup()
                        },
                        onLogout = onLogout
                    )
                }
                else -> {
                    SimpleWomenSafetyScreen(
                        contacts = contacts,
                        sosStatus = sosStatus,
                        onSosTriggered = { viewModel.triggerSOS() },
                        onCancelSos = { viewModel.cancelSOS() },
                        onNavigateToGuardians = { showGuardians = true },
                        onNavigateToSettings = { showSettings = true }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleWomenSafetyScreen(
    contacts: List<EmergencyContact>,
    sosStatus: SimpleMainViewModel.SosStatus,
    onSosTriggered: () -> Unit,
    onCancelSos: () -> Unit,
    onNavigateToGuardians: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val hasGuardians = contacts.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text("Safety Alert System", style = MaterialTheme.typography.displaySmall,
             color = MaterialTheme.colorScheme.onBackground,
             modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("Emergency Response Ready", style = MaterialTheme.typography.bodyLarge,
             color = MaterialTheme.colorScheme.onSurfaceVariant,
             modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(16.dp))

        // SOS button with ripple animation (only when guardians exist)
        Box(modifier = Modifier.size(320.dp), contentAlignment = Alignment.Center) {
            val infiniteTransition = rememberInfiniteTransition(label = "sos_ripple")

            if (hasGuardians && sosStatus is SimpleMainViewModel.SosStatus.Idle) {
                val rippleDuration = 3000
                val targetScale = 2.2f

                @Composable
                fun createRipple(offset: Int) {
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f, targetValue = targetScale,
                        animationSpec = infiniteRepeatable(
                            initialStartOffset = androidx.compose.animation.core.StartOffset(offset),
                            animation = tween(rippleDuration, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ), label = "scale"
                    )
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.5f, targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            initialStartOffset = androidx.compose.animation.core.StartOffset(offset),
                            animation = tween(rippleDuration, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ), label = "alpha"
                    )
                    Box(modifier = Modifier.size(140.dp).scale(scale).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = alpha)))
                }
                createRipple(0); createRipple(500); createRipple(1000)
                createRipple(1500); createRipple(2000); createRipple(2500)
            }

            val buttonScale by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse
                ), label = "buttonPulse"
            )

            // Show countdown overlay if in countdown state
            if (sosStatus is SimpleMainViewModel.SosStatus.Countdown) {
                Button(
                    onClick = onCancelSos,
                    modifier = Modifier.size(150.dp).clip(CircleShape),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${sosStatus.secondsLeft}", style = MaterialTheme.typography.displayMedium,
                             color = Color.White, fontWeight = FontWeight.Bold, fontSize = 40.sp)
                        Text("TAP TO CANCEL", color = Color.White, fontSize = 10.sp, letterSpacing = 0.5.sp)
                    }
                }
            } else {
                Button(
                    onClick = { if (hasGuardians) onSosTriggered() else onNavigateToGuardians() },
                    modifier = Modifier.size(150.dp)
                        .scale(if (hasGuardians && sosStatus is SimpleMainViewModel.SosStatus.Idle) buttonScale else 1f)
                        .clip(CircleShape),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            !hasGuardians -> MaterialTheme.colorScheme.surfaceVariant
                            sosStatus is SimpleMainViewModel.SosStatus.Sending -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        },
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    enabled = true,
                    shape = CircleShape,
                    elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 8.dp, pressedElevation = 4.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("SOS", style = MaterialTheme.typography.displayMedium,
                             color = Color.White, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = 2.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status card
        when {
            !hasGuardians -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    onClick = onNavigateToGuardians
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, modifier = Modifier.size(24.dp),
                             tint = MaterialTheme.colorScheme.error)
                        Text("Add a guardian to activate SOS",
                             style = MaterialTheme.typography.bodyMedium,
                             color = MaterialTheme.colorScheme.onErrorContainer,
                             modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            sosStatus is SimpleMainViewModel.SosStatus.Sending -> {
                val s = sosStatus as SimpleMainViewModel.SosStatus.Sending
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                     colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Sending SOS...", style = MaterialTheme.typography.titleSmall,
                             color = MaterialTheme.colorScheme.onTertiaryContainer)
                        LinearProgressIndicator(
                            progress = if (s.totalCount > 0) s.sentCount.toFloat() / s.totalCount else 0f,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        Text("Sent to ${s.sentCount} of ${s.totalCount} guardians",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            sosStatus is SimpleMainViewModel.SosStatus.Done -> {
                val d = sosStatus as SimpleMainViewModel.SosStatus.Done
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                     colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(24.dp),
                             tint = MaterialTheme.colorScheme.secondary)
                        Text("SOS sent to ${d.sentCount}/${d.totalCount} guardians",
                             style = MaterialTheme.typography.bodyMedium,
                             color = MaterialTheme.colorScheme.onSecondaryContainer,
                             modifier = Modifier.weight(1f))
                        TextButton(onClick = onCancelSos) { Text("I'm safe") }
                    }
                }
            }
            else -> {
                // Idle with guardians
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                     colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp),
                             tint = MaterialTheme.colorScheme.secondary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${contacts.size} guardian${if (contacts.size != 1) "s" else ""} configured",
                                 style = MaterialTheme.typography.bodyMedium,
                                 color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("System ready to send emergency alerts",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }

        // Navigation buttons
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onNavigateToGuardians,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.People, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manage Guardians", style = MaterialTheme.typography.labelLarge)
            }

            Button(
                onClick = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Settings", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
