package com.biomatters.plugins.biocode.assembler.download;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.plugins.biocode.labbench.PlateDocument;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class DownloadChromatogramsFromLimsOptions extends Options {

    private MultipleOptions plateNamesMultipleOptions;
    private static final String PLATE_NAME = "plateName";

    public DownloadChromatogramsFromLimsOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        super(DownloadChromatogramsFromLimsOptions.class);
        Options plateNameOptions = new Options(DownloadChromatogramsFromLimsOptions.class);
        StringOption plateNameOption = plateNameOptions.addStringOption(PLATE_NAME, "Sequencing Plate Name:", "");
        plateNameOption.setDescription("The name of a cycle sequencing plate in the LIMS");
        plateNamesMultipleOptions = addMultipleOptions("plateNames", plateNameOptions, false);
        List<String> plateNames = new ArrayList<String>();
        for(AnnotatedPluginDocument doc : documents) {
            if(PlateDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                PlateDocument plateDoc = (PlateDocument)doc.getDocument();
                if(plateDoc.getPlate().getReactionType() == Reaction.Type.CycleSequencing) {
                    plateNames.add(plateDoc.getName());
                }
            }
        }

        if(plateNames.size() > 0) {
            restoreDefaults();
            for (int i = 0; i < plateNames.size(); i++) {
                String name = plateNames.get(i);
                //plateNamesMultipleOptions.set
                if(i > 0) {
                    plateNamesMultipleOptions.addValue(false);
                }
                plateNamesMultipleOptions.getValues().get(i).setValue(PLATE_NAME, name);
            }
        }
    }

    public List<String> getPlateNames() {
        List<String> plateNames = new ArrayList<String>();
        for (Options plateNameOptions : plateNamesMultipleOptions.getValues()) {
            plateNames.add(plateNameOptions.getValueAsString(PLATE_NAME));
        }
        return plateNames;
    }
}
