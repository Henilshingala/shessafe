package com.womensafety.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import com.womensafety.app.data.models.EmergencyContact
import com.womensafety.app.ui.screens.SettingsScreen
import com.womensafety.app.ui.screens.SimpleOTPScreen
import com.womensafety.app.ui.screens.SimpleContactManagementScreen
import com.womensafety.app.ui.screens.OnboardingFlow
import com.womensafety.app.ui.screens.XiaomiSetupScreen
import com.womensafety.app.ui.theme.WomenSafetyAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val simpleViewModel: SimpleMainViewModel by viewModels {
        SimpleMainViewModelFactory(application)
    }
    
    private lateinit var permissionManager: PermissionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionManager(this)
        setContent {
            WomenSafetyAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SimpleWomenSafetyApp(simpleViewModel, permissionManager)
                }
            }
        }
    }
}

@Composable
fun SimpleWomenSafetyApp(viewModel: SimpleMainViewModel, permissionManager: PermissionManager) {
    val contacts by viewModel.contacts.collectAsState()
    val otpState by viewModel.otpState.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    var allPermissionsGranted by remember { mutableStateOf(permissionManager.hasAllPermissions()) }
    var xiaomiSetupComplete by remember { 
        mutableStateOf(XiaomiAutostartHelper.isSetupComplete(context)) 
    }

    // Re-check permissions when app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                allPermissionsGranted = permissionManager.hasAllPermissions()
                xiaomiSetupComplete = XiaomiAutostartHelper.isSetupComplete(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val needsOnboarding = !allPermissionsGranted ||
        settingsState.userPhoneNumber.isBlank() ||
        settingsState.userRole.isBlank() ||
        (settingsState.userRole == "sender" && settingsState.iotSimNumber.isBlank())

    if (needsOnboarding) {
        OnboardingFlow(
            initialUserPhoneNumber = settingsState.userPhoneNumber,
            initialIotSimNumber = settingsState.iotSimNumber,
            onFinish = { userPhoneNumber, iotSimNumber, role ->
                // Validate phone number against registered numbers
                val (isValid, errorMessage) = viewModel.validatePhoneNumberForRole(userPhoneNumber, role)
                
                if (isValid) {
                    // Register phone number with role
                    viewModel.registerPhoneNumberWithRole(userPhoneNumber, role)
                    
                    viewModel.setUserRole(role)
                    viewModel.updateUserPhoneNumber(userPhoneNumber)
                    if (role == "sender") {
                        viewModel.updateIotSimNumber(iotSimNumber)
                    }

                    permissionManager.requestAllPermissions { granted ->
                        if (granted) {
                            allPermissionsGranted = true
                        } else {
                            (context as? ComponentActivity)?.finishAffinity()
                        }
                    }
                } else {
                    // Show error and prevent onboarding completion
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            },
            onBack = {
                // Handle back press from onboarding - exit the app
                (context as? ComponentActivity)?.finishAffinity()
            }
        )
    } else if (XiaomiAutostartHelper.isXiaomiDevice() && !xiaomiSetupComplete) {
        // Show Xiaomi-specific setup for Autostart (mandatory, one-time)
        XiaomiSetupScreen(
            onComplete = {
                // Mark as complete and trigger recomposition
                xiaomiSetupComplete = true
            }
        )
    } else {
        AppContent(
            viewModel = viewModel,
            permissionManager = permissionManager,
            contacts = contacts,
            otpState = otpState,
            settingsState = settingsState
        )
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
                .padding(horizontal = 16.dp, vertical = 16.dp), // Taller header like a website navbar
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center // Centered like a brand logo
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Nirbhaya-Who Safe",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )

        }
    }
}

@Composable
fun AppContent(
    viewModel: SimpleMainViewModel,
    permissionManager: PermissionManager,
    contacts: List<EmergencyContact>,
    otpState: SimpleOTPState,
    settingsState: SimpleSettingsState
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var showContactList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var manualSosDelay by remember { mutableStateOf(settingsState.manualSosDelay) }
    var showBatteryDialog by remember { mutableStateOf(false) }
    
    // Check battery optimization for BOTH sender and receiver
    LaunchedEffect(settingsState.userRole) {
        if (settingsState.userRole.isNotEmpty()) {
            if (!BatteryOptimizationHelper.isOptimizationDisabled(context)) {
                // Show dialog after a short delay to allow UI to settle
                kotlinx.coroutines.delay(1000)
                showBatteryDialog = true
            }
        }
    }
    
    // Website-like structure with fixed Header
    Scaffold(
        topBar = { AppHeader() }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (showBatteryDialog) {
                BatteryOptimizationDialog(
                    onDismiss = { showBatteryDialog = false },
                    onConfirm = { showBatteryDialog = false }
                )
            }

            if (settingsState.userRole == "receiver") {
                ReceiverHomeScreen(
                    userPhoneNumber = settingsState.userPhoneNumber
                )
            } else if (otpState.isActive) {
                BackHandler { viewModel.cancelOTPVerification() }
                SimpleOTPScreen(
                    phoneNumber = otpState.phoneNumber,
                    onOTPVerified = { enteredOTP -> viewModel.verifyOTP(enteredOTP) },
                    onResendOTP = { viewModel.resendOTP() },
                    onBack = { viewModel.cancelOTPVerification() },
                    isLoading = otpState.isLoading,
                    errorMessage = otpState.errorMessage
                )
            } else if (showContactList) {
                BackHandler { showContactList = false }
                SimpleContactManagementScreen(
                    contacts = contacts,
                    onAddContact = { name, phone, relationship ->
                        if (permissionManager.hasSMSPermission()) {
                            viewModel.startOTPVerification(phone, name, relationship)
                        } else {
                            permissionManager.requestSMSPermission { granted ->
                                if (granted) {
                                    viewModel.startOTPVerification(phone, name, relationship)
                                }
                            }
                        }
                    },
                    onDeleteContact = { contact -> viewModel.deleteContact(contact) },
                    onBack = { showContactList = false }
                )
            } else if (showSettings) {
                BackHandler { showSettings = false }
                SettingsScreen(
                    onNavigateBack = { showSettings = false },
                    manualSosDelay = manualSosDelay,
                    onManualSosDelayChange = { newDelay -> 
                        manualSosDelay = newDelay
                        viewModel.updateManualSosDelay(newDelay)
                    }
                )
            } else {
                SimpleWomenSafetyScreen(
                    contacts = contacts,
                    onSosTriggered = {
                        val success = viewModel.triggerSOS()
                        coroutineScope.launch {
                            if (success) {
                                Toast.makeText(context, "SOS Alert sent to ${contacts.size} contacts!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to send SOS alerts. Please check your contacts.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onNavigateToContacts = { showContactList = true },
                    onNavigateToSettings = { showSettings = true }
                )
            }
        }
    }
}

@Composable
fun ReceiverHomeScreen(
    userPhoneNumber: String
) {
    val context = LocalContext.current
    var showBatteryDialog by remember { mutableStateOf(false) }
    
    // Check battery optimization on first composition
    LaunchedEffect(Unit) {
        if (!BatteryOptimizationHelper.isOptimizationDisabled(context)) {
            // Show dialog after a short delay to allow UI to settle
            kotlinx.coroutines.delay(1000)
            showBatteryDialog = true
        }
    }
    
    if (showBatteryDialog) {
        BatteryOptimizationDialog(
            onDismiss = { showBatteryDialog = false },
            onConfirm = { showBatteryDialog = false }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Alert Receiver",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Ready to receive emergency notifications",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Text(
                    text = "Your Contact Number",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = userPhoneNumber.ifBlank { "Phone number not configured" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                Divider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                )

                Text(
                    text = "Emergency alerts from the sender will be delivered to this number",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Important",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    text = "Keep this app installed with permissions enabled to receive emergency alerts reliably.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SimpleWomenSafetyScreen(
    contacts: List<EmergencyContact>,
    onSosTriggered: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Safety Alert System",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Emergency Response Ready",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        val hasContacts = contacts.isNotEmpty()
        
        // Animated SOS Button with SMOOTH Professional Ripple Effect
        Box(
            modifier = Modifier
                .size(320.dp), // Increased size much more for "Bigger" effect
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "sos_ripple")
            
            // Generate 6 ripples for a denser, bigger wave effect
            // Duration increased to 3000ms to accommodate larger expansion
            
            if (hasContacts) {
                val rippleDuration = 3000
                val targetScale = 2.2f
                
                // Helper to create ripple animation
                @Composable
                fun createRipple(offset: Int) {
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = targetScale,
                        animationSpec = infiniteRepeatable(
                            initialStartOffset = androidx.compose.animation.core.StartOffset(offset),
                            animation = tween(rippleDuration, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "scale"
                    )
                    
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            initialStartOffset = androidx.compose.animation.core.StartOffset(offset),
                            animation = tween(rippleDuration, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "alpha"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = alpha))
                    )
                }

                // Create 6 ripples staggered by 500ms (3000/6)
                createRipple(0)
                createRipple(500)
                createRipple(1000)
                createRipple(1500)
                createRipple(2000)
                createRipple(2500)
            }
            
            // Button Pulse Animation (Subtle breathing effect)
            val buttonScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "buttonPulse"
            )
            
            // Main Button
            Button(
                onClick = { 
                    if (hasContacts) {
                        onSosTriggered()
                    }
                },
                modifier = Modifier
                    .size(150.dp)
                    .scale(if (hasContacts) buttonScale else 1f)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasContacts) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                enabled = hasContacts,
                shape = CircleShape,
                elevation = ButtonDefaults.elevatedButtonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 4.dp
                ),
                contentPadding = PaddingValues(0.dp) // Reset padding
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "SOS",
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (!hasContacts) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Add emergency contacts to enable SOS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "${contacts.size} contact${if (contacts.size != 1) "s" else ""} configured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = "System is ready to send emergency alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onNavigateToContacts,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Manage Contacts",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            
            Button(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}
