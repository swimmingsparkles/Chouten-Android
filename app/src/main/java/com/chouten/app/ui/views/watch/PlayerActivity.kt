package com.chouten.app.ui.views.watch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WebStories
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.chouten.app.Mapper
import com.chouten.app.ModuleLayer
import com.chouten.app.PrimaryDataLayer
import com.chouten.app.data.InfoResult
import com.chouten.app.data.ModuleModel
import com.chouten.app.data.ModuleResponse
import com.chouten.app.data.SnackbarVisualsWithError
import com.chouten.app.data.WatchResult
import com.chouten.app.data.WebviewHandler
import com.chouten.app.formatMinSec
import com.chouten.app.ui.components.CustomSlider
import com.chouten.app.ui.components.MaterialSliderDefaults
import com.chouten.app.ui.components.SliderBrushColor
import com.chouten.app.ui.theme.ChoutenTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable

class PlayerActivity : ComponentActivity() {

    var url: String = ""
        private set

    private var isInitialized by mutableStateOf(false)

    private val _webview = WebviewHandler()

    private var _player: ExoPlayer? by mutableStateOf(null)

    private var currentModule: ModuleModel? by mutableStateOf(null)

    private val syncLock = Mutex(false)

    private var _mediaUrl by mutableStateOf("")
    val mediaUrl: String
        get() = _mediaUrl

    private var _mediaTitle: String? by mutableStateOf(null)
    private val mediaTitle: String?
        get() = _mediaTitle

    private var _episodeTitle: String? by mutableStateOf(null)
    private val episodeTitle: String?
        get() = _episodeTitle
    private var _episodeNumber: Float? by mutableStateOf(null)
    private val episodeNumber: Float?
        get() = _episodeNumber

    private var _currentEpisodeIndex: Int? by mutableStateOf(null)
    private val currentEpisodeIndex: Int?
        get() = _currentEpisodeIndex

    private var _servers by mutableStateOf(listOf<WatchResult.Server>())
    val servers: List<WatchResult.Server>
        get() = _servers

    private var _sources by mutableStateOf(listOf<WatchResult.Source>())
    val sources: List<WatchResult.Source>
        get() = _sources

    private var _subtitles by mutableStateOf(listOf<WatchResult.Subtitles>())
    val subtitles: List<WatchResult.Subtitles>
        get() = _subtitles

    private var _skips by mutableStateOf(listOf<WatchResult.SkipTimes>())
    val skips: List<WatchResult.SkipTimes>
        get() = _skips

    private val handler = Handler(Looper.getMainLooper())

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        this.hideSystemUI()
        super.onCreate(savedInstanceState)
        initialize()
        setContent {
            ChoutenTheme {
                // A surface container using the 'background' color from the theme
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    content = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(it)
                                .background(Color.Black)
                        ) {
                            Player(
                                WatchResult(
                                    sources = _sources,
                                    skips = _skips,
                                    subtitles = _subtitles
                                ),
                                context = this@PlayerActivity
                            )
                        }
                    }
                )
            }
        }
    }

    private fun hideSystemUI() {
        actionBar?.hide()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun initialize() {
        println("INITIALIZING PLAYER ACTIVITY")
        currentModule = ModuleLayer.selectedModule ?: throw Exception("No module selected")

        // Both title and url are url-encoded.
        val title = this.intent.getStringExtra("title").also {
            _mediaTitle = it
        }
        this.intent.getStringExtra("episode").also {
            _episodeTitle = it
        }
        this.intent.getFloatExtra("episodeNumber", -1f).also {
            _episodeNumber = it
        }
        // TODO: We need to get the episodes from the intent
//        this.intent.parcelable<>("episodes").also {
//            val episodes = it
//           println("EPISODES: $episodes")
//        }

        this.intent.getIntExtra("currentEpisodeIndex", -1).also {
            _currentEpisodeIndex = it
        }
        val url = this.intent.getStringExtra("url")

        // We want to get the info code from the webview handler
        // and then load the page with that code.
        _webview.initialize(this)
        _webview.updateNextUrl(url)
        currentModule?.subtypes?.forEach { subtype ->
            currentModule!!.code?.get(subtype)?.mediaConsume?.forEach { watchFn ->
                // We need the info function to
                // be executed synchronously
                this.lifecycleScope.launch {
                    syncLock.lock()
                    if (!_webview.load(watchFn)) {
                        return@launch
                    }

                    val res = _webview.inject(watchFn)
                    if (res.isBlank()) {
                        PrimaryDataLayer.enqueueSnackbar(
                            SnackbarVisualsWithError(
                                "No results found for $title", false
                            )
                        )
                        return@launch
                    }

                    try {
                        val results =
                            Mapper.parse<ModuleResponse<List<WatchResult.Server>>>(
                                res
                            )
                        _webview.updateNextUrl(results.nextUrl)
                        println("Results for servers are ${results.result}")

                        _servers = results.result
                    } catch (e: Exception) {
                        try {
                            val results =
                                Mapper.parse<ModuleResponse<WatchResult>>(
                                    res
                                )
                            _webview.updateNextUrl(results.nextUrl)
                            println("Results for watch are ${results.result}")

                            _sources = results.result.sources
                            _subtitles = results.result.subtitles
                            _skips = results.result.skips
                        } catch (e: Exception) {
                            e.printStackTrace()
                            PrimaryDataLayer.enqueueSnackbar(
                                SnackbarVisualsWithError(
                                    "Error parsing results for $title",
                                    false
                                )
                            )
                            syncLock.unlock()
                            return@launch
                        }
                    }
                    syncLock.unlock()
                }
            }
        }
    }

    @SuppressLint("RememberReturnType")
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Composable
    fun Player(watchResult: WatchResult, modifier: Modifier = Modifier, context: Context) {
        println("Player called with $watchResult")

        _player = remember {
            ExoPlayer.Builder(context).build()
        }

        if (watchResult.sources.isNotEmpty() && _player != null) {
            _player?.apply {
                setMediaItem(
                    MediaItem.fromUri(
                        Uri.parse(
                            watchResult.sources[0].file
                        )
                    )
                )
                prepare()
            }
        }

        _player?.playWhenReady = watchResult.sources.isNotEmpty()
        _player?.videoScalingMode =
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        _player?.repeatMode = ExoPlayer.REPEAT_MODE_OFF

        LaunchedEffect((context as Activity).requestedOrientation) {
            context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        val interactionSource = remember { MutableInteractionSource() }

        var duration by remember { mutableStateOf(0L) }
        var currentTime by remember { mutableStateOf(_player?.currentPosition?.coerceAtLeast(0L)) }
        var bufferedPercentage by remember { mutableStateOf(0) }
        var shouldShowControls by remember { mutableStateOf(false) }
        var isPlaying by remember { mutableStateOf(_player?.isPlaying) }
        var isBuffering by remember { mutableStateOf(false) }
        var playbackState by remember { mutableStateOf(_player?.playbackState) }

        Box(modifier = Modifier.fillMaxSize()) {
            DisposableEffect(key1 = Unit) {
                val listener =
                    object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            super.onPlayerError(error)
                            PrimaryDataLayer.enqueueSnackbar(
                                SnackbarVisualsWithError(
                                    "Error playing video",
                                    false
                                )
                            )
                        }

                        override fun onEvents(player: Player, events: Player.Events) {
                            super.onEvents(player, events)
                            duration = player.duration.coerceAtLeast(0L)
                            bufferedPercentage = player.bufferedPercentage
                            isPlaying = player.isPlaying
                            playbackState = player.playbackState

                            if (player.playbackState == Player.STATE_READY && !isInitialized) {
                                isInitialized = true
                                val handler = Handler(Looper.getMainLooper())
                                handler.postDelayed(object : Runnable {
                                    override fun run() {
                                        currentTime = player.currentPosition.coerceAtLeast(0L)
                                        handler.postDelayed(this, 350)
                                    }
                                }, 350)

                            }

                            isBuffering = player.playbackState == Player.STATE_BUFFERING
                        }
                    }

                _player?.addListener(listener)

                onDispose {
                    _player?.removeListener(listener)
                    _player?.release()
                }
            }

            AndroidView(
                modifier = Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    shouldShowControls = !shouldShowControls
                },
                factory = {
                    PlayerView(context).apply {
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                        player = _player
                        useController = false
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                }
            )

            PlayerControls(
                modifier = Modifier.fillMaxSize(),
                title = mediaTitle,
                // episode number is a float, check if it's a whole number and if it is, cast it to an int
                episodeTitle = "${if ((episodeNumber?.rem(1) ?: 0) == 0f) episodeNumber?.toInt() else episodeNumber}: $episodeTitle",
                currentModule = currentModule?.name,
              //  resulotion = _player.trackSelectionParameters?.get(0)?.maxVideoWidth ?: 0,
                onBackClick = {
                    context.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    context.finish()
                },
                isVisible = { shouldShowControls },
                isPlaying = { isPlaying ?: false },
                isBuffering = { isBuffering },
                onReplayClick = { _player?.seekBack() },
                onPauseToggle = {
                    if (isPlaying == true) _player?.pause()
                    else _player?.play()
                },
                onForwardClick = { _player?.seekForward() },
                duration = { duration },
                currentTime = { currentTime ?: 0L },
                bufferPercentage = { bufferedPercentage },
                onSeekChanged = { _player?.seekTo(it.toLong()) }
            )
        }
    }

    override fun onPause() {
        _player?.pause()
        super.onPause()
    }

    override fun onStop() {
        _player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        _player?.release()
        _webview.destroy()

        super.onDestroy()
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    title: String?,
    episodeTitle: String?,
    currentModule: String?,
    onBackClick: () -> Unit,
    isVisible: () -> Boolean,
    isPlaying: () -> Boolean,
    isBuffering: () -> Boolean,
    onReplayClick: () -> Unit,
    onPauseToggle: () -> Unit,
    onForwardClick: () -> Unit,
    duration: () -> Long,
    currentTime: () -> Long,
    bufferPercentage: () -> Int,
    onSeekChanged: (timeMs: Float) -> Unit
) {
    val visible = remember(isVisible()) {
        isVisible()
    }

    AnimatedVisibility(
        modifier = modifier,
        visible = visible || isBuffering(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(start = 20.dp, end = 20.dp)
        ) {
            TopControls(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .animateEnterExit(
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ),
                title = title ?: "",
                episodeTitle = episodeTitle,
                currentModule = currentModule,
                onBackClick = onBackClick
            )

            CenterControls(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                onReplayClick = onReplayClick,
                onPauseToggle = onPauseToggle,
                onForwardClick = onForwardClick,
            )

            BottomControls(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .animateEnterExit(
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ),
                duration = duration,
                currentTime = currentTime,
                bufferPercentage = bufferPercentage,
                onSeekChanged = onSeekChanged,
            )

        }
    }
}

@Composable
fun TopControls(
    modifier: Modifier = Modifier,
    title: String?,
    episodeTitle: String?,
    currentModule: String?,
    onBackClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // back button
        IconButton(modifier = Modifier.size(40.dp), onClick = onBackClick) {
            Icon(
                modifier = Modifier.fillMaxSize(),
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = "Back",
            )
        }

        // column with episode title and anime title
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .align(Alignment.CenterVertically)
        ) {
            if (episodeTitle != null) {
                Text(
                    text = episodeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Module Name, with the resolution
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .align(Alignment.CenterVertically),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = currentModule ?: "No Module",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "1920x1080",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CenterControls(
    modifier: Modifier = Modifier,
    isPlaying: () -> Boolean,
    isBuffering: () -> Boolean,
    onReplayClick: () -> Unit,
    onPauseToggle: () -> Unit,
    onForwardClick: () -> Unit
) {

    val isVideoPlaying = remember(isPlaying()) {
        isPlaying()
    }

    //black overlay across the video player
    Box(modifier = modifier.background(Color.Transparent)) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            //replay button
            IconButton(modifier = Modifier.size(40.dp), onClick = onReplayClick) {
                Icon(
                    modifier = Modifier.fillMaxSize(),
                    imageVector = Icons.Rounded.Replay,
                    contentDescription = "Replay"
                )
            }

            Spacer(modifier = Modifier.width(90.dp))



            Box {
                // somehow if you don't use the fully qualified name, it doesn't work :/
                androidx.compose.animation.AnimatedVisibility(
                    visible = isBuffering(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(80.dp)
                            .zIndex(1f),
                        strokeWidth = 4.dp,
                        color = Color.White
                    )
                }

                //pause/play toggle button
                IconButton(modifier = Modifier.size(80.dp), onClick = onPauseToggle) {
                    Icon(
                        modifier = Modifier.fillMaxSize(),
                        imageVector = if (isVideoPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Replay"
                    )
                }

            }
            Spacer(modifier = Modifier.width(90.dp))

            //forward button
            IconButton(
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        // flip the replay icon to make it a forward icon
                        rotationY = 180f
                    }, onClick = onForwardClick
            ) {
                Icon(
                    modifier = Modifier.fillMaxSize(),
                    imageVector = Icons.Rounded.Replay,
                    contentDescription = "Replay"
                )
            }
        }
    }
}

@Composable
fun BottomControls(
    modifier: Modifier = Modifier,
    duration: () -> Long,
    currentTime: () -> Long,
    bufferPercentage: () -> Int,
    onSeekChanged: (timeMs: Float) -> Unit
) {
    val duration = remember(duration()) { duration() }
    val videoTime = remember(currentTime()) { currentTime() }
    val buffer = remember(bufferPercentage()) { bufferPercentage() }

    val interactionSource = MutableInteractionSource()
    var isBeingDragged by remember { mutableStateOf(false) }

    val sliderHeight by animateDpAsState(
        if (isBeingDragged) 10.dp
        else 6.dp,
        animationSpec = tween(
            durationMillis = 1000, easing = LinearEasing
        )
    )

    Column(modifier = modifier.padding(bottom = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                modifier = Modifier,
                text = "${videoTime.formatMinSec()} / ${duration.formatMinSec()}",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(
                    modifier = Modifier.size(40.dp),
                    onClick = {},
                ) {
                    Icon(
                        modifier = Modifier.fillMaxSize(0.70F),
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = "Replay",
                    )
                }

                IconButton(
                    modifier = Modifier
                        .size(40.dp)
                        .rotate(-90F),
                    onClick = {},
                ) {
                    Icon(
                        modifier = Modifier.fillMaxSize(0.70F),
                        imageVector = Icons.Rounded.WebStories,
                        contentDescription = "Replay",
                    )
                }

                IconButton(
                    modifier = Modifier.size(40.dp),
                    onClick = {},
                ) {
                    Icon(
                        modifier = Modifier.fillMaxSize(0.70F),
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Replay",
                    )
                }

                IconButton(
                    modifier = Modifier.size(40.dp),
                    onClick = {}
                ) {
                    Icon(
                        modifier = Modifier.fillMaxSize(),
                        imageVector = Icons.Rounded.FastForward,
                        contentDescription = "Replay"
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        ) {
            // buffer slider
            CustomSlider(
                value = buffer.toFloat(),
                onValueChange = { _, _ -> },
                valueRange = 0f..100f,
                trackHeight = sliderHeight,
                thumbRadius = 0.dp,
                colors = MaterialSliderDefaults.customColors(
                    activeTrackColor = SliderBrushColor(color = Color.White.copy(alpha = 0.4F)),
                    inactiveTrackColor = SliderBrushColor(color = Color.White.copy(alpha = 0.4F)),
                    thumbColor = SliderBrushColor(color = Color.Transparent),
                    activeTickColor = SliderBrushColor(color = Color.Transparent),
                    inactiveTickColor = SliderBrushColor(color = Color.Transparent),
                    disabledActiveTrackColor = SliderBrushColor(color = Color.Transparent),
                    disabledInactiveTrackColor = SliderBrushColor(color = Color.Transparent),
                    disabledThumbColor = SliderBrushColor(color = Color.Transparent),
                    disabledActiveTickColor = SliderBrushColor(color = Color.Transparent),
                    disabledInactiveTickColor = SliderBrushColor(color = Color.Transparent),
                ),
            )

            // seek bar
            CustomSlider(
                value = videoTime.toFloat(),
                onValueChange = { float, _ ->
                    isBeingDragged = true
                    onSeekChanged(float)
                },
                onValueChangeFinished = { isBeingDragged = false },
                valueRange = 0f..duration.toFloat(),
                trackHeight = sliderHeight,
                thumbRadius = 0.dp,
                colors = MaterialSliderDefaults.customColors(
                    activeTrackColor = SliderBrushColor(color = MaterialTheme.colorScheme.primary),
                    inactiveTrackColor = SliderBrushColor(color = Color.Transparent),
                    thumbColor = SliderBrushColor(color = Color.Transparent),
                    activeTickColor = SliderBrushColor(color = Color.Transparent),
                    inactiveTickColor = SliderBrushColor(color = Color.Transparent),
                    disabledActiveTrackColor = SliderBrushColor(color = Color.Transparent),
                    disabledInactiveTrackColor = SliderBrushColor(color = Color.Transparent),
                    disabledThumbColor = SliderBrushColor(color = Color.Transparent),
                    disabledActiveTickColor = SliderBrushColor(color = Color.Transparent),
                    disabledInactiveTickColor = SliderBrushColor(color = Color.Transparent),
                ),
            )
        }
    }
}