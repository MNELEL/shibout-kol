package com.example

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.models.VoiceProfile
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.AudioRecorder
import com.example.viewmodel.VoiceViewModel
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MyApplicationTheme {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val viewModel: VoiceViewModel = viewModel()
    
    val recorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }

    // Permission handling
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, context.getString(R.string.permission_required), Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo_1779694589878),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.nav_transcription)) },
                    label = { Text(stringResource(R.string.nav_transcription)) },
                    selected = currentRoute == "transcription",
                    onClick = { navController.navigate("transcription") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.InterpreterMode, contentDescription = stringResource(R.string.nav_profiles)) },
                    label = { Text(stringResource(R.string.nav_profiles)) },
                    selected = currentRoute == "profiles",
                    onClick = { navController.navigate("profiles") }
                )
            }
        },
        floatingActionButton = {
            if (currentRoute == "transcription") {
                FloatingActionButton(
                    onClick = {
                        if (!isRecording) {
                            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            
                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                Toast.makeText(context, "נא אשר את הרשאת ההקלטה ונסה שנית", Toast.LENGTH_SHORT).show()
                            } else {
                                val success = recorder.startRecording("temp_audio")
                                if (success) {
                                    isRecording = true
                                } else {
                                    Toast.makeText(context, "שגיאה בהפעלת המיקרופון, אנא פתח הרשאות", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            val file = recorder.stopRecording()
                            isRecording = false
                            if (file != null) {
                                viewModel.transcribeAudio(file, recorder.currentMimeType)
                            } else {
                                Toast.makeText(context, "שגיאה בסיום ההקלטה", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    containerColor = if (isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = "Record",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "transcription",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("transcription") {
                TranscriptionScreen(viewModel, isRecording)
            }
            composable("profiles") {
                ProfilesScreen(viewModel)
            }
        }
    }
}

@Composable
fun TranscriptionScreen(viewModel: VoiceViewModel, isRecording: Boolean) {
    val transcription by viewModel.transcription.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Visualizer Simulation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    ),
                    RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isRecording) {
                WaveformAnimation()
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo_1779694589878),
                        contentDescription = null,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.record_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.phonetic_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                if (uiState is VoiceViewModel.UiState.Loading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(
                        text = transcription.ifEmpty { stringResource(R.string.waiting_for_record) },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (transcription.isNotEmpty() && !transcription.startsWith("Error:") && !transcription.startsWith("שגיאה:") && transcription != stringResource(R.string.waiting_for_record)) {
            val context = LocalContext.current
            var showSaveDialog by remember { mutableStateOf(false) }
            var profileName by remember { mutableStateOf("") }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { showSaveDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("שמור כפרופיל קול")
            }

            if (showSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text("שמור פרופיל קול חדש") },
                    text = {
                        Column {
                            Text("הזן שם עבור פרופיל קול זה:")
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(
                                value = profileName,
                                onValueChange = { profileName = it },
                                placeholder = { Text("למשל: הקול שלי") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (profileName.isNotEmpty()) {
                                    var tempFile = File(context.cacheDir, "temp_audio.m4a")
                                    if (!tempFile.exists()) {
                                        tempFile = File(context.cacheDir, "temp_audio.3gp")
                                    }
                                    viewModel.saveVoiceProfile(profileName, transcription, tempFile)
                                    showSaveDialog = false
                                    profileName = ""
                                    Toast.makeText(context, "פרופיל קול נשמר בהצלחה!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("שמור")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSaveDialog = false }) {
                            Text("ביטול")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ProfilesScreen(viewModel: VoiceViewModel) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val playingAudioPath by viewModel.playingAudioPath.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Hoist TextToSpeech engine safely with try-catch
    var tts: android.speech.tts.TextToSpeech? by remember { mutableStateOf(null) }
    
    DisposableEffect(context) {
        var obj: android.speech.tts.TextToSpeech? = null
        try {
            obj = android.speech.tts.TextToSpeech(context) { status ->
                if (status != android.speech.tts.TextToSpeech.SUCCESS) {
                    android.util.Log.e("ProfilesScreen", "TTS initialization failed with status: $status")
                }
            }
            tts = obj
        } catch (e: Exception) {
            android.util.Log.e("ProfilesScreen", "Failed to construct TextToSpeech engine", e)
        }
        onDispose {
            try {
                obj?.stop()
                obj?.shutdown()
            } catch (e: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            stringResource(R.string.profiles_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo_1779694589878),
                        contentDescription = null,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        alpha = 0.6f
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.no_profiles),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "הקלט ונתח קול בלשונית התמלול כדי ליצור פרופיל מעובד ראשון",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(profiles) { profile ->
                    val isPlaying = playingAudioPath == profile.audioPath
                    VoiceProfileCard(
                        profile = profile,
                        viewModel = viewModel,
                        isPlaying = isPlaying,
                        onPlayClick = {
                            viewModel.togglePlayProfile(profile.audioPath)
                        },
                        onSynthesizeClick = {
                            val textToSpeak = profile.transcription ?: "שלום, קול פונטי"
                            if (tts != null) {
                                try {
                                    tts?.speak(textToSpeak, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                                    Toast.makeText(context, "משחזר קול משובץ...", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "שגיאה בהקראת הטקסט", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "מנוע דיבור אינו זמין במכשיר זה", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceProfileCard(
    profile: VoiceProfile,
    viewModel: VoiceViewModel,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onSynthesizeClick: () -> Unit
) {
    val context = LocalContext.current
    val audioFileExists = remember(profile.audioPath) {
        profile.audioPath != null && java.io.File(profile.audioPath).exists()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo_1779694589878),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1.0f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!profile.transcription.isNullOrEmpty()) {
                    Text(
                        profile.transcription,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "${stringResource(R.string.created_at)} ${java.text.SimpleDateFormat("dd/MM/yy").format(profile.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Play Original Sample Button
            if (audioFileExists) {
                IconButton(onClick = onPlayClick) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.play_sample),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Clone Synthesis Text-To-Speech Button
            IconButton(onClick = onSynthesizeClick) {
                Icon(
                    Icons.Rounded.AutoFixNormal,
                    contentDescription = stringResource(R.string.clone_voice),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Delete Button
            IconButton(onClick = {
                viewModel.deleteVoiceProfile(profile)
                Toast.makeText(context, "פרופיל נמחק", Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "מחק פרופיל",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun WaveformAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    Row(
        modifier = Modifier.height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(15) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 10f,
                targetValue = 50f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 300 + (index * 30),
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
            )
        }
    }
}
