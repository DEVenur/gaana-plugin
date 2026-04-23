package com.github.notdeltaxd.plugin;

import com.github.notdeltaxd.gaana.source.GaanaAudioSourceManager;
import com.github.topi314.lavasearch.SearchManager;
import com.github.topi314.lavasearch.api.SearchManagerConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GaanaPlugin implements AudioPlayerManagerConfiguration, SearchManagerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GaanaPlugin.class);

    private final GaanaSourceConfig config;
    private final GaanaAudioSourceManager gaanaSourceManager;

    public GaanaPlugin(GaanaSourceConfig config) {
        this.config = config;
        log.info("Loaded Gaana plugin (apiUrl: {}, searchLimit: {}, playlistTrackLimit: {}, recommendationsTrackLimit: {})",
            config.getApiUrl(),
            config.getSearchLimit(),
            config.getPlaylistTrackLimit(),
            config.getRecommendationsTrackLimit()
        );
        this.gaanaSourceManager = new GaanaAudioSourceManager(
            config.getSearchLimit(),
            config.getPlaylistTrackLimit(),
            config.getRecommendationsTrackLimit(),
            config.getApiUrl()
        );
    }

    @NotNull
    @Override
    public AudioPlayerManager configure(@NotNull AudioPlayerManager manager) {
        log.info("Registering Gaana audio source manager...");
        manager.registerSourceManager(this.gaanaSourceManager);
        return manager;
    }

    @NotNull
    @Override
    public SearchManager configure(@NotNull SearchManager manager) {
        log.info("Registering Gaana search manager...");
        manager.registerSearchManager(this.gaanaSourceManager);
        return manager;
    }
}
