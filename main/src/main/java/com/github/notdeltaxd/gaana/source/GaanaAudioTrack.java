package com.github.notdeltaxd.gaana.source;

import com.sedmelluq.discord.lavaplayer.container.adts.AdtsAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpegts.MpegTsElementaryInputStream;
import com.sedmelluq.discord.lavaplayer.container.mpegts.PesPacketInputStream;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URLEncoder;

public class GaanaAudioTrack extends DelegatedAudioTrack {

    private static final Logger log = LoggerFactory.getLogger(GaanaAudioTrack.class);

    private final GaanaAudioSourceManager sourceManager;
    private volatile GaanaHlsInputStream hlsStream;
    private volatile boolean seeking;
    private volatile long seekTarget;
    private volatile boolean tokenExpired;

    public GaanaAudioTrack(AudioTrackInfo trackInfo, GaanaAudioSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        executor.executeProcessingLoop(() -> playback(executor), this::handleSeek);
    }

    private void handleSeek(long position) {
        log.debug("Seek to {}ms", position);
        seeking = true;
        seekTarget = position;
        closeCurrentStream();
    }

    void markExpired() {
        tokenExpired = true;
        closeCurrentStream();
    }

    private void closeCurrentStream() {
        if (hlsStream != null) {
            try {
                hlsStream.close();
            } catch (IOException ignored) {}
        }
    }

    private void playback(LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            while (true) {
                long startPosition = seeking ? seekTarget : (tokenExpired && hlsStream != null ? hlsStream.getPosition() : 0);
                seeking = false;
                tokenExpired = false;

                String hlsUrl = fetchStreamUrl(httpInterface, trackInfo.identifier);
                log.debug("HLS URL: {}", hlsUrl);

                try {
                    hlsStream = new GaanaHlsInputStream(httpInterface, hlsUrl, trackInfo.length, startPosition, this);
                    BufferedInputStream bufferedStream = new BufferedInputStream(hlsStream, 65536);

                    MpegTsElementaryInputStream tsStream = new MpegTsElementaryInputStream(
                        bufferedStream, MpegTsElementaryInputStream.ADTS_ELEMENTARY_STREAM
                    );
                    PesPacketInputStream pesStream = new PesPacketInputStream(tsStream);
                    AdtsAudioTrack adtsTrack = new AdtsAudioTrack(trackInfo, pesStream);

                    adtsTrack.process(executor);
                    break;

                } catch (Exception e) {
                    if (seeking || tokenExpired) {
                        log.debug("Restarting stream (seek={}, expired={})", seeking, tokenExpired);
                        continue;
                    }
                    throw e;
                } finally {
                    hlsStream = null;
                }
            }
        } catch (Exception e) {
            throw new FriendlyException("Gaana playback failed", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    /**
     * Fetches the HLS stream URL via GaanaPy API.
     * GaanaPy returns the stream URL directly - no decryption needed.
     *
     * Endpoint: GET {apiUrl}/songs/info?seokey={seokey}
     * Uses field: stream_urls.urls.high_quality
     */
    private String fetchStreamUrl(HttpInterface httpInterface, String trackId) throws IOException {
        String apiUrl = sourceManager.getApiUrl();
        String url = apiUrl + "/songs/info?seokey=" + URLEncoder.encode(trackId, "UTF-8");

        log.debug("Fetching stream from GaanaPy: {}", url);

        HttpGet request = new HttpGet(url);
        request.setHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new IOException("GaanaPy returned HTTP " + statusCode + " for: " + trackId);
            }

            // GaanaPy returns a JSON array — pick first element
            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            JsonBrowser first = json.index(0);

            if (first.isNull()) {
                throw new IOException("GaanaPy returned empty array for: " + trackId);
            }

            // Try qualities in order: high > very_high > medium > low
            String[] qualities = {"high_quality", "very_high_quality", "medium_quality", "low_quality"};
            for (String quality : qualities) {
                String streamUrl = first.get("stream_urls").get("urls").get(quality).text();
                if (streamUrl != null && !streamUrl.isEmpty()) {
                    log.debug("Using quality '{}': {}", quality, streamUrl);
                    return streamUrl;
                }
            }

            throw new IOException("No stream URL found in GaanaPy response for: " + trackId);
        }
    }

    @Override
    public long getPosition() {
        return hlsStream != null ? hlsStream.getPosition() : super.getPosition();
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new GaanaAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
