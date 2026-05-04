package com.example.ijkradio.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.example.ijkradio.R
import com.example.ijkradio.data.Station
import com.example.ijkradio.player.IPlayerManager
import com.example.ijkradio.player.ExoPlayerManager

class PlayerFullscreenFragment : Fragment() {

    private lateinit var textViewGeneralInfo: TextView
    private lateinit var textViewStationName: TextView
    private lateinit var textViewTimePlayed: TextView
    private lateinit var textViewTimeCached: TextView
    private lateinit var textViewNetworkUsageInfo: TextView
    private lateinit var textViewNoStation: TextView
    private lateinit var buttonPlay: ImageButton
    private lateinit var pagerArtAndInfo: ViewPager
    private lateinit var artAndInfoPagerAdapter: ArtAndInfoPagerAdapter

    private var playerManager: IPlayerManager? = null
    private var currentStation: Station? = null
    private var isPlaying = false

    private var isViewReady = false
    private var pendingStation: Station? = null

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateTimeInfo()
            handler.postDelayed(this, 1000)
        }
    }

    interface PlayerFullscreenListener {
        fun onExitFullscreen()
    }

    private var listener: PlayerFullscreenListener? = null

    fun setPlayerFullscreenListener(listener: PlayerFullscreenListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_player_fullscreen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textViewGeneralInfo = view.findViewById(R.id.textViewGeneralInfo)
        textViewStationName = view.findViewById(R.id.textViewStationName)
        textViewTimePlayed = view.findViewById(R.id.textViewTimePlayed)
        textViewTimeCached = view.findViewById(R.id.textViewTimeCached)
        textViewNetworkUsageInfo = view.findViewById(R.id.textViewNetworkUsageInfo)
        textViewNoStation = view.findViewById(R.id.textViewNoStation)
        buttonPlay = view.findViewById(R.id.buttonPlay)
        pagerArtAndInfo = view.findViewById(R.id.pagerArtAndInfo)

        artAndInfoPagerAdapter = ArtAndInfoPagerAdapter(requireContext())
        pagerArtAndInfo.adapter = artAndInfoPagerAdapter

        buttonPlay.setOnClickListener {
            togglePlayPause()
        }

        view.findViewById<ImageButton>(R.id.buttonFullscreenExit)?.setOnClickListener {
            listener?.onExitFullscreen()
        }

        isViewReady = true
        pendingStation?.let { applyStationInfo(it) }
        pendingStation = null
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    fun setPlayerManager(manager: IPlayerManager) {
        this.playerManager = manager
    }

    fun updateStationInfo(station: Station?) {
        currentStation = station
        if (!isViewReady) {
            pendingStation = station
            return
        }
        applyStationInfo(station)
    }

    private fun applyStationInfo(station: Station?) {
        if (station != null) {
            textViewStationName.text = station.name
            textViewStationName.visibility = View.VISIBLE
            textViewNoStation.visibility = View.GONE
        } else {
            textViewStationName.visibility = View.GONE
            textViewNoStation.visibility = View.VISIBLE
        }
        artAndInfoPagerAdapter.refresh(station)
    }

    fun updatePlaybackState(state: PlaybackState) {
        when (state) {
            is PlaybackState.Stopped -> {
                textViewGeneralInfo.text = getString(R.string.status_stopped)
                textViewStationName.visibility = View.GONE
                buttonPlay.setImageResource(R.drawable.ic_play_circle)
                textViewNoStation.visibility = View.VISIBLE
                isPlaying = false
            }
            is PlaybackState.Buffering -> {
                textViewGeneralInfo.text = getString(R.string.buffering)
                buttonPlay.setImageResource(R.drawable.ic_pause_circle)
                isPlaying = true
            }
            is PlaybackState.Playing -> {
                textViewGeneralInfo.text = state.stationName
                textViewStationName.visibility = View.VISIBLE
                textViewStationName.text = state.stationName
                buttonPlay.setImageResource(R.drawable.ic_pause_circle)
                textViewNoStation.visibility = View.GONE
                isPlaying = true
            }
            is PlaybackState.Paused -> {
                textViewGeneralInfo.text = getString(R.string.status_paused)
                buttonPlay.setImageResource(R.drawable.ic_play_circle)
                isPlaying = false
            }
            is PlaybackState.Error -> {
                textViewGeneralInfo.text = getString(R.string.status_error, state.message)
                buttonPlay.setImageResource(R.drawable.ic_play_circle)
                isPlaying = false
            }
        }
    }

    fun updateMetadata(title: String?) {
        if (!title.isNullOrEmpty()) {
            textViewGeneralInfo.text = title
        }
    }

    private fun togglePlayPause() {
        playerManager?.let { manager ->
            if (manager.isPlaying()) {
                manager.pause()
            } else {
                currentStation?.let { station ->
                    manager.playStation(station)
                }
            }
        }
    }

    private fun updateTimeInfo() {
        playerManager?.let { manager ->
            if (manager is ExoPlayerManager) {
                val exoPlayer = manager.getExoPlayer()
                exoPlayer?.let { player ->
                    val bufferedPosition = player.bufferedPosition
                    val currentPosition = player.currentPosition

                    if (currentPosition > 0) {
                        textViewTimePlayed.text = formatTime(currentPosition)
                    }
                    if (bufferedPosition > currentPosition) {
                        textViewTimeCached.text = "缓冲: ${(bufferedPosition - currentPosition) / 1000}s"
                    }
                }
            }
        }
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        val hours = milliseconds / (1000 * 60 * 60)

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    private inner class ArtAndInfoPagerAdapter(private val context: android.content.Context) : PagerAdapter() {

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val inflater = LayoutInflater.from(context)
            val view = when (position) {
                0 -> inflater.inflate(R.layout.page_player_album_art, container, false)
                else -> inflater.inflate(R.layout.page_player_station_info, container, false)
            }
            bindView(view, position)
            container.addView(view)
            return view
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(`object` as View)
        }

        override fun getCount(): Int = 2

        override fun isViewFromObject(view: View, `object`: Any): Boolean = view == `object`

        override fun getItemPosition(`object`: Any): Int = POSITION_NONE

        override fun getPageTitle(position: Int): CharSequence {
            return if (position == 0) getString(R.string.album_art)
            else getString(R.string.station_description_hint)
        }

        private fun bindView(view: View, position: Int) {
            if (position == 0) {
                view.findViewById<ImageView>(R.id.imageViewArt)?.setImageResource(R.drawable.ic_launcher_foreground)
            } else {
                view.findViewById<TextView>(R.id.textViewStationDescription)?.text = currentStation?.description
            }
        }

        fun refresh(station: Station?) {
            currentStation = station
            notifyDataSetChanged()
        }
    }

    companion object {
        fun newInstance() = PlayerFullscreenFragment()
    }
}