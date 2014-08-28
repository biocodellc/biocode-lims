package com.biomatters.plugins.biocode.submission.bold;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.GLabel;
import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.components.GTextPane;
import com.biomatters.geneious.publicapi.plugin.Options;

import javax.swing.*;
import java.awt.*;
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

    @Override
    protected JPanel createPanel() {
        GPanel mainPanel = new GPanel(new BorderLayout());

        GPanel infoPanel = new GPanel();
        infoPanel.add(new GLabel(Dialogs.DialogIcon.INFORMATION.getIcon()));
        GTextPane infoPane = GTextPane.createHtmlPane(
                "Geneious has found the following primers and loci linked to your traces.<br>" +
                "Please assign markers for each locus and rename any primers if necessary.<br><br>" +
                "<strong>Note</strong>: Names are case sensitive and primers must already exist in BOLD.<br>The list of " +
                "existing primers can be found <a href=\"http://www.boldsystems.org/index.php/Public_Primer_PrimerSearch\">here</a>.");
        infoPanel.add(infoPane);
        mainPanel.add(infoPanel, BorderLayout.NORTH);

        JPanel original = super.createPanel();
        mainPanel.add(original, BorderLayout.CENTER);
        return mainPanel;
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
