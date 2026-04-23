package com.github.notdeltaxd.gaana.source;

import com.github.notdeltaxd.gaana.ExtendedAudioPlaylist;
import com.github.notdeltaxd.gaana.ExtendedAudioSourceManager;
import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasearch.result.BasicAudioSearchResult;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GaanaAudioSourceManager extends ExtendedAudioSourceManager implements AudioSearchManager {

    private static final Logger log = LoggerFactory.getLogger(GaanaAudioSourceManager.class);

    public static final String SEARCH_PREFIX = "gaanasearch:";

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";

    public static final Pattern URL_PATTERN = Pattern.compile(
        "https?://(?:www\\.)?gaana\\.com/(?<type>song|album|playlist|artist)/(?<identifier>[\\w-]+)"
    );

    public static final Set<AudioSearchResult.Type> SEARCH_TYPES = Set.of(AudioSearchResult.Type.TRACK);

    private int searchLimit = 20;
    private int playlistTrackLimit = 50;
    private int recommendationsTrackLimit = 10;
    private String apiUrl = "http://localhost:8000";

    private static final int HTTP_TIMEOUT_MS = 20000;

    private void applyTimeout() {
        configureRequests(config -> RequestConfig.copy(config)
            .setConnectTimeout(HTTP_TIMEOUT_MS)
            .setSocketTimeout(HTTP_TIMEOUT_MS)
            .setConnectionRequestTimeout(HTTP_TIMEOUT_MS)
            .build());
    }

    public GaanaAudioSourceManager() {
        super();
        applyTimeout();
    }

    public GaanaAudioSourceManager(int searchLimit) {
        super();
        this.searchLimit = searchLimit > 0 ? searchLimit : 20;
        applyTimeout();
    }

    public GaanaAudioSourceManager(int searchLimit, int playlistTrackLimit, int recommendationsTrackLimit, String apiUrl) {
        super();
        this.searchLimit = searchLimit > 0 ? searchLimit : 20;
        this.playlistTrackLimit = playlistTrackLimit > 0 ? playlistTrackLimit : 50;
        this.recommendationsTrackLimit = recommendationsTrackLimit > 0 ? recommendationsTrackLimit : 10;
        if (apiUrl != null && !apiUrl.isEmpty()) {
            this.apiUrl = apiUrl.replaceAll("/+$", "");
        }
        applyTimeout();
    }

    public String getApiUrl() { return apiUrl; }

    public void setApiUrl(String apiUrl) {
        if (apiUrl != null && !apiUrl.isEmpty()) {
            this.apiUrl = apiUrl.replaceAll("/+$", "");
        }
    }

    public void setSearchLimit(int searchLimit) { this.searchLimit = searchLimit > 0 ? searchLimit : 20; }
    public void setPlaylistTrackLimit(int playlistTrackLimit) { this.playlistTrackLimit = playlistTrackLimit > 0 ? playlistTrackLimit : 50; }
    public void setRecommendationsTrackLimit(int recommendationsTrackLimit) { this.recommendationsTrackLimit = recommendationsTrackLimit > 0 ? recommendationsTrackLimit : 10; }

    @Override
    public String getSourceName() { return "gaana"; }

    // ── LavaSearch ───────────────────────────────────────────────────────────

    @Nullable
    @Override
    public AudioSearchResult loadSearch(@NotNull String query, @NotNull Set<AudioSearchResult.Type> types) {
        if (!query.startsWith(SEARCH_PREFIX)) return null;
        if (types.isEmpty()) types = SEARCH_TYPES;
        if (!types.contains(AudioSearchResult.Type.TRACK)) return null;
        try {
            AudioItem result = search(query.substring(SEARCH_PREFIX.length()).trim());
            if (result == AudioReference.NO_TRACK || !(result instanceof AudioPlaylist)) return null;
            List<AudioTrack> tracks = ((AudioPlaylist) result).getTracks();
            if (tracks.isEmpty()) return null;
            return new BasicAudioSearchResult(tracks, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        } catch (Exception e) {
            log.error("Gaana LavaSearch failed", e);
            return null;
        }
    }

    // ── loadItem ─────────────────────────────────────────────────────────────

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        try {
            String identifier = reference.identifier;

            if (identifier.startsWith(SEARCH_PREFIX)) {
                return search(identifier.substring(SEARCH_PREFIX.length()).trim());
            }

            Matcher matcher = URL_PATTERN.matcher(identifier);
            if (matcher.find()) {
                String type = matcher.group("type");
                String id   = matcher.group("identifier");

                switch (type) {
                    case "song":     return loadSong(id);
                    case "album":    return loadAlbum(id);
                    case "playlist": return loadPlaylist(id);
                    case "artist":   return loadArtist(id);
                }
            }

            return null;
        } catch (IOException e) {
            throw new FriendlyException("Failed to load Gaana item", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    // ── Search ───────────────────────────────────────────────────────────────

    private AudioItem search(String query) throws IOException {
        try (HttpInterface httpInterface = getHttpInterface()) {
            String url = apiUrl + "/songs/search/?query=" + URLEncoder.encode(query, "UTF-8")
                + "&limit=" + searchLimit;

            HttpGet request = new HttpGet(url);
            request.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpInterface.execute(request)) {
                if (response.getStatusLine().getStatusCode() != 200) return AudioReference.NO_TRACK;

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (json.isNull() || json.values().isEmpty()) return AudioReference.NO_TRACK;

                List<AudioTrack> results = new ArrayList<>();
                for (JsonBrowser item : json.values()) {
                    AudioTrack track = mapGaanaPyTrack(item);
                    if (track != null) {
                        results.add(track);
                        if (results.size() >= searchLimit) break;
                    }
                }

                if (results.isEmpty()) return AudioReference.NO_TRACK;
                return new BasicAudioPlaylist("Gaana Search: " + query, results, null, true);
            }
        }
    }

    // ── Song ─────────────────────────────────────────────────────────────────

    private AudioItem loadSong(String seokey) throws IOException {
        try (HttpInterface httpInterface = getHttpInterface()) {
            String url = apiUrl + "/songs/info/?seokey=" + URLEncoder.encode(seokey, "UTF-8");

            HttpGet request = new HttpGet(url);
            request.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpInterface.execute(request)) {
                if (response.getStatusLine().getStatusCode() != 200) return AudioReference.NO_TRACK;

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (json.isNull() || json.values().isEmpty()) return AudioReference.NO_TRACK;

                AudioTrack track = mapGaanaPyTrack(json.index(0));
                return track != null ? track : AudioReference.NO_TRACK;
            }
        }
    }

    // ── Album ─────────────────────────────────────────────────────────────────

    private AudioItem loadAlbum(String seokey) throws IOException {
        try (HttpInterface httpInterface = getHttpInterface()) {
            String url = apiUrl + "/albums/info/?seokey=" + URLEncoder.encode(seokey, "UTF-8");
            log.debug("Loading album: {}", url);

            HttpGet request = new HttpGet(url);
            request.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpInterface.execute(request)) {
                int status = response.getStatusLine().getStatusCode();
                if (status != 200) { log.warn("Album info returned HTTP {}", status); return AudioReference.NO_TRACK; }

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (json.isNull() || json.values().isEmpty()) return AudioReference.NO_TRACK;

                JsonBrowser albumObj = json.index(0);
                if (albumObj.isNull()) return AudioReference.NO_TRACK;

                String albumName = albumObj.get("title").text();
                if (albumName == null) albumName = seokey;

                String artworkUrl = albumObj.get("images").get("urls").get("large_artwork").text();

                JsonBrowser tracks = albumObj.get("tracks");
                if (tracks.isNull() || tracks.values().isEmpty()) return AudioReference.NO_TRACK;

                List<AudioTrack> trackList = new ArrayList<>();
                for (JsonBrowser item : tracks.values()) {
                    AudioTrack track = mapGaanaPyTrack(item);
                    if (track != null) {
                        trackList.add(track);
                        if (trackList.size() >= playlistTrackLimit) break;
                    }
                }

                if (trackList.isEmpty()) return AudioReference.NO_TRACK;
                return new GaanaAudioPlaylist(albumName, trackList, ExtendedAudioPlaylist.Type.ALBUM,
                    "https://gaana.com/album/" + seokey, artworkUrl, null, trackList.size());
            }
        }
    }

    // ── Playlist ─────────────────────────────────────────────────────────────

    private AudioItem loadPlaylist(String seokey) throws IOException {
        try (HttpInterface httpInterface = getHttpInterface()) {
            String url = apiUrl + "/playlists/info/?seokey=" + URLEncoder.encode(seokey, "UTF-8");

            HttpGet request = new HttpGet(url);
            request.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpInterface.execute(request)) {
                if (response.getStatusLine().getStatusCode() != 200) return AudioReference.NO_TRACK;

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (json.isNull() || json.values().isEmpty()) return AudioReference.NO_TRACK;

                List<AudioTrack> trackList = new ArrayList<>();
                String artworkUrl = null;

                for (JsonBrowser item : json.values()) {
                    if (artworkUrl == null) artworkUrl = item.get("images").get("urls").get("large_artwork").text();
                    AudioTrack track = mapGaanaPyTrack(item);
                    if (track != null) {
                        trackList.add(track);
                        if (trackList.size() >= playlistTrackLimit) break;
                    }
                }

                if (trackList.isEmpty()) return AudioReference.NO_TRACK;
                return new GaanaAudioPlaylist(seokey, trackList, ExtendedAudioPlaylist.Type.PLAYLIST,
                    "https://gaana.com/playlist/" + seokey, artworkUrl, null, trackList.size());
            }
        }
    }

    // ── Artist ───────────────────────────────────────────────────────────────

    private AudioItem loadArtist(String seokey) throws IOException {
        try (HttpInterface httpInterface = getHttpInterface()) {
            String url = apiUrl + "/artists/info/?seokey=" + URLEncoder.encode(seokey, "UTF-8");
            log.debug("Loading artist: {}", url);

            HttpGet request = new HttpGet(url);
            request.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpInterface.execute(request)) {
                int status = response.getStatusLine().getStatusCode();
                if (status != 200) { log.warn("Artist info returned HTTP {}", status); return AudioReference.NO_TRACK; }

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (json.isNull() || json.values().isEmpty()) return AudioReference.NO_TRACK;

                JsonBrowser artistObj = json.index(0);
                if (artistObj.isNull()) return AudioReference.NO_TRACK;

                String artistName = artistObj.get("name").text();
                if (artistName == null) artistName = seokey;

                String artworkUrl = artistObj.get("images").get("urls").get("large_artwork").text();

                JsonBrowser topTracks = artistObj.get("top_tracks");
                if (topTracks.isNull() || topTracks.values().isEmpty()) return AudioReference.NO_TRACK;

                List<AudioTrack> trackList = new ArrayList<>();
                for (JsonBrowser item : topTracks.values()) {
                    AudioTrack track = mapGaanaPyTrack(item);
                    if (track != null) {
                        trackList.add(track);
                        if (trackList.size() >= recommendationsTrackLimit) break;
                    }
                }

                if (trackList.isEmpty()) return AudioReference.NO_TRACK;
                return new GaanaAudioPlaylist(artistName + "'s Top Tracks", trackList, ExtendedAudioPlaylist.Type.ARTIST,
                    "https://gaana.com/artist/" + seokey, artworkUrl, artistName, trackList.size());
            }
        }
    }

    // ── Map GaanaPy JSON → AudioTrack ────────────────────────────────────────

    private AudioTrack mapGaanaPyTrack(JsonBrowser item) {
        if (item == null || item.isNull()) return null;

        String seokey = item.get("seokey").text();
        if (seokey == null || seokey.isEmpty()) return null;

        String title = item.get("title").text();
        if (title == null || title.isEmpty()) return null;

        String artist = item.get("artists").text();
        if (artist == null || artist.isEmpty()) artist = "Unknown Artist";

        long duration = item.get("duration").asLong(0) * 1000;

        String artwork = item.get("images").get("urls").get("large_artwork").text();
        if (artwork == null) artwork = item.get("images").get("urls").get("medium_artwork").text();

        String uri = item.get("song_url").text();
        if (uri == null) uri = "https://gaana.com/song/" + seokey;

        String isrc = item.get("isrc").text();

        AudioTrackInfo trackInfo = new AudioTrackInfo(title, artist, duration, seokey, false, uri, artwork, isrc);
        return new GaanaAudioTrack(trackInfo, this);
    }

    // ── Encode/Decode ────────────────────────────────────────────────────────

    @Override
    public boolean isTrackEncodable(AudioTrack track) { return true; }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {}

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new GaanaAudioTrack(trackInfo, this);
    }
}
