package com.biomatters.plugins.biocode.submission.bold;

import com.biomatters.geneious.publicapi.plugin.Options;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Matthew Cheung
 *         Created on 28/08/14 10:02 AM
 */
class RenamingOptions extends Options {

    private final String PRIMER_OPTIONS = "primer";
    private final String MARKER_OPTIONS = "marker";

    RenamingOptions() {
        // For XML de-serialization
    }

    RenamingOptions(Set<String> primers, Set<String> loci) {
        // todo Better messaging
        addLabel("Geneious has found the following primers and loci for the selected traces.  You may choose " +
                "an alternative name for primers");

        Options primerOptions = new Options(RenamingOptions.class);
        for (String primer : primers) {
            primerOptions.addStringOption(primer, primer + ":", primer);
        }
        addChildOptions(PRIMER_OPTIONS, "Primers", "", primerOptions);

        Options markerOptions = new Options(RenamingOptions.class);
        for (String locus : loci) {
            markerOptions.addStringOption(locus, locus + ":", locus);
        }
        addChildOptions(MARKER_OPTIONS, "Locus -> Marker", "", markerOptions);
    }

    RenameMap getRenameMap() {
        RenameMap renameMap = new RenameMap();
        mapNamesFromOptions(PRIMER_OPTIONS, renameMap.primerRenameMap);
        mapNamesFromOptions(MARKER_OPTIONS, renameMap.markerRenameMap);
        return renameMap;
    }

    private void mapNamesFromOptions(String childOptionsKey, Map<String, String> mapToFill) {
        Options primerOptions = getChildOptions().get(childOptionsKey);
        if(primerOptions == null) {
            throw new IllegalStateException("Child options for " + childOptionsKey + " is missing.");
        }
        for (Option option : primerOptions.getOptions()) {
            mapToFill.put(option.getName(), String.valueOf(option.getValue()));
        }
    }


    static class RenameMap {
        private Map<String, String> primerRenameMap = new HashMap<String, String>();
        private Map<String, String> markerRenameMap = new HashMap<String, String>();

        String getNameForPrimer(String primer) {
            String name = primerRenameMap.get(primer);
            return name == null ? "" : name.trim();
        }

        String getMarkerForLocus(String locus) {
            String name = markerRenameMap.get(locus);
            return name == null ? "" : name.trim();
        }
    }
}
