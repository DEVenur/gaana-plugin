package com.github.notdeltaxd.gaana.source;

import com.github.notdeltaxd.gaana.ExtendedAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public class GaanaAudioPlaylist extends ExtendedAudioPlaylist {
    public GaanaAudioPlaylist(String name, List<AudioTrack> tracks, Type type, String url, String artworkURL, String author, Integer totalTracks) {
        super(name, tracks, type, url, artworkURL, author, totalTracks);
    }
}
