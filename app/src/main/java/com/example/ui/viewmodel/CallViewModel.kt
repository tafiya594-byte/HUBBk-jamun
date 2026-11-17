package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.CallSession
import com.example.data.repository.CallRepository
import com.example.webrtc.WebRtcCallManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer

class CallViewModel(
    private val callId: String,
    private val currentUserId: String,
    private val callRepo: CallRepository,
    val webRtcManager: WebRtcCallManager
) : ViewModel() {

    val callSession: StateFlow<CallSession?> = callRepo.observeCall(callId, currentUserId)
        .catch { error ->
            val fe = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fe == null && error is CancellationException) throw error
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _peerState = MutableStateFlow("Connecting...")
    val peerState: StateFlow<String> = _peerState.asStateFlow()

    private var initializedWebRtc = false
    private var offerSent = false
    private var answerSent = false
    private var remoteAnswerApplied = false
    private val appliedCandidateIds = mutableSetOf<String>()
    private var timerJob: Job? = null

    fun startIfReady(session: CallSession) {
        if (initializedWebRtc) return
        initializedWebRtc = true
        val isVideo = session.callType == "video"
        _isVideoEnabled.value = isVideo
        _isSpeakerOn.value = isVideo

        webRtcManager.initialize(
            isVideoCall = isVideo,
            onIceCandidate = { candidate ->
                viewModelScope.launch {
                    callRepo.addIceCandidate(
                        callId = callId,
                        participantUids = session.participantUids,
                        sdpMid = candidate.sdpMid ?: "",
                        sdpMLineIndex = candidate.sdpMLineIndex,
                        sdp = candidate.sdp
                    )
                }
            },
            onStateChange = { state ->
                _peerState.value = when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        startCallTimer()
                        "Connected"
                    }
                    PeerConnection.PeerConnectionState.CONNECTING -> "Connecting..."
                    PeerConnection.PeerConnectionState.DISCONNECTED -> "Reconnecting..."
                    PeerConnection.PeerConnectionState.FAILED -> "Connection failed"
                    PeerConnection.PeerConnectionState.CLOSED -> "Call ended"
                    else -> "Ringing..."
                }
            }
        )
        webRtcManager.setSpeakerphoneOn(isVideo)

        val isCaller = session.callerId == currentUserId
        if (isCaller && !offerSent) {
            offerSent = true
            _peerState.value = "Ringing..."
            webRtcManager.createOffer { sdp ->
                viewModelScope.launch {
                    callRepo.setOfferSdp(callId, sdp.description, sdp.type.canonicalForm())
                }
            }
        }

        viewModelScope.launch {
            callRepo.observeIceCandidates(callId, currentUserId).collect { list ->
                list.forEach { cand ->
                    if (cand.senderId != currentUserId && cand.id !in appliedCandidateIds) {
                        appliedCandidateIds.add(cand.id)
                        webRtcManager.addRemoteIceCandidate(
                            cand.sdpMid,
                            cand.sdpMLineIndex.toInt(),
                            cand.sdp
                        )
                    }
                }
            }
        }
    }

    fun handleSessionUpdate(session: CallSession) {
        startIfReady(session)
        val isCaller = session.callerId == currentUserId
        if (!isCaller && !answerSent && !session.offerSdp.isNullOrBlank()) {
            answerSent = true
            _peerState.value = "Connecting..."
            webRtcManager.setRemoteOfferAndCreateAnswer(session.offerSdp) { answer ->
                viewModelScope.launch {
                    callRepo.answerCall(callId, answer.description, answer.type.canonicalForm())
                }
            }
        }
        if (isCaller && !remoteAnswerApplied && !session.answerSdp.isNullOrBlank()) {
            remoteAnswerApplied = true
            webRtcManager.setRemoteAnswer(session.answerSdp)
            startCallTimer()
        }
    }

    private fun startCallTimer() {
        if (timerJob != null) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _elapsedSeconds.value += 1L
            }
        }
    }

    fun attachRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        webRtcManager.attachRenderers(local, remote)
    }

    fun toggleMic() {
        val next = !_isMicMuted.value
        _isMicMuted.value = next
        webRtcManager.setMicEnabled(!next)
    }

    fun toggleSpeaker() {
        val next = !_isSpeakerOn.value
        _isSpeakerOn.value = next
        webRtcManager.setSpeakerphoneOn(next)
    }

    fun toggleVideo() {
        val next = !_isVideoEnabled.value
        _isVideoEnabled.value = next
        webRtcManager.setVideoEnabled(next)
    }

    fun switchCamera() {
        webRtcManager.switchCamera()
    }

    fun endCall(onFinished: () -> Unit) {
        val session = callSession.value
        val nextStatus = when {
            session == null -> "ended"
            session.status == "ringing" && session.callerId == currentUserId -> "missed"
            else -> "ended"
        }
        viewModelScope.launch {
            callRepo.updateCallStatus(callId, nextStatus, _elapsedSeconds.value)
            onFinished()
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        webRtcManager.dispose()
    }

    companion object {
        fun factory(context: Context, callId: String, currentUserId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                    val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                    CallViewModel(
                        callId = callId,
                        currentUserId = currentUserId,
                        callRepo = CallRepository(db),
                        webRtcManager = WebRtcCallManager(context.applicationContext)
                    )
                }
            }
    }
}
