package com.zirakusa.stone.nativeapp.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.DeviceMediaAccess
import com.zirakusa.stone.nativeapp.data.persistence.StoneLanguages
import com.zirakusa.stone.nativeapp.ui.theme.LauncherIcon
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/**
 * First launch, drawn over the app so the library underneath is already loading:
 * 1. "Elige tu idioma" and Empezar.
 * 2. Music and video access; the device scan starts the moment it is granted.
 * 3. Notifications (Android 13+), then the home screen with the songs already listed.
 */
@Composable fun OnboardingScreen(language: String, onLanguage: (String) -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    var asking by rememberSaveable { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onFinished() }
    val media = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        DeviceMediaAccess.rescanRequests.tryEmit(Unit)
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else onFinished()
    }
    Box(Modifier.fillMaxSize().background(StonePalette.Background)
        .background(Brush.verticalGradient(listOf(StonePalette.Yellow.copy(alpha = .16f), StonePalette.Background)))
        // Swallow touches so nothing underneath reacts while the welcome is on screen.
        .clickable(remember { MutableInteractionSource() }, indication = null) {}
        .systemBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(LauncherIcon.current.drawable), null, Modifier.padding(top = 36.dp).size(150.dp))
            Text(stringResource(if (asking) R.string.onboarding_permission_title else R.string.onboarding_choose_language),
                Modifier.padding(top = 18.dp), color = StonePalette.Text, fontSize = 28.sp, fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center)
            if (asking) PermissionStep() else LanguageList(language, onLanguage, Modifier.weight(1f))
            if (!asking) Button(onClick = { asking = true; media.launch(DeviceMediaAccess.permissions()) },
                Modifier.fillMaxWidth().padding(vertical = 20.dp).height(58.dp), shape = RoundedCornerShape(30.dp)) {
                Text(stringResource(R.string.onboarding_start), fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable private fun LanguageList(selected: String, onSelect: (String) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 22.dp).clip(RoundedCornerShape(28.dp))
        .background(StonePalette.Surface).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        StoneLanguages.all.forEach { (code, label) ->
            val active = code == selected
            Row(Modifier.fillMaxWidth().clickable { if (!active) onSelect(code) }.padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), fontSize = 18.sp,
                    color = if (active) StonePalette.Yellow else StonePalette.Text,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                if (active) Icon(Icons.Default.Check, null, tint = StonePalette.Yellow)
            }
        }
    }
}

@Composable private fun PermissionStep() {
    Text(stringResource(R.string.onboarding_permission_hint), Modifier.padding(top = 12.dp),
        color = StonePalette.Muted, fontSize = 16.sp, textAlign = TextAlign.Center)
    CircularProgressIndicator(Modifier.padding(top = 32.dp).size(36.dp), color = StonePalette.Yellow, strokeWidth = 3.dp)
}
