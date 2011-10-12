package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import jebl.util.ProgressListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Steve
 * @version $Id: AnnotateFimsDataOptions.java 36017 2010-08-30 19:32:39Z steve $
 */
public class AnnotateFimsDataOperation extends DocumentOperation {
    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Annotate with FIMS Data Only...",
                "Annotate sequences/assemblies with data from the Field Information Management System, bypassing the LIMS")
                .setInPopupMenu(true, 0.23);
        return GeneiousActionOptions.createSubmenuActionOptions(BiocodePlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public String getHelp() {
        return "Select one or more sequencing reads to annotate them with data from the FIMS (Field Information Management System) and LIMS (Lab Information Managment System).";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(NucleotideSequenceDocument.class, 1, Integer.MAX_VALUE),
                new DocumentSelectionSignature(SequenceAlignmentDocument.class,1, Integer.MAX_VALUE)
        };
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if (!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        return new AnnotateFimsDataOptions(documents);
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options o) throws DocumentOperationException {
        final AnnotateFimsDataOptions options = (AnnotateFimsDataOptions)o;
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }

        final List<FimsSample> fimsSamples;
        try {
            fimsSamples = BiocodeService.getInstance().getActiveFIMSConnection().getMatchingSamples(getQuery(annotatedDocuments, options));
        } catch (ConnectionException e) {
            e.printStackTrace();
            throw new DocumentOperationException("There was an error communicating with the FIMS database: "+e.getMessage(), e);
        }

        FimsDataGetter fimsDataGetter = new FimsDataGetter() {
            public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException {
                if(!BiocodeService.getInstance().isLoggedIn()) {
                    throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
                }
                try {
                    if(options.matchField()) {
                        FimsSample sample = getFimsSample(fimsSamples, options.getFieldToMatch(), document, options);
                        if(sample != null) {
                            return new FimsData(sample, null, null);
                        }
                    }
                    else {
                        BiocodeUtilities.Well well = getWell(document, options);
                        String plateId = getPlateId(document, options);
                        FimsSample sample = getFimsSample(fimsSamples, plateId, well);
                        if(sample != null) {
                            return new FimsData(sample, plateId, well);
                        }
                    }
                    return null;
                } catch (Exception e) {
                    throw new DocumentOperationException("Failed to connect to FIMS: " + e.getMessage(), e);
                }
            }
        };
        AnnotateUtilities.annotateFimsData(annotatedDocuments, progressListener, fimsDataGetter);
        return null;
    }

    private Query getQuery(AnnotatedPluginDocument[] annotatedDocuments, AnnotateFimsDataOptions options) throws DocumentOperationException{
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        if(options.matchField()) {
            return getFieldQuery(options.getFieldToMatch(), getFieldValues(annotatedDocuments, DocumentField.NAME_FIELD, options.getNamePart(), options.getNameSeaparator()));
        }
        return getPlateQuery(getPlateIds(annotatedDocuments, options));
    }

    /**
     * Must have checked that we are logged in (BiocodeService.getInstance().isLoggedIn()) before calling this method
     * @param fimsSamples
     * @param plate
     * @param well
     * @return
     */
    private static FimsSample getFimsSample(List<FimsSample> fimsSamples, String plate, BiocodeUtilities.Well well) {
        for(FimsSample sample : fimsSamples) {
            BiocodeUtilities.Well sampleWell = new BiocodeUtilities.Well(""+sample.getFimsAttributeValue(BiocodeService.getInstance().getActiveFIMSConnection().getWellDocumentField().getCode()));
            String samplePlate = ""+sample.getFimsAttributeValue(BiocodeService.getInstance().getActiveFIMSConnection().getPlateDocumentField().getCode());
            if(samplePlate.equalsIgnoreCase(plate) && sampleWell.equals(well)) {
                return sample;
            }
        }
        return null;
    }

    private static FimsSample getFimsSample(List<FimsSample> fimsSamples, DocumentField searchField, AnnotatedPluginDocument document, AnnotateFimsDataOptions options) {
        String searchValue = getFieldValue(document, options);
        if(searchValue == null) {
            return null;
        }
        for(FimsSample sample : fimsSamples) {
            Object resultValue = sample.getFimsAttributeValue(searchField.getCode());
            if(resultValue != null && searchValue.equalsIgnoreCase(resultValue.toString())) {
                return sample;
            }
        }
        return null;
    }

    private static Query getFieldQuery(DocumentField field, List<String> values) throws DocumentOperationException{
        if(values.size() == 0) {
            throw new DocumentOperationException("None of your documents contain field values for the name part that you specified");
        }
        if(values.size() == 1) {
            return Query.Factory.createFieldQuery(field, Condition.EQUAL, values.get(0));
        }
        else {
            Query[] queries = new Query[values.size()];
            for (int i = 0, plateIdsSize = values.size(); i < plateIdsSize; i++) {
                queries[i] = Query.Factory.createFieldQuery(field, Condition.EQUAL, values.get(i));
            }
            return Query.Factory.createOrQuery(queries, Collections.EMPTY_MAP);
        }
    }

    private static Query getPlateQuery(List<String> plateIds) throws DocumentOperationException{
        if(plateIds.size() == 0) {
            throw new IllegalArgumentException("You tried to search for no plates!");
        }
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        return getFieldQuery(BiocodeService.getInstance().getActiveFIMSConnection().getPlateDocumentField(), plateIds);
    }

    /**
     * must check that BiocodeService.getInstance().isLoggedIn() returns true before calling this method.
     * @param document
     * @param options
     * @return
     */
    private static String getPlateId(AnnotatedPluginDocument document, AnnotateFimsDataOptions options) {
        if(options.useExistingPlate()) {
            Object value = document.getFieldValue(BiocodeService.getInstance().getActiveFIMSConnection().getPlateDocumentField());
            if(value != null) {
                return value.toString();
            }
            return null;
        }
        else {
            return options.getExistingPlateName();
        }
    }

    private static String getFieldValue(AnnotatedPluginDocument document, AnnotateFimsDataOptions options) {
        if(options.matchField()) {
            return BiocodeUtilities.getStringFromFileName(document.getName(), options.getNameSeaparator(), options.getNamePart());
        }
        return null;
    }

    private static BiocodeUtilities.Well getWell(AnnotatedPluginDocument document, AnnotateFimsDataOptions options) throws DocumentOperationException{
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        if(options.useExistingPlate()) {
            Object value = document.getFieldValue(BiocodeService.getInstance().getActiveFIMSConnection().getWellDocumentField());
            if(value != null) {
                return new BiocodeUtilities.Well(value.toString());
            }
            return null;
        }
        else {
            return BiocodeUtilities.getWellFromFileName(document.getName(), options.getNameSeaparator(), options.getNamePart());
        }
    }

    private static List<String> getPlateIds(AnnotatedPluginDocument[] documents, AnnotateFimsDataOptions options) throws DocumentOperationException{
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        List<String> result = new ArrayList<String>();
        if(options.useExistingPlate()) {
            DocumentField plateField = BiocodeService.getInstance().getActiveFIMSConnection().getPlateDocumentField();
            List<String> fieldValues = getFieldValues(documents, plateField, options.getNamePart(), options.getNameSeaparator());
            if(fieldValues == null || fieldValues.size() == 0) {
                throw new DocumentOperationException("At least some of your documents do not have a plate field annotated.  Please annotate them by selecting annotate from the biocode menu, and entering a plate name in the options dialog.");
            }
            result.addAll(fieldValues);
        }
        else {
            result.add(options.getExistingPlateName());
        }
        return result;
    }

    private static List<String> getFieldValues(AnnotatedPluginDocument[] documents, DocumentField field, int part, String separator) {
        List<String> fieldValues = new ArrayList<String>();
        for(AnnotatedPluginDocument document : documents) {
            Object value = document.getFieldValue(field);
            if(value != null) {
                String finalValue = BiocodeUtilities.getStringFromFileName(value.toString(), separator, part);
                if(finalValue != null) {
                    fieldValues.add(finalValue);
                }
            }
        }
        return fieldValues;
    }


}
