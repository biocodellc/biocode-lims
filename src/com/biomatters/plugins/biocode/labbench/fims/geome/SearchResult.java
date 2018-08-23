package com.biomatters.plugins.biocode.labbench.fims.geome;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchResult {

    public SearchResultsContents content;
    public int page;
    public int limit;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResultsContents {
        public List<Map<String,Object>> Tissue = new ArrayList<>();
        public List<Map<String,Object>> Sample = new ArrayList<>();
        public List<Map<String,Object>> Event = new ArrayList<>();
    }
}
