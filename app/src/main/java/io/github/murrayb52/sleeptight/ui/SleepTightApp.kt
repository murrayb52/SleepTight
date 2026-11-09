package io.github.murrayb52.sleeptight.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.SleepTightApplication
import io.github.murrayb52.sleeptight.health.GarminImporter
import io.github.murrayb52.sleeptight.metrics.NightDate
import io.github.murrayb52.sleeptight.ui.diary.DiaryPrompts
import io.github.murrayb52.sleeptight.ui.history.HistoryRoute
import io.github.murrayb52.sleeptight.ui.history.NightDetailRoute
import io.github.murrayb52.sleeptight.ui.home.HomeRoute
import io.github.murrayb52.sleeptight.ui.profile.AlarmSettingsRoute
import io.github.murrayb52.sleeptight.ui.profile.CreditsScreen
import io.github.murrayb52.sleeptight.ui.profile.GoalSettingsRoute
import io.github.murrayb52.sleeptight.ui.profile.NotificationSettingsRoute
import io.github.murrayb52.sleeptight.ui.profile.SettingsPageId
import io.github.murrayb52.sleeptight.ui.profile.ProfileRoute
import io.github.murrayb52.sleeptight.ui.theme.Palette
import java.time.format.DateTimeFormatter

private enum class Tab(val route: String, val label: Int) { SLEEP("sleep", R.string.tab_sleep), HISTORY("history", R.string.tab_history), PROFILE("profile", R.string.tab_profile) }

private const val NIGHT_ROUTE = "night/{id}"
private const val CREDITS_ROUTE = "credits"
private const val SETTINGS_ROUTE = "settings/{page}"

/** The app: three tabs with a bottom bar, plus a night's detail page. [openNightId] jumps straight to one. */
@Composable
fun SleepTightApp(openNightId: Long? = null) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val container = (LocalContext.current.applicationContext as SleepTightApplication).container
    val openNight by container.nightRepository.openNight.collectAsStateWithLifecycle(null)

    LaunchedEffect(openNightId) {
        if (openNightId != null) nav.navigate("night/$openNightId")
    }

    // No nav bar in bed (nothing to fiddle with at night) or on a night's page.
    val showBar = route != NIGHT_ROUTE && route != CREDITS_ROUTE && route != SETTINGS_ROUTE && !(route == Tab.SLEEP.route && openNight != null)
    Column(Modifier.fillMaxSize().background(Palette.Sky)) {
        Box(Modifier.weight(1f)) {
            NavHost(nav, startDestination = Tab.SLEEP.route) {
                composable(Tab.SLEEP.route) { HomeRoute() }
                composable(Tab.HISTORY.route) { HistoryRoute(onOpenNight = { nav.navigate("night/$it") }) }
                composable(Tab.PROFILE.route) {
                    ProfileRoute(onOpenCredits = { nav.navigate(CREDITS_ROUTE) }, onOpenSettings = { nav.navigate("settings/${it.name}") })
                }
                composable(SETTINGS_ROUTE) { e ->
                    val back = { nav.popBackStack(); Unit }
                    when (e.arguments?.getString("page")?.let { p -> SettingsPageId.entries.firstOrNull { it.name == p } }) {
                        SettingsPageId.ALARM -> AlarmSettingsRoute(onBack = back)
                        SettingsPageId.NOTIFICATIONS -> NotificationSettingsRoute(onBack = back)
                        SettingsPageId.GOALS -> GoalSettingsRoute(onBack = back)
                        null -> LaunchedEffect(Unit) { nav.popBackStack() }
                    }
                }
                composable(CREDITS_ROUTE) { CreditsScreen(onBack = { nav.popBackStack() }) }
                composable(NIGHT_ROUTE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    NightDetailRoute(e.arguments?.getLong("id") ?: 0, onBack = { nav.popBackStack() })
                }
            }
            DiaryPrompts(container)
            // Garmin rewrote a night after it was imported: say so, near the bottom, until swiped away.
            val updates by container.garminUpdates.collectAsStateWithLifecycle()
            updates.firstOrNull()?.let { update ->
                val dismiss = { container.garminUpdates.value = container.garminUpdates.value.filterNot { it.nightId == update.nightId } }
                key(update.nightId) {
                    GarminUpdateNote(
                        update,
                        onOpen = {
                            dismiss()
                            nav.navigate("night/${update.nightId}")
                        },
                        onDismiss = dismiss,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        if (showBar) {
            BottomBar(current = route) { tab ->
                nav.navigate(tab.route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
}

@Composable
private fun BottomBar(current: String?, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF0E1430)).navigationBarsPadding()) {
        HorizontalDivider(color = Palette.Line)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Tab.entries.forEach { tab ->
                val selected = current == tab.route
                val colour = if (selected) Palette.Ember else Palette.Muted
                Column(
                    Modifier
                        .widthIn(min = 72.dp)
                        .heightIn(min = 56.dp)
                        .selectable(selected = selected, role = Role.Tab) { onSelect(tab) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Canvas(Modifier.size(24.dp)) { drawTabIcon(tab, colour) }
                    Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium, color = colour)
                }
            }
        }
    }
}

private fun DrawScope.drawTabIcon(tab: Tab, colour: Color) {
    val w = 1.8.dp.toPx()
    val s = size.minDimension
    when (tab) {
        Tab.SLEEP -> {
            val moon = Path().apply {
                moveTo(s * 0.83f, s * 0.6f)
                arcTo(androidx.compose.ui.geometry.Rect(Offset(s * 0.5f, s * 0.5f), s * 0.36f), 20f, 280f, false)
                quadraticTo(s * 0.4f, s * 0.55f, s * 0.83f, s * 0.6f)
            }
            drawPath(moon, colour, style = Stroke(w))
        }
        Tab.HISTORY -> {
            listOf(0.22f to 0.45f, 0.5f to 0.2f, 0.78f to 0.58f).forEach { (x, top) ->
                drawLine(colour, Offset(s * x, s * 0.84f), Offset(s * x, s * top), w, StrokeCap.Round)
            }
        }
        Tab.PROFILE -> {
            drawCircle(colour, radius = s * 0.17f, center = Offset(s / 2, s * 0.33f), style = Stroke(w))
            val body = Path().apply {
                moveTo(s * 0.16f, s * 0.84f)
                cubicTo(s * 0.25f, s * 0.6f, s * 0.75f, s * 0.6f, s * 0.84f, s * 0.84f)
            }
            drawPath(body, colour, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

/** A small card saying Garmin changed a night's data and score. Swipe either way to dismiss; tap to open the night. */
@Composable
private fun GarminUpdateNote(update: GarminImporter.Update, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) onDismiss()
    }
    val date = DateTimeFormatter.ofPattern("EEE d MMM").format(NightDate.of(update.bedTapAt, update.zone))
    SwipeToDismissBox(state, backgroundContent = {}, modifier = modifier.padding(16.dp).testTag("garminUpdate")) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.Raised)
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.garmin_updated, date, update.scoreBefore?.toString() ?: "–", update.scoreAfter?.toString() ?: "–"),
                style = MaterialTheme.typography.titleSmall,
                color = Palette.Text,
            )
            Text(stringResource(R.string.garmin_updated_hint), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}
