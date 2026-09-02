package com.lockin.app.ui.verify

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.lockin.app.ui.theme.Emerald
import com.lockin.app.ui.theme.InkHigh
import com.lockin.app.ui.theme.Violet
import kotlin.math.roundToInt

/**
 * Checks you're actually at the place the task names. More reliable than the
 * photo check for gym/office/library tasks, and much harder to fake casually.
 */
@Composable
fun LocationVerifyScreen(
    taskTitle: String,
    placeLabel: String,
    targetLat: Double?,
    targetLng: Double?,
    radiusMeters: Int,
    onCancel: () -> Unit,
    onVerified: () -> Unit
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    var checking by remember { mutableStateOf(false) }
    var distance by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape)
                .background(if (distance != null && distance!! <= radiusMeters)
                    Emerald.copy(alpha = 0.2f) else InkHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = if (distance != null && distance!! <= radiusMeters) Emerald else Violet,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(taskTitle, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            if (placeLabel.isNotBlank()) "Needs you at: $placeLabel"
            else "Needs you at the saved place",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        distance?.let {
            Spacer(Modifier.height(24.dp))
            Text(
                if (it < 1000) "$it m away" else "%.1f km away".format(it / 1000f),
                style = MaterialTheme.typography.displayMedium,
                color = if (it <= radiusMeters) Emerald else MaterialTheme.colorScheme.error
            )
            Text(
                "within $radiusMeters m counts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        message?.let {
            Spacer(Modifier.height(14.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = {
                if (targetLat == null || targetLng == null) {
                    message = "This task has no saved place. Edit it and set one."
                    return@Button
                }
                checking = true
                message = "Getting a fix…"
                fetchLocation(context) { location ->
                    checking = false
                    if (location == null) {
                        message = "Couldn't get your location. Is GPS on?"
                        return@fetchLocation
                    }
                    val result = FloatArray(1)
                    Location.distanceBetween(
                        location.latitude, location.longitude, targetLat, targetLng, result
                    )
                    val metres = result[0].roundToInt()
                    distance = metres
                    if (metres <= radiusMeters) {
                        message = "Verified"
                        onVerified()
                    } else {
                        message = "Not close enough yet."
                    }
                }
            },
            enabled = hasPermission && !checking,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Violet),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (checking) "Checking…" else "Check where I am")
        }

        if (!hasPermission) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Location permission is needed for this check.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onCancel) {
            Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@SuppressLint("MissingPermission")
internal fun fetchLocation(
    context: android.content.Context,
    onResult: (Location?) -> Unit
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        onResult(null)
        return
    }
    val client = LocationServices.getFusedLocationProviderClient(context)
    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        .addOnSuccessListener { onResult(it) }
        .addOnFailureListener { onResult(null) }
}
