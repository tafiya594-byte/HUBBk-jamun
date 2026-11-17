package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.model.UiState
import com.example.data.repository.AuthAndProfileRepository
import com.example.data.repository.authStateFlow
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.GroupInfoScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.IncomingCallScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.StatusViewerScreen
import com.example.ui.theme.ABIDMessengerTheme
import com.example.ui.viewmodel.CallViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.util.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannels(this)

        setContent {
            AppRoot()
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val authRepo = remember(context) { AuthAndProfileRepository(context.applicationContext) }

    val user = currentUser
    if (user == null) {
        ABIDMessengerTheme {
            AuthScreen(
                authRepo = authRepo,
                onAuthSuccess = {}
            )
        }
    } else {
        key(user.uid) {
            AuthenticatedApp(currentUserId = user.uid)
        }
    }
}

@Composable
private fun AuthenticatedApp(currentUserId: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainViewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = MainViewModel.factory(currentUserId)
    )

    val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
    val myPublicState by mainViewModel.myPublicProfile.collectAsStateWithLifecycle()
    val myPrivate by mainViewModel.myPrivateProfile.collectAsStateWithLifecycle()
    val allUsers by mainViewModel.allUsers.collectAsStateWithLifecycle()
    val usersById by mainViewModel.usersById.collectAsStateWithLifecycle()
    val chatsState by mainViewModel.chatsState.collectAsStateWithLifecycle()
    val statuses by mainViewModel.filteredStatuses.collectAsStateWithLifecycle()
    val callsState by mainViewModel.callsState.collectAsStateWithLifecycle()
    val incomingCall by mainViewModel.incomingRingingCall.collectAsStateWithLifecycle()
    val isBusy by mainViewModel.isBusy.collectAsStateWithLifecycle()
    val usernameAvailable by mainViewModel.usernameAvailable.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        mainViewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Request runtime notification permission on Android 13+
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Notify on incoming ringing call
    var lastNotifiedCallId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(incomingCall?.id) {
        val call = incomingCall
        if (call != null && call.id != lastNotifiedCallId) {
            lastNotifiedCallId = call.id
            NotificationHelper.showIncomingCallNotification(
                context = context,
                callId = call.id,
                callerName = call.callerName,
                isVideo = call.callType == "video"
            )
        }
    }

    // Presence lifecycle
    DisposableEffect(lifecycleOwner, currentUserId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mainViewModel.setPresence(true)
                Lifecycle.Event.ON_PAUSE -> mainViewModel.setPresence(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mainViewModel.setPresence(false)
        }
    }

    ABIDMessengerTheme(themeMode = themeMode) {
        when (val profileState = myPublicState) {
            is UiState.Loading -> {
                Scaffold { padding ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                Scaffold { padding ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text(profileState.message)
                    }
                }
            }
            is UiState.Success -> {
                val myProfile = profileState.data
                if (myProfile == null) {
                    ProfileSetupScreen(
                        isBusy = isBusy,
                        usernameAvailable = usernameAvailable,
                        onCheckUsername = { mainViewModel.checkUsername(it) },
                        onCreateProfile = { u, d, a, av ->
                            mainViewModel.createInitialProfile(u, d, a, av)
                        },
                        onSignOut = { mainViewModel.signOut(context) }
                    )
                } else {
                    val navController = rememberNavController()
                    var activeCallId by remember { mutableStateOf<String?>(null) }
                    var currentOpenChatId by remember { mutableStateOf<String?>(null) }
                    var chatsInitialized by remember { mutableStateOf(false) }
                    val lastSeenMessageMs = remember { mutableStateMapOf<String, Long>() }

                    LaunchedEffect(chatsState, myPrivate, usersById, currentOpenChatId) {
                        val chats = (chatsState as? UiState.Success)?.data ?: return@LaunchedEffect
                        val muted = myPrivate?.mutedChatIds?.toSet().orEmpty()
                        val blocked = myPrivate?.blockedUids?.toSet().orEmpty()

                        if (!chatsInitialized) {
                            chats.forEach { chat ->
                                lastSeenMessageMs[chat.id] = chat.lastMessageAt?.toDate()?.time ?: 0L
                            }
                            chatsInitialized = true
                            return@LaunchedEffect
                        }

                        chats.forEach { chat ->
                            val msgMs = chat.lastMessageAt?.toDate()?.time ?: 0L
                            val prevMs = lastSeenMessageMs[chat.id] ?: 0L
                            if (msgMs > prevMs) {
                                lastSeenMessageMs[chat.id] = msgMs
                                val senderId = chat.lastMessageSenderId
                                if (senderId.isNotBlank() &&
                                    senderId != currentUserId &&
                                    senderId !in blocked &&
                                    chat.id !in muted &&
                                    currentOpenChatId != chat.id
                                ) {
                                    val senderName = usersById[senderId]?.displayName?.ifBlank { "New message" } ?: "New message"
                                    val title = if (chat.type == "group") {
                                        "${chat.title.ifBlank { "Group" }} • $senderName"
                                    } else {
                                        senderName
                                    }
                                    val body = chat.lastMessageText.ifBlank { "Sent an attachment" }
                                    NotificationHelper.showMessageNotification(
                                        context = context,
                                        chatId = chat.id,
                                        title = title,
                                        body = body
                                    )
                                }
                            }
                        }
                    }

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) }
                    ) { _ ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = AppRoute.Home
                            ) {
                                composable<AppRoute.Home> {
                                    HomeScreen(
                                        currentUid = currentUserId,
                                        myProfile = myProfile,
                                        myPrivate = myPrivate,
                                        allUsers = allUsers,
                                        usersById = usersById,
                                        chatsState = chatsState,
                                        statuses = statuses,
                                        callsState = callsState,
                                        onOpenChat = { chatId ->
                                            navController.navigate(AppRoute.ChatRoom(chatId))
                                        },
                                        onStartDirectChat = { other ->
                                            mainViewModel.openDirectChat(other) { chatId ->
                                                navController.navigate(AppRoute.ChatRoom(chatId))
                                            }
                                        },
                                        onCreateGroup = { title, desc, members, avatar ->
                                            mainViewModel.createGroup(title, desc, members, avatar) { chatId ->
                                                navController.navigate(AppRoute.ChatRoom(chatId))
                                            }
                                        },
                                        onTogglePinned = { cid, p -> mainViewModel.togglePinned(cid, p) },
                                        onToggleArchived = { cid, a -> mainViewModel.toggleArchived(cid, a) },
                                        onToggleMuted = { cid, m -> mainViewModel.toggleMuted(cid, m) },
                                        onToggleBlock = { uid, b -> mainViewModel.toggleBlockUser(uid, b) },
                                        onSubmitReport = { uid, cid, r, d -> mainViewModel.submitReport(uid, cid, r, d) },
                                        onOpenStatusViewer = { ownerUid ->
                                            navController.navigate(AppRoute.StatusViewer(ownerUid))
                                        },
                                        onPostStatus = { type, text, bg, media, priv ->
                                            mainViewModel.postStatus(type, text, bg, media, priv)
                                        },
                                        onStartCall = { other, type ->
                                            mainViewModel.initiateCall(other, type) { callId ->
                                                activeCallId = callId
                                                navController.navigate(AppRoute.ActiveCall(callId))
                                            }
                                        },
                                        onUpdateProfile = { cur, u, d, a, av ->
                                            mainViewModel.updateProfile(cur, u, d, a, av)
                                        },
                                        onUpdatePrivacy = { ls, sp, rr ->
                                            mainViewModel.updatePrivacy(ls, sp, rr)
                                        },
                                        onUpdateThemeMode = { mainViewModel.updateThemeMode(it) },
                                        onSignOut = { mainViewModel.signOut(context) },
                                        onDeleteAccount = { mainViewModel.deleteAccount(context) {} }
                                    )
                                }

                                composable<AppRoute.ChatRoom> { backStackEntry ->
                                    val route = backStackEntry.toRoute<AppRoute.ChatRoom>()
                                    DisposableEffect(route.chatId) {
                                        currentOpenChatId = route.chatId
                                        onDispose {
                                            if (currentOpenChatId == route.chatId) {
                                                currentOpenChatId = null
                                            }
                                        }
                                    }
                                    val chatViewModel: ChatViewModel = viewModel(
                                        key = "${currentUserId}_${route.chatId}",
                                        factory = ChatViewModel.factory(route.chatId, currentUserId)
                                    )
                                    val allChats = (chatsState as? UiState.Success)?.data.orEmpty()
                                    ChatScreen(
                                        currentUid = currentUserId,
                                        myProfile = myProfile,
                                        myPrivate = myPrivate,
                                        allChats = allChats,
                                        usersById = usersById,
                                        chatViewModel = chatViewModel,
                                        onBack = { navController.popBackStack() },
                                        onOpenGroupInfo = { cid ->
                                            navController.navigate(AppRoute.GroupInfo(cid))
                                        },
                                        onStartCall = { other, type ->
                                            mainViewModel.initiateCall(other, type) { callId ->
                                                activeCallId = callId
                                                navController.navigate(AppRoute.ActiveCall(callId))
                                            }
                                        },
                                        onForwardMessage = { target, msg ->
                                            mainViewModel.forwardMessageToChat(target, msg)
                                        },
                                        onToggleMuted = { cid, m -> mainViewModel.toggleMuted(cid, m) },
                                        onToggleBlock = { uid, b -> mainViewModel.toggleBlockUser(uid, b) },
                                        onSubmitReport = { uid, cid, r, d -> mainViewModel.submitReport(uid, cid, r, d) }
                                    )
                                }

                                composable<AppRoute.GroupInfo> { backStackEntry ->
                                    val route = backStackEntry.toRoute<AppRoute.GroupInfo>()
                                    val chatViewModel: ChatViewModel = viewModel(
                                        key = "${currentUserId}_grp_${route.chatId}",
                                        factory = ChatViewModel.factory(route.chatId, currentUserId)
                                    )
                                    GroupInfoScreen(
                                        currentUid = currentUserId,
                                        allUsers = allUsers,
                                        usersById = usersById,
                                        chatViewModel = chatViewModel,
                                        onBack = { navController.popBackStack() },
                                        onLeftGroup = {
                                            navController.popBackStack(AppRoute.Home, inclusive = false)
                                        }
                                    )
                                }

                                composable<AppRoute.StatusViewer> { backStackEntry ->
                                    val route = backStackEntry.toRoute<AppRoute.StatusViewer>()
                                    val ownerStatuses = statuses.filter { it.userId == route.ownerUid }
                                    StatusViewerScreen(
                                        currentUid = currentUserId,
                                        statuses = ownerStatuses,
                                        usersById = usersById,
                                        onMarkViewed = { mainViewModel.markStatusViewed(it) },
                                        onDeleteStatus = { mainViewModel.deleteStatus(it) },
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable<AppRoute.ActiveCall> { backStackEntry ->
                                    val route = backStackEntry.toRoute<AppRoute.ActiveCall>()
                                    val callViewModel: CallViewModel = viewModel(
                                        key = "${currentUserId}_call_${route.callId}",
                                        factory = CallViewModel.factory(context, route.callId, currentUserId)
                                    )
                                    ActiveCallScreen(
                                        currentUid = currentUserId,
                                        callViewModel = callViewModel,
                                        onCallEnded = {
                                            activeCallId = null
                                            navController.popBackStack()
                                        }
                                    )
                                }
                            }

                            // Incoming call full-screen overlay
                            val ringing = incomingCall
                            if (ringing != null && activeCallId != ringing.id) {
                                IncomingCallScreen(
                                    call = ringing,
                                    onAccept = {
                                        activeCallId = ringing.id
                                        navController.navigate(AppRoute.ActiveCall(ringing.id))
                                    },
                                    onReject = {
                                        mainViewModel.rejectIncomingCall(ringing.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
