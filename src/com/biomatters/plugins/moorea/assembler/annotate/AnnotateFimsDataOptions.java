package com.biomatters.plugins.moorea.assembler.annotate;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.MooreaUtilities;
import com.biomatters.plugins.moorea.labbench.ConnectionException;
import com.biomatters.plugins.moorea.labbench.FimsSample;
import com.biomatters.plugins.moorea.labbench.MooreaLabBenchService;
import com.biomatters.plugins.moorea.labbench.fims.FIMSConnection;
import com.biomatters.plugins.moorea.options.NamePartOption;
import com.biomatters.plugins.moorea.options.NameSeparatorOption;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Richard
 * @version $Id$
 */
public class AnnotateFimsDataOptions extends Options {

    private final NamePartOption namePartOption = new NamePartOption("namePart", "");
    private final NameSeparatorOption nameSeparatorOption = new NameSeparatorOption("nameSeparator", "");
    private final StringOption plateNameOption;
    private final ComboBoxOption<OptionValue> idType;

    private static final OptionValue WELL_NUMBER = new OptionValue("wellNumber", "Well number");
    private static final OptionValue BARCODE = new OptionValue("barcode", "Barcode");
    private Option<String,? extends JComponent> afterLabel;

    private Map<MooreaUtilities.Well, FimsSample> plateSpecimens = null;

    public AnnotateFimsDataOptions() {
        super(AnnotateFimsDataOptions.class);
        plateNameOption = addStringOption("plateName", "Sequencing Plate Name:", "");
        plateNameOption.setDescription("eg. My Sequencing Plate");
        beginAlignHorizontally(null, false);
        idType = addComboBoxOption("idType", "", new OptionValue[] {WELL_NUMBER, BARCODE}, WELL_NUMBER);
        addLabel("is");
        addCustomOption(namePartOption);
        addLabel("part of name");
        afterLabel = addLabel("");
        addCustomOption(nameSeparatorOption);
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
        endAlignHorizontally();
    }

    private void updateOptions() {
        boolean requiresSeparator = namePartOption.getPart() != 0 || idType.getValue().equals(BARCODE);
        afterLabel.setEnabled(requiresSeparator);
        nameSeparatorOption.setEnabled(requiresSeparator);
        if (idType.getValue().equals(BARCODE)) {
            afterLabel.setValue(", separated by");
        } else {
            afterLabel.setValue(", after");
        }
    }

    private String getPlateName() {
        return plateNameOption.getValue();
    }

    private int getNamePart() {
        return namePartOption.getPart();
    }

    private String getNameSeaparator() {
        return nameSeparatorOption.getSeparatorString();
    }

    public FimsSample getTissueRecord(AnnotatedPluginDocument annotatedDocument, FIMSConnection activeFimsConnection) throws DocumentOperationException, ConnectionException {
        if (idType.getValue() == BARCODE) {
            //noinspection ConstantIfStatement
            if (true) {
                throw new DocumentOperationException("FIMS data cannot be retrieved using barcodes. Please contact Biomatters if you require this functionality.");
            } else {
                //NOTE keeping this code around for testing purposes
                DocumentField plateQueryField = activeFimsConnection.getPlateDocumentField();
                Query plateFieldQuery = Query.Factory.createFieldQuery(plateQueryField, Condition.CONTAINS, getPlateName());
                String barcode = MooreaUtilities.getBarcodeFromFileName(annotatedDocument.getName(), getNameSeaparator(), getNamePart());
                String tissueId = activeFimsConnection.getTissueIdsFromExtractionBarcodes(Collections.singletonList(barcode)).get(barcode);
                if (tissueId == null) {
                    return null;
                }
                DocumentField tissueIdField = activeFimsConnection.getTissueSampleDocumentField();
                Query tissueFieldQuery = Query.Factory.createFieldQuery(tissueIdField, Condition.EQUAL , tissueId);
                Query compoundQuery = Query.Factory.createAndQuery(new Query[] {plateFieldQuery, tissueFieldQuery}, Collections.<String, Object>emptyMap());
                List<FimsSample> samples = activeFimsConnection.getMatchingSamples(compoundQuery);
                if (samples.size() != 1) {
                    return null;
                }
                return samples.get(0);
            }
        } else {
            MooreaUtilities.Well well = MooreaUtilities.getWellFromFileName(annotatedDocument.getName(), getNameSeaparator(), getNamePart());
            if (well == null) {
                return null;
            }
            if (plateSpecimens == null) {
                try {
                    plateSpecimens = MooreaLabBenchService.getInstance().getFimsSamplesForCycleSequencingPlate(getPlateName());
                } catch (SQLException e) {
                    throw new DocumentOperationException("Failed to retrieve FIMS data for plate " + getPlateName());
                }
            }
            return plateSpecimens.get(well);
        }
    }
}
