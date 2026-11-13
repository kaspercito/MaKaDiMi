package com.kaspercito.makadimi.ui.component

import com.kaspercito.makadimi.ui.theme.neonGlow
import com.kaspercito.makadimi.ui.theme.auroraBrush
import com.kaspercito.makadimi.ui.theme.glassPanel
import com.kaspercito.makadimi.ui.theme.monoFontFamily
import androidx.compose.ui.unit.sp
import com.kaspercito.makadimi.ui.theme.neonBorder
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import com.kaspercito.makadimi.ui.theme.neonBrush
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.koin.compose.koinInject
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kaspercito.makadimi.extension.greyScale
import com.kaspercito.makadimi.ui.navigation.destination.home.AnalyticsDestination
import com.kaspercito.makadimi.ui.navigation.destination.home.HomeDestination
import com.kaspercito.makadimi.ui.navigation.destination.library.LibraryDestination
import com.kaspercito.makadimi.ui.navigation.destination.library.MixForYouDestination
import com.kaspercito.makadimi.ui.navigation.destination.search.SearchDestination
import com.kaspercito.makadimi.ui.theme.typo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import makadimi.composeapp.generated.resources.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt
import kotlin.reflect.KClass

/**
 * The phone bottom bar with liquid glass OFF: the same floating capsule-and-FAB form as the glass
 * bar, drawn flat. One capsule of tabs (Search stays out — it is the round button beside it, the
 * same split the glass bar makes), a sliding rounded indicator instead of the frosted blob, solid
 * theme surfaces instead of refraction. Geometry mirrors the glass bar so switching the setting
 * changes the material, not the layout: 96dp tab cap, 64dp bar, 56dp indicator and FAB.
 */
@Composable
fun AppBottomNavigationBar(
    startDestination: Any = HomeDestination,
    navController: NavController,
    showAnalyticsTab: Boolean = false,
    showMixForYouTab: Boolean = false,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit = { _ -> },
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    // `ordinal` identifies a tab, it is NOT the position — Mix for you and Analytics sit before
    // Library here while keeping the ordinal they were declared with, so that the numbering stays
    // stable whether or not those tabs are present.
    val bottomNavScreens =
        listOfNotNull(
            BottomNavScreen.Home,
            BottomNavScreen.MixForYou.takeIf { showMixForYouTab },
            BottomNavScreen.Analytics.takeIf { showAnalyticsTab },
            BottomNavScreen.Library,
            BottomNavScreen.Search,
        )
    var selectedIndex by rememberSaveable {
        mutableIntStateOf(
            when (startDestination) {
                is HomeDestination -> BottomNavScreen.Home.ordinal
                is SearchDestination -> BottomNavScreen.Search.ordinal
                is LibraryDestination -> BottomNavScreen.Library.ordinal
                is AnalyticsDestination -> BottomNavScreen.Analytics.ordinal
                is MixForYouDestination -> BottomNavScreen.MixForYou.ordinal
                else -> BottomNavScreen.Home.ordinal // Default to Home if not recognized
            },
        )
    }
    // A tab can disappear from the list under the user: tracking gets turned off while Analytics is
    // selected, or the YouTube session ends while Mix for you is. Fall back to Home in both cases so
    // nothing is left highlighted.
    LaunchedEffect(showAnalyticsTab, showMixForYouTab) {
        if ((!showAnalyticsTab && selectedIndex == BottomNavScreen.Analytics.ordinal) ||
            (!showMixForYouTab && selectedIndex == BottomNavScreen.MixForYou.ordinal)
        ) {
            selectedIndex = BottomNavScreen.Home.ordinal
        }
    }
    val selectTab: (BottomNavScreen) -> Unit = { screen ->
        if (selectedIndex == screen.ordinal) {
            if (currentBackStackEntry?.destination?.hierarchy?.any {
                    it.hasRoute(screen.destination::class)
                } == true
            ) {
                reloadDestinationIfNeeded(screen.destination::class)
            } else {
                navController.navigate(screen.destination)
            }
        } else {
            selectedIndex = screen.ordinal
            navController.navigate(screen.destination) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    NeonDock(
        tabs = bottomNavScreens,
        selectedIndex = selectedIndex,
        onTabSelected = selectTab,
    )
}

/**
 * Menú inferior MaKaDiMi: un dock a todo el ancho con el borde superior en degradado neón.
 * Todas las pestañas (Buscar incluida) viven en la misma barra; la activa se expande en una
 * píldora con degradado que muestra icono + nombre, y las demás quedan como iconos limpios.
 */
@Composable
private fun NeonDock(
    tabs: List<BottomNavScreen>,
    selectedIndex: Int,
    onTabSelected: (BottomNavScreen) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val brush = auroraBrush()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val dockShape = RoundedCornerShape(32.dp)
    var showDj by remember { mutableStateOf(false) }
    val aiDjValue by koinInject<com.kaspercito.domain.manager.DataStoreManager>().aiDj
        .collectAsState(com.kaspercito.domain.manager.DataStoreManager.FALSE)
    val djActive = aiDjValue == com.kaspercito.domain.manager.DataStoreManager.TRUE
    val tabShape = RoundedCornerShape(topStart = 22.dp, topEnd = 7.dp, bottomEnd = 22.dp, bottomStart = 7.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // Resplandor suave detrás del dock: se siente "flotando" sobre el contenido.
                    .neonGlow(cs.primary.copy(alpha = 0.16f), 18.dp)
                    .clip(dockShape)
                    .glassPanel(dockShape, tint = cs.surfaceContainerHigh, alpha = 0.82f, borderAlpha = 0.9f)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val renderTab: @Composable (BottomNavScreen) -> Unit = { screen ->
                val selected = screen.ordinal == selectedIndex
                val contentColor by animateColorAsState(
                    if (selected) cs.onPrimary else cs.onSurfaceVariant,
                    label = "dockTabColor",
                )
                val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val pressScale by androidx.compose.animation.core.animateFloatAsState(
                    if (pressed) 0.9f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 500f),
                    label = "dockTabPress",
                )
                Row(
                    modifier =
                        Modifier
                            .animateContentSize(
                                androidx.compose.animation.core.spring(
                                    dampingRatio = 0.7f,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                                ),
                            ).graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            }.height(48.dp)
                            .then(if (selected) Modifier.neonGlow(cs.primary.copy(alpha = 0.45f), 10.dp) else Modifier)
                            .clip(tabShape)
                            .then(if (selected) Modifier.background(brush) else Modifier)
                            .clickable(interactionSource = interaction, indication = null) {
                                // Vibración ligera al cambiar de pestaña, como una tecla de verdad.
                                if (!selected) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onTabSelected(screen)
                            }.padding(horizontal = if (selected) 20.dp else 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                        screen.icon()
                    }
                    if (selected) {
                        Text(
                            stringResource(screen.title).uppercase(),
                            style =
                                typo().labelSmall.copy(
                                    fontFamily = monoFontFamily(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.2.sp,
                                ),
                            color = contentColor,
                            maxLines = 1,
                        )
                    }
                }
            }
            // El DJ vive en el centro del dock: tabs a la izquierda, orbe, tabs a la derecha.
            val half = (tabs.size + 1) / 2
            tabs.take(half).forEach { renderTab(it) }
            DjOrb(active = djActive, size = 34.dp, onClick = { showDj = true })
            tabs.drop(half).forEach { renderTab(it) }
        }
    }
    if (showDj) DjSheet(onDismiss = { showDj = false })
}

/**
 * The flat capsule of tabs. Like the glass tab bar it can be handed any width from the folded
 * [CollapsedBarSize] circle up to its full width: the surface follows that width, the tabs keep
 * their full-width layout and are uncovered from the start edge, and folded it shows
 * [collapsedContent] instead.
 */
@Composable
internal fun FlatTabBar(
    tabs: List<BottomNavScreen>,
    selectedIndex: Int,
    availableWidth: Dp,
    onTabSelected: (BottomNavScreen) -> Unit,
    modifier: Modifier = Modifier,
    collapsedContent: (@Composable () -> Unit)? = null,
) {
    val capsuleColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)
    val indicatorBrush = auroraBrush()
    val indicatorShape = RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 24.dp, bottomStart = 8.dp)
    val tabWidth = flatTabWidth(tabs.size, availableWidth)
    val fullWidth = tabWidth * tabs.size + CapsuleInset * 2
    val selectedPosition = tabs.indexOfFirst { it.ordinal == selectedIndex }
    // Where a finger is holding the indicator, in tabs (0f = first); null when nobody is.
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val indicatorOffset by animateDpAsState(
        tabWidth * (dragPosition ?: selectedPosition.coerceAtLeast(0).toFloat()),
        // Under the finger while dragging; on release it springs to the tab it was let go on.
        animationSpec = if (dragPosition != null) snap() else spring(visibilityThreshold = Dp.VisibilityThreshold),
        label = "flatBarIndicator",
    )
    // The tab under the indicator lights up as it is dragged across.
    val activePosition = dragPosition?.roundToInt() ?: selectedPosition
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    Box(
        modifier =
            modifier
                .height(FlatBarHeight)
                .width(fullWidth)
                // Also what cuts the full-width tabs down to the surface while the bar is folding.
                .clip(RoundedCornerShape(FlatBarHeight / 2))
                .background(capsuleColor)
                .neonBorder(RoundedCornerShape(FlatBarHeight / 2), alpha = 0.6f),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.matchParentSize().unfoldingTabs(fullWidth, clipShape = null)) {
            Box(
                modifier =
                    Modifier
                        .wrapContentSize(Alignment.CenterStart, unbounded = true)
                        .size(fullWidth, FlatBarHeight)
                        .padding(horizontal = CapsuleInset)
                        // Press and drag along the tabs to slide the indicator; letting go selects the
                        // tab it rests on. Once the drag starts it cancels the tab's own click.
                        .pointerInput(tabs, tabWidth, isLtr) {
                            fun positionAt(x: Float): Float {
                                val fromStart = if (isLtr) x else size.width - x
                                return (fromStart / tabWidth.toPx() - 0.5f).coerceIn(0f, tabs.lastIndex.toFloat())
                            }
                            detectHorizontalDragGestures(
                                onDragStart = { dragPosition = positionAt(it.x) },
                                onDragEnd = {
                                    val target = dragPosition?.roundToInt()?.let(tabs::get)
                                    dragPosition = null
                                    if (target != null && target.ordinal != selectedIndex) onTabSelected(target)
                                },
                                onDragCancel = { dragPosition = null },
                            ) { change, _ ->
                                change.consume()
                                dragPosition = positionAt(change.position.x)
                            }
                        },
                contentAlignment = Alignment.CenterStart,
            ) {
                // The sliding indicator — the flat stand-in for the glass bar's frosted blob. Hidden
                // while Search (a non-capsule tab) is the selection, so nothing sits half-lit.
                if (activePosition >= 0) {
                    Box(
                        modifier =
                            Modifier
                                .offset(x = indicatorOffset)
                                .size(width = tabWidth, height = FlatIndicatorHeight)
                                .neonGlow(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), 16.dp)
                                .clip(indicatorShape)
                                .background(indicatorBrush),
                    )
                }
                Row {
                    tabs.forEachIndexed { position, screen ->
                        val selected = position == activePosition
                        val contentColor =
                            if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        Column(
                            modifier =
                                Modifier
                                    .width(tabWidth)
                                    .fillMaxHeight()
                                    .clip(indicatorShape)
                                    .clickable { onTabSelected(screen) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CompositionLocalProvider(LocalContentColor provides contentColor) {
                                screen.icon()
                            }
                            Text(
                                stringResource(screen.title).uppercase(),
                                style =
                                    typo().bodySmall.copy(
                                        fontFamily = monoFontFamily(),
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.8.sp,
                                    ),
                                color = contentColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        if (collapsedContent != null) {
            Box(Modifier.matchParentSize().foldedCircle(fullWidth), contentAlignment = Alignment.Center) {
                collapsedContent()
            }
        }
    }
}

/** El botón de búsqueda junto a [FlatTabBar]: hoja con degradado aurora, vidrio cuando está activo. */
@Composable
internal fun FlatSearchButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 10.dp, bottomEnd = 28.dp, bottomStart = 10.dp)
    Box(
        modifier =
            modifier
                .size(FlatIndicatorHeight)
                .neonGlow(MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.4f else 0.25f), 14.dp)
                .clip(shape)
                .then(
                    if (selected) {
                        Modifier.background(auroraBrush())
                    } else {
                        Modifier.glassPanel(shape, alpha = 0.85f, borderAlpha = 1f)
                    },
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides
                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        ) {
            BottomNavScreen.Search.icon()
        }
    }
}

// Mirrors the glass tab bar's geometry (TabWidth/BarHeight/BlobHeight/BarInset in
// LiquidGlassTabBar.android.kt) so the two bars are one form in two materials.
private val FlatTabWidth = 96.dp
private val FlatBarHeight = 64.dp
private val FlatIndicatorHeight = 56.dp
private val CapsuleInset = 6.dp

// Every tab the same width, capped so two tabs on a wide screen do not stretch into slabs — the
// same budget rule as the glass tab bar.
private fun flatTabWidth(
    tabsCount: Int,
    availableWidth: Dp,
): Dp = ((availableWidth - CapsuleInset * 2) / tabsCount).coerceAtMost(FlatTabWidth)

/** Full width of [FlatTabBar] for [tabsCount] tabs given [availableWidth]. */
internal fun flatTabBarWidth(
    tabsCount: Int,
    availableWidth: Dp,
): Dp = flatTabWidth(tabsCount, availableWidth) * tabsCount + CapsuleInset * 2

// The phone bar folds into one circle at its start edge (LiquidGlassAppBottomNavigationBar). Both
// tab bars accept any width between this and their full width and fold by it.
internal val CollapsedBarSize = 48.dp

// 0 at the folded circle, 1 at [fullWidth], read off the width the bar was actually handed.
private fun Density.foldProgress(
    width: Int,
    fullWidth: Dp,
): Float {
    val folded = CollapsedBarSize.roundToPx()
    return ((width - folded).toFloat() / (fullWidth.roundToPx() - folded).coerceAtLeast(1)).coerceIn(0f, 1f)
}

/**
 * For a tab bar's tabs, laid out at the bar's [fullWidth] while the bar itself may be narrower.
 * They fade in once the folded circle's icon has gone, stay unplaced (so untappable) before that,
 * and while the bar folds they are clipped to [clipShape] — null when the surface clips already.
 */
internal fun Modifier.unfoldingTabs(
    fullWidth: Dp,
    clipShape: Shape?,
): Modifier =
    layout { measurable, constraints ->
        val progress = foldProgress(constraints.maxWidth, fullWidth)
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            val tabsAlpha = ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f)
            if (tabsAlpha > 0f) {
                placeable.placeRelativeWithLayer(0, 0) {
                    alpha = tabsAlpha
                    if (clipShape != null) {
                        // Only mid-fold, so whatever bulges out of the bar at rest still can.
                        clip = progress < 1f
                        shape = clipShape
                    }
                }
            }
        }
    }

/** For the folded circle's content: a [CollapsedBarSize] square at the bar's start, faded out as it unfolds. */
internal fun Modifier.foldedCircle(fullWidth: Dp): Modifier =
    layout { measurable, constraints ->
        val progress = foldProgress(constraints.maxWidth, fullWidth)
        val size = CollapsedBarSize.roundToPx()
        val placeable = measurable.measure(Constraints.fixed(size, size))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val circleAlpha = (1f - progress / 0.4f).coerceIn(0f, 1f)
            // Unplaced once gone, so the open bar has nothing over its first tab.
            if (circleAlpha > 0f) {
                placeable.placeRelativeWithLayer(0, (constraints.maxHeight - size) / 2) {
                    alpha = circleAlpha
                }
            }
        }
    }

@Composable
fun AppNavigationRail(
    startDestination: Any = HomeDestination,
    navController: NavController,
    showAnalyticsTab: Boolean = false,
    showMixForYouTab: Boolean = false,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit = { _ -> },
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    // See the note in AppBottomNavigationBar: `ordinal` is the tab's identity, not its position.
    val bottomNavScreens =
        listOfNotNull(
            BottomNavScreen.Home,
            BottomNavScreen.MixForYou.takeIf { showMixForYouTab },
            BottomNavScreen.Analytics.takeIf { showAnalyticsTab },
            BottomNavScreen.Library,
            BottomNavScreen.Search,
        )
    var selectedIndex by rememberSaveable {
        mutableIntStateOf(
            when (startDestination) {
                is HomeDestination -> BottomNavScreen.Home.ordinal
                is SearchDestination -> BottomNavScreen.Search.ordinal
                is LibraryDestination -> BottomNavScreen.Library.ordinal
                is AnalyticsDestination -> BottomNavScreen.Analytics.ordinal
                is MixForYouDestination -> BottomNavScreen.MixForYou.ordinal
                else -> BottomNavScreen.Home.ordinal // Default to Home if not recognized
            },
        )
    }
    // A tab can disappear from the list under the user: tracking gets turned off while Analytics is
    // selected, or the YouTube session ends while Mix for you is. Fall back to Home in both cases so
    // nothing is left highlighted.
    LaunchedEffect(showAnalyticsTab, showMixForYouTab) {
        if ((!showAnalyticsTab && selectedIndex == BottomNavScreen.Analytics.ordinal) ||
            (!showMixForYouTab && selectedIndex == BottomNavScreen.MixForYou.ordinal)
        ) {
            selectedIndex = BottomNavScreen.Home.ordinal
        }
    }
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        modifier = Modifier.neonBorder(RoundedCornerShape(0.dp), alpha = 0.25f),
    ) {
        Spacer(Modifier.height(16.dp))
        Box(Modifier.padding(horizontal = 16.dp)) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomEnd = 18.dp, bottomStart = 6.dp))
                    .background(auroraBrush()),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.mono),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .height(32.dp)
                            .clip(CircleShape),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        bottomNavScreens.forEach { screen ->
            NavigationRailItem(
                colors =
                    NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                icon = screen.icon,
                label = {
                    Text(
                        stringResource(screen.title),
                        style =
                            if (selectedIndex == screen.ordinal) {
                                typo().bodySmall
                            } else {
                                typo().bodySmall.greyScale()
                            },
                    )
                },
                selected = selectedIndex == screen.ordinal,
                onClick = {
                    if (selectedIndex == screen.ordinal) {
                        if (currentBackStackEntry?.destination?.hierarchy?.any {
                                it.hasRoute(screen.destination::class)
                            } == true
                        ) {
                            reloadDestinationIfNeeded(
                                screen.destination::class,
                            )
                        } else {
                            navController.navigate(screen.destination)
                        }
                    } else {
                        selectedIndex = screen.ordinal
                        navController.navigate(screen.destination) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}