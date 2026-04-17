package at.kidstune.kids.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import at.kidstune.kids.ui.viewmodel.PlaylistTrackListViewModel
import at.kidstune.kids.ui.viewmodel.TrackListIntent

@Composable
fun PlaylistTrackListScreen(
    modifier: Modifier = Modifier,
    viewModel: PlaylistTrackListViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit = {},
    onNavigateToNowPlaying: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.navigateToNowPlaying) {
        if (state.navigateToNowPlaying) {
            onNavigateToNowPlaying()
            viewModel.onIntent(TrackListIntent.NavigationHandled)
        }
    }

    TrackListScreen(
        modifier     = modifier,
        state        = state,
        onIntent     = viewModel::onIntent,
        onNavigateUp = onNavigateUp
    )
}
