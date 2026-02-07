package com.github.notdeltaxd.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component("gaanaSourceConfig")
@ConfigurationProperties(prefix = "plugins.gaana")
public class GaanaSourceConfig {
    private int searchLimit = 20;

    public int getSearchLimit() {
        return searchLimit;
    }

    public void setSearchLimit(int searchLimit) {
        this.searchLimit = searchLimit;
    }
}
