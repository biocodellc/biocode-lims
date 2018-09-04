package com.biomatters.plugins.biocode;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Release {
    public String url;
    public String tag_name;
    public boolean draft;
    public boolean prerelease;
    public String body;

    public List<Asset> assets = new ArrayList<>();

    public boolean shouldNotifyEveryone() {
        return !draft && !prerelease;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Asset {
        public String name;
        public String browser_download_url;
    }
}


