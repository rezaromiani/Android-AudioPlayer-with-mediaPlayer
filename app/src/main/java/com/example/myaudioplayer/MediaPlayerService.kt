package com.example.myaudioplayer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import com.example.myaudioplayer.data.Audio
import com.example.myaudioplayer.data.AudioStorage


enum class MusicState {
    PLAYING, PAUSED, STOPPED
}

const val ACTION_PLAY = "audioplayer.ACTION_PLAY"
const val ACTION_PAUSE = "audioplayer.ACTION_PAUSE"
const val ACTION_PREVIOUS = "audioplayer.ACTION_PREVIOUS"
const val ACTION_NEXT = "audioplayer.ACTION_NEXT"
const val ACTION_STOP = "audioplayer.ACTION_STOP"
const val Broadcast_CHANGE_AUDIO_STATE = "audioplayer.ChangeAudioState"
const val Broadcast_ONCOMPLETION_AUDIO = "audioplayer.ONCOMPLETIONAudio"
const val Broadcast_ONPREPARED_AUDIO = "audioplayer.ONPREPAREDAudio"

class MediaPlayerService : Service(), MediaPlayer.OnPreparedListener,
    MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {

    private val TAG = "MediaPlayerService"
    private val iBinder: IBinder = LocalBinder()
    private var mediaPlayer: MediaPlayer = MediaPlayer()
    private lateinit var audioManager: AudioManager
    var musicState = MusicState.STOPPED
        private set
    private lateinit var audioList: ArrayList<Audio>
    var audioIndex = -1
        private set
    lateinit var activeAudio: Audio
        private set
    private var resumePosition: Int = 0
    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var mediaSession: MediaSession
    private lateinit var transportControls: MediaController.TransportControls
    private var onGoingCall: Boolean = false
    private lateinit var telephonyManager: TelephonyManager

    var IsRepeat: Boolean = false
    var Isshuffle: Boolean = false


    private fun callStateListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager.registerTelephonyCallback(
                this.mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        when (state) {
                            TelephonyManager.CALL_STATE_OFFHOOK, TelephonyManager.CALL_STATE_RINGING -> {
                                if (mediaPlayer.isPlaying)
                                    pauseMedia()
                                onGoingCall = true
                            }
                            TelephonyManager.CALL_STATE_IDLE -> {
                                if (onGoingCall) {
                                    onGoingCall = false;
                                    resumeMedia();
                                }
                            }
                        }
                    }
                })
        } else {
            telephonyManager.listen(object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    when (state) {
                        TelephonyManager.CALL_STATE_OFFHOOK, TelephonyManager.CALL_STATE_RINGING -> {
                            if (mediaPlayer.isPlaying)
                                pauseMedia()
                            onGoingCall = true
                        }
                        TelephonyManager.CALL_STATE_IDLE -> {
                            if (onGoingCall) {
                                onGoingCall = false
                                resumeMedia()
                            }
                        }
                    }
                }
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    override fun onCreate() {
        super.onCreate()
        callStateListener()
    }

    private fun initMediaPlayer() {
        mediaPlayer.setOnPreparedListener(this)
        mediaPlayer.setOnCompletionListener(this)
        mediaPlayer.reset()
        try {
            mediaPlayer.setDataSource(activeAudio.data)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
        mediaPlayer.prepareAsync()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "initMediaPlayer: onStartCommandCall")

        try {
            val audioStorage = AudioStorage(applicationContext)
            audioList = audioStorage.loadAudio()
            audioIndex = audioStorage.loadAudioIndex()

            if (audioIndex != -1 && audioIndex < audioList.size) {
                activeAudio = audioList[audioIndex]
            } else {
                stopSelf()
            }
        } catch (e: Exception) {
            stopSelf()
        }
        if (!requestAudioFocus())
            stopSelf()

        if (!this::mediaSessionManager.isInitialized) {
            initMediaSession()
            initMediaPlayer()
            buildNotification(MusicState.PLAYING)
        }
        handleIncomingActions(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder {
        return iBinder;
    }


    fun getMediaPlayerPosition(): Int {
        return mediaPlayer.currentPosition
    }

    fun getDuration(): Int =  mediaPlayer.duration

    fun startPlayAudio() {
        audioIndex = AudioStorage(applicationContext).loadAudioIndex()
        if (audioIndex != -1 && audioIndex < audioList.size) {
            activeAudio = audioList[audioIndex]
        } else {
            stopSelf()
        }

        stopMedia()
        mediaPlayer.reset()
        initMediaPlayer()
    }

    fun changeMediaPlayState(musicState: MusicState) {
        when (musicState) {
            MusicState.PLAYING -> {
                playMedia()
            }
            MusicState.PAUSED -> {
                pauseMedia()
            }
            MusicState.STOPPED -> {
                stopMedia()
            }
        }
    }

    fun mediaPlaySeekTo(value: Int) {
        mediaPlayer.seekTo(value)
    }


    private fun playMedia() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            musicState = MusicState.PLAYING
            onMediaChangeState()
        }
    }

    private fun stopMedia() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            musicState = MusicState.STOPPED
            onMediaChangeState()

        }
    }

    private fun pauseMedia() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            resumePosition = mediaPlayer.currentPosition
            musicState = MusicState.PAUSED
            onMediaChangeState()

        }
    }

    private fun resumeMedia() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.seekTo(resumePosition)
            mediaPlayer.start()
            musicState = MusicState.PLAYING
            onMediaChangeState()
        }
    }

    private fun skipToNext() {
        if (Isshuffle) {
            audioIndex = (0 until audioList.size - 1).random()
            activeAudio = audioList[audioIndex]
        } else {
            if (audioIndex == audioList.size - 1) {
                //if last in playlist
                audioIndex = 0
                activeAudio = audioList[audioIndex]
            } else {
                //get next in playlist
                activeAudio = audioList[++audioIndex]
            }
        }

        //Update stored index
        AudioStorage(applicationContext).storeAudioIndex(audioIndex)
        mediaPlayer.stop()
        initMediaPlayer()
    }

    private fun skipToPrevious() {
        if (Isshuffle) {
            audioIndex = (0 until audioList.size - 1).random()
            activeAudio = audioList[audioIndex]
        } else {
            if (audioIndex == 0) {
                //if first in playlist
                //set index to the last of audioList
                audioIndex = audioList.size - 1
                activeAudio = audioList[audioIndex]
            } else {
                //get previous in playlist
                activeAudio = audioList[--audioIndex]
            }
        }

        //Update stored index
        AudioStorage(applicationContext).storeAudioIndex(audioIndex)
        mediaPlayer.stop()
        initMediaPlayer()
    }


    override fun onPrepared(p0: MediaPlayer?) {
        playMedia()
        Intent(Broadcast_ONPREPARED_AUDIO).also {
            sendBroadcast(it)
        }
    }

    override fun onCompletion(p0: MediaPlayer?) {
        Intent(Broadcast_ONCOMPLETION_AUDIO).also {
            sendBroadcast(it)
        }
        if (IsRepeat) {
            stopMedia()
            initMediaPlayer()
        } else {
            skipToNext()
        }
    }

    inner class LocalBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }


    override fun onDestroy() {
        mediaPlayer.stop()
        mediaPlayer.reset()
        mediaPlayer.release()
        removeAudioFocus()
        removeNotification()
        super.onDestroy()
    }


    private fun initMediaSession() {
        if (this::mediaSessionManager.isInitialized) return

        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

        mediaSession = MediaSession(applicationContext, "AudioPlayer")

        transportControls = mediaSession.controller.transportControls

        mediaSession.isActive = true
        //Set mediaSession's MetaData
        updateMetaData()
        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                super.onPlay()
                resumeMedia()
            }

            override fun onPause() {
                super.onPause()
                pauseMedia()
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                skipToNext()
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                skipToPrevious()
            }

            override fun onStop() {
                super.onStop()
                //removeNotification
                stopSelf()
            }

            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
            }
        })
    }

    private fun updateMetaData() {
        val albumArt = BitmapFactory.decodeResource(resources, R.drawable.image)

        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, activeAudio.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, activeAudio.album)
                .putString(MediaMetadata.METADATA_KEY_TITLE, activeAudio.title)
                .build()
        )
    }

    private fun handleIncomingActions(playbackAction: Intent) {
        val actionString = playbackAction.action
        if (actionString.equals(ACTION_PLAY, ignoreCase = true)) {
            transportControls.play()
        } else if (actionString.equals(ACTION_PAUSE, ignoreCase = true)) {
            transportControls.pause()
        } else if (actionString.equals(ACTION_NEXT, ignoreCase = true)) {
            transportControls.skipToNext()
        } else if (actionString.equals(ACTION_PREVIOUS, ignoreCase = true)) {
            transportControls.skipToPrevious()
        } else if (actionString.equals(ACTION_STOP, ignoreCase = true)) {
            transportControls.stop()
        }
    }

    private fun buildNotification(musicState: MusicState) {
        var notificationAction = R.drawable.ic_round_pause_24
        var playPauseAction: PendingIntent? = null
        if (musicState == MusicState.PLAYING) {
            playPauseAction = playbackAction(1);
            notificationAction = R.drawable.ic_round_pause_24
        } else if (musicState == MusicState.PAUSED) {
            notificationAction = R.drawable.ic_round_play_arrow_24
            playPauseAction = playbackAction(0);
        }


        val largeIcon = BitmapFactory.decodeResource(resources, R.drawable.image)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val noti = Notification.Builder(this, CHANNEL_ID)
                .setStyle(
                    Notification.MediaStyle()
                        .setMediaSession(mediaSession.sessionToken)

                        .setShowActionsInCompactView(0, 1, 2)
                )
                .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentText(activeAudio.artist)
                .setContentTitle(activeAudio.title)
                .addAction(R.drawable.ic_round_skip_previous_24, "previous", playbackAction(3))
                .addAction(notificationAction, "pause", playPauseAction)
                .addAction(R.drawable.ic_round_skip_next_24, "next", playbackAction(2))

            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
                101,
                noti.build()
            )
        }
    }

    private fun removeNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(101)
    }

    private fun playbackAction(actionNumber: Int): PendingIntent {
        val playbackAction = Intent(this, MediaPlayerService::class.java)

        when (actionNumber) {
            0 -> {
                playbackAction.action = ACTION_PLAY
                return PendingIntent.getService(
                    this,
                    actionNumber,
                    playbackAction,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }
            1 -> {
                playbackAction.action = ACTION_PAUSE;
                return PendingIntent.getService(
                    this,
                    actionNumber,
                    playbackAction,
                    PendingIntent.FLAG_IMMUTABLE
                );
            }
            2 -> {
                playbackAction.action = ACTION_NEXT;
                return PendingIntent.getService(
                    this,
                    actionNumber,
                    playbackAction,
                    PendingIntent.FLAG_IMMUTABLE
                );
            }
            3 -> {
                playbackAction.action = ACTION_PREVIOUS;
                return PendingIntent.getService(
                    this,
                    actionNumber,
                    playbackAction,
                    PendingIntent.FLAG_IMMUTABLE
                );
            }
            else -> {
                throw IllegalStateException("")
            }
        }
    }

    private fun onMediaChangeState() {
        Intent(Broadcast_CHANGE_AUDIO_STATE).also {
            sendBroadcast(it)
        }
        updateMetaData()
        buildNotification(musicState)
    }

    override fun onAudioFocusChange(focusState: Int) {
        when (focusState) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // resume playback
                if (!mediaPlayer.isPlaying) mediaPlayer.start()
                mediaPlayer.setVolume(1.0f, 1.0f)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                    mediaPlayer.release()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                if (mediaPlayer.isPlaying) pauseMedia()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mediaPlayer.isPlaying) mediaPlayer.setVolume(0.1f, 0.1f)
            }

        }
    }

    private fun requestAudioFocus(): Boolean {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result = audioManager.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun removeAudioFocus(): Boolean {
        return if (this::audioManager.isInitialized)
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                    audioManager.abandonAudioFocus(this)
        else
            false
    }
}