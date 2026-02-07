package com.github.notdeltaxd.plugin;

import com.github.notdeltaxd.gaana.source.GaanaAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GaanaPlugin implements AudioPlayerManagerConfiguration {
    private static final Logger log = LoggerFactory.getLogger(GaanaPlugin.class);

    private final GaanaSourceConfig config;
    private final GaanaAudioSourceManager gaanaSourceManager;

    public GaanaPlugin(GaanaSourceConfig config) {
        log.info("Loaded Gaana plugin...");
        this.config = config;
        this.gaanaSourceManager = new GaanaAudioSourceManager(this.config.getSearchLimit());
    }

    @NotNull
    @Override
    public AudioPlayerManager configure(@NotNull AudioPlayerManager manager) {
        log.info("Registering Gaana audio source manager...");
        manager.registerSourceManager(this.gaanaSourceManager);
        return manager;
    }
}
