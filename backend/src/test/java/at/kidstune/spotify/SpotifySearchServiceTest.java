package at.kidstune.spotify;

import at.kidstune.spotify.dto.SearchResultsResponse;
import at.kidstune.spotify.dto.SpotifyItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpotifySearchServiceTest {

    @Mock
    SpotifyWebApiClient apiClient;

    SpotifySearchService service;

    static final String FAMILY_ID = "family-123";

    @BeforeEach
    void setUp() {
        service = new SpotifySearchService(apiClient);
    }

    @Test
    void search_groupsResultsByType() {
        RawSearchData rawData = new RawSearchData(
            List.of(new RawSpotifyItem("a1", "Artist One", "img-a1", "spotify:artist:a1", null, false)),
            List.of(new RawSpotifyItem("b1", "Album One", "img-b1", "spotify:album:b1", "Artist One", false)),
            List.of(new RawSpotifyItem("p1", "Playlist One", "img-p1", "spotify:playlist:p1", "Spotify", false))
        );
        when(apiClient.search(anyString(), anyString(), anyInt())).thenReturn(Mono.just(rawData));

        StepVerifier.create(service.search(FAMILY_ID, "bibi", 10))
            .assertNext(result -> {
                assertThat(result.artists()).hasSize(1);
                assertThat(result.albums()).hasSize(1);
                assertThat(result.playlists()).hasSize(1);

                assertThat(result.artists().get(0).id()).isEqualTo("a1");
                assertThat(result.albums().get(0).id()).isEqualTo("b1");
                assertThat(result.playlists().get(0).id()).isEqualTo("p1");
            })
            .verifyComplete();
    }

    @Test
    void search_filtersExplicitAlbums() {
        RawSearchData rawData = new RawSearchData(
            List.of(),
            List.of(
                new RawSpotifyItem("clean-album", "Clean Album", "img", "spotify:album:clean-album", "Artist", false),
                new RawSpotifyItem("explicit-album", "Explicit Album", "img", "spotify:album:explicit-album", "Artist", true)
            ),
            List.of()
        );
        when(apiClient.search(anyString(), anyString(), anyInt())).thenReturn(Mono.just(rawData));

        StepVerifier.create(service.search(FAMILY_ID, "test", 10))
            .assertNext(result -> {
                assertThat(result.albums()).hasSize(1);
                assertThat(result.albums().get(0).id()).isEqualTo("clean-album");
            })
            .verifyComplete();
    }

    @Test
    void search_filtersExplicitPlaylists() {
        RawSearchData rawData = new RawSearchData(
            List.of(),
            List.of(),
            List.of(
                new RawSpotifyItem("clean-pl", "Clean Playlist", "img", "spotify:playlist:clean-pl", "Owner", false),
                new RawSpotifyItem("explicit-pl", "Explicit Playlist", "img", "spotify:playlist:explicit-pl", "Owner", true)
            )
        );
        when(apiClient.search(anyString(), anyString(), anyInt())).thenReturn(Mono.just(rawData));

        StepVerifier.create(service.search(FAMILY_ID, "test", 10))
            .assertNext(result -> {
                assertThat(result.playlists()).hasSize(1);
                assertThat(result.playlists().get(0).id()).isEqualTo("clean-pl");
            })
            .verifyComplete();
    }

    @Test
    void search_doesNotFilterArtists() {
        // Artists have no explicit flag — all should be returned
        RawSearchData rawData = new RawSearchData(
            List.of(
                new RawSpotifyItem("a1", "Artist One", null, "spotify:artist:a1", null, false),
                new RawSpotifyItem("a2", "Artist Two", null, "spotify:artist:a2", null, false)
            ),
            List.of(),
            List.of()
        );
        when(apiClient.search(anyString(), anyString(), anyInt())).thenReturn(Mono.just(rawData));

        StepVerifier.create(service.search(FAMILY_ID, "test", 10))
            .assertNext(result -> assertThat(result.artists()).hasSize(2))
            .verifyComplete();
    }

    @Test
    void search_limitsResultsToMaxPerType() {
        List<RawSpotifyItem> manyAlbums = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            manyAlbums.add(new RawSpotifyItem("album-" + i, "Album " + i, null, "spotify:album:" + i, "Artist", false));
        }
        RawSearchData rawData = new RawSearchData(List.of(), manyAlbums, List.of());
        when(apiClient.search(anyString(), anyString(), anyInt())).thenReturn(Mono.just(rawData));

        StepVerifier.create(service.search(FAMILY_ID, "test", 10))
            .assertNext(result -> assertThat(result.albums()).hasSize(SpotifySearchService.MAX_PER_TYPE))
            .verifyComplete();
    }

    @Test
    void search_mapsEnrichedDtoFields() {
        RawSearchData rawData = new RawSearchData(
            List.of(),
            List.of(new RawSpotifyItem("id-1", "My Album", "https://img.jpg", "spotify:album:id-1", "Cool Artist", false)),
            List.of()
        );
        when(apiClient.search(anyString(), anyString(), anyInt())).thenReturn(Mono.just(rawData));

        StepVerifier.create(service.search(FAMILY_ID, "test", 10))
            .assertNext(result -> {
                SpotifyItemDto dto = result.albums().get(0);
                assertThat(dto.id()).isEqualTo("id-1");
                assertThat(dto.title()).isEqualTo("My Album");
                assertThat(dto.imageUrl()).isEqualTo("https://img.jpg");
                assertThat(dto.spotifyUri()).isEqualTo("spotify:album:id-1");
                assertThat(dto.artistName()).isEqualTo("Cool Artist");
            })
            .verifyComplete();
    }
}
