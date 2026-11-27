package xendroid.compose.ui.library

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import xendroid.compose.core.AllFilesAccess
import xendroid.compose.core.EmuProcessLink
import xendroid.compose.data.Game
import xendroid.compose.data.GameFormat
import xendroid.compose.ui.compress.GameCompressViewModel
import xendroid.compose.ui.compress.GameCompressViewModel.CompressState
import xendroid.compose.ui.userdata.openUserData
import xendroid.compose.updater.CooldownDialog
import xendroid.compose.updater.getRemainingCooldown
import xendroid.compose.updater.LatestVersionDialog
import xendroid.compose.updater.UpdateDialog
import xendroid.compose.updater.UpdateResult
import xendroid.compose.updater.checkForUpdates
import xendroid.compose.updater.shouldCheckForUpdates
import xendroid.compose.updater.saveLastCheck

// ---- XenDroid Nightly look: black, neon red, white -------------------------------------

private val NeonRed = Color(0xFFFF1E1E)
private val DeepRed = Color(0xFF8B0000)
private val PanelBlack = Color(0xE6080808)

/** Hexagon-ish button shape used by the menu and the Play button. */
private val HexShape = GenericShape { size, _ ->
    val c = size.height * 0.3f
    moveTo(c, 0f)
    lineTo(size.width - c, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - c, size.height)
    lineTo(c, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

private data class MenuEntry(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameLibraryScreen(
    viewModel: GameLibraryViewModel,
    onOpenSettings: () -> Unit,
    onOpenKeymap: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenTouchControls: () -> Unit,
    onOpenPerGameSettings: (titleId: String, gameName: String, format: GameFormat, launchUri: String) -> Unit,
    onOpenGamePatches: (titleId: String, gameName: String) -> Unit,
    onOpenContentManager: (titleId: String, gameName: String) -> Unit,
    onOpenInstallContent: () -> Unit,
    onInstallFromDisc: (String) -> Unit,
    compressVm: GameCompressViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }

    var pendingGame by remember { mutableStateOf<Game?>(null) }
    // A disc whose content is not installed yet; the launch waits on the answer.
    var pendingDiscInstall by remember { mutableStateOf<Pair<Game, Int>?>(null) }
    var compressConfirmFor by remember { mutableStateOf<Game?>(null) }
    val compressState by compressVm.state.collectAsStateWithLifecycle()
    val titleIdState by viewModel.titleIdState.collectAsStateWithLifecycle()

    var showBrowser by remember { mutableStateOf(false) }
    var allFilesGranted by remember { mutableStateOf(AllFilesAccess.isGranted()) }
    // Not-yet-granted sends the user to Settings; the grant returns no result, so it is
    // observed on the next ON_START.
    val startRealPathMode: () -> Unit = {
        if (AllFilesAccess.isGranted()) showBrowser = true
        else AllFilesAccess.requestAccess(context)
    }

    // Re-scan on return to foreground to pick up games added while backgrounded. The
    // ViewModel's init does the first cold-start load, so the first ON_START is skipped.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var firstStart = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                allFilesGranted = AllFilesAccess.isGranted()
                if (firstStart) firstStart = false else viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showBrowser) {
        FolderBrowserScreen(
            onFolderChosen = { path ->
                showBrowser = false
                viewModel.onRealPathFolderPicked(path)
            },
            onCancel = { showBrowser = false },
        )
        return
    }

    var menuOpen by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val loadedGames = (state as? LibraryUiState.Loaded)?.games.orEmpty()
    val selectedGame = loadedGames.firstOrNull { it.stableId == selectedId }
        ?: loadedGames.firstOrNull()
    val setFolderLabel = if (allFilesGranted) "Set game folder" else "Grant All Files Access"

    val launch: (Game) -> Unit = { game ->
        scope.launch {
            // A mandatory-install disc is still bootable, so this asks
            // rather than diverting the launch on its own.
            val pending = viewModel.uninstalledDiscContent(game)
            if (pending.isNotEmpty()) {
                pendingDiscInstall = game to pending.size
            } else {
                launchGame(context, viewModel, game)
            }
        }
    }

    XenBackground(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            val wide = maxWidth > maxHeight

            val entries = buildList {
                add(MenuEntry("Jogos", Icons.Default.Home, true) {})
                // Only offered where All Files Access exists (API 30+).
                if (AllFilesAccess.isSupported) {
                    add(MenuEntry("Arquivos", Icons.Default.Add, false) { startRealPathMode() })
                }
                add(MenuEntry("Configurações", Icons.Default.Settings, false) { onOpenSettings() })
                add(MenuEntry("Sobre", Icons.Default.Info, false) { onOpenAbout() })
                add(MenuEntry("Mais", Icons.Default.MoreVert, false) { menuOpen = true })
            }

            val body: @Composable (Modifier) -> Unit = { mod ->
                LibraryBody(
                    state = state,
                    wide = wide,
                    viewModel = viewModel,
                    setFolderLabel = setFolderLabel,
                    startRealPathMode = startRealPathMode,
                    selected = selectedGame,
                    onSelect = { selectedId = it.stableId },
                    onLaunch = launch,
                    onLongPress = { pendingGame = it },
                    onQuit = { (context as? Activity)?.finish() },
                    modifier = mod,
                )
            }

            if (wide) {
                Column(Modifier.fillMaxSize()) {
                    XenHeader(Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp))
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Column(
                            Modifier
                                .width(230.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            entries.forEach {
                                MenuButton(it, Modifier.fillMaxWidth())
                            }
                        }
                        body(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    XenHeader(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        entries.forEach { MenuButton(it, Modifier) }
                    }
                    body(Modifier.weight(1f).fillMaxWidth())
                }
            }

            // Everything the old top-bar overflow menu offered still lives here.
            Box(Modifier.align(Alignment.TopStart)) {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Atualizar biblioteca") },
                        onClick = { menuOpen = false; viewModel.refresh() },
                    )
                    if (AllFilesAccess.isSupported) {
                        DropdownMenuItem(
                            text = { Text("Set game folder") },
                            onClick = { menuOpen = false; startRealPathMode() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Install content") },
                        onClick = { menuOpen = false; onOpenInstallContent() },
                    )
                    DropdownMenuItem(
                        text = { Text("Profiles") },
                        onClick = { menuOpen = false; onOpenProfiles() },
                    )
                    DropdownMenuItem(
                        text = { Text("Key mapping") },
                        onClick = { menuOpen = false; onOpenKeymap() },
                    )
                    DropdownMenuItem(
                        text = { Text("Touch controls") },
                        onClick = { menuOpen = false; onOpenTouchControls() },
                    )
                    DropdownMenuItem(
                        text = { Text("Open user data") },
                        onClick = {
                            menuOpen = false
                            openUserData(context)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Check for Updates") },
                        onClick = {
                            menuOpen = false
                            checkForUpdatesClicked(
                                context = context,
                                scope = scope,
                                onResult = { updateResult = it }
                            )
                        }
                    )
                }
            }
        }
    }

    when (val result = updateResult) {
        is UpdateResult.Available -> {
            UpdateDialog(
                release = result.release,
                onDismiss = { updateResult = null }
            )
        }

        is UpdateResult.Latest -> {
            LatestVersionDialog(
                commitHash = result.commitHash,
                onDismiss = { updateResult = null }
            )
        }

        is UpdateResult.Cooldown -> {
            CooldownDialog(
                remainingMillis = result.remainingMillis,
                onDismiss = { updateResult = null }
            )
        }

        null -> {}
    }

    pendingDiscInstall?.let { (game, count) ->
        AlertDialog(
            onDismissRequest = { pendingDiscInstall = null },
            title = { Text("Install disc") },
            text = {
                Text("This disc carries $count content package(s) the game installs before " +
                     "it will run. Install them now, or boot the disc anyway?")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDiscInstall = null
                    onInstallFromDisc(game.launchUri)
                }) { Text("Install") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDiscInstall = null
                    launchGame(context, viewModel, game)
                }) { Text("Boot anyway") }
            },
        )
    }

    pendingGame?.let { game ->
        val dismiss = { pendingGame = null; viewModel.clearTitleIdRequest() }
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = dismiss,
            sheetState = sheetState,
        ) {
            Column {
                // The title-id status line shows ONLY while resolving or on error.
                val statusContent: (@Composable () -> Unit)? = when (val st = titleIdState) {
                    is TitleIdState.Loading -> ({
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Reading title id…")
                        }
                    })
                    is TitleIdState.Error -> ({ Text(st.message) })
                    else -> null
                }
                ListItem(
                    headlineContent = {
                        Text(game.name, style = MaterialTheme.typography.titleLarge)
                        if (game.isMultiDisc) {
                            Text(
                                "Disc ${game.discNumber} of ${game.discCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    supportingContent = if (game.titleId != null || game.mediaId != null || statusContent != null) {
                        {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                game.titleId?.let { Text("Title ID: $it") }
                                game.mediaId?.let { Text("Media ID: $it") }
                                statusContent?.invoke()
                            }
                        }
                    } else {
                        null
                    },
                )

                val perGameEnabled = titleIdState !is TitleIdState.Loading
                ListItem(
                    headlineContent = { Text("Per-game settings") },
                    colors = if (perGameEnabled) {
                        ListItemDefaults.colors()
                    } else {
                        ListItemDefaults.colors(
                            headlineColor =
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    },
                    modifier = Modifier.clickable(enabled = perGameEnabled) {
                        viewModel.requestPerGameSettings(game)
                    },
                )

                ListItem(
                    headlineContent = { Text("Game patches") },
                    colors = if (perGameEnabled) {
                        ListItemDefaults.colors()
                    } else {
                        ListItemDefaults.colors(
                            headlineColor =
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    },
                    modifier = Modifier.clickable(enabled = perGameEnabled) {
                        viewModel.requestGamePatches(game)
                    },
                )

                ListItem(
                    headlineContent = { Text("Manage content") },
                    colors = if (perGameEnabled) {
                        ListItemDefaults.colors()
                    } else {
                        ListItemDefaults.colors(
                            headlineColor =
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    },
                    modifier = Modifier.clickable(enabled = perGameEnabled) {
                        viewModel.requestContentManager(game)
                    },
                )

                if (game.format == GameFormat.ISO) {
                    ListItem(
                        headlineContent = { Text("Compress to .zar") },
                        modifier = Modifier.clickable {
                            compressConfirmFor = game
                            pendingGame = null
                            viewModel.clearTitleIdRequest()
                        },
                    )
                }

                if (viewModel.canLaunchGames && viewModel.isPinShortcutSupported) {
                    ListItem(
                        headlineContent = { Text("Create shortcut") },
                        modifier = Modifier.clickable {
                            viewModel.createShortcut(game)
                            dismiss()
                        },
                    )
                }
            }
        }
    }

    LaunchedEffect(titleIdState) {
        (titleIdState as? TitleIdState.Resolved)?.let { r ->
            when (r.action) {
                GameAction.PER_GAME_SETTINGS ->
                    onOpenPerGameSettings(r.titleId, r.game.name, r.game.format, r.game.launchUri)
                GameAction.GAME_PATCHES ->
                    onOpenGamePatches(r.titleId, r.game.name)
                GameAction.MANAGE_CONTENT ->
                    onOpenContentManager(r.titleId, r.game.name)
            }
            pendingGame = null
            viewModel.clearTitleIdRequest()
        }
    }

    compressConfirmFor?.let { game ->
        AlertDialog(
            onDismissRequest = { compressConfirmFor = null },
            title = { Text("Compress to .zar?") },
            text = {
                Text(
                    "This packs the disc into a smaller .zar. The original .iso is left alone " +
                        "until the .zar is created and verified, and you are asked before it is " +
                        "deleted. The game stays in your library.")
            },
            confirmButton = {
                TextButton(onClick = {
                    compressConfirmFor = null
                    compressVm.compress(game.launchUri)
                }) { Text("Compress") }
            },
            dismissButton = {
                TextButton(onClick = { compressConfirmFor = null }) { Text("Cancel") }
            },
        )
    }

    when (val s = compressState) {
        is CompressState.Busy -> AlertDialog(
            onDismissRequest = {},   // not cancelable while running
            title = { Text(s.message) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${(s.progress * 100).toInt()}%  ·  this may take a while.")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("This may take a while.")
                    }
                }
            },
            confirmButton = {},
        )
        is CompressState.ConfirmDelete -> AlertDialog(
            // Dismissing keeps it: a stray tap outside must never delete the .iso.
            onDismissRequest = compressVm::keepIso,
            title = { Text("Delete the original .iso?") },
            text = {
                Text(
                    "“${s.zarName}” was created and verified. Deleting “${s.isoName}” " +
                        "frees ${formatBytes(s.isoBytes)}.")
            },
            confirmButton = {
                TextButton(onClick = compressVm::deleteIso) { Text("Delete .iso") }
            },
            dismissButton = { TextButton(onClick = compressVm::keepIso) { Text("Keep it") } },
        )
        is CompressState.Done -> AlertDialog(
            onDismissRequest = { compressVm.dismiss(); viewModel.refresh() },
            title = { Text("Done") },
            text = { Text(s.message) },
            confirmButton = {
                TextButton(onClick = { compressVm.dismiss(); viewModel.refresh() }) { Text("OK") }
            },
        )
        is CompressState.Failed -> AlertDialog(
            onDismissRequest = compressVm::dismiss,
            title = { Text("Failed") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = compressVm::dismiss) { Text("OK") } },
        )
        else -> {}
    }
}

// ---- Visual building blocks -------------------------------------------------------------

/** Black background with the diagonal red stripes from the mockups. */
@Composable
private fun XenBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .background(Color.Black)
            .drawBehind {
                val w = size.width
                val h = size.height
                fun stripe(x0: Float, x1: Float, color: Color) {
                    val p = Path().apply {
                        moveTo(x0, 0f)
                        lineTo(x1, 0f)
                        lineTo(x1 - h * 0.6f, h)
                        lineTo(x0 - h * 0.6f, h)
                        close()
                    }
                    drawPath(p, color)
                }
                stripe(w * 0.62f, w * 0.70f, DeepRed.copy(alpha = 0.55f))
                stripe(w * 0.72f, w * 0.74f, NeonRed.copy(alpha = 0.85f))
                stripe(w * 0.80f, w * 0.90f, DeepRed.copy(alpha = 0.35f))
                stripe(w * 0.92f, w * 0.94f, Color.White.copy(alpha = 0.35f))
            },
        content = content,
    )
}

@Composable
private fun XenHeader(modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White)) { append("Xen") }
            withStyle(SpanStyle(color = NeonRed)) { append("D") }
            withStyle(SpanStyle(color = Color.White)) { append("roid") }
            withStyle(SpanStyle(color = Color.White, fontSize = 14.sp)) { append("  Nightly") }
        },
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@Composable
private fun MenuButton(entry: MenuEntry, modifier: Modifier = Modifier) {
    val fill = if (entry.selected) {
        Brush.horizontalGradient(listOf(DeepRed, NeonRed))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF0D0D0D), Color(0xFF1C1C1C)))
    }
    Row(
        modifier
            .clip(HexShape)
            .background(fill, HexShape)
            .border(2.dp, if (entry.selected) Color.White else DeepRed, HexShape)
            .clickable(onClick = entry.onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(entry.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(entry.label, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Switches between the library states; same cases and actions as before. */
@Composable
private fun LibraryBody(
    state: LibraryUiState,
    wide: Boolean,
    viewModel: GameLibraryViewModel,
    setFolderLabel: String,
    startRealPathMode: () -> Unit,
    selected: Game?,
    onSelect: (Game) -> Unit,
    onLaunch: (Game) -> Unit,
    onLongPress: (Game) -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (val s = state) {
            LibraryUiState.NoVulkan -> NoVulkanDialog(onQuit = onQuit)
            LibraryUiState.Loading -> CircularProgressIndicator(color = NeonRed)
            // All Files Access is API 30+; on API 29 there is no games path at all.
            LibraryUiState.NoFolder ->
                if (AllFilesAccess.isSupported)
                    EmptyMessage("No game folder set", setFolderLabel, onAction = startRealPathMode)
                else
                    EmptyMessage(
                        "Setting a game folder requires Android 11 or newer.",
                        "OK", onAction = {})
            LibraryUiState.PermissionLost ->
                EmptyMessage("Folder access lost", setFolderLabel, onAction = startRealPathMode)
            is LibraryUiState.Error ->
                EmptyMessage(s.message, "Retry", onAction = { viewModel.refresh() })
            is LibraryUiState.Loaded ->
                if (s.games.isEmpty() || selected == null)
                    EmptyMessage("No games in this folder", "Choose another",
                        onAction = startRealPathMode)
                else GameShowcase(
                    games = s.games,
                    selected = selected,
                    wide = wide,
                    viewModel = viewModel,
                    onSelect = onSelect,
                    onLaunch = onLaunch,
                    onLongPress = onLongPress,
                )
        }
    }
}

/** Big cover + info panel + a carousel of every game, like the mockup. */
@Composable
private fun GameShowcase(
    games: List<Game>,
    selected: Game,
    wide: Boolean,
    viewModel: GameLibraryViewModel,
    onSelect: (Game) -> Unit,
    onLaunch: (Game) -> Unit,
    onLongPress: (Game) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (wide) {
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GameCover(selected, viewModel, Modifier.weight(1.4f).fillMaxHeight())
                GameInfoPanel(selected, onLaunch, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            GameCover(selected, viewModel, Modifier.weight(1f).fillMaxWidth().padding(8.dp))
            GameInfoPanel(selected, onLaunch, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
        }
        GameCarousel(games, selected, viewModel, onSelect, onLaunch, onLongPress)
    }
}

@Composable
private fun GameCover(game: Game, viewModel: GameLibraryViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconModel = remember(game.stableId) { viewModel.iconFileOrFallback(game) }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color(0xFF0D0D0D))
            .border(3.dp, NeonRed, shape),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(iconModel).build(),
            contentDescription = game.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(16.dp),
        )
    }
}

@Composable
private fun GameInfoPanel(game: Game, onLaunch: (Game) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(PanelBlack)
            .border(2.dp, DeepRed, shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            game.name,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        InfoLine("Formato", game.format.toString())
        if (game.isMultiDisc) InfoLine("Disco", "${game.discNumber} de ${game.discCount}")
        game.titleId?.let { InfoLine("Title ID", it) }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(HexShape)
                .background(Brush.horizontalGradient(listOf(DeepRed, NeonRed)), HexShape)
                .border(2.dp, Color.White, HexShape)
                .clickable { onLaunch(game) },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Jogar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text("$label: ", color = NeonRed, fontWeight = FontWeight.SemiBold)
        Text(value, color = Color.White)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCarousel(
    games: List<Game>,
    selected: Game,
    viewModel: GameLibraryViewModel,
    onSelect: (Game) -> Unit,
    onLaunch: (Game) -> Unit,
    onLongPress: (Game) -> Unit,
) {
    val context = LocalContext.current
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(games, key = { it.stableId }) { game ->
            val isSelected = game.stableId == selected.stableId
            // Once per tile: the File.exists() stat must not run on every recomposition.
            val iconModel = remember(game.stableId) { viewModel.iconFileOrFallback(game) }
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .size(96.dp)
                    .clip(shape)
                    .background(Color(0xFF0D0D0D))
                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) NeonRed else DeepRed, shape)
                    .combinedClickable(
                        // Tap selects; tapping the selected one again launches it.
                        onClick = { if (isSelected) onLaunch(game) else onSelect(game) },
                        onLongClick = { onLongPress(game) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(iconModel).build(),
                    contentDescription = game.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(
    text: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = Color.White),
        ) { Text(action) }
    }
}

@Composable
private fun NoVulkanDialog(onQuit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onQuit,
        confirmButton = { TextButton(onClick = onQuit) { Text("Quit") } },
        title = { Text("Unsupported device") },
        text = { Text("This device has no Vulkan GPU; the emulator cannot run.") },
    )
}

fun checkForUpdatesClicked(
    context: Context,
    scope: CoroutineScope,
    onResult: (UpdateResult) -> Unit
) {
    scope.launch {
        if (!shouldCheckForUpdates(context)) {
            Log.d("Updater", "Skipping update check")
            onResult(UpdateResult.Cooldown(getRemainingCooldown(context)))
            return@launch
        }

        try {
            val result = checkForUpdates()
            saveLastCheck(context)
            onResult(result)
        } catch (e: Exception) {
            Log.e("Updater", "Failed to check updates", e)
        }
    }
}

/** Same shape as the content-install formatter, which is private to that file. */
private fun formatBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val u = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = -1
    do { v /= 1024.0; i++ } while (v >= 1024.0 && i < u.lastIndex)
    return "%.1f %s".format(v, u[i])
}

/** Reap any stale/orphaned :emu first (single-shot core). The new :emu links itself to the
 *  launcher by binding MainAliveService, so nothing rides on the Intent. */
private fun launchGame(context: Context, viewModel: GameLibraryViewModel, game: Game) {
    runCatching {
        EmuProcessLink.killStaleEmu(context)
        context.startActivity(viewModel.buildLaunchIntent(game))
    }
}
