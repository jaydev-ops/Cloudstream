import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ⚠️ Make sure VideoPlayer is imported correctly.

@Composable
fun App(extraPlugins: List<CloudStreamPlugin> = emptyList())
{
    MaterialTheme(
        colors = darkColors(
            primary = Color(0xFFE50914),
            background = Color(0xFF141414),
            surface = Color(0xFF1F1F1F),
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        val scope = rememberCoroutineScope()

        // 1. ✅ INIT MANAGER FIRST
        val pluginManager = remember {
            PluginManager().apply {
                extraPlugins.forEach { registerPlugin(it)}
            }
        }

        // 2. ✅ CREATE SAFETY SNAPSHOT
        var safePluginList by remember { mutableStateOf(pluginManager.activePlugins.toList()) }

        // 3. ✅ RESTORE & UPDATE SNAPSHOT
        LaunchedEffect(Unit) {
            pluginManager.restorePlugins()
            safePluginList = pluginManager.activePlugins.toList()
        }

        // DIALOG STATES
        var showImportDialog by remember { mutableStateOf(false) }
        var importUrl by remember { mutableStateOf("") }
        var importStatus by remember { mutableStateOf("") }
        var showAddRepoDialog by remember { mutableStateOf(false) }
        var showManageReposDialog by remember { mutableStateOf(false) }

        // APP STATES
        var selectedMovie by remember { mutableStateOf<Movie?>(null) }
        var activeTab by remember { mutableStateOf(ContentType.MOVIE) }
        var searchQuery by remember { mutableStateOf("") }
        var isPlayingVideo by remember { mutableStateOf(false) }

        // LOGIC STATES
        var isScraping by remember { mutableStateOf(false) }
        var debugLog by remember { mutableStateOf("Ready to search...\n") }

        var videoUrl by remember { mutableStateOf("") }
        var currentSource by remember { mutableStateOf("Unknown") }

        val favoritesManager = remember { FavoritesManager() }
        val favorites = remember { mutableStateListOf<Movie>() }

        // ⚡️ DEBUG CLOUDSTREAM ENGINE
        LaunchedEffect(isScraping) {
            if (isScraping && selectedMovie != null) {
                debugLog = "🚀 Starting Search for: ${selectedMovie!!.title}\n"
                delay(100)

                try {
                    debugLog += "🔍 Asking PluginManager...\n"
                    val results = pluginManager.searchAll(selectedMovie!!.title)
                    debugLog += "✅ Found ${results.size} total results.\n"

                    if (results.isNotEmpty()) {
                        val bestMatch = results.first()
                        debugLog += "🎯 Best match: ${bestMatch.title} (Source: ${bestMatch.sourceName})\n"
                        val provider = pluginManager.activePlugins.find { it.name == bestMatch.sourceName }

                        if (provider != null) {
                            debugLog += "🔌 Using provider: ${provider.name}\n"
                            debugLog += "⛓ Fetching stream link...\n"
                            val links = provider.loadLinks(bestMatch.url)
                            debugLog += "📦 Links found: ${links.size}\n"

                            if (!links.isNullOrEmpty()) {
                                val stream = links.first()
                                videoUrl = stream.url
                                currentSource = "${provider.name} (${stream.quality})"
                                debugLog += "▶️ Playing URL: $videoUrl\n"
                                delay(1000)
                                isPlayingVideo = true
                                isScraping = false
                            } else {
                                debugLog += "❌ Error: Provider returned no links.\n"
                                delay(3000)
                                isScraping = false
                            }
                        } else {
                            debugLog += "❌ Error: Could not find provider instance.\n"
                            delay(3000)
                            isScraping = false
                        }
                    } else {
                        debugLog += "❌ No results found. Check Internet or Plugin URL.\n"
                        delay(3000)
                        isScraping = false
                    }
                } catch (e: Exception) {
                    debugLog += " CRITICAL ERROR: ${e.message}\n"
                    e.printStackTrace()
                    delay(5000)
                    isScraping = false
                }
            }
        }

        LaunchedEffect(Unit) {
            val saved = favoritesManager.loadFavorites()
            favorites.addAll(saved)
        }

        // 📦 MAIN UI CONTAINER (Added Box to layer the Watermark)
        Box(modifier = Modifier.fillMaxSize()) {

            // --- MAIN UI SWITCHER ---
            if (isPlayingVideo) {
                VideoPlayer(
                    modifier = Modifier.fillMaxSize(),
                    url = videoUrl,
                    title = selectedMovie?.title ?: "Now Playing",
                    currentEpisode = "Source: $currentSource",
                    nextEpisode = null,
                    onNext = {},
                    onPrev = {},
                    onClose = { isPlayingVideo = false }
                )
            } else if (isScraping) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                        CircularProgressIndicator(color = Color.Red)
                        Spacer(modifier = Modifier.height(20.dp))
                        Text("DEBUG MODE", color = Color.Red, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(Color(0xFF222222), RoundedCornerShape(8.dp)).padding(10.dp)) {
                            Text(text = debugLog, color = Color.Green, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = { isScraping = false }) { Text("Cancel") }
                    }
                }
            } else if (selectedMovie == null) {
                CatalogScreen(
                    activeTab = activeTab,
                    searchQuery = searchQuery,
                    favorites = favorites,
                    onTabSelected = { activeTab = it },
                    onSearchChanged = { searchQuery = it },
                    onMovieClick = { selectedMovie = it },
                    onAddRepoClick = { showAddRepoDialog = true },
                    onManageReposClick = { showManageReposDialog = true },
                    onImportClick = { showImportDialog = true }
                )
            } else {
                DetailsScreen(
                    movie = selectedMovie!!,
                    activeTab = activeTab,
                    isFavorite = favorites.any { it.id == selectedMovie!!.id },
                    onToggleFavorite = {
                        if (favorites.any { it.id == selectedMovie!!.id }) favorites.removeAll { it.id == selectedMovie!!.id }
                        else favorites.add(selectedMovie!!)
                        favoritesManager.saveFavorites(favorites)
                    },
                    onBack = { selectedMovie = null },
                    onPlay = { isScraping = true }
                )
            }

            // 🖋️ COPYRIGHT WATERMARK (JAYESH YADAV)
            // This sits on top of all screens (Z-Index)
            Text(
                text = "Architected by © Jayesh Yadav",
                color = Color.White.copy(alpha = 0.3f), // Subtle watermark opacity
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.BottomEnd) // Bottom Right Corner
                    .padding(16.dp)
                    .zIndex(99f) // Always on top
            )
        }

        // 🔌 DIALOGS (Kept outside the logic flow, but inside composition)
        if (showAddRepoDialog) {
            var name by remember { mutableStateOf("ToonStream") }
            var url by remember { mutableStateOf("https://archive.org/advancedsearch.php?q=%query%+AND+collection:animationandcartoons&sort[]=downloads+desc&output=json") }
            var regex by remember { mutableStateOf("\"identifier\":\"([^\"]+)\"") }
            var urlPattern by remember { mutableStateOf("https://archive.org/download/%id%/%id%.mp4") }

            AlertDialog(
                onDismissRequest = { showAddRepoDialog = false },
                backgroundColor = Color(0xFF222222), contentColor = Color.White,
                title = { Text("Add New Plugin 🔌") },
                text = {
                    Column {
                        Text("Name:", fontSize = 12.sp, color = Color.Gray); TextField(value = name, onValueChange = { name = it }, textStyle = TextStyle(color = Color.White))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Search URL:", fontSize = 12.sp, color = Color.Gray); TextField(value = url, onValueChange = { url = it }, textStyle = TextStyle(color = Color.White))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Regex Pattern:", fontSize = 12.sp, color = Color.Gray); TextField(value = regex, onValueChange = { regex = it }, textStyle = TextStyle(color = Color.White))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Link Builder:", fontSize = 12.sp, color = Color.Gray); TextField(value = urlPattern, onValueChange = { urlPattern = it }, textStyle = TextStyle(color = Color.White))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val finalPattern = if (urlPattern.isBlank()) null else urlPattern
                        pluginManager.addCustomPlugin(name, url, regex, finalPattern)
                        safePluginList = pluginManager.activePlugins.toList()
                        showAddRepoDialog = false
                    }) { Text("Add") }
                }
            )
        }

        if (showManageReposDialog) {
            AlertDialog(
                onDismissRequest = { showManageReposDialog = false },
                backgroundColor = Color(0xFF222222), contentColor = Color.White,
                title = { Text("Active Repositories (${pluginManager.activePlugins.size})") },
                text = {
                    Column(modifier = Modifier.height(300.dp).width(300.dp)) {
                        Text("Plugins run top to bottom.", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            pluginManager.activePlugins.clear()
                            saveTextData("installed_repos.txt", "")
                            safePluginList = emptyList()
                        }) { Text("⚠️ Factory Reset Plugins") }
                        LazyColumn {
                            items(safePluginList) { plugin ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("🔌 ${plugin.name}", fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Active", fontSize = 10.sp, color = Color.Green)
                                    }
                                    IconButton(onClick = {
                                        pluginManager.removePlugin(plugin)
                                        safePluginList = pluginManager.activePlugins.toList()
                                    }) { Icon(Icons.Default.Delete, "Remove", tint = Color.Red) }
                                }
                                Divider(color = Color.DarkGray)
                            }
                        }
                    }
                },
                confirmButton = { Button(onClick = { showManageReposDialog = false }) { Text("Close") } }
            )
        }

        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                backgroundColor = Color(0xFF222222), contentColor = Color.White,
                title = { Text("Import Repository 📦") },
                text = {
                    Column {
                        Text("Paste Repository JSON URL:", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = importUrl, onValueChange = { importUrl = it },
                            label = { Text("URL", color = Color.Gray) },
                            textStyle = TextStyle(color = Color.White),
                            colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color.Red, unfocusedBorderColor = Color.Gray, cursorColor = Color.Red),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (importStatus.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = importStatus, color = if (importStatus.startsWith("Success")) Color.Green else Color.Red, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            importStatus = "Downloading..."
                            scope.launch {
                                importStatus = pluginManager.installRepository(importUrl)
                                safePluginList = pluginManager.activePlugins.toList()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
                    ) { Text("Install", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { showImportDialog = false }) { Text("Close", color = Color.Gray) } }
            )
        }
    }
}

// ... The rest of your Screens (CatalogScreen, DetailsScreen, etc.) stay exactly the same ...
// Paste the rest of the file here as normal.

@Composable
fun CatalogScreen(
    activeTab: ContentType,
    searchQuery: String,
    favorites: List<Movie>,
    onTabSelected: (ContentType) -> Unit,
    onSearchChanged: (String) -> Unit,
    onMovieClick: (Movie) -> Unit,
    onAddRepoClick: () -> Unit,
    onManageReposClick: () -> Unit,
    onImportClick: () -> Unit
) {
    val repo = remember { TmdbRepository() }
    var trending by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var masterpieces by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var mustWatch by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var underrated by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var isSearchExpanded by remember { mutableStateOf(searchQuery.isNotEmpty() && searchQuery != "MY_FAVORITES_SECRET_CODE") }

    LaunchedEffect(activeTab) {
        trending = emptyList(); masterpieces = emptyList(); mustWatch = emptyList()
        repo.getList(Category.TRENDING, activeTab).onSuccess { trending = it; underrated = it.shuffled().take(5) }
        repo.getList(Category.TOP_RATED, activeTab).onSuccess { masterpieces = it }
        repo.getList(Category.POPULAR, activeTab).onSuccess { mustWatch = it }
    }

    LaunchedEffect(searchQuery, activeTab) {
        if (searchQuery.isNotEmpty() && searchQuery != "MY_FAVORITES_SECRET_CODE") {
            delay(500)
            repo.search(searchQuery, activeTab).onSuccess { searchResults = it }
        }
    }

    Scaffold(
        backgroundColor = MaterialTheme.colors.background,
        topBar = {
            TopAppBar(
                backgroundColor = Color.Transparent, elevation = 0.dp,
                navigationIcon = if (searchQuery == "MY_FAVORITES_SECRET_CODE") {
                    { IconButton(onClick = { onSearchChanged("") }) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } }
                } else null,
                title = {
                    if (searchQuery == "MY_FAVORITES_SECRET_CODE") {
                        Text("My List ❤️", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    } else if (!isSearchExpanded) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ContentType.values().forEach { type ->
                                Text(
                                    text = type.name, fontSize = 14.sp,
                                    fontWeight = if (activeTab == type) FontWeight.Bold else FontWeight.Normal,
                                    color = if (activeTab == type) Color.Red else Color.Gray,
                                    modifier = Modifier.padding(end = 20.dp).clickable { onTabSelected(type) }
                                )
                            }
                            Spacer(modifier = Modifier.width(20.dp))
                            Text("MY LIST", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Yellow, modifier = Modifier.clickable { onSearchChanged("MY_FAVORITES_SECRET_CODE") })
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onAddRepoClick) { Icon(Icons.Default.Add, "Add Repo", tint = Color.White) }
                    IconButton(onClick = onManageReposClick) { Icon(Icons.Default.List, "Manage Repos", tint = Color.Gray) }
                    IconButton(onClick = onImportClick) { Icon(Icons.Default.ArrowDropDown, "Import", tint = Color.Red) }
                    if (searchQuery != "MY_FAVORITES_SECRET_CODE") {
                        AnimatedVisibility(visible = isSearchExpanded) {
                            TextField(
                                value = searchQuery, onValueChange = onSearchChanged, placeholder = { Text("Search...", color = Color.Gray) },
                                singleLine = true, colors = TextFieldDefaults.textFieldColors(backgroundColor = Color(0xFF222222), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, textColor = Color.White, cursorColor = Color.Red),
                                shape = RoundedCornerShape(8.dp), modifier = Modifier.width(200.dp).height(50.dp)
                            )
                        }
                        IconButton(onClick = { if (isSearchExpanded && searchQuery.isNotEmpty()) onSearchChanged("") else isSearchExpanded = !isSearchExpanded }) {
                            Icon(if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search, "Search", tint = Color.White)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (searchQuery == "MY_FAVORITES_SECRET_CODE") {
            if (favorites.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No favorites yet! ❤️", color = Color.Gray) }
            else LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(padding)) { items(favorites) { movie -> MovieCard(movie, onClick = { onMovieClick(movie) }) } }
        } else if (searchQuery.isNotEmpty()) {
            LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(padding)) { items(searchResults) { movie -> MovieCard(movie, onClick = { onMovieClick(movie) }) } }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                if (underrated.isNotEmpty()) item { HeroCarousel(movies = underrated, onMovieClick = onMovieClick) }
                item { ContentShelf("🔥 Trending Now", trending, onMovieClick) }
                item { ContentShelf("💎 Masterpieces", masterpieces, onMovieClick) }
                item { ContentShelf("🍿 Must Watch", mustWatch, onMovieClick) }
            }
        }
    }
}

@Composable
fun DetailsScreen(
    movie: Movie,
    activeTab: ContentType,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onPlay: () -> Unit
) {
    var fullDetails by remember { mutableStateOf(movie) }
    val repo = remember { TmdbRepository() }

    LaunchedEffect(movie.id) {
        val type = if (activeTab == ContentType.ANIME) ContentType.SERIES else activeTab
        repo.getDetails(movie.id, type).onSuccess { fullDetails = it }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        KamelImage(
            resource = asyncPainterResource(fullDetails.fullPosterUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(radius = 50.dp).alpha(0.4f)
        )
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colors.background.copy(0.5f), MaterialTheme.colors.background), startY = 0f, endY = 1000f)))

        Scaffold(
            backgroundColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    backgroundColor = Color.Transparent, elevation = 0.dp, title = {},
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                Spacer(modifier = Modifier.height(60.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp), verticalAlignment = Alignment.Bottom) {
                    Card(shape = RoundedCornerShape(12.dp), elevation = 12.dp, modifier = Modifier.width(200.dp).aspectRatio(0.67f)) {
                        KamelImage(resource = asyncPainterResource(fullDetails.fullPosterUrl), contentDescription = null, contentScale = ContentScale.Crop)
                    }
                    Spacer(modifier = Modifier.width(32.dp))
                    Column(modifier = Modifier.weight(1f).padding(bottom = 12.dp)) {
                        Text(fullDetails.displayTitle, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, lineHeight = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Badge(fullDetails.displayDate.take(4))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("⭐ ${fullDetails.voteAverage ?: "N/A"}", color = Color.Yellow, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = onPlay,
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Play Now", color = Color.White, fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            OutlinedButton(
                                onClick = onToggleFavorite,
                                shape = CircleShape,
                                border = BorderStroke(2.dp, Color.White),
                                colors = ButtonDefaults.outlinedButtonColors(backgroundColor = Color.Transparent),
                                modifier = Modifier.size(50.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(text = if (isFavorite) "❤️" else "🤍", fontSize = 20.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
                Column(modifier = Modifier.padding(horizontal = 40.dp)) {
                    Text("Overview", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(fullDetails.description, color = Color(0xFFCCCCCC), lineHeight = 28.sp, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(40.dp))
                if (fullDetails.credits?.cast?.isNotEmpty() == true) {
                    Text("Cast", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 40.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(fullDetails.credits!!.cast.take(10)) { castMember -> CastCard(castMember) }
                    }
                }
            }
        }
    }
}

@Composable
fun HeroCarousel(movies: List<Movie>, onMovieClick: (Movie) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            if (movies.isNotEmpty()) {
                val nextIndex = (listState.firstVisibleItemIndex + 1) % movies.size
                listState.animateScrollToItem(nextIndex)
            }
        }
    }
    LazyRow(state = listState, modifier = Modifier.height(500.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        items(movies) { movie ->
            Box(modifier = Modifier.fillParentMaxWidth().fillMaxHeight().clickable { onMovieClick(movie) }) {
                KamelImage(resource = asyncPainterResource(movie.fullPosterUrl), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)), startY = 200f)))
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(text = movie.displayTitle, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row { Badge("Trending"); Spacer(modifier = Modifier.width(8.dp)); Text("⭐ ${movie.voteAverage ?: "N/A"}", color = Color.Yellow, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun ContentShelf(title: String, movies: List<Movie>, onMovieClick: (Movie) -> Unit) {
    if (movies.isNotEmpty()) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(movies) { movie ->
                    Column(modifier = Modifier.width(130.dp).clickable { onMovieClick(movie) }) {
                        Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.height(190.dp).fillMaxWidth()) {
                            KamelImage(resource = asyncPainterResource(movie.fullPosterUrl), contentDescription = null, contentScale = ContentScale.Crop)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(movie.displayTitle, color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun MovieCard(movie: Movie, onClick: () -> Unit) {
    Card(modifier = Modifier.padding(8.dp).fillMaxWidth().height(250.dp).clickable { onClick() }, shape = RoundedCornerShape(8.dp), elevation = 4.dp) {
        KamelImage(resource = asyncPainterResource(movie.fullPosterUrl), contentDescription = movie.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun Badge(text: String) {
    Surface(color = Color(0xFF333333), shape = RoundedCornerShape(4.dp)) {
        Text(text = text, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
fun CastCard(member: CastMember) {
    Column(modifier = Modifier.width(100.dp).padding(end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(shape = CircleShape, modifier = Modifier.size(80.dp)) {
            KamelImage(resource = asyncPainterResource(member.fullProfileUrl), contentDescription = member.name, contentScale = ContentScale.Crop)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(member.name, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}