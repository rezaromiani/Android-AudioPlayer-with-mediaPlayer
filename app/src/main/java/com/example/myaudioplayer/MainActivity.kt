package com.example.myaudioplayer

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myaudioplayer.data.Audio
import com.example.myaudioplayer.data.AudioStorage
import com.example.myaudioplayer.data.convertMillisToString
import com.example.myaudioplayer.databinding.ActivityMainBinding
import com.google.android.material.slider.Slider
import com.vmadalin.easypermissions.EasyPermissions
import java.io.FileDescriptor
import java.util.*

enum class SingleMusicShowState {
    HIDE, HALF_SHOW, SHOW
}

class MainActivity : AppCompatActivity(), MusicAdapter.MusicEventListener,
    EasyPermissions.PermissionCallbacks {
    lateinit var musicAdapter: MusicAdapter
    private lateinit var timer: Timer
    private var isDragging = false
    private var cursor = 0
    lateinit var storage: AudioStorage
    lateinit var audios: ArrayList<Audio>
    private lateinit var binding: ActivityMainBinding
    private var mBound: Boolean = false
    private var singleMusicShowState: SingleMusicShowState = SingleMusicShowState.HIDE
    private lateinit var mediaPlayerService: MediaPlayerService

    private val PERMISSION_REQUEST_CODE = 10024
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        storage = AudioStorage(applicationContext)
        registerMusicBroadCastReceiver()
        if (hasPermissions()) {
            initViews()
        } else {
            requestStoragePermission()
        }

    }


    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MediaPlayerService.LocalBinder
            mediaPlayerService = binder.service
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }


    }

    private fun initViews() {
        audios = loadAudio()
        musicAdapter = MusicAdapter(this, this)
        musicAdapter.audios = audios
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.VERTICAL, false)
            adapter = musicAdapter
        }
        binding.pausePlayIV.setOnClickListener {
            if (mBound) {
                when (mediaPlayerService.musicState) {
                    MusicState.PLAYING -> {
                        mediaPlayerService.changeMediaPlayState(MusicState.PAUSED)
                    }
                    MusicState.PAUSED -> {
                        mediaPlayerService.changeMediaPlayState(MusicState.PLAYING)
                    }
                    else -> {}
                }
            }
        }
        binding.previousBtn.setOnClickListener {

            if (cursor == 0) {
                cursor = 0
            } else {
                cursor--
                storage.storeAudioIndex(cursor)
                mediaPlayerService.startPlayAudio()
                binding.musicSlider.value = 0f
            }


        }
        binding.musicSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            @SuppressLint("RestrictedApi")
            override fun onStartTrackingTouch(slider: Slider) {
                isDragging = true
            }

            @SuppressLint("RestrictedApi")
            override fun onStopTrackingTouch(slider: Slider) {
                isDragging = false
                mediaPlayerService.mediaPlaySeekTo(slider.value.toInt())
            }

        })
        binding.nextBtn.setOnClickListener {

            if (cursor < audios.size - 1) {
                cursor++
            } else {
                cursor = 0
            }
            storage.storeAudioIndex(cursor)
            mediaPlayerService.startPlayAudio()
            binding.musicSlider.value = 0f

        }

        binding.playViewContainer.setOnClickListener {
            if (singleMusicShowState == SingleMusicShowState.HALF_SHOW) {
                binding.motionLayout.setTransition(R.id.from_halfShow_to_show)
                binding.motionLayout.transitionToEnd()
                binding.musicPlayTv.gravity = Gravity.CENTER
                singleMusicShowState = SingleMusicShowState.SHOW
            }
        }

        binding.repeatBtn.setOnClickListener {
            if (mediaPlayerService.IsRepeat) {
                binding.repeatBtn.setImageResource(R.drawable.ic_round_repeat_24_in_active)
                mediaPlayerService.IsRepeat = false
            } else {
                binding.repeatBtn.setImageResource(R.drawable.ic_round_repeat_24_active)
                mediaPlayerService.IsRepeat = true
            }
        }
        binding.shuffleBtn.setOnClickListener {
            if (mediaPlayerService.Isshuffle) {
                binding.shuffleBtn.setImageResource(R.drawable.ic_round_shuffle_24_in_active)
                mediaPlayerService.Isshuffle = false
            } else {
                binding.shuffleBtn.setImageResource(R.drawable.ic_round_shuffle_24_active)
                mediaPlayerService.Isshuffle = true
            }
        }
    }

    @SuppressLint("Range")
    private fun loadAudio(): ArrayList<Audio> {

        val projection = arrayOf(
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
        )
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"
        val sortOrder = MediaStore.Audio.Media.TITLE + " ASC"
        val audioList: ArrayList<Audio> = arrayListOf()
        val cursor: Cursor? = contentResolver.query(uri, projection, selection, null, sortOrder)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val data = cursor.getString(0)
                val title = cursor.getString(1)
                val album = cursor.getString(2)
                val artist = cursor.getString(3)
                val albumID = cursor.getString(4)
                val audio = Audio(
                    data,
                    title,
                    album,
                    artist,
                    albumID
                )
                audio.image = getAlbumArt(audio.albumID.toLong())
                audioList.add(
                    audio
                )
            }
        }
        cursor?.close()
        return audioList
    }

    private fun playAudio(audioIndex: Int) {
        if (!mBound) {
            storage.storeAudio(audios)
            storage.storeAudioIndex(audioIndex)
            Intent(this, MediaPlayerService::class.java).also {
                startService(it)
                bindService(it, connection, Context.BIND_AUTO_CREATE)
            }
        } else {
            storage.storeAudioIndex(audioIndex)
            mediaPlayerService.startPlayAudio()
        }

    }

    override fun onItemClicked(audio: Audio, position: Int) {
        if (this::timer.isInitialized) {
            timer.cancel()
            timer.purge()
        }
        cursor = position
        playAudio(position)
    }

    private val onMusicPreparedReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            binding.musicPlayTv.text = mediaPlayerService.activeAudio.title
            binding.durationTv.text = convertMillisToString(mediaPlayerService.getDuration())
        }

    }
    private val onMusicStateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            if (singleMusicShowState == SingleMusicShowState.HIDE) {
                binding.motionLayout.setTransition(R.id.from_hide_to_halfShow)
                binding.motionLayout.transitionToEnd()
                binding.musicPlayTv.gravity = Gravity.START
                singleMusicShowState = SingleMusicShowState.HALF_SHOW
            }
            if (mediaPlayerService.activeAudio.image != null) {
                binding.musicCoverIv.setImageBitmap(mediaPlayerService.activeAudio.image)
            } else {
                binding.musicCoverIv.setImageBitmap(
                    BitmapFactory.decodeResource(
                        resources,
                        R.drawable.image
                    )
                )
            }

            when (mediaPlayerService.musicState) {
                MusicState.PLAYING -> {
                    binding.pausePlayIV.setImageResource(R.drawable.ic_round_pause_24)
                    binding.musicSlider.valueTo = mediaPlayerService.getDuration().toFloat()
                    if (this@MainActivity::timer.isInitialized) {
                        timer.cancel()
                        timer.purge()
                    }
                    timer = Timer()
                    timer.schedule(object : TimerTask() {
                        override fun run() {
                            runOnUiThread {
                                binding.currentTimeTv.text =
                                    convertMillisToString(mediaPlayerService.getMediaPlayerPosition())
                            }
                            if (!isDragging) {
                                if (mediaPlayerService.getDuration() >= mediaPlayerService.getMediaPlayerPosition()
                                    && binding.musicSlider.valueTo >= mediaPlayerService.getMediaPlayerPosition()
                                ) {
                                    binding.musicSlider.value =
                                        mediaPlayerService.getMediaPlayerPosition().toFloat()
                                } else
                                    binding.musicSlider.value = 0f
                            }

                        }

                    }, 100, 100)

                }
                MusicState.PAUSED -> {
                    timer.cancel()
                    if (mediaPlayerService.getDuration() >= mediaPlayerService.getMediaPlayerPosition()
                        && binding.musicSlider.valueTo >= mediaPlayerService.getMediaPlayerPosition()
                    )
                        binding.musicSlider.value =
                            mediaPlayerService.getMediaPlayerPosition().toFloat()


                    binding.pausePlayIV.setImageResource(R.drawable.ic_round_play_arrow_24)
                }
                MusicState.STOPPED -> {
                    timer.cancel()
                    binding.musicSlider.value = 0f
                    binding.pausePlayIV.setImageResource(R.drawable.ic_round_play_arrow_24)
                }
            }
            musicAdapter.notifyMusicChange(mediaPlayerService.activeAudio)
        }

    }


    private val onMusicCompletionReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            binding.musicSlider.value = 0f
        }

    }

    private fun registerMusicBroadCastReceiver() {
        IntentFilter(Broadcast_CHANGE_AUDIO_STATE).also {
            registerReceiver(onMusicStateChangeReceiver, it)
        }
        IntentFilter(Broadcast_ONCOMPLETION_AUDIO).also {
            registerReceiver(onMusicCompletionReceiver, it)
        }
        IntentFilter(Broadcast_ONPREPARED_AUDIO).also {
            registerReceiver(onMusicPreparedReceiver, it)
        }
    }

    override fun onDestroy() {
        timer.cancel()
        timer.purge()
        mediaPlayerService.stopSelf()
        unregisterReceiver(onMusicStateChangeReceiver)
        super.onDestroy()
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        Toast.makeText(
            this,
            "this app cannot work without Phone state and Storage Permissions",
            Toast.LENGTH_LONG
        )
            .show()
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        initViews()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    private fun getAlbumArt(album_id: Long?): Bitmap? {
        var bm: Bitmap? = null
        try {
            val sArtworkUri = Uri
                .parse("content://media/external/audio/albumart")
            val uri = ContentUris.withAppendedId(sArtworkUri, album_id!!)
            val pfd: ParcelFileDescriptor? = this.contentResolver
                .openFileDescriptor(uri, "r")
            if (pfd != null) {
                val fd: FileDescriptor = pfd.fileDescriptor
                bm = BitmapFactory.decodeFileDescriptor(fd)
            }
        } catch (e: Exception) {
        }
        return bm
    }

    private fun hasPermissions(): Boolean =
        EasyPermissions.hasPermissions(this, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                EasyPermissions.hasPermissions(this, Manifest.permission.READ_PHONE_STATE)

    private fun requestStoragePermission() {
        val perms =
            arrayOf<String>(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE
            )
        EasyPermissions.requestPermissions(
            this,
            "this app cannot work without storage Permission",
            PERMISSION_REQUEST_CODE,
            *perms
        )

    }

    override fun onBackPressed() {
        if (singleMusicShowState == SingleMusicShowState.SHOW) {
            binding.motionLayout.setTransition(R.id.from_halfShow_to_show)
            binding.motionLayout.transitionToStart()
            singleMusicShowState = SingleMusicShowState.HALF_SHOW
            binding.musicPlayTv.gravity = Gravity.START

        } else {
            super.onBackPressed()
        }
    }
}