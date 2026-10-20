package com.rekixo.ludopaws.feature.online

import android.content.Context
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.util.concurrent.ConcurrentHashMap

internal class FriendsVoiceChatSession(
    context: Context,
    private val localPlayerId: String,
    private val sendSignal: (String, JSONObject) -> Boolean,
    private val onStatusChanged: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val peers = ConcurrentHashMap<String, PeerConnection>()
    private val peerStates = ConcurrentHashMap<String, VoicePeerStatus>()
    private val pendingCandidates = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private var factory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    @Volatile private var disposed = false
    private var micEnabled = true

    init {
        synchronized(FACTORY_LOCK) {
            if (!factoryInitialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions
                        .builder(appContext)
                        .createInitializationOptions(),
                )
                factoryInitialized = true
            }
        }
        val audioModule =
            org.webrtc.audio.JavaAudioDeviceModule.builder(appContext)
                .createAudioDeviceModule()
        factory =
            PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioModule)
                .setVideoEncoderFactory(
                    DefaultVideoEncoderFactory(null, true, true),
                )
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(null))
                .createPeerConnectionFactory()
        audioSource = factory?.createAudioSource(MediaConstraints())
        audioTrack = factory?.createAudioTrack("friends-voice-audio", audioSource)
        audioTrack?.setEnabled(micEnabled)
    }

    fun setMicEnabled(enabled: Boolean) {
        micEnabled = enabled
        audioTrack?.setEnabled(enabled)
    }

    fun updatePeers(playerIds: Set<String>) {
        if (disposed) return
        val desired = playerIds.filter { it.isNotBlank() && it != localPlayerId }.toSet()
        (peers.keys - desired).forEach(::closePeer)
        val added = desired - peers.keys
        added.forEach { playerId ->
            val peer = createPeer(playerId) ?: return@forEach
            if (localPlayerId < playerId) createOffer(playerId, peer)
        }
        publishPeerStatus()
    }

    fun handleSignal(playerId: String, signal: JSONObject) {
        if (disposed || playerId.isBlank() || playerId == localPlayerId) return
        val peer = peers[playerId] ?: createPeer(playerId) ?: return
        when (signal.optString("type")) {
            "offer" -> {
                val sdp = signal.optString("sdp")
                if (sdp.isBlank()) return
                peer.setRemoteDescription(
                    sdpObserver(
                        onSuccess = {
                            drainCandidates(playerId, peer)
                            peer.createAnswer(
                                sdpObserver(
                                    onSuccess = { description ->
                                        peer.setLocalDescription(
                                            sdpObserver(
                                                onSuccess = {
                                                    sendDescription(playerId, peer)
                                                },
                                                onFailure = ::showError,
                                            ),
                                            description,
                                        )
                                    },
                                    onFailure = ::showError,
                                ),
                                MediaConstraints(),
                            )
                        },
                        onFailure = ::showError,
                    ),
                    SessionDescription(SessionDescription.Type.OFFER, sdp),
                )
            }
            "answer" -> {
                val sdp = signal.optString("sdp")
                if (sdp.isBlank()) return
                peer.setRemoteDescription(
                    sdpObserver(
                        onSuccess = { drainCandidates(playerId, peer) },
                        onFailure = ::showError,
                    ),
                    SessionDescription(SessionDescription.Type.ANSWER, sdp),
                )
            }
            "candidate" -> {
                val candidateText = signal.optString("candidate")
                if (candidateText.isBlank()) return
                val candidate =
                    IceCandidate(
                        signal.optString("sdpMid").takeIf(String::isNotBlank),
                        signal.optInt("sdpMLineIndex", 0),
                        candidateText,
                    )
                if (peer.remoteDescription != null) {
                    peer.addIceCandidate(candidate)
                } else {
                    val waiting = pendingCandidates.computeIfAbsent(playerId) { mutableListOf() }
                    synchronized(waiting) {
                        VoiceCandidateBufferPolicy.enqueue(waiting, candidate)
                    }
                }
            }
        }
    }

    fun close() {
        if (disposed) return
        disposed = true
        peers.keys.toList().forEach(::closePeer)
        audioTrack?.dispose()
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
        factory?.dispose()
        factory = null
    }

    private fun createPeer(playerId: String): PeerConnection? {
        peers[playerId]?.let { return it }
        val peerFactory = factory ?: return null
        val configuration =
            PeerConnection.RTCConfiguration(
                listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                ),
            )
        val peer =
            peerFactory.createPeerConnection(
                configuration,
                object : PeerConnection.Observer {
                    override fun onIceCandidate(candidate: IceCandidate) {
                        sendSignal(
                            playerId,
                            JSONObject()
                                .put("type", "candidate")
                                .put("candidate", candidate.sdp)
                                .put("sdpMid", candidate.sdpMid)
                                .put("sdpMLineIndex", candidate.sdpMLineIndex),
                        )
                    }

                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                        if (disposed || !peers.containsKey(playerId)) return
                        peerStates[playerId] = when (newState) {
                            PeerConnection.PeerConnectionState.CONNECTED -> VoicePeerStatus.CONNECTED
                            PeerConnection.PeerConnectionState.FAILED -> VoicePeerStatus.FAILED
                            PeerConnection.PeerConnectionState.DISCONNECTED -> VoicePeerStatus.INTERRUPTED
                            else -> VoicePeerStatus.CONNECTING
                        }
                        publishPeerStatus()
                    }

                    override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
                    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
                    override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
                    override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
                    override fun onDataChannel(channel: DataChannel) = channel.dispose()
                    override fun onRenegotiationNeeded() = Unit
                    override fun onAddTrack(receiver: RtpReceiver, streams: Array<out org.webrtc.MediaStream>) = Unit
                    override fun onTrack(transceiver: org.webrtc.RtpTransceiver) = Unit
                },
            ) ?: run {
                onStatusChanged("Could not start voice connection")
                return null
            }
        audioTrack?.let { peer.addTrack(it, listOf("friends-voice")) }
        peers[playerId] = peer
        peerStates[playerId] = VoicePeerStatus.CONNECTING
        return peer
    }

    private fun createOffer(playerId: String, peer: PeerConnection) {
        peer.createOffer(
            sdpObserver(
                onSuccess = { description ->
                    peer.setLocalDescription(
                        sdpObserver(
                            onSuccess = { sendDescription(playerId, peer) },
                            onFailure = ::showError,
                        ),
                        description,
                    )
                },
                onFailure = ::showError,
            ),
            MediaConstraints(),
        )
    }

    private fun sendDescription(playerId: String, peer: PeerConnection) {
        val description = peer.localDescription ?: return
        sendSignal(
            playerId,
            JSONObject()
                .put("type", description.type.canonicalForm())
                .put("sdp", description.description),
        )
    }

    private fun drainCandidates(playerId: String, peer: PeerConnection) {
        pendingCandidates.remove(playerId)?.let { waiting ->
            val pending = synchronized(waiting) { waiting.toList() }
            pending.forEach(peer::addIceCandidate)
        }
    }

    private fun publishPeerStatus() {
        if (!disposed) onStatusChanged(
            VoicePeerStatusPolicy.message(peers.keys.mapNotNull(peerStates::get)),
        )
    }

    private fun closePeer(playerId: String) {
        peerStates.remove(playerId)
        peers.remove(playerId)?.let {
            it.close()
            it.dispose()
        }
        pendingCandidates.remove(playerId)
    }

    private fun showError(message: String?) {
        onStatusChanged(message?.takeIf(String::isNotBlank) ?: "Voice connection failed")
    }

    private fun sdpObserver(
        onSuccess: (SessionDescription) -> Unit = {},
        onFailure: (String?) -> Unit,
    ) = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = onSuccess(description)
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = onFailure(error)
        override fun onSetFailure(error: String) = onFailure(error)
    }

    private companion object {
        val FACTORY_LOCK = Any()
        var factoryInitialized = false
    }
}
