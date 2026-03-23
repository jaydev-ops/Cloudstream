

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun VideoPlayer(
    modifier: Modifier,
    url: String,
    title: String,          // 👈 We added this
    currentEpisode: String, // 👈 We added this
    nextEpisode: String?,   // 👈 We added this
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit     // 👈 We added this
)