package com.github.notdeltaxd.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component("gaanaSourceConfig")
@ConfigurationProperties(prefix = "plugins.gaana")
public class GaanaSourceConfig {

    /**
     * Maximum number of search results (default 20)
     */
    private int searchLimit = 20;

    /**
     * Maximum number of tracks to return from a given playlist (default 50)
     */
    private int playlistTrackLimit = 50;

    /**
     * Maximum number of tracks to return from recommendations (default 10)
     */
    private int recommendationsTrackLimit = 10;

    /**
     * Base URL of your GaanaPy instance.
     * Examples:
     *   http://localhost:8000         (local)
     *   https://your-app.leapcell.io  (Leapcell serverless)
     */
    private String apiUrl = "http://localhost:8000";

    public int getSearchLimit() {
        return searchLimit;
    }

    public void setSearchLimit(int searchLimit) {
        this.searchLimit = searchLimit > 0 ? searchLimit : 20;
    }

    public int getPlaylistTrackLimit() {
        return playlistTrackLimit;
    }

    public void setPlaylistTrackLimit(int playlistTrackLimit) {
        this.playlistTrackLimit = playlistTrackLimit > 0 ? playlistTrackLimit : 50;
    }

    public int getRecommendationsTrackLimit() {
        return recommendationsTrackLimit;
    }

    public void setRecommendationsTrackLimit(int recommendationsTrackLimit) {
        this.recommendationsTrackLimit = recommendationsTrackLimit > 0 ? recommendationsTrackLimit : 10;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        if (apiUrl != null && !apiUrl.isEmpty()) {
            this.apiUrl = apiUrl.replaceAll("/+$", "");
        }
    }
}
