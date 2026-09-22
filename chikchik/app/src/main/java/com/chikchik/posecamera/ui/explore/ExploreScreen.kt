package com.chikchik.posecamera.ui.explore

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chikchik.posecamera.R
import com.chikchik.posecamera.explore.ExploreError
import com.chikchik.posecamera.explore.ExploreLocalStore
import com.chikchik.posecamera.explore.ExplorePhoto
import com.chikchik.posecamera.explore.ExploreRepository
import com.chikchik.posecamera.explore.ExploreResult
import com.chikchik.posecamera.explore.HistoryEntry
import com.chikchik.posecamera.explore.PoseSearchCategory
import com.chikchik.posecamera.util.loadRemoteBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long to wait after the last keystroke before firing a search request. */
private const val SEARCH_DEBOUNCE_MS = 500L

/** Which full-screen section [ExploreScreen] is currently showing below the top bar. */
private enum class ExploreSection { FEED, FAVORITES, HISTORY }

/**
 * Stage 15 built this screen and its entry point; Stage 16 filled [ExploreContent] with a
 * real online feed (from [ExploreRepository]) instead of a permanent placeholder message.
 * Stage 17 added a search bar inside [ExploreContent]. Stage 18 adds Gender/Style/Pose
 * filters next to it. Stage 19 adds Favorites and History: a heart button on every photo
 * (feed, favorites or history), plus two entry points in [ExploreTopBar] that switch which
 * [ExploreSection] is shown, and a full-screen detail viewer (opening one records it into
 * History). All three sections share the same [ExplorePhotoGrid] and detail viewer.
 *
 * Stage 20: the detail viewer also offers "Use as Reference" ([onUseAsReference]), which
 * hands the tapped photo up to the caller (see [com.chikchik.posecamera.MainActivity]) and
 * closes both the dialog and Explore, so the app lands back on the camera's existing
 * reference flow - Explore itself does not know anything about the camera/reference system.
 *
 * Stage 22 (Pose Search): the "Pose" filter chip's option list now covers the 8 simple pose
 * criteria requested for Pose Search (see [PoseSearchCategory]) instead of Stage 18's smaller
 * placeholder set. It is still just one more chip contributing to the same combined query as
 * Gender/Style and the search box - no new screen, request path or state machine was added.
 */
@Composable
fun ExploreScreen(onBack: () -> Unit, onUseAsReference: (ExplorePhoto) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { ExploreLocalStore.ensureLoaded(context) }

    val favoritesMap by ExploreLocalStore.favorites.collectAsState()
    val historyEntries by ExploreLocalStore.history.collectAsState()
    val favoriteIds = favoritesMap.keys

    var section by remember { mutableStateOf(ExploreSection.FEED) }
    var detailPhoto by remember { mutableStateOf<ExplorePhoto?>(null) }

    val onToggleFavorite: (ExplorePhoto) -> Unit = { photo ->
        scope.launch { ExploreLocalStore.toggleFavorite(context, photo) }
    }
    val onOpenDetail: (ExplorePhoto) -> Unit = { photo -> detailPhoto = photo }

    // Matches CapturePreviewScreen's pattern for the system/gesture back action, extended so
    // it first closes whatever overlay is on top instead of jumping straight back to camera.
    BackHandler {
        when {
            detailPhoto != null -> detailPhoto = null
            section != ExploreSection.FEED -> section = ExploreSection.FEED
            else -> onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ExploreTopBar(
            onBack = { if (section != ExploreSection.FEED) section = ExploreSection.FEED else onBack() },
            hasFavorites = favoritesMap.isNotEmpty(),
            hasHistory = historyEntries.isNotEmpty(),
            onFavoritesClick = { section = ExploreSection.FAVORITES },
            onHistoryClick = { section = ExploreSection.HISTORY }
        )
        when (section) {
            ExploreSection.FEED -> ExploreContent(
                modifier = Modifier.weight(1f),
                favoriteIds = favoriteIds,
                onToggleFavorite = onToggleFavorite,
                onOpenDetail = onOpenDetail
            )
            ExploreSection.FAVORITES -> ExploreFavoritesSection(
                modifier = Modifier.weight(1f),
                photos = favoritesMap.values.toList().asReversed(),
                favoriteIds = favoriteIds,
                onToggleFavorite = onToggleFavorite,
                onOpenDetail = onOpenDetail,
                onBack = { section = ExploreSection.FEED }
            )
            ExploreSection.HISTORY -> ExploreHistorySection(
                modifier = Modifier.weight(1f),
                entries = historyEntries,
                favoriteIds = favoriteIds,
                onToggleFavorite = onToggleFavorite,
                onOpenDetail = onOpenDetail,
                onClearHistory = { scope.launch { ExploreLocalStore.clearHistory(context) } },
                onBack = { section = ExploreSection.FEED }
            )
        }
    }

    detailPhoto?.let { photo ->
        ExplorePhotoDetailDialog(
            photo = photo,
            isFavorite = favoriteIds.contains(photo.id),
            onToggleFavorite = { onToggleFavorite(photo) },
            onDismiss = { detailPhoto = null },
            onUseAsReference = {
                detailPhoto = null
                onUseAsReference(photo)
            },
            // Stage 23: tapping a "Similar Ideas" suggestion reuses this same callback, so it
            // opens exactly like tapping a grid thumbnail (records into History, shows its own
            // detail dialog - including its own Similar Ideas).
            onOpenDetail = onOpenDetail
        )
    }
}

/**
 * Simple top bar: Back + screen title, plus (Stage 19) two icon buttons opening Favorites
 * and History. Styled like the rest of the app's plain (non-camera) screens.
 */
@Composable
private fun ExploreTopBar(
    onBack: () -> Unit,
    hasFavorites: Boolean,
    hasHistory: Boolean,
    onFavoritesClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.back),
                    onClick = onBack
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = stringResource(R.string.explore_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        ExploreTopBarIconButton(
            icon = if (hasFavorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(R.string.explore_favorites_title),
            onClick = onFavoritesClick
        )
        ExploreTopBarIconButton(
            icon = Icons.Filled.History,
            contentDescription = stringResource(R.string.explore_history_title),
            onClick = onHistoryClick
        )
    }
}

@Composable
private fun ExploreTopBarIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** The four states the online feed can be in - kept separate from network/parsing details. */
private sealed interface ExploreUiState {
    data object Loading : ExploreUiState
    data class Loaded(val photos: List<ExplorePhoto>) : ExploreUiState
    data object Empty : ExploreUiState
    data class Error(val error: ExploreError) : ExploreUiState
}

/**
 * Stage 18: one entry of a filter category's dropdown. [keyword] is the plain English term
 * folded into the Pexels search query when this option is picked (empty for "All", meaning
 * this category contributes nothing to the query). Pexels has no structured Gender/Style/Pose
 * parameters (see [ExploreRepository]'s doc comment) - these keywords are exactly the same
 * kind of free-text term Stage 17's search box already sends, just supplied by a chip instead
 * of typing. No metadata is ever attached to a returned photo; the keyword only shapes which
 * photos the source returns for the query.
 */
private data class FilterOption(@StringRes val labelRes: Int, val keyword: String)

private val GENDER_FILTER_OPTIONS = listOf(
    FilterOption(R.string.explore_filter_all, ""),
    FilterOption(R.string.explore_filter_gender_women, "Women"),
    FilterOption(R.string.explore_filter_gender_men, "Men"),
    FilterOption(R.string.explore_filter_gender_couple, "Couple"),
)

private val STYLE_FILTER_OPTIONS = listOf(
    FilterOption(R.string.explore_filter_all, ""),
    FilterOption(R.string.explore_filter_style_casual, "Casual"),
    FilterOption(R.string.explore_filter_style_fashion, "Fashion"),
    FilterOption(R.string.explore_filter_style_gym, "Gym"),
    FilterOption(R.string.explore_filter_style_street, "Street"),
    FilterOption(R.string.explore_filter_style_indoor, "Indoor"),
    FilterOption(R.string.explore_filter_style_outdoor, "Outdoor"),
)

/**
 * Stage 22 (Pose Search): built from [PoseSearchCategory] instead of a hardcoded list, so the
 * 8 requested criteria (Standing, Sitting, Full Body, Half Body, One Hand, Two Hands, Mirror
 * Selfie, Head/Face Pose) and their grouping against ChikChik's real on-device pose model live
 * in one place - [PoseSearchCategory]'s own doc comment - instead of being duplicated here.
 * Exactly like Gender/Style, picking one only ever adds its English keyword to the same
 * free-text query [ExploreRepository] already sends; there is no separate Pose Search
 * request, screen or state machine, so it cannot conflict with the search box or the other
 * filters (requirement 7) - all of them just contribute words to one [combinedQuery] below.
 */
private val POSE_FILTER_OPTIONS = listOf(FilterOption(R.string.explore_filter_all, "")) +
    PoseSearchCategory.entries.map { FilterOption(it.labelRes, it.searchKeyword) }

/**
 * Body of the Explore screen, separated from [ExploreScreen] so a later stage (favorites,
 * ...) can build on top of this feed without reworking the top bar or the entry point.
 * Holds the search bar and the Gender/Style/Pose filters, and switches between the curated
 * feed and search results depending on whether there is any search text or active filter.
 *
 * Owns the fetch: runs it in a coroutine via [LaunchedEffect] (the same pattern the camera
 * screen already uses for its own async work), so a slow or failing connection suspends
 * here instead of blocking the UI thread.
 */
@Composable
private fun ExploreContent(
    modifier: Modifier = Modifier,
    favoriteIds: Set<String>,
    onToggleFavorite: (ExplorePhoto) -> Unit,
    onOpenDetail: (ExplorePhoto) -> Unit
) {
    // Text as the user is typing it, updated on every keystroke.
    var searchQuery by remember { mutableStateOf("") }
    // Query actually sent to the repository, updated only after SEARCH_DEBOUNCE_MS of no
    // typing (requirement 7: no request per keystroke). Blank means "no typed search text".
    var debouncedQuery by remember { mutableStateOf("") }

    // Stage 18: one selected index per filter category; index 0 is always "All" (no filter).
    var genderIndex by remember { mutableIntStateOf(0) }
    var styleIndex by remember { mutableIntStateOf(0) }
    var poseIndex by remember { mutableIntStateOf(0) }

    var state by remember { mutableStateOf<ExploreUiState>(ExploreUiState.Loading) }
    // Bumped by the "retry" button; also forces ExploreRepository to skip its cache.
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            // Clearing the field drops its contribution immediately - no need to wait.
            debouncedQuery = ""
        } else {
            delay(SEARCH_DEBOUNCE_MS)
            debouncedQuery = searchQuery.trim()
        }
    }

    // Requirement 4: filters and Stage 17's search combine into one request. Pexels has no
    // separate Gender/Style/Pose parameters (see ExploreRepository's doc comment), so each
    // active filter's keyword is folded into the same free-text query as the typed search
    // text - e.g. "Mirror Selfie" (typed) + Women + Fashion -> "Mirror Selfie Women Fashion".
    // Filter changes apply immediately (no debounce): picking a chip is a discrete action,
    // not continuous typing, so there is no burst of requests to coalesce.
    val combinedQuery = listOfNotNull(
        debouncedQuery.takeIf { it.isNotBlank() },
        GENDER_FILTER_OPTIONS[genderIndex].keyword.takeIf { it.isNotEmpty() },
        STYLE_FILTER_OPTIONS[styleIndex].keyword.takeIf { it.isNotEmpty() },
        POSE_FILTER_OPTIONS[poseIndex].keyword.takeIf { it.isNotEmpty() },
    ).joinToString(" ")

    LaunchedEffect(combinedQuery, attempt) {
        state = ExploreUiState.Loading
        val result = if (combinedQuery.isBlank()) {
            ExploreRepository.getCuratedPhotos(forceRefresh = attempt > 0)
        } else {
            ExploreRepository.searchPhotos(combinedQuery, forceRefresh = attempt > 0)
        }
        state = when (result) {
            is ExploreResult.Success ->
                if (result.photos.isEmpty()) ExploreUiState.Empty else ExploreUiState.Loaded(result.photos)
            is ExploreResult.Failure -> ExploreUiState.Error(result.error)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ExploreSearchBar(
            query = searchQuery,
            onQueryChange = {
                searchQuery = it
                attempt = 0 // a fresh query should not force-bypass the cache anymore
            },
            onClear = {
                searchQuery = ""
                attempt = 0
            }
        )
        ExploreFilterRow(
            genderIndex = genderIndex,
            onGenderSelect = { genderIndex = it; attempt = 0 },
            styleIndex = styleIndex,
            onStyleSelect = { styleIndex = it; attempt = 0 },
            poseIndex = poseIndex,
            onPoseSelect = { poseIndex = it; attempt = 0 },
            onClearFilters = {
                genderIndex = 0
                styleIndex = 0
                poseIndex = 0
                attempt = 0
            }
        )

        val isSearching = combinedQuery.isNotBlank()
        when (val current = state) {
            is ExploreUiState.Loading -> ExploreLoadingState(Modifier.weight(1f))
            is ExploreUiState.Loaded -> ExplorePhotoGrid(
                photos = current.photos,
                favoriteIds = favoriteIds,
                onToggleFavorite = onToggleFavorite,
                onOpenDetail = onOpenDetail,
                modifier = Modifier.weight(1f)
            )
            is ExploreUiState.Empty -> ExploreMessageState(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.PhotoLibrary,
                message = stringResource(
                    if (isSearching) R.string.explore_empty_search_results else R.string.explore_empty_results
                )
            )
            is ExploreUiState.Error -> ExploreErrorState(
                modifier = Modifier.weight(1f),
                error = current.error,
                onRetry = { attempt++ }
            )
        }
    }
}

/**
 * Compact search field pinned to the top of the feed (requirement 8: simple, small,
 * single row). Leading search icon, trailing clear ("X") icon that only appears once there
 * is text (requirement 6), plain placeholder text otherwise.
 */
@Composable
private fun ExploreSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.explore_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.explore_search_clear),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.explore_search_clear),
                        onClick = onClear
                    )
            )
        }
    }
}

/**
 * Stage 18: the three filter dropdowns (requirement 1: Chip/Dropdown/BottomSheet - a
 * dropdown-triggering chip per category keeps this to one compact row). Horizontally
 * scrollable so long Persian labels never force the row to wrap onto a second line.
 * A small clear ("X") chip appears only once at least one category is off "All"
 * (requirement 5).
 */
@Composable
private fun ExploreFilterRow(
    genderIndex: Int,
    onGenderSelect: (Int) -> Unit,
    styleIndex: Int,
    onStyleSelect: (Int) -> Unit,
    poseIndex: Int,
    onPoseSelect: (Int) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasActiveFilter = genderIndex != 0 || styleIndex != 0 || poseIndex != 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterDropdownChip(
            title = stringResource(R.string.explore_filter_gender),
            options = GENDER_FILTER_OPTIONS,
            selectedIndex = genderIndex,
            onSelect = onGenderSelect
        )
        FilterDropdownChip(
            title = stringResource(R.string.explore_filter_style),
            options = STYLE_FILTER_OPTIONS,
            selectedIndex = styleIndex,
            onSelect = onStyleSelect
        )
        FilterDropdownChip(
            title = stringResource(R.string.explore_filter_pose),
            options = POSE_FILTER_OPTIONS,
            selectedIndex = poseIndex,
            onSelect = onPoseSelect
        )
        if (hasActiveFilter) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.explore_filter_clear),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.explore_filter_clear),
                        onClick = onClearFilters
                    )
            )
        }
    }
}

/**
 * One filter category as a chip: shows [title] alone while on "All", or "title: value" once
 * something else is picked, and tapping it opens a plain dropdown of [options]. Highlighted
 * with the primary container color while active so an applied filter is visible at a glance.
 */
@Composable
private fun FilterDropdownChip(
    title: String,
    options: List<FilterOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isActive = selectedIndex != 0
    val labelColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(
                    if (isActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable(role = Role.Button, onClick = { expanded = true })
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isActive) {
                    stringResource(
                        R.string.explore_filter_chip_active,
                        title,
                        stringResource(options[selectedIndex].labelRes)
                    )
                } else {
                    title
                },
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ExploreLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(R.string.explore_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/** Shared layout for the "empty results" state (icon + message, no retry action). */
@Composable
private fun ExploreMessageState(icon: ImageVector, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun ExploreErrorState(error: ExploreError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val message = when (error) {
        ExploreError.NOT_CONFIGURED -> stringResource(R.string.explore_error_not_configured)
        ExploreError.NETWORK -> stringResource(R.string.explore_error_network)
        ExploreError.SERVER -> stringResource(R.string.explore_error_server)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)
        )
        // A missing API key needs a rebuild with secrets.properties filled in, not a tap -
        // retrying it again cannot succeed, so no button is shown for that one case.
        if (error != ExploreError.NOT_CONFIGURED) {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.explore_retry))
            }
        }
    }
}

@Composable
private fun ExplorePhotoGrid(
    photos: List<ExplorePhoto>,
    favoriteIds: Set<String>,
    onToggleFavorite: (ExplorePhoto) -> Unit,
    onOpenDetail: (ExplorePhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(photos, key = { it.id }) { photo ->
            ExplorePhotoThumbnail(
                photo = photo,
                isFavorite = favoriteIds.contains(photo.id),
                onToggleFavorite = { onToggleFavorite(photo) },
                onOpenDetail = { onOpenDetail(photo) }
            )
        }
    }
}

/**
 * One grid cell: downloads + decodes its own thumbnail (via [loadRemoteBitmap], which has
 * its own small in-memory cache) and shows a spinner while it loads, or a plain broken-image
 * icon if that one photo fails - never anything that blocks the rest of the grid.
 *
 * Stage 19: tapping the image opens the detail viewer ([onOpenDetail], which is where the
 * photo gets recorded into History); the heart button in the corner toggles Favorites
 * without opening anything, and reflects [isFavorite] immediately.
 */
@Composable
private fun ExplorePhotoThumbnail(
    photo: ExplorePhoto,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenDetail: () -> Unit
) {
    var bitmap by remember(photo.id) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(photo.id) { mutableStateOf(false) }

    LaunchedEffect(photo.id) {
        val decoded = loadRemoteBitmap(photo.thumbnailUrl)
        if (decoded != null) bitmap = decoded.asImageBitmap() else failed = true
    }

    val contentDescription = when {
        photo.altText.isNotBlank() -> photo.altText
        photo.photographerName.isNotBlank() ->
            stringResource(R.string.explore_photo_by, photo.photographerName)
        else -> null
    }

    val aspectRatio = if (photo.width > 0 && photo.height > 0) {
        (photo.width.toFloat() / photo.height.toFloat()).coerceIn(0.5f, 1.6f)
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onOpenDetail
            ),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        when {
            currentBitmap != null -> Image(
                bitmap = currentBitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            failed -> Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            else -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FavoriteButton(
            isFavorite = isFavorite,
            onClick = onToggleFavorite,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        )
    }
}

/** Small round heart button used on every thumbnail and in the detail viewer. */
@Composable
private fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(
        if (isFavorite) R.string.explore_favorite_remove else R.string.explore_favorite_add
    )
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = label,
            tint = if (isFavorite) MaterialTheme.colorScheme.error else Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Stage 19: Favorites section - a local list, no fetching/search/filters involved. Reuses
 * [ExplorePhotoGrid] so a favorited photo looks and behaves exactly like it does in the feed.
 */
@Composable
private fun ExploreFavoritesSection(
    photos: List<ExplorePhoto>,
    favoriteIds: Set<String>,
    onToggleFavorite: (ExplorePhoto) -> Unit,
    onOpenDetail: (ExplorePhoto) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        ExploreSubScreenHeader(title = stringResource(R.string.explore_favorites_title), onBack = onBack)
        if (photos.isEmpty()) {
            ExploreMessageState(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.FavoriteBorder,
                message = stringResource(R.string.explore_favorites_empty)
            )
        } else {
            ExplorePhotoGrid(
                photos = photos,
                favoriteIds = favoriteIds,
                onToggleFavorite = onToggleFavorite,
                onOpenDetail = onOpenDetail,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Stage 19: History section - most-recently-opened photo first. [onClearHistory] wipes it
 * (with a trash icon in the header); favoriting a photo from here works the same as anywhere
 * else and does not remove it from History.
 */
@Composable
private fun ExploreHistorySection(
    entries: List<HistoryEntry>,
    favoriteIds: Set<String>,
    onToggleFavorite: (ExplorePhoto) -> Unit,
    onOpenDetail: (ExplorePhoto) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        ExploreSubScreenHeader(
            title = stringResource(R.string.explore_history_title),
            onBack = onBack,
            trailing = {
                if (entries.isNotEmpty()) {
                    ExploreTopBarIconButton(
                        icon = Icons.Filled.DeleteSweep,
                        contentDescription = stringResource(R.string.explore_history_clear),
                        onClick = onClearHistory
                    )
                }
            }
        )
        if (entries.isEmpty()) {
            ExploreMessageState(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.History,
                message = stringResource(R.string.explore_history_empty)
            )
        } else {
            ExplorePhotoGrid(
                photos = entries.map { it.photo },
                favoriteIds = favoriteIds,
                onToggleFavorite = onToggleFavorite,
                onOpenDetail = onOpenDetail,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Shared header row for the Favorites/History sub-screens: Back + title (+ optional trailing action). */
@Composable
private fun ExploreSubScreenHeader(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExploreTopBarIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            onClick = onBack
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

/**
 * Stage 19: full-screen photo detail viewer, opened by tapping a thumbnail anywhere in
 * Explore (feed, Favorites or History). This is also the single place a photo gets recorded
 * into History ([ExploreLocalStore.recordViewed]), fired once per open via [LaunchedEffect].
 * Shows the same image ([photo.thumbnailUrl] - the only image URL Explore has, already
 * server-resized by the source) larger, plus attribution and the Favorite button.
 *
 * Stage 20: also shows a "Use as Reference" button ([onUseAsReference]). It is disabled
 * (taps show a short explanation instead of doing anything) whenever this same preview image
 * failed to load, since that is the clearest signal this photo is not currently reachable -
 * the actual reference-quality download is a separate request the camera screen makes after
 * this dialog closes, so a slow/failed reference download is reported there instead.
 *
 * Stage 23: also shows a "Similar Ideas" row ([SimilarIdeasSection]) fetched fresh for
 * whichever [photo] is currently shown. Tapping a suggestion calls [onOpenDetail] - the same
 * callback the feed/Favorites/History grids already use - which the caller wires to just swap
 * which photo this same dialog shows, so it behaves like navigating to that photo's own detail
 * (its own image, buttons and its own Similar Ideas) without closing and reopening the dialog.
 */
@Composable
private fun ExplorePhotoDetailDialog(
    photo: ExplorePhoto,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onUseAsReference: () -> Unit,
    onOpenDetail: (ExplorePhoto) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(photo.id) {
        ExploreLocalStore.recordViewed(context, photo)
    }

    var bitmap by remember(photo.id) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(photo.id) { mutableStateOf(false) }
    LaunchedEffect(photo.id) {
        val decoded = loadRemoteBitmap(photo.thumbnailUrl)
        if (decoded != null) bitmap = decoded.asImageBitmap() else failed = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val currentBitmap = bitmap
            when {
                currentBitmap != null -> Image(
                    bitmap = currentBitmap,
                    contentDescription = photo.altText.ifBlank { null },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                failed -> Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                )
                else -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
            ) {
                ExploreTopBarIconButtonOnDark(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    onClick = onDismiss
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
            ) {
                FavoriteButton(isFavorite = isFavorite, onClick = onToggleFavorite)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (currentBitmap == null || failed) {
                            Toast.makeText(
                                context,
                                R.string.explore_reference_unavailable,
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            onUseAsReference()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.explore_use_as_reference))
                }
                if (photo.photographerName.isNotBlank() || photo.sourceName.isNotBlank()) {
                    Text(
                        text = if (photo.photographerName.isNotBlank()) {
                            stringResource(R.string.explore_photo_by, photo.photographerName)
                        } else {
                            photo.sourceName
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
                SimilarIdeasSection(sourcePhoto = photo, onOpenDetail = onOpenDetail)
            }
        }
    }
}

/**
 * Stage 23: fetches and shows "Similar Ideas" for [sourcePhoto] (re-fetches whenever it
 * changes, e.g. after tapping a suggestion). Reuses [ExploreUiState] - the same four states
 * [ExploreContent] already models - so Loading/Error/Empty are handled the same simple way as
 * the rest of Explore instead of a second state model just for this row (requirement 8).
 *
 * A quiet, compact treatment on purpose: this sits inside an already busy full-image dialog,
 * and it is a secondary "by the way, also try..." feature, not core functionality - so Empty
 * (nothing reliable to suggest, e.g. blank alt text) simply shows nothing rather than a dead
 * end message, and Error shows one plain line with no retry button, since retrying an
 * incidental suggestion is not worth another network round trip (requirement 6).
 */
@Composable
private fun SimilarIdeasSection(sourcePhoto: ExplorePhoto, onOpenDetail: (ExplorePhoto) -> Unit) {
    var state by remember(sourcePhoto.id) { mutableStateOf<ExploreUiState>(ExploreUiState.Loading) }

    LaunchedEffect(sourcePhoto.id) {
        state = ExploreUiState.Loading
        state = when (val result = ExploreRepository.findSimilarPhotos(sourcePhoto)) {
            is ExploreResult.Success ->
                if (result.photos.isEmpty()) ExploreUiState.Empty else ExploreUiState.Loaded(result.photos)
            is ExploreResult.Failure -> ExploreUiState.Error(result.error)
        }
    }

    when (val current = state) {
        is ExploreUiState.Empty -> Unit
        is ExploreUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.explore_similar_ideas_loading),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
        is ExploreUiState.Error -> Text(
            text = stringResource(R.string.explore_similar_ideas_error),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White
        )
        is ExploreUiState.Loaded -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.explore_similar_ideas_title),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                current.photos.forEach { suggestion ->
                    SimilarIdeaThumbnail(photo = suggestion, onClick = { onOpenDetail(suggestion) })
                }
            }
        }
    }
}

/** One small "Similar Ideas" thumbnail - no Favorite button (its own detail dialog has one). */
@Composable
private fun SimilarIdeaThumbnail(photo: ExplorePhoto, onClick: () -> Unit) {
    var bitmap by remember(photo.id) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(photo.id) { mutableStateOf(false) }
    LaunchedEffect(photo.id) {
        val decoded = loadRemoteBitmap(photo.thumbnailUrl)
        if (decoded != null) bitmap = decoded.asImageBitmap() else failed = true
    }

    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(
                role = Role.Button,
                onClickLabel = photo.altText.ifBlank { null },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        when {
            currentBitmap != null -> Image(
                bitmap = currentBitmap,
                contentDescription = photo.altText.ifBlank { null },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            failed -> Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            else -> CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ExploreTopBarIconButtonOnDark(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}
