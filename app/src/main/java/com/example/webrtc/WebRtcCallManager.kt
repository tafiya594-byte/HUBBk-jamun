package com.example.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class WebRtcCallManager(private val context: Context) {

    val eglBase: EglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    private var onIceCandidateGenerated: ((IceCandidate) -> Unit)? = null
    private var onConnectionStateChanged: ((PeerConnection.PeerConnectionState) -> Unit)? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
    )

    fun initialize(
        isVideoCall: Boolean,
        onIceCandidate: (IceCandidate) -> Unit,
        onStateChange: (PeerConnection.PeerConnectionState) -> Unit
    ) {
        onIceCandidateGenerated = onIceCandidate
        onConnectionStateChanged = onStateChange

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        val factory = peerConnectionFactory ?: return

        val audioConstraints = MediaConstraints()
        localAudioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("ABID_AUDIO_TRACK", localAudioSource)?.apply {
            setEnabled(true)
        }

        if (isVideoCall) {
            setupLocalVideo(factory)
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    onIceCandidateGenerated?.invoke(candidate)
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                stream?.videoTracks?.firstOrNull()?.let { track ->
                    remoteVideoTrack = track
                    remoteRenderer?.let { renderer -> track.addSink(renderer) }
                }
            }
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    remoteRenderer?.let { renderer -> track.addSink(renderer) }
                }
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                if (newState != null) {
                    onConnectionStateChanged?.invoke(newState)
                }
            }
        })

        val streamIds = listOf("ABID_STREAM")
        localAudioTrack?.let { peerConnection?.addTrack(it, streamIds) }
        localVideoTrack?.let { peerConnection?.addTrack(it, streamIds) }
    }

    private fun setupLocalVideo(factory: PeerConnectionFactory) {
        try {
            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames
            val frontCamera = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: deviceNames.firstOrNull()
                ?: return

            videoCapturer = enumerator.createCapturer(frontCamera, null)
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            localVideoSource = factory.createVideoSource(videoCapturer?.isScreencast == true)
            videoCapturer?.initialize(surfaceTextureHelper, context, localVideoSource?.capturerObserver)
            videoCapturer?.startCapture(640, 480, 24)
            localVideoTrack = factory.createVideoTrack("ABID_VIDEO_TRACK", localVideoSource)?.apply {
                setEnabled(true)
            }
            localRenderer?.let { localVideoTrack?.addSink(it) }
        } catch (e: Exception) {
            Log.w("WebRTC", "Camera capture unavailable: ${e.message}")
        }
    }

    fun attachRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        localRenderer = local
        remoteRenderer = remote
        local?.let { renderer ->
            localVideoTrack?.addSink(renderer)
        }
        remote?.let { renderer ->
            remoteVideoTrack?.addSink(renderer)
        }
    }

    fun createOffer(onOfferCreated: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null) {
                    pc.setLocalDescription(SimpleSdpObserver(), sdp)
                    onOfferCreated(sdp)
                }
            }
        }, constraints)
    }

    fun setRemoteOfferAndCreateAnswer(
        offerSdp: String,
        onAnswerCreated: (SessionDescription) -> Unit
    ) {
        val pc = peerConnection ?: return
        val offer = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        if (sdp != null) {
                            pc.setLocalDescription(SimpleSdpObserver(), sdp)
                            onAnswerCreated(sdp)
                        }
                    }
                }, constraints)
            }
        }, offer)
    }

    fun setRemoteAnswer(answerSdp: String) {
        val pc = peerConnection ?: return
        val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        pc.setRemoteDescription(SimpleSdpObserver(), answer)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
    }

    fun setMicEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun setSpeakerphoneOn(enabled: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = enabled
    }

    fun dispose() {
        try {
            localRenderer?.let { localVideoTrack?.removeSink(it) }
            remoteRenderer?.let { remoteVideoTrack?.removeSink(it) }
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            surfaceTextureHelper?.dispose()
            localAudioTrack?.dispose()
            localAudioSource?.dispose()
            localVideoTrack?.dispose()
            localVideoSource?.dispose()
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnectionFactory?.dispose()
            eglBase.release()
        } catch (e: Exception) {
            Log.w("WebRTC", "Dispose warning: ${e.message}")
        } finally {
            videoCapturer = null
            surfaceTextureHelper = null
            localAudioTrack = null
            localAudioSource = null
            localVideoTrack = null
            localVideoSource = null
            peerConnection = null
            peerConnectionFactory = null
            localRenderer = null
            remoteRenderer = null
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {
            Log.w("WebRTC", "SDP create failure: $error")
        }
        override fun onSetFailure(error: String?) {
            Log.w("WebRTC", "SDP set failure: $error")
        }
    }
}
