package xendroid.compose.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xendroid.compose.settings.SettingValue
import xendroid.compose.settings.SettingsCategory
import xendroid.compose.settings.SettingsViewModel

// ---- XenDroid Nightly look: black, neon red, white -------------------------------------

private val SetNeonRed = Color(0xFFFF1E1E)
private val SetDeepRed = Color(0xFF8B0000)
private val SetPanelBlack = Color(0xE6080808)

private val SetHexShape = GenericShape { size, _ ->
    val c = size.height * 0.3f
    moveTo(c, 0f)
    lineTo(size.width - c, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - c, size.height)
    lineTo(c, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

/**
 * Settings screen in the XenDroid Nightly style: a category menu on the left (a row on top on
 * portrait phones) and the rows of the selected category in a dark panel. The rows themselves
 * and every setting action are unchanged (see SettingRows.kt); only the layout is new.
 * The system back button leaves the screen.
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val values by vm.values.collectAsStateWithLifecycle()

    // Durable flush on pause; re-open on resume. Dispose flush = backstop.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> vm.flush()
                Lifecycle.Event.ON_RESUME -> vm.onResume()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); vm.flush() }
    }

    val leave: () -> Unit = { vm.flush(); onBack() }
    BackHandler { leave() }

    var selectedTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = vm.categories
    val category = categories.firstOrNull { it.title == selectedTitle } ?: categories.firstOrNull()

    fun labelOf(cat: SettingsCategory): String {
        val changed = cat.settings.count { values[it.key]?.modified == true }
        return if (changed > 0) "${cat.title} ($changed)" else cat.title
    }

    Box(
        Modifier
            .fillMaxSize()
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
                stripe(w * 0.62f, w * 0.70f, SetDeepRed.copy(alpha = 0.55f))
                stripe(w * 0.72f, w * 0.74f, SetNeonRed.copy(alpha = 0.85f))
                stripe(w * 0.80f, w * 0.90f, SetDeepRed.copy(alpha = 0.35f))
                stripe(w * 0.92f, w * 0.94f, Color.White.copy(alpha = 0.35f))
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            val wide = maxWidth > maxHeight
            if (wide) {
                Column(Modifier.fillMaxSize()) {
                    SettingsHeader(Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp))
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Column(
                            Modifier
                                .width(240.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            BackButton(onClick = leave, modifier = Modifier.fillMaxWidth())
                            categories.forEach { cat ->
                                CategoryButton(
                                    label = labelOf(cat),
                                    selected = cat.title == category?.title,
                                    onClick = { selectedTitle = cat.title },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        SettingsPanel(
                            category, values, vm,
                            Modifier.weight(1f).fillMaxHeight().padding(end = 12.dp, bottom = 12.dp),
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    SettingsHeader(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BackButton(onClick = leave)
                        categories.forEach { cat ->
                            CategoryButton(
                                label = labelOf(cat),
                                selected = cat.title == category?.title,
                                onClick = { selectedTitle = cat.title },
                            )
                        }
                    }
                    SettingsPanel(
                        category, values, vm,
                        Modifier.weight(1f).fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White)) { append("Xen") }
            withStyle(SpanStyle(color = SetNeonRed)) { append("D") }
            withStyle(SpanStyle(color = Color.White)) { append("roid") }
            withStyle(SpanStyle(color = Color.White, fontSize = 14.sp)) { append("  Configurações") }
        },
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@Composable
private fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(SetHexShape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF0D0D0D), Color(0xFF1C1C1C))), SetHexShape)
            .border(2.dp, SetDeepRed, SetHexShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar",
            tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text("Voltar", color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun CategoryButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = if (selected) {
        Brush.horizontalGradient(listOf(SetDeepRed, SetNeonRed))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF0D0D0D), Color(0xFF1C1C1C)))
    }
    Row(
        modifier
            .clip(SetHexShape)
            .background(fill, SetHexShape)
            .border(2.dp, if (selected) Color.White else SetDeepRed, SetHexShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** The rows of one category, inside the dark red-bordered panel. */
@Composable
private fun SettingsPanel(
    category: SettingsCategory?,
    values: Map<String, SettingValue>,
    vm: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(SetPanelBlack)
            .border(2.dp, SetDeepRed, shape),
    ) {
        if (category == null) {
            Text(
                "Nenhuma configuração disponível",
                color = Color.White,
                modifier = Modifier.padding(20.dp),
            )
            return@Column
        }
        Text(
            category.title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
        HorizontalDivider(color = SetNeonRed, thickness = 2.dp)
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(category.settings, key = { it.key }) { setting ->
                val sv = values[setting.key]
                SettingRow(vm, setting, modified = sv?.modified == true, raw = sv?.raw)
                HorizontalDivider(color = Color(0xFF3A0A0A))
            }
        }
    }
}
