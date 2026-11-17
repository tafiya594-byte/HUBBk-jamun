package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CallSession
import com.example.ui.components.AbidAvatar
import com.example.ui.theme.CallAcceptGreen
import com.example.ui.theme.CallRejectRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.EmeraldPrimaryDark
import com.example.ui.viewmodel.CallViewModel
import com.example.util.TimeFormatters
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun IncomingCallScreen(
    call: CallSession,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "ring_pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(EmeraldPrimaryDark, DarkBackground))
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(28.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (call.callType == "video") "INCOMING VIDEO CALL" else "INCOMING AUDIO CALL",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
            Box(modifier = Modifier.scale(scale)) {
                AbidAvatar(call.callerName, call.callerAvatar, size = 116.dp)
            }
            Text(
                text = call.callerName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "ABID Messenger Call",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 36.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = CircleShape,
                    color = CallRejectRed,
                    modifier = Modifier
                        .size(72.dp)
                        .clickable(onClick = onReject)
                        .testTag("reject_call_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Decline call", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
                Text("Decline", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = CircleShape,
                    color = CallAcceptGreen,
                    modifier = Modifier
                        .size(72.dp)
                        .clickable(onClick = onAccept)
                        .testTag("accept_call_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (call.callType == "video") Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = "Accept call",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Text("Accept", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun ActiveCallScreen(
    currentUid: String,
    callViewModel: CallViewModel,
    onCallEnded: () -> Unit
) {
    val context = LocalContext.current
    val session by callViewModel.callSession.collectAsStateWithLifecycle()
    val isMicMuted by callViewModel.isMicMuted.collectAsStateWithLifecycle()
    val isSpeakerOn by callViewModel.isSpeakerOn.collectAsStateWithLifecycle()
    val isVideoEnabled by callViewModel.isVideoEnabled.collectAsStateWithLifecycle()
    val elapsed by callViewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val peerState by callViewModel.peerState.collectAsStateWithLifecycle()

    var permissionsGranted by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioOk = results[Manifest.permission.RECORD_AUDIO] == true
        if (audioOk) {
            permissionsGranted = true
        } else {
            Toast.makeText(context, "Microphone permission is required for calls.", Toast.LENGTH_SHORT).show()
            callViewModel.endCall(onCallEnded)
        }
    }

    LaunchedEffect(session?.callType) {
        val s = session ?: return@LaunchedEffect
        val needed = if (s.callType == "video") {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        val allOk = needed.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allOk) {
            permissionsGranted = true
        } else {
            permLauncher.launch(needed)
        }
    }

    LaunchedEffect(session, permissionsGranted) {
        val s = session ?: return@LaunchedEffect
        if (s.status in listOf("rejected", "ended", "missed")) {
            onCallEnded()
            return@LaunchedEffect
        }
        if (permissionsGranted) {
            callViewModel.handleSessionUpdate(s)
        }
    }

    val isVideoCall = session?.callType == "video"
    val remoteName = session?.let {
        if (it.callerId == currentUid) it.calleeName else it.callerName
    } ?: "Connecting..."
    val remoteAvatar = session?.let {
        if (it.callerId == currentUid) it.calleeAvatar else it.callerAvatar
    }

    var localRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var remoteRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            localRenderer?.release()
            remoteRenderer?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(EmeraldPrimaryDark, DarkBackground)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (isVideoCall && permissionsGranted) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(callViewModel.webRtcManager.eglBase.eglBaseContext, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        setEnableHardwareScaler(true)
                        remoteRenderer = this
                        callViewModel.attachRenderers(localRenderer, this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 16.dp)
                    .size(width = 112.dp, height = 156.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(callViewModel.webRtcManager.eglBase.eglBaseContext, null)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            setZOrderMediaOverlay(true)
                            setMirror(true)
                            localRenderer = this
                            callViewModel.attachRenderers(this, remoteRenderer)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Caller info overlay
        Column(
            modifier = Modifier
                .align(if (isVideoCall) Alignment.TopStart else Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = if (isVideoCall) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!isVideoCall) {
                AbidAvatar(remoteName, remoteAvatar, size = 120.dp)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = remoteName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (elapsed > 0) TimeFormatters.formatDurationSeconds(elapsed) else peerState,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        // Bottom Controls Bar
        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlIcon(
                    icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMicMuted) "Unmute" else "Mute",
                    active = isMicMuted,
                    onClick = { callViewModel.toggleMic() }
                )
                CallControlIcon(
                    icon = Icons.Default.VolumeUp,
                    label = "Speaker",
                    active = isSpeakerOn,
                    onClick = { callViewModel.toggleSpeaker() }
                )
                if (isVideoCall) {
                    CallControlIcon(
                        icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        label = "Camera",
                        active = !isVideoEnabled,
                        onClick = { callViewModel.toggleVideo() }
                    )
                    CallControlIcon(
                        icon = Icons.Default.Cameraswitch,
                        label = "Flip",
                        active = false,
                        onClick = { callViewModel.switchCamera() }
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = CallRejectRed,
                    modifier = Modifier
                        .size(58.dp)
                        .clickable { callViewModel.endCall(onCallEnded) }
                        .testTag("end_call_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CallEnd, contentDescription = "End call", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlIcon(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (active) Color.White else Color.White.copy(alpha = 0.2f),
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) Color.Black else Color.White
                )
            }
        }
    }
}
