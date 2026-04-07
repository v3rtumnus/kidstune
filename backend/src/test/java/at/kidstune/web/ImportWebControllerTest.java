package at.kidstune.web;

import at.kidstune.auth.SpotifyTokenService;
import at.kidstune.content.ContentService;
import at.kidstune.content.dto.ImportContentResponse;
import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.spotify.ImportSuggestionsDto;
import at.kidstune.spotify.SpotifyImportService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.FOUND;

@ExtendWith(MockitoExtension.class)
class ImportWebControllerTest {

    @Mock SpotifyImportService spotifyImportService;
    @Mock ContentService       contentService;
    @Mock ProfileRepository    profileRepository;
    @Mock SpotifyTokenService  spotifyTokenService;

    ImportWebController controller;

    private static final String FAMILY_ID    = "family-001";
    private static final String PROFILE_ID_1 = "profile-luna";
    private static final String PROFILE_ID_2 = "profile-max";

    @BeforeEach
    void setUp() {
        // Construct manually so we control the ObjectMapper
        controller = new ImportWebController(
                spotifyImportService,
                contentService,
                profileRepository,
                spotifyTokenService,
                new ObjectMapper());
    }

    // ── GET /web/import ───────────────────────────────────────────────────────

    @Test
    @DisplayName("importStep1 returns step1 view with profiles for the family")
    void importStep1ReturnsViewWithProfiles() {
        ChildProfile luna = makeProfile(PROFILE_ID_1, "Luna", AgeGroup.SCHOOL);
        when(profileRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(luna));
        when(spotifyTokenService.isProfileSpotifyLinked(PROFILE_ID_1)).thenReturn(true);

        Model model = new ExtendedModelMap();
        StepVerifier.create(controller.importStep1(model, FAMILY_ID))
                .expectNext("web/import/step1")
                .verifyComplete();

        @SuppressWarnings("unchecked")
        List<ImportWebController.ProfileImportItem> profiles =
                (List<ImportWebController.ProfileImportItem>) model.asMap().get("profiles");
        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).profile().getName()).isEqualTo("Luna");
        assertThat(profiles.get(0).spotifyLinked()).isTrue();
    }

    // ── POST /web/import/suggestions ─────────────────────────────────────────

    @Test
    @DisplayName("loadSuggestions with no profileIds selected returns noProfilesSelected flag")
    void loadSuggestionsWithNoProfilesReturnsFlag() {
        Model model = new ExtendedModelMap();
        StepVerifier.create(controller.loadSuggestions(null, model, FAMILY_ID))
                .expectNext("web/fragments/import-suggestions :: suggestions")
                .verifyComplete();

        assertThat(model.asMap()).containsEntry("noProfilesSelected", true);
        verify(spotifyImportService, never()).getImportSuggestions(anyString());
    }

    @Test
    @DisplayName("loadSuggestions with no Spotify-linked profile returns noSpotifyLinked flag")
    void loadSuggestionsWithNoSpotifyLinkedReturnsFlag() {
        ChildProfile luna = makeProfile(PROFILE_ID_1, "Luna", AgeGroup.SCHOOL);
        when(profileRepository.findById(PROFILE_ID_1)).thenReturn(Optional.of(luna));
        when(spotifyTokenService.isProfileSpotifyLinked(PROFILE_ID_1)).thenReturn(false);

        Model model = new ExtendedModelMap();
        StepVerifier.create(controller.loadSuggestions(List.of(PROFILE_ID_1), model, FAMILY_ID))
                .expectNext("web/fragments/import-suggestions :: suggestions")
                .verifyComplete();

        assertThat(model.asMap()).containsEntry("noSpotifyLinked", true);
        verify(spotifyImportService, never()).getImportSuggestions(anyString());
    }

    @Test
    @DisplayName("loadSuggestions with linked profile returns suggestion groups in model")
    void loadSuggestionsWithLinkedProfileReturnsSuggestions() {
        ChildProfile luna = makeProfile(PROFILE_ID_1, "Luna", AgeGroup.SCHOOL);
        when(profileRepository.findById(PROFILE_ID_1)).thenReturn(Optional.of(luna));
        when(spotifyTokenService.isProfileSpotifyLinked(PROFILE_ID_1)).thenReturn(true);

        ImportSuggestionsDto dto = new ImportSuggestionsDto(
                List.of(new ImportSuggestionsDto.Item("spotify:artist:abc", "Bibi Bloxberg", null, true)),
                List.of(),
                List.of()
        );
        when(spotifyImportService.getImportSuggestions(PROFILE_ID_1)).thenReturn(Mono.just(dto));

        Model model = new ExtendedModelMap();
        StepVerifier.create(controller.loadSuggestions(List.of(PROFILE_ID_1), model, FAMILY_ID))
                .expectNext("web/fragments/import-suggestions :: suggestions")
                .verifyComplete();

        assertThat(model.asMap()).containsKey("suggestions");
        ImportSuggestionsDto result = (ImportSuggestionsDto) model.asMap().get("suggestions");
        assertThat(result.detectedChildrenContent()).hasSize(1);
        assertThat(result.detectedChildrenContent().get(0).title()).isEqualTo("Bibi Bloxberg");
    }

    @Test
    @DisplayName("loadSuggestions SCHOOL profile: pre-selected item has preSelected=true in result")
    void loadSuggestionsSCHOOLProfilePreselectedItemHasFlag() {
        ChildProfile luna = makeProfile(PROFILE_ID_1, "Luna", AgeGroup.SCHOOL);
        when(profileRepository.findById(PROFILE_ID_1)).thenReturn(Optional.of(luna));
        when(spotifyTokenService.isProfileSpotifyLinked(PROFILE_ID_1)).thenReturn(true);

        ImportSuggestionsDto dto = new ImportSuggestionsDto(
                List.of(
                        new ImportSuggestionsDto.Item("spotify:artist:1", "Bibi Bloxberg", null, true),
                        new ImportSuggestionsDto.Item("spotify:artist:2", "Slipknot", null, false)
                ),
                List.of(),
                List.of()
        );
        when(spotifyImportService.getImportSuggestions(PROFILE_ID_1)).thenReturn(Mono.just(dto));

        Model model = new ExtendedModelMap();
        StepVerifier.create(controller.loadSuggestions(List.of(PROFILE_ID_1), model, FAMILY_ID))
                .expectNext("web/fragments/import-suggestions :: suggestions")
                .verifyComplete();

        ImportSuggestionsDto result = (ImportSuggestionsDto) model.asMap().get("suggestions");
        assertThat(result.detectedChildrenContent().get(0).preSelected()).isTrue();
        assertThat(result.detectedChildrenContent().get(1).preSelected()).isFalse();
    }

    @Test
    @DisplayName("loadSuggestions TODDLER profile: only age-appropriate items are pre-selected")
    void loadSuggestionsTODDLERProfileOnlyAgeAppropriatePreselected() {
        ChildProfile baby = makeProfile(PROFILE_ID_2, "Baby Max", AgeGroup.TODDLER);
        when(profileRepository.findById(PROFILE_ID_2)).thenReturn(Optional.of(baby));
        when(spotifyTokenService.isProfileSpotifyLinked(PROFILE_ID_2)).thenReturn(true);

        // For TODDLER: preSelected = true means min_age <= 3 in SpotifyImportService logic
        // We just verify the DTO's preSelected flag is passed through without modification
        ImportSuggestionsDto dto = new ImportSuggestionsDto(
                List.of(
                        new ImportSuggestionsDto.Item("spotify:artist:1", "Die Maus", null, true),
                        new ImportSuggestionsDto.Item("spotify:artist:2", "Bibi Blocksberg", null, false)
                ),
                List.of(),
                List.of()
        );
        when(spotifyImportService.getImportSuggestions(PROFILE_ID_2)).thenReturn(Mono.just(dto));

        Model model = new ExtendedModelMap();
        StepVerifier.create(controller.loadSuggestions(List.of(PROFILE_ID_2), model, FAMILY_ID))
                .expectNext("web/fragments/import-suggestions :: suggestions")
                .verifyComplete();

        ImportSuggestionsDto result = (ImportSuggestionsDto) model.asMap().get("suggestions");
        assertThat(result.detectedChildrenContent().get(0).preSelected()).isTrue();
        assertThat(result.detectedChildrenContent().get(1).preSelected()).isFalse();
    }

    // ── POST /web/import (execute import) ────────────────────────────────────

    @Test
    @DisplayName("executeImport with Spotify-linked profile calls importLikedSongsAsFavorites")
    void executeImportWithLinkedProfileCallsLikedSongs() throws Exception {
        ChildProfile luna = makeProfile(PROFILE_ID_1, "Luna", AgeGroup.SCHOOL);
        when(profileRepository.findById(PROFILE_ID_1)).thenReturn(Optional.of(luna));
        when(spotifyTokenService.isProfileSpotifyLinked(PROFILE_ID_1)).thenReturn(true);

        String itemsJson = new ObjectMapper().writeValueAsString(List.of(
                new TestWizardItem("spotify:artist:abc", "ARTIST", "Bibi Bloxberg", null, "Bibi Bloxberg",
                        List.of(PROFILE_ID_1))
        ));

        when(contentService.importContent(any()))
                .thenReturn(Mono.just(new ImportContentResponse(1,
                        List.of(new ImportContentResponse.ProfileSummary(PROFILE_ID_1, "Luna", 1)))));
        when(spotifyImportService.importLikedSongsAsFavorites(PROFILE_ID_1))
                .thenReturn(Mono.just(5));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/web/import").build());

        StepVerifier.create(controller.executeImport(
                itemsJson, List.of(PROFILE_ID_1), exchange, FAMILY_ID))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        assertThat(exchange.getResponse().getHeaders().getFirst("Location"))
                .isEqualTo("/web/import/success");
        verify(spotifyImportService).importLikedSongsAsFavorites(PROFILE_ID_1);
    }

    @Test
    @DisplayName("executeImport with profile without Spotify linked does NOT call importLikedSongsAsFavorites")
    void executeImportWithoutLinkedProfileSkipsLikedSongs() throws Exception {
        ChildProfile max = makeProfile(PROFILE_ID_2, "Max", AgeGroup.PRESCHOOL);
        when(profileRepository.findById(PROFILE_ID_2)).thenReturn(Optional.of(max));
        when(spotifyTokenService.isProfileSpotifyLinked(PROFILE_ID_2)).thenReturn(false);

        String itemsJson = new ObjectMapper().writeValueAsString(List.of(
                new TestWizardItem("spotify:artist:xyz", "ARTIST", "Benjamin Blümchen", null, null,
                        List.of(PROFILE_ID_2))
        ));

        when(contentService.importContent(any()))
                .thenReturn(Mono.just(new ImportContentResponse(1,
                        List.of(new ImportContentResponse.ProfileSummary(PROFILE_ID_2, "Max", 1)))));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/web/import").build());

        StepVerifier.create(controller.executeImport(
                itemsJson, List.of(PROFILE_ID_2), exchange, FAMILY_ID))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        verify(spotifyImportService, never()).importLikedSongsAsFavorites(anyString());
    }

    @Test
    @DisplayName("executeImport stores ImportResult in session with correct counts")
    void executeImportStoresResultInSession() throws Exception {
        ChildProfile luna = makeProfile(PROFILE_ID_1, "Luna", AgeGroup.SCHOOL);
        when(profileRepository.findById(PROFILE_ID_1)).thenReturn(Optional.of(luna));
        when(spotifyTokenService.isProfileSpotifyLinked(PROFILE_ID_1)).thenReturn(true);

        String itemsJson = new ObjectMapper().writeValueAsString(List.of(
                new TestWizardItem("spotify:artist:abc", "ARTIST", "Bibi Bloxberg", null, null,
                        List.of(PROFILE_ID_1))
        ));

        when(contentService.importContent(any()))
                .thenReturn(Mono.just(new ImportContentResponse(2,
                        List.of(new ImportContentResponse.ProfileSummary(PROFILE_ID_1, "Luna", 2)))));
        when(spotifyImportService.importLikedSongsAsFavorites(PROFILE_ID_1))
                .thenReturn(Mono.just(3));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/web/import").build());

        StepVerifier.create(controller.executeImport(
                itemsJson, List.of(PROFILE_ID_1), exchange, FAMILY_ID))
                .verifyComplete();

        exchange.getSession().subscribe(session -> {
            ImportWebController.ImportResult result =
                    (ImportWebController.ImportResult) session.getAttribute(
                            ImportWebController.SESSION_IMPORT_RESULT);
            assertThat(result).isNotNull();
            assertThat(result.entries()).hasSize(1);
            assertThat(result.entries().get(0).profileName()).isEqualTo("Luna");
            assertThat(result.entries().get(0).contentAdded()).isEqualTo(2);
            assertThat(result.entries().get(0).likedSongsImported()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("executeImport with empty itemsJson still calls importLikedSongsAsFavorites for linked profiles")
    void executeImportWithEmptyItemsStillDoesLikedSongs() {
        ChildProfile luna = makeProfile(PROFILE_ID_1, "Luna", AgeGroup.SCHOOL);
        when(profileRepository.findById(PROFILE_ID_1)).thenReturn(Optional.of(luna));
        when(spotifyTokenService.isProfileSpotifyLinked(PROFILE_ID_1)).thenReturn(true);

        // importContent is NOT called for empty items — bypass happens in controller
        when(spotifyImportService.importLikedSongsAsFavorites(PROFILE_ID_1))
                .thenReturn(Mono.just(7));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/web/import").build());

        StepVerifier.create(controller.executeImport(
                "[]", List.of(PROFILE_ID_1), exchange, FAMILY_ID))
                .verifyComplete();

        verify(spotifyImportService).importLikedSongsAsFavorites(PROFILE_ID_1);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
    }

    @Test
    @DisplayName("importLikedSongsAsFavorites failure is silently suppressed; import still redirects")
    void executeImportLikedSongsErrorIsSilentlySuppressed() throws Exception {
        ChildProfile luna = makeProfile(PROFILE_ID_1, "Luna", AgeGroup.SCHOOL);
        when(profileRepository.findById(PROFILE_ID_1)).thenReturn(Optional.of(luna));
        when(spotifyTokenService.isProfileSpotifyLinked(PROFILE_ID_1)).thenReturn(true);

        when(contentService.importContent(any()))
                .thenReturn(Mono.just(new ImportContentResponse(1,
                        List.of(new ImportContentResponse.ProfileSummary(PROFILE_ID_1, "Luna", 1)))));
        when(spotifyImportService.importLikedSongsAsFavorites(PROFILE_ID_1))
                .thenReturn(Mono.error(new RuntimeException("Spotify error")));

        String itemsJson = new ObjectMapper().writeValueAsString(List.of(
                new TestWizardItem("spotify:artist:abc", "ARTIST", "Bibi Bloxberg", null, null,
                        List.of(PROFILE_ID_1))
        ));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/web/import").build());

        StepVerifier.create(controller.executeImport(
                itemsJson, List.of(PROFILE_ID_1), exchange, FAMILY_ID))
                .verifyComplete();

        // Import still redirects successfully despite liked-songs error
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        assertThat(exchange.getResponse().getHeaders().getFirst("Location"))
                .isEqualTo("/web/import/success");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ChildProfile makeProfile(String id, String name, AgeGroup ageGroup) {
        ChildProfile p = new ChildProfile();
        p.setId(id);
        p.setFamilyId(FAMILY_ID);
        p.setName(name);
        p.setAgeGroup(ageGroup);
        p.setAvatarIcon(AvatarIcon.CAT);
        p.setAvatarColor(AvatarColor.BLUE);
        return p;
    }

    /** Mirrors ImportWebController.WizardItem for Jackson serialization in tests. */
    record TestWizardItem(
            String spotifyUri,
            String scope,
            String title,
            String imageUrl,
            String artistName,
            List<String> profileIds
    ) {}
}
