package com.biomatters.plugins.biocode.assembler.download;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.PlateDocument;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

import javax.swing.*;
import java.util.*;

/**
 * @author Richard
 * @version $Id$
 */
public class DownloadChromatogramsFromLimsOptions extends Options {

    public final OptionValue SPECIFY_PLATES = new OptionValue("byPlate", "specified plates");
    public final OptionValue SELECTED_SEQUENCES = new OptionValue("selectedSequences", "selected sequences");

    public RadioOption<OptionValue> downloadMethodOption;
    public BooleanOption assembleTracesOption;
    private MultipleOptions plateNamesMultipleOptions;
    private static final String PLATE_NAME = "plateName";

    public DownloadChromatogramsFromLimsOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        super(DownloadChromatogramsFromLimsOptions.class);
        downloadMethodOption = addRadioOption("method", "Download traces matching: ",
                Arrays.asList(SELECTED_SEQUENCES, SPECIFY_PLATES), SELECTED_SEQUENCES, Alignment.VERTICAL_ALIGN);

        Options plateSectionOptions = new Options(DownloadChromatogramsFromLimsOptions.class);
        addChildOptions("plates", "", null, plateSectionOptions);
        downloadMethodOption.addChildOptionsDependent(plateSectionOptions, SPECIFY_PLATES, true);
        assembleTracesOption = addBooleanOption("assemble", "Assemble Traces to Sequences", false);
        downloadMethodOption.addDependent(assembleTracesOption, SELECTED_SEQUENCES);

        Options plateNameOptions = new Options(DownloadChromatogramsFromLimsOptions.class);
        StringOption plateNameOption = plateNameOptions.addStringOption(PLATE_NAME, "Sequencing Plate Name:", "");
        plateNameOption.setDescription("The name of a cycle sequencing plate in the LIMS");
        plateNamesMultipleOptions = plateSectionOptions.addMultipleOptions("plateNames", plateNameOptions, false);

        Set<String> plateNames = new LinkedHashSet<String>();
        boolean hasPlateField = false;
        for(AnnotatedPluginDocument doc : documents) {
            if(PlateDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                PlateDocument plateDoc = (PlateDocument)doc.getDocument();
                if(plateDoc.getPlate().getReactionType() == Reaction.Type.CycleSequencing) {
                    plateNames.add(plateDoc.getName());
                }
            } else {
                for (DocumentField plateField : BiocodeUtilities.PLATE_FIELDS) {
                    Object plateValue = doc.getFieldValue(plateField);
                    Object workflowValue = doc.getFieldValue(BiocodeUtilities.WORKFLOW_NAME_FIELD);
                    if(plateValue != null && workflowValue != null) {
                        plateNames.add(plateValue.toString());
                        hasPlateField = true;
                    }
                }
            }
        }
        if(!hasPlateField) {
            SELECTED_SEQUENCES.setEnabled(false);
        }

        if(plateNames.size() > 0) {
            restoreDefaults();
            Iterator<String> iterator = plateNames.iterator();
            for (int i = 0; i < plateNames.size(); i++) {
                String name = iterator.next();
                if(i > 0) {
                    plateNamesMultipleOptions.addValue(false);
                }
                plateNamesMultipleOptions.getValues().get(i).setValue(PLATE_NAME, name);
            }
        }
    }

    private List<String> getPlateNames() {
        List<String> plateNames = new ArrayList<String>();
        for (Options plateNameOptions : plateNamesMultipleOptions.getValues()) {
            plateNames.add(plateNameOptions.getValueAsString(PLATE_NAME));
        }
        return plateNames;
    }

    public Map<String, List<String>> getPlatesAndWorkflowsToRetrieve(AnnotatedPluginDocument[] documents) {
        Map<String, List<String>> plates = new HashMap<String, List<String>>();
        if(downloadMethodOption.getValue() == SPECIFY_PLATES) {
            for (String plateName : getPlateNames()) {
                plates.put(plateName, null);
            }
        } else {
            for (AnnotatedPluginDocument doc : documents) {
                for (DocumentField plateField : BiocodeUtilities.PLATE_FIELDS) {
                    Object plateValue = doc.getFieldValue(plateField);
                    Object workflowValue = doc.getFieldValue(BiocodeUtilities.WORKFLOW_NAME_FIELD);
                    if(plateValue != null && workflowValue != null) {
                        List<String> workflows = plates.get(plateValue);
                        if(workflows == null) {
                            workflows = new ArrayList<String>();
                            plates.put(plateValue.toString(), workflows);
                        }
                        workflows.add(workflowValue.toString());
                    }
                }
            }
        }
        return plates;
    }

    public boolean isAssembleTraces() {
        return assembleTracesOption.getValue();
    }
}
