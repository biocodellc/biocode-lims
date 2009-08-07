package com.biomatters.plugins.moorea.assembler.annotate;

import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.moorea.MooreaUtilities;
import com.biomatters.plugins.moorea.labbench.ConnectionException;
import com.biomatters.plugins.moorea.labbench.FimsSample;
import com.biomatters.plugins.moorea.labbench.MooreaLabBenchService;
import com.biomatters.plugins.moorea.labbench.fims.FIMSConnection;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class AnnotateFimsDataOperation extends DocumentOperation {

    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions("Annotate with FIMS Data...",
                "Annotate sequences/assemblies with data from the Field Information Management System. eg. Taxonomy, Collector")
                .setMainMenuLocation(GeneiousActionOptions.MainMenu.Sequence);
    }

    public String getHelp() {
        return "Select one or more sequences or assemblies to attempt to annotate them with data from the FIMS (Field Information Management System).";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(PluginDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        return new AnnotateFimsDataOptions();
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options o) throws DocumentOperationException {
        AnnotateFimsDataOptions options = (AnnotateFimsDataOptions) o;
        String plateName = options.getPlateName();
        String nameSeaparator = options.getNameSeaparator();
        int namePart = options.getNamePart();
        List<String> nameParseFail = new ArrayList<String>();
        List<String> noTissueFail = new ArrayList<String>();
        MooreaLabBenchService mooreaLabBenchService = MooreaLabBenchService.getInstance();
        FIMSConnection activeFIMSConnection = mooreaLabBenchService.getActiveFIMSConnection();
        if (activeFIMSConnection == null) {
            throw new DocumentOperationException("You must connect to the lab bench service first");
        }
        DocumentField plateQueryField = activeFIMSConnection.getPlateDocumentField();
        DocumentField wellQueryField = activeFIMSConnection.getWellDocumentField();
        CompositeProgressListener compositeProgress = new CompositeProgressListener(progressListener, annotatedDocuments.length);
        if (compositeProgress.getRootProgressListener() instanceof ProgressFrame) {
            ((ProgressFrame)compositeProgress.getRootProgressListener()).setCancelButtonLabel("Stop");
        }
        //todo id by barcode option
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            compositeProgress.beginSubtask();
            if (compositeProgress.isCanceled()) {
                break;
            }
            MooreaUtilities.Well well = MooreaUtilities.getWellString(annotatedDocument.getName(), nameSeaparator, namePart);
            if (well == null) {
                nameParseFail.add(annotatedDocument.getName());
                continue;
            }
            Query plateFieldQuery = Query.Factory.createFieldQuery(plateQueryField, Condition.CONTAINS, plateName);
            Query wellFieldQueryPadded = Query.Factory.createFieldQuery(wellQueryField, Condition.EQUAL , well.toPaddedString());
            Query wellFieldQueryUnpadded = Query.Factory.createFieldQuery(wellQueryField, Condition.EQUAL , well.toString());
            Query compoundQuery = Query.Factory.createAndQuery(new Query[] {plateFieldQuery, wellFieldQueryPadded}, Collections.<String, Object>emptyMap());
            Query compoundQuery2 = Query.Factory.createAndQuery(new Query[] {plateFieldQuery, wellFieldQueryUnpadded}, Collections.<String, Object>emptyMap());
            FimsSample tissue;
            try {
                List<FimsSample> samples = activeFIMSConnection.getMatchingSamples(compoundQuery);
                if (samples.size() != 1) {
                    samples = activeFIMSConnection.getMatchingSamples(compoundQuery2);
                    if (samples.size() != 1) {
                        noTissueFail.add(annotatedDocument.getName());
                        continue;
                    }
                }
                tissue = samples.get(0);
            } catch (ConnectionException e) {
                throw new DocumentOperationException("Failed to connect to FIMS", e);
            }

            for (DocumentField documentField : tissue.getFimsAttributes()) {
                annotatedDocument.setFieldValue(documentField, tissue.getFimsAttributeValue(documentField.getCode()));
            }
            StringBuilder taxonomy = new StringBuilder();
            for (DocumentField documentField : tissue.getTaxonomyAttributes()) {
                Object taxon = tissue.getFimsAttributeValue(documentField.getCode());
                if (taxon != null) {
                    taxonomy.append(taxon.toString()).append("; ");
                }
            }
            if (taxonomy.length() > 0) {
                annotatedDocument.setFieldValue(DocumentField.TAXONOMY_FIELD, taxonomy.substring(0, taxonomy.length() - 2));
            }
            annotatedDocument.save();
        }
        if (!nameParseFail.isEmpty() || !noTissueFail.isEmpty()) {
            StringBuilder b = new StringBuilder("<html>");
            if (!nameParseFail.isEmpty()) {
                b.append("The well name could not be extracted from the following sequences:<br><br>");
                for (String s : nameParseFail) {
                    b.append(s).append("<br>");
                }
            }
            if (!noTissueFail.isEmpty()) {
                b.append("No tissue records were found in the database for the following sequences:<br><br>");
                for (String s : noTissueFail) {
                    b.append(s).append("<br>");
                }
            }
            b.append("</html>");
            throw new DocumentOperationException(b.toString());
        }
        return null;
    }
}
