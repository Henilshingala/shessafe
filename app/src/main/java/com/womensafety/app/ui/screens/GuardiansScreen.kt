package com.womensafety.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.womensafety.app.data.models.EmergencyContact
import com.womensafety.app.util.PhoneUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardiansScreen(
    contacts: List<EmergencyContact>,
    userPhoneNumber: String,
    onAddGuardian: (name: String, phone: String) -> Unit,
    onDeleteContact: (EmergencyContact) -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }

    val callingCode = remember { PhoneUtil.callingCodeForRegion() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guardians", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (contacts.size < 5) {
                        IconButton(onClick = { showAddDialog = true; addError = null }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add guardian")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (contacts.size < 5) {
                FloatingActionButton(onClick = { showAddDialog = true; addError = null }) {
                    Icon(Icons.Default.Add, contentDescription = "Add guardian")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.People, null, modifier = Modifier.size(64.dp),
                             tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No guardians added yet", style = MaterialTheme.typography.bodyLarge,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Add up to 5 guardians who will receive SOS alerts",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { showAddDialog = true }) { Text("Add Guardian") }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("${contacts.size}/5 guardians",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant,
                             modifier = Modifier.padding(bottom = 4.dp))
                    }
                    items(contacts, key = { it.id }) { contact ->
                        GuardianItem(contact = contact, onDelete = { onDeleteContact(contact) })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Guardian") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it; addError = null },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it; addError = null },
                        label = { Text("Phone number") },
                        placeholder = { Text(callingCode + "XXXXXXXXXX") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (addError != null) {
                        Text(addError!!, color = MaterialTheme.colorScheme.error,
                             style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nameInput.isBlank()) { addError = "Name is required"; return@TextButton }
                        if (phoneInput.isBlank()) { addError = "Phone number is required"; return@TextButton }
                        onAddGuardian(nameInput.trim(), phoneInput.trim())
                        showAddDialog = false
                        nameInput = ""
                        phoneInput = ""
                        addError = null
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; addError = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun GuardianItem(
    contact: EmergencyContact,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp),
                 tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleSmall,
                     fontWeight = FontWeight.Medium)
                Text(PhoneUtil.maskPhoneNumber(contact.phoneNumber),
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete",
                     tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove Guardian") },
            text = { Text("Remove ${contact.name} as your guardian?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
