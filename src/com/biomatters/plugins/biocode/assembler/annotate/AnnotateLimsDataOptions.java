package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.SetReadDirectionOperation;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.options.NamePartOption;
import com.biomatters.plugins.biocode.options.NameSeparatorOption;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Richard
 * @version $Id$
 */
public class AnnotateLimsDataOptions extends Options {

    private final NamePartOption namePartOption = new NamePartOption("namePart", "");
    private final NameSeparatorOption nameSeparatorOption = new NameSeparatorOption("nameSeparator", "");
    private final StringOption forwardPlateNameOption;
    private final StringOption reversePlateNameOption;
    private final ComboBoxOption<OptionValue> idType;
    private final RadioOption<OptionValue> useExistingPlates;
    private final OptionValue[] useExistingValues;

    private static final OptionValue WELL_NUMBER = new OptionValue("wellNumber", "Well number");
    private static final OptionValue BARCODE = new OptionValue("barcode", "Barcode");
    private Option<String,? extends JComponent> afterLabel;

    private final AtomicBoolean loadedPlates = new AtomicBoolean(false);
    private Map<BiocodeUtilities.Well, WorkflowDocument> forwardPlateSpecimens = null;
    private String actualForwardPlateName = null;
    private Map<BiocodeUtilities.Well, WorkflowDocument> reversePlateSpecimens = null;
    private String actualReversePlateName = null;
    private Map<BiocodeUtilities.Well, WorkflowDocument> noDirectionPlateSpecimens = null;
    private String actualNoDirectionPlateName = null;
    private final BiocodeUtilities.ReadDirection noReadDirectionValue;

    public AnnotateLimsDataOptions(AnnotatedPluginDocument[] documents) throws DocumentOperationException {
        super(AnnotateLimsDataOptions.class);
        Options useExistingOptions = new Options(this.getClass());
        forwardPlateNameOption = useExistingOptions.addStringOption("forwardPlateName", "Forward Sequencing Plate Name:", "");
        reversePlateNameOption = useExistingOptions.addStringOption("reversePlateName", "Reverse Sequencing Plate Name:", "");
        forwardPlateNameOption.setDescription("Name of cycle sequencing plate in LIMS for forward reads, must not be empty.");
        reversePlateNameOption.setDescription("Name of cycle sequencing plate in LIMS for reverse reads, may be the same as forward plate or empty.");
        useExistingOptions.beginAlignHorizontally(null, false);
        idType = useExistingOptions.addComboBoxOption("idType", "", new OptionValue[] {WELL_NUMBER, BARCODE}, WELL_NUMBER);
        useExistingOptions.addLabel("is");
        useExistingOptions.addCustomOption(namePartOption);
        useExistingOptions.addLabel("part of name");
        afterLabel = useExistingOptions.addLabel("separated by");
        useExistingOptions.addCustomOption(nameSeparatorOption);
        SimpleListener namePartListener = new SimpleListener() {
            public void objectChanged() {
                updateOptions();
            }
        };
        namePartOption.addChangeListener(namePartListener);
        SimpleListener idTypeListener = new SimpleListener() {
            public void objectChanged() {
                updateOptions();
            }
        };
        idType.addChangeListener(idTypeListener);
        idTypeListener.objectChanged();
        namePartListener.objectChanged();
        useExistingOptions.endAlignHorizontally();

        noReadDirectionValue = BiocodeUtilities.getNoReadDirectionValue(Arrays.asList(documents));

        useExistingValues = new OptionValue[] {
                new OptionValue("plateFromOptions", "Use the following plate/well"),
                new OptionValue("existingPlate", "Use annotated workflow/plate/well",
                        "Your sequences will have annotated workflow, plate and well values if you have run annotate with \n" +
                                "FIMS/LIMS data on them before, or if you downloaded them directly from the database.")
        };

        addChildOptions("useExistingOptions", "", "", useExistingOptions);

        useExistingPlates = addRadioOption("useExistingPlate", "", useExistingValues, useExistingValues[0], Alignment.VERTICAL_ALIGN);
        useExistingPlates.addDependent(useExistingValues[0], useExistingOptions, true);
        
    }

    private void updateOptions() {
        boolean requiresSeparator = namePartOption.getPart() != 0 || idType.getValue().equals(BARCODE);
        afterLabel.setEnabled(requiresSeparator);
        nameSeparatorOption.setEnabled(requiresSeparator);
//        if (idType.getValue().equals(BARCODE)) {
//            afterLabel.setValue(", separated by");
//        } else {
//            afterLabel.setValue(", after");
//        }
    }

    private String getForwardPlateName() {
        return forwardPlateNameOption.getValue();
    }

    private String getReversePlateName() {
        return reversePlateNameOption.getValue();
    }

    private int getNamePart() {
        return namePartOption.getPart();
    }

    private String getNameSeaparator() {
        return nameSeparatorOption.getSeparatorString();
    }

    boolean isAnnotateWithSpecifiedPlate() {
        return useExistingPlates.getValue() == useExistingValues[0];
    }

    /**
     *
     * @param annotatedDocument
     * @return never null
     * @throws com.biomatters.plugins.biocode.labbench.ConnectionException
     * @throws com.biomatters.geneious.publicapi.plugin.DocumentOperationException
     */
    FimsData getFIMSDataForGivenPlate(AnnotatedPluginDocument annotatedDocument) throws DocumentOperationException {
        if (idType.getValue() == BARCODE) {
//            if (true) {
                throw new DocumentOperationException("FIMS data cannot be retrieved using barcodes. Please contact Biomatters if you require this functionality.");
//            } else {
//                DocumentField plateQueryField = activeFimsConnection.getPlateDocumentField();
//                Query plateFieldQuery = Query.Factory.createFieldQuery(plateQueryField, Condition.CONTAINS, getPlateName());
//                String barcode = BiocodeUtilities.getBarcodeFromFileName(annotatedDocument.getName(), getNameSeaparator(), getNamePart());
//                String tissueId = activeFimsConnection.getTissueIdsFromExtractionBarcodes(Collections.singletonList(barcode)).get(barcode);
//                if (tissueId == null) {
//                    return null;
//                }
//                DocumentField tissueIdField = activeFimsConnection.getTissueSampleDocumentField();
//                Query tissueFieldQuery = Query.Factory.createFieldQuery(tissueIdField, Condition.EQUAL , tissueId);
//                Query compoundQuery = Query.Factory.createAndQuery(new Query[] {plateFieldQuery, tissueFieldQuery}, Collections.<String, Object>emptyMap());
//                List<FimsSample> samples = activeFimsConnection.retrieveSamplesForTissueIds(compoundQuery);
//                if (samples.size() != 1) {
//                    return null;
//                }
//                return samples.get(0);
//            }
        } else {
            BiocodeUtilities.Well well = BiocodeUtilities.getWellFromFileName(annotatedDocument.getName(), getNameSeaparator(), getNamePart());
            if (well == null) {
                return null;
            }
            Object isForwardValue = annotatedDocument.getFieldValue(SetReadDirectionOperation.IS_FORWARD_FIELD);
            if(isForwardValue == null && noDirectionPlateSpecimens == null) {
                //happens if user specifies different plates for forward and reverse but the sequences aren't annotated with directions
                throw new DocumentOperationException("Could not determine direction of reads, make sure you have run " +
                        "<strong>Set Read Direction</strong> first if specifying both forward and reverse sequencing " +
                        "plates to annotate from.");
            }
            loadPlateSpecimens();
            WorkflowDocument workflow;
            String sequencingPlateName;
            if (isForwardValue == null) {
                assert(noDirectionPlateSpecimens != null);
                workflow = noDirectionPlateSpecimens.get(well);
                sequencingPlateName = actualNoDirectionPlateName;
            } else if ((Boolean)isForwardValue) {
                workflow = forwardPlateSpecimens.get(well);
                sequencingPlateName = actualForwardPlateName;
            } else {
                workflow = reversePlateSpecimens.get(well);
                sequencingPlateName = actualReversePlateName;
            }
            return new FimsData(workflow, sequencingPlateName, well);
        }
    }



    @Override
    public String verifyOptionsAreValid() {
        if (isAnnotateWithSpecifiedPlate() && getForwardPlateName().trim().equals("")) {
            return "A forward plate name must be entered (reverse can be empty)";
        }
        return null;
    }

    private void loadPlateSpecimens() throws DocumentOperationException {
        if (!loadedPlates.getAndSet(true)) {
            String forwardPlateName = getForwardPlateName().trim();
            String reversePlateName = getReversePlateName().trim();
            try {
                forwardPlateSpecimens = BiocodeService.getInstance().getWorkflowsForCycleSequencingPlate(forwardPlateName);
                actualForwardPlateName = forwardPlateName;
                if (reversePlateName.equals("") || reversePlateName.equals(forwardPlateName)) {
                    reversePlateSpecimens = forwardPlateSpecimens;
                    actualReversePlateName = forwardPlateName;
                } else {
                    reversePlateSpecimens = BiocodeService.getInstance().getWorkflowsForCycleSequencingPlate(reversePlateName);
                    actualReversePlateName = reversePlateName;
                }

                if (forwardPlateSpecimens == null) {
                    throw new DocumentOperationException("The cycle sequencing plate \"" + forwardPlateName + "\" could not found in the LIMS. Please check the name is correct.");
                }
                if (reversePlateSpecimens == null) {
                    throw new DocumentOperationException("The cycle sequencing plate \"" + reversePlateName + "\" could not found in the LIMS. Please check the name is correct.");
                }
                switch (noReadDirectionValue) {
                    case FORWARD:
                        noDirectionPlateSpecimens = forwardPlateSpecimens;
                        actualNoDirectionPlateName = forwardPlateName;
                        break;
                    case REVERSE:
                        noDirectionPlateSpecimens = reversePlateSpecimens;
                        actualNoDirectionPlateName = reversePlateName;
                        break;
                    case NONE:
                        if (forwardPlateSpecimens != reversePlateSpecimens) {
                            noDirectionPlateSpecimens = null;
                        } else {
                            noDirectionPlateSpecimens = forwardPlateSpecimens;
                            actualNoDirectionPlateName = forwardPlateName;
                        }
                        break;
                }
            } catch (SQLException e) {
                throw new DocumentOperationException("Failed to retrieve FIMS data for plates " + forwardPlateName + " and " + reversePlateName, e);
            } catch (DatabaseServiceException e) {
                throw new DocumentOperationException("Failed to retrieve FIMS data for plates " + forwardPlateName + " and " + reversePlateName, e);
            }
        }
    }
}
