package com.example.musicplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.musicplayer.ui.*
import com.example.musicplayer.ui.theme.MusicPlayerTheme
import com.example.musicplayer.viewmodel.MusicViewModel
import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        // Only the audio-read permission is essential for listing/playing songs; a denied
        // POST_NOTIFICATIONS just means no media notification, not a blocked app.
        val audioGranted = results[audioPermissionName()]
            ?: (ContextCompat.checkSelfPermission(this, audioPermissionName()) == PackageManager.PERMISSION_GRANTED)
        if (audioGranted) {
            showMusicPlayer()
        } else {
            showPermissionDenied()
        }
    }

    private fun audioPermissionName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MobileAds.initialize(this) {}
        checkRequiredPermissions()
    }

    private fun checkRequiredPermissions() {
        val permissions = mutableListOf<String>()
        permissions.add(audioPermissionName())

        // Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isEmpty()) {
            showMusicPlayer()
        } else {
            requestPermission.launch(neededPermissions.toTypedArray())
        }
    }

    private fun showMusicPlayer() {
        setContent {
            MusicPlayerTheme {
                val viewModel: MusicViewModel = viewModel()
                MusicPlayerScreen(viewModel)
            }
        }
    }

    private fun showPermissionDenied() {
        setContent {
            MusicPlayerTheme {
                PermissionDeniedScreen(
                    onTryAgain = { checkRequiredPermissions() },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun MusicPlayerScreen(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        viewModel.init(context)
        viewModel.loadSongs(context)
        
        val intent = (context as? ComponentActivity)?.intent
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                viewModel.playExternalUri(uri)
                navController.navigate("full_player")
            }
        }
    }

    NavHost(navController = navController, startDestination = "song_list") {
        composable("song_list") {
            SongListScreen(
                viewModel = viewModel,
                onNavigateToPlayer = { navController.navigate("full_player") },
                onNavigateToPlaylists = { navController.navigate("playlists") }
            )
        }
        composable("full_player") {
            FullPlayerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("playlists") {
            PlaylistScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlaylistDetail = { id -> navController.navigate("playlist_detail/$id") },
                onNavigateToPlayer = { navController.navigate("full_player") }
            )
        }
        composable(
            route = "playlist_detail/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
            PlaylistDetailScreen(
                viewModel = viewModel,
                playlistId = playlistId,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = { navController.navigate("full_player") }
            )
        }
    }
}

@Composable
fun PermissionDeniedScreen(onTryAgain: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.MusicOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Required permissions are needed to list your songs and show the music player controls.", textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) { Text("Try Again") }
        TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Open Settings") }
    }
}
