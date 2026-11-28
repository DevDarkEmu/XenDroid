package xendroid.compose.ui.library

import android.app.Activity
import android.content.Context
import android.util.Log
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.core.graphics.drawable.toBitmap
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

// ---- XenDroid Adreno look: black, green, white -----------------------------------------

private val Green = Color(0xFF76B900)
private val GreenDim = Color(0xFF3D6100)
private val PageBg = Color(0xFF050505)
private val PanelBg = Color(0xFF0A0A0A)
private val LineGreen = Color(0xFF1E2B0E)
private val MutedText = Color(0xFFB0B0B0)

private data class SideEntry(
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
    var tab by rememberSaveable { mutableStateOf("home") }
    val games = (state as? LibraryUiState.Loaded)?.games.orEmpty()
    val addLabel = if (allFilesGranted) "ADICIONAR JOGO" else "CONCEDER ACESSO AOS ARQUIVOS"

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

    // Everything the old top-bar overflow menu offered still lives here (the "⋮" in the header).
    val dropdown: @Composable () -> Unit = {
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

    Box(Modifier.fillMaxSize().background(PageBg)) {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            val wide = maxWidth > maxHeight

            val entries = buildList {
                add(SideEntry("INÍCIO", Icons.Default.Home, tab == "home") { tab = "home" })
                add(SideEntry("JOGOS", Icons.Default.PlayArrow, tab == "games") { tab = "games" })
                // Only offered where All Files Access exists (API 30+).
                if (AllFilesAccess.isSupported) {
                    add(SideEntry("ARQUIVOS", Icons.Default.Add, false) { startRealPathMode() })
                }
                add(SideEntry("CONFIGURAÇÕES", Icons.Default.Settings, false) { onOpenSettings() })
                add(SideEntry("CONTROLES", Icons.Default.Face, false) { onOpenTouchControls() })
                add(SideEntry("SOBRE", Icons.Default.Info, false) { onOpenAbout() })
            }

            val panel: @Composable (Modifier) -> Unit = { mod ->
                LibraryPanel(
                    state = state,
                    tab = tab,
                    viewModel = viewModel,
                    addLabel = addLabel,
                    onAdd = startRealPathMode,
                    onScan = { viewModel.refresh() },
                    onInstall = onOpenInstallContent,
                    onLaunch = launch,
                    onLongPress = { pendingGame = it },
                    onQuit = { (context as? Activity)?.finish() },
                    modifier = mod,
                )
            }

            if (wide) {
                Column(Modifier.fillMaxSize()) {
                    AdrenoHeader(
                        wide = true,
                        onMenu = { menuOpen = true },
                        menu = dropdown,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Row(
                        Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val shape = RoundedCornerShape(16.dp)
                        Column(
                            Modifier
                                .width(250.dp)
                                .fillMaxHeight()
                                .clip(shape)
                                .background(PanelBg)
                                .border(1.dp, LineGreen, shape)
                                .padding(8.dp),
                        ) {
                            Column(
                                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                entries.forEach { SideItem(it, Modifier.fillMaxWidth()) }
                            }
                            StatusCard(count = games.size, ready = state is LibraryUiState.Loaded)
                        }
                        panel(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    AdrenoHeader(
                        wide = false,
                        onMenu = { menuOpen = true },
                        menu = dropdown,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        entries.forEach { SideItem(it, Modifier) }
                    }
                    panel(Modifier.weight(1f).fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp))
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

@Composable
private fun AdrenoHeader(
    wide: Boolean,
    onMenu: () -> Unit,
    menu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The launcher icon doubles as the logo, so this follows whatever icon the APK ships.
    val logo = remember {
        runCatching {
            context.packageManager.getApplicationIcon(context.packageName)
                .toBitmap(160, 160).asImageBitmap()
        }.getOrNull()
    }
    val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        logo?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("XenDroid") }
                    withStyle(SpanStyle(color = Green)) { append(" ADRENO") }
                },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("Emulador de Xbox 360 para Android", color = Color.White, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        if (wide) {
            Text(soc, color = Color.White, fontSize = 14.sp)
            Text("   |   ", color = Color(0xFF444444), fontSize = 14.sp)
            Text(Build.MODEL, color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
        }
        Box {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
            }
            menu()
        }
    }
}

@Composable
private fun SideItem(entry: SideEntry, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val base = modifier.clip(shape)
    val styled = if (entry.selected) {
        base
            .background(Brush.horizontalGradient(listOf(Color(0xFF1B2D08), Color(0xFF0C1206))), shape)
            .border(1.5.dp, Green, shape)
    } else {
        base
    }
    Row(
        styled.clickable(onClick = entry.onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            entry.icon,
            contentDescription = null,
            tint = if (entry.selected) Green else MutedText,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(entry.label, color = Color.White, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
private fun StatusCard(count: Int, ready: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(shape)
            .background(Color(0xFF111111))
            .padding(12.dp),
    ) {
        Text("STATUS DO EMULADOR", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (ready) Green else Color(0xFFAA8800)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (ready) "Pronto para jogar" else "Aguardando jogos",
                color = Color.White,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("Android ${Build.VERSION.RELEASE}", color = MutedText, fontSize = 12.sp)
        Text("Jogos: $count", color = MutedText, fontSize = 12.sp)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Green, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
private fun GreenButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Green)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Switches between the library states; same cases and actions as before. */
@Composable
private fun LibraryPanel(
    state: LibraryUiState,
    tab: String,
    viewModel: GameLibraryViewModel,
    addLabel: String,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    onInstall: () -> Unit,
    onLaunch: (Game) -> Unit,
    onLongPress: (Game) -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.clip(shape).background(PanelBg).border(1.dp, LineGreen, shape)) {
        when (val s = state) {
            LibraryUiState.NoVulkan -> NoVulkanDialog(onQuit = onQuit)
            LibraryUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Green)
                }
            // All Files Access is API 30+; on API 29 there is no games path at all.
            LibraryUiState.NoFolder ->
                if (AllFilesAccess.isSupported)
                    EmptyLibrary(
                        "NENHUM JOGO ADICIONADO",
                        "Adicione seus jogos de Xbox 360 para começar a jogar.",
                        addLabel, onAdd, onScan, onInstall,
                    )
                else
                    EmptyLibrary(
                        "PASTA DE JOGOS INDISPONÍVEL",
                        "Definir uma pasta de jogos exige Android 11 ou mais novo.",
                        null, {}, onScan, onInstall,
                    )
            LibraryUiState.PermissionLost ->
                EmptyLibrary(
                    "ACESSO À PASTA PERDIDO",
                    "Conceda o acesso aos arquivos novamente para ver seus jogos.",
                    addLabel, onAdd, onScan, onInstall,
                )
            is LibraryUiState.Error ->
                EmptyLibrary("ALGO DEU ERRADO", s.message, "TENTAR NOVAMENTE", onScan, onScan, onInstall)
            is LibraryUiState.Loaded ->
                when {
                    s.games.isEmpty() ->
                        EmptyLibrary(
                            "NENHUM JOGO ENCONTRADO",
                            "Esta pasta não tem jogos reconhecidos.",
                            "ESCOLHER OUTRA PASTA", onAdd, onScan, onInstall,
                        )
                    tab == "games" -> GamesGrid(s.games, viewModel, onLaunch, onLongPress)
                    else -> HomeContent(s.games, viewModel, onLaunch, onLongPress, onAdd, onScan, onInstall)
                }
        }
    }
}

@Composable
private fun EmptyLibrary(
    title: String,
    subtitle: String,
    actionLabel: String?,
    onAction: () -> Unit,
    onScan: () -> Unit,
    onInstall: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SectionTitle("MEUS JOGOS", Modifier.align(Alignment.Start))
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().clip(shape).border(1.dp, LineGreen, shape).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = MutedText, fontSize = 14.sp, textAlign = TextAlign.Center)
            if (actionLabel != null) {
                Spacer(Modifier.height(16.dp))
                GreenButton(actionLabel, onAction)
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionTitle("AÇÕES RÁPIDAS", Modifier.align(Alignment.Start))
        Spacer(Modifier.height(10.dp))
        QuickActions(onFolder = onAction, onScan = onScan, onInstall = onInstall)
    }
}

@Composable
private fun HomeContent(
    games: List<Game>,
    viewModel: GameLibraryViewModel,
    onLaunch: (Game) -> Unit,
    onLongPress: (Game) -> Unit,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    onInstall: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        SectionTitle("MEUS JOGOS")
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(games, key = { it.stableId }) { game ->
                GameCard(game, viewModel, onLaunch, onLongPress, Modifier.width(130.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = LineGreen)
        Spacer(Modifier.height(16.dp))
        SectionTitle("AÇÕES RÁPIDAS")
        Spacer(Modifier.height(10.dp))
        QuickActions(onFolder = onAdd, onScan = onScan, onInstall = onInstall)
    }
}

@Composable
private fun GamesGrid(
    games: List<Game>,
    viewModel: GameLibraryViewModel,
    onLaunch: (Game) -> Unit,
    onLongPress: (Game) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SectionTitle("TODOS OS JOGOS", Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 130.dp),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(games, key = { it.stableId }) { game ->
                GameCard(game, viewModel, onLaunch, onLongPress)
            }
        }
    }
}

/** Tap launches the game (like the mockup); long-press opens the per-game options. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCard(
    game: Game,
    viewModel: GameLibraryViewModel,
    onLaunch: (Game) -> Unit,
    onLongPress: (Game) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Once per card: the File.exists() stat must not run on every recomposition.
    val iconModel = remember(game.stableId) { viewModel.iconFileOrFallback(game) }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.combinedClickable(onClick = { onLaunch(game) }, onLongClick = { onLongPress(game) }),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(Color(0xFF0D0D0D))
                .border(1.dp, GreenDim, shape),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(iconModel).build(),
                contentDescription = game.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            game.name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // A set shares one title, so the cards would otherwise be identical.
        if (game.isMultiDisc) {
            Text(
                "Disco ${game.discNumber} de ${game.discCount}",
                color = Green,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QuickActions(onFolder: () -> Unit, onScan: () -> Unit, onInstall: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickAction(Icons.Default.Search, "ESCANEAR JOGOS", "Buscar na pasta", onScan, Modifier.weight(1f))
        if (AllFilesAccess.isSupported) {
            QuickAction(Icons.Default.Add, "PASTA DE JOGOS", "Escolher uma pasta", onFolder, Modifier.weight(1f))
        }
        QuickAction(Icons.Default.Check, "INSTALAR CONTEÚDO", "DLC e atualizações", onInstall, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color(0xFF111111))
            .border(1.dp, LineGreen, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Green, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center)
        Text(subtitle, color = Color(0xFF9A9A9A), fontSize = 11.sp, textAlign = TextAlign.Center)
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
