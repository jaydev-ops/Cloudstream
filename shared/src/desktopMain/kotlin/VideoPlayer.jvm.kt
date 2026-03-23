

// --- 1. COMPOSE & KOTLIN IMPORTS ---
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

// --- 2. VLCJ IMPORTS ---
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat

@Composable
actual fun VideoPlayer(
    modifier: Modifier,
    url: String,
    title: String,
    currentEpisode: String,
    nextEpisode: String?,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit
) {
    // STATE
    var vlcStatus by remember { mutableStateOf<Boolean?>(null) }
    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }

    // UI CONTROLS STATE
    var isPlaying by remember { mutableStateOf(true) }
    var currentTime by remember { mutableStateOf(0L) }
    var totalTime by remember { mutableStateOf(1L) }
    var showControls by remember { mutableStateOf(true) }

    // PLAYER REFERENCE (For controls)
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    // SAFETY LOCK (Prevents Crash on Exit)
    val isDisposed = remember { AtomicBoolean(false) }

    // INITIALIZE VLC
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            vlcStatus = NativeDiscovery().discover()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
    ) {
        when (vlcStatus) {
            null -> CircularProgressIndicator(color = Color.Red, modifier = Modifier.align(Alignment.Center))
            false -> Text("❌ VLC Engine Not Found", color = Color.Red, modifier = Modifier.align(Alignment.Center))
            true -> {
                // --- PLAYER ENGINE SETUP ---
                DisposableEffect(url) {
                    val width = 1920
                    val height = 1080
                    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

                    val component = CallbackMediaPlayerComponent(
                        null, null, null, false, null,
                        object : RenderCallback {
                            override fun display(mediaPlayer: MediaPlayer?, nativeBuffers: Array<out ByteBuffer>?, bufferFormat: BufferFormat?) {
                                // 🛑 STOP IF CLOSING
                                if (isDisposed.get()) return
                                try {
                                    if (nativeBuffers != null && nativeBuffers.isNotEmpty()) {
                                        val buffer = nativeBuffers[0]
                                        val intBuffer = buffer.asIntBuffer()
                                        val pixels = IntArray(width * height)
                                        intBuffer.get(pixels)
                                        img.setRGB(0, 0, width, height, pixels, 0, width)
                                        currentFrame = img.toComposeImageBitmap()
                                    }
                                } catch (e: Exception) { /* Ignore errors during shutdown */ }
                            }
                        },
                        object : BufferFormatCallback {
                            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                                return RV32BufferFormat(width, height)
                            }
                            override fun allocatedBuffers(buffers: Array<out ByteBuffer>?) {}
                        },
                        null
                    )

                    val player = component.mediaPlayer()
                    mediaPlayerRef = player
                    player.media().play(url)
                    isPlaying = true

                    // 🛑 CLEANUP BLOCK (The Anti-Crash Fix)
                    onDispose {
                        isDisposed.set(true) // 1. Lock memory immediately

                        // 2. Stop playback gently
                        try {
                            if (player.status().isPlaying) {
                                player.controls().stop()
                            }
                        } catch (e: Exception) {}

                        // 3. Release resources in BACKGROUND thread
                        // This prevents the UI from freezing/crashing while VLC cleans up
                        thread {
                            try {
                                component.release()
                            } catch (e: Exception) {
                                println("Error releasing VLC: ${e.message}")
                            }
                        }
                    }
                }

                // --- TIMER SYNC LOOP ---
                LaunchedEffect(mediaPlayerRef) {
                    while (isActive) {
                        mediaPlayerRef?.let { player ->
                            if (player.status().isPlaying) {
                                currentTime = player.status().time()
                                totalTime = player.status().length().coerceAtLeast(1)
                                isPlaying = true
                            } else {
                                isPlaying = false
                            }
                        }
                        delay(500)
                    }
                }

                // --- UI LAYER: VIDEO ---
                if (currentFrame != null) {
                    Image(
                        bitmap = currentFrame!!,
                        contentDescription = "Video",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
                }

                // --- UI LAYER: CONTROLS ---
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {

                        // TOP BAR
                        Row(
                            modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                            }
                            Column(modifier = Modifier.padding(start = 16.dp)) {
                                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(currentEpisode, color = Color.Gray, fontSize = 14.sp)
                            }
                        }

                        // CENTER BUTTONS
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(32.dp)
                        ) {
                            // SKIP BACK
                            IconButton(onClick = { mediaPlayerRef?.controls()?.skipTime(-10000) }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ArrowBack, "-10s", tint = Color.White)
                                    Text("-10s", color = Color.White, fontSize = 10.sp)
                                }
                            }

                            // PLAY/PAUSE
                            IconButton(
                                onClick = {
                                    mediaPlayerRef?.let {
                                        if (it.status().isPlaying) it.controls().pause() else it.controls().play()
                                        isPlaying = !isPlaying
                                    }
                                },
                                modifier = Modifier.size(72.dp).background(Color.Red, CircleShape)
                            ) {
                                if (isPlaying) {
                                    Canvas(modifier = Modifier.size(24.dp)) {
                                        drawRect(Color.White, topLeft = Offset(0f, 0f), size = Size(8.dp.toPx(), 24.dp.toPx()))
                                        drawRect(Color.White, topLeft = Offset(14.dp.toPx(), 0f), size = Size(8.dp.toPx(), 24.dp.toPx()))
                                    }
                                } else {
                                    Icon(Icons.Default.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(48.dp))
                                }
                            }

                            // SKIP FORWARD
                            IconButton(onClick = { mediaPlayerRef?.controls()?.skipTime(10000) }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ArrowForward, "+10s", tint = Color.White)
                                    Text("+10s", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }

                        // BOTTOM BAR
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onPrev) {
                                    Icon(Icons.Default.ArrowBack, "Prev Ep", tint = Color.Gray)
                                }

                                if (nextEpisode != null) {
                                    Button(
                                        onClick = onNext,
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text("Next: $nextEpisode", color = Color.White)
                                        Icon(Icons.Default.ArrowForward, null, tint = Color.White)
                                    }
                                }
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatTime(currentTime), color = Color.White, fontSize = 12.sp)
                                Text(formatTime(totalTime), color = Color.White, fontSize = 12.sp)
                            }

                            Slider(
                                value = currentTime.toFloat(),
                                valueRange = 0f..totalTime.toFloat(),
                                onValueChange = { newTime ->
                                    currentTime = newTime.toLong()
                                    mediaPlayerRef?.controls()?.setTime(newTime.toLong())
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.Red,
                                    activeTrackColor = Color.Red,
                                    inactiveTrackColor = Color.Gray
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// HELPER FUNCTION (Must be outside the VideoPlayer function)
fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}