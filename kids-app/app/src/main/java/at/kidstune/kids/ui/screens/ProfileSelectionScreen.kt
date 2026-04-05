package at.kidstune.kids.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import at.kidstune.kids.domain.model.MockProfile
import at.kidstune.kids.domain.model.mockProfiles
import at.kidstune.kids.ui.theme.AudiobookPrimary
import at.kidstune.kids.ui.theme.DiscoverPrimary
import at.kidstune.kids.ui.theme.FavoritePrimary
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.theme.MusicPrimary
import at.kidstune.kids.ui.viewmodel.ProfileSelectionIntent
import at.kidstune.kids.ui.viewmodel.ProfileSelectionState
import at.kidstune.kids.ui.viewmodel.ProfileSelectionViewModel

// ── Stateful entry-point (used by NavHost) ────────────────────────────────

@Composable
fun ProfileSelectionScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileSelectionViewModel = hiltViewModel(),
    onProfileBound: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    ProfileSelectionScreen(
        modifier       = modifier,
        state          = state,
        onIntent       = { intent ->
            viewModel.onIntent(intent)
            if (intent == ProfileSelectionIntent.ConfirmBinding) onProfileBound()
        }
    )
}

// ── Stateless composable (used in Previews and tests) ─────────────────────

@Composable
fun ProfileSelectionScreen(
    modifier: Modifier = Modifier,
    state: ProfileSelectionState,
    onIntent: (ProfileSelectionIntent) -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text      = "Willkommen bei KidsTune!",
                style     = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "Wer bist du?",
                style     = MaterialTheme.typography.headlineMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                state.profiles.forEach { profile ->
                    ProfileAvatarTile(
                        profile         = profile,
                        backgroundColor = profileBackgroundColor(profile.id),
                        onClick         = { onIntent(ProfileSelectionIntent.SelectProfile(profile)) }
                    )
                }
            }
        }

        // Confirmation dialog
        state.pendingProfile?.let { profile ->
            AlertDialog(
                onDismissRequest = { onIntent(ProfileSelectionIntent.DismissConfirmation) },
                title = {
                    Text(
                        text      = "Bist du ${profile.name}?",
                        style     = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Text(
                        text      = "Dieses Gerät wird für ${profile.name} eingerichtet.",
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onIntent(ProfileSelectionIntent.ConfirmBinding) }
                    ) {
                        Text("Ja, ich bin's!")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { onIntent(ProfileSelectionIntent.DismissConfirmation) }
                    ) {
                        Text("Nein")
                    }
                }
            )
        }
    }
}

@Composable
private fun ProfileAvatarTile(
    modifier: Modifier = Modifier,
    profile: MockProfile,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .size(140.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Profil: ${profile.name}" },
        shape     = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors    = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = profile.emoji, fontSize = 56.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text  = profile.name,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }
    }
}

private val profileColorPalette = listOf(MusicPrimary, AudiobookPrimary, DiscoverPrimary, FavoritePrimary)

private fun profileBackgroundColor(profileId: String): Color = when (profileId) {
    "profile-luna" -> MusicPrimary
    "profile-max"  -> AudiobookPrimary
    else           -> profileColorPalette[Math.abs(profileId.hashCode()) % profileColorPalette.size]
}

// ── Previews ──────────────────────────────────────────────────────────────

@Preview(name = "ProfileSelectionScreen", showBackground = true, showSystemUi = true)
@Composable
private fun ProfileSelectionScreenPreview() {
    KidstuneTheme {
        ProfileSelectionScreen(
            state = ProfileSelectionState(profiles = mockProfiles)
        )
    }
}

@Preview(name = "ProfileSelectionScreen – confirmation dialog", showBackground = true, showSystemUi = true)
@Composable
private fun ProfileSelectionConfirmPreview() {
    KidstuneTheme {
        ProfileSelectionScreen(
            state = ProfileSelectionState(
                profiles       = mockProfiles,
                pendingProfile = mockProfiles.first()
            )
        )
    }
}
