package com.biomatters.plugins.biocode.assembler.download;

import com.biomatters.geneious.publicapi.plugin.Options;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class DownloadChromatogramsFromLimsOptions extends Options {

    private MultipleOptions plateNamesMultipleOptions;
    private static final String PLATE_NAME = "plateName";

    public DownloadChromatogramsFromLimsOptions() {
        super(DownloadChromatogramsFromLimsOptions.class);
        Options plateNameOptions = new Options(DownloadChromatogramsFromLimsOptions.class);
        StringOption plateNameOption = plateNameOptions.addStringOption(PLATE_NAME, "Sequencing Plate Name:", "");
        plateNameOption.setDescription("The name of a cycle sequencing plate in the LIMS");
        plateNamesMultipleOptions = addMultipleOptions("plateNames", plateNameOptions, false);
    }

    public List<String> getPlateNames() {
        List<String> plateNames = new ArrayList<String>();
        for (Options plateNameOptions : plateNamesMultipleOptions.getValues()) {
            plateNames.add(plateNameOptions.getValueAsString(PLATE_NAME));
        }
        return plateNames;
    }
}
