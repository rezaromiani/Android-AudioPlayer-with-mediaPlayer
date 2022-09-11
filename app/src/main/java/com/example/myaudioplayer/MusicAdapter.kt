package com.example.myaudioplayer

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.example.myaudioplayer.data.Audio
import com.example.myaudioplayer.view.CustomImageView

class MusicAdapter(private val eventListener: MusicEventListener, private val context: Context) :
    RecyclerView.Adapter<MusicAdapter.MusicViewHolder>() {
    var audios: List<Audio> = arrayListOf()
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var playingMusicPos = -1

    inner class MusicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTV: TextView = itemView.findViewById(R.id.musicTitleTv)
        private val musicIv: CustomImageView = itemView.findViewById(R.id.musicIv)
        private val lottieAnimationView: LottieAnimationView =
            itemView.findViewById(R.id.lottieAnimationView)

        fun onBind(audio: Audio) {
            titleTV.text = audio.title
            if (audio.image != null) {
                musicIv.setImageBitmap(audio.image)
            } else {
                musicIv.setImageBitmap(
                    BitmapFactory.decodeResource(
                        context.resources,
                        R.drawable.image
                    )
                )
            }
            itemView.setOnClickListener {
                eventListener.onItemClicked(audio, adapterPosition)
                notifyItemChanged(adapterPosition)
            }
            if (adapterPosition == playingMusicPos) {
                lottieAnimationView.visibility = View.VISIBLE
            } else {
                lottieAnimationView.visibility = View.GONE
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        return MusicViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.music_item, parent, false)
        )
    }


    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        holder.onBind(audios[position])
    }


    override fun getItemCount(): Int = audios.size

    interface MusicEventListener {
        fun onItemClicked(audio: Audio, position: Int)
    }

    fun notifyMusicChange(audio: Audio) {
        val index: Int = audios.indexOf(audio)
        if (index != -1) {
            notifyItemChanged(playingMusicPos)
            playingMusicPos = index
            notifyItemChanged(playingMusicPos)
        }
    }
}