package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.XmlUtilities;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.io.IOException;

/**
 * @author Steve
 * @version $Id$
 */
public abstract class TableFimsConnection extends FIMSConnection{

    protected String tissueCol;
    protected String specimenCol;
    protected boolean storePlates;
    protected String plateCol;
    protected String wellCol;
    protected List<DocumentField> fields;
    protected List<DocumentField> taxonomyFields;
    protected List<DocumentField> columns;

    public final PasswordOptions getConnectionOptions() {
        return _getConnectionOptions();
    }

    public abstract TableFimsConnectionOptions _getConnectionOptions();

    public final void connect(Options optionsa) throws ConnectionException {

        TableFimsConnectionOptions options = (TableFimsConnectionOptions)optionsa;
        tissueCol = options.getTissueColumn();
        specimenCol = options.getSpecimenColumn();

        storePlates = options.storePlates();
        if(storePlates) {
            plateCol = options.getPlateColumn();
            wellCol = options.getWellColumn();
        }
        fields = new ArrayList<DocumentField>();
        taxonomyFields = new ArrayList<DocumentField>();

        _connect(options);
        try {
            columns = getTableColumns();
        } catch (IOException e) {
            throw new ConnectionException(e.getMessage(), e);
        }

        List<Options> taxOptions = options.getMultipleOptions("taxFields").getValues();
        for(Options taxOptionsValue : taxOptions){
            Options.OptionValue colValue = (Options.OptionValue)((Options.ComboBoxOption)taxOptionsValue.getOption("taxCol")).getValue();
            String value = XmlUtilities.encodeXMLChars(colValue.getLabel());
            String code = XmlUtilities.encodeXMLChars(colValue.getName());
            String description = XmlUtilities.encodeXMLChars(colValue.getDescription());
            taxonomyFields.add(new DocumentField(value, description, code, String.class, true, false));
        }

        for (int i = 0, cellValuesSize = columns.size(); i < cellValuesSize; i++) {
            DocumentField field = columns.get(i);
            if (!taxonomyFields.contains(field)) {
                fields.add(field);
            }
        }


        //if the tissue or specimen id is also a taxonomy field, it won't be in the fields list, and will cause problems later on
        if(getTableCol(fields, tissueCol) == null) {
            throw new ConnectionException(null, "You have listed your tissue sample field as also being a taxonomy field.  This is not allowed.");
        }
        if(getTableCol(fields, specimenCol) == null) {
            throw new ConnectionException(null, "You have listed your specimen field as also being a taxonomy field.  This is not allowed.");
        }
        if(getTissueSampleDocumentField() == null) {
            throw new ConnectionException("You have not set a tissue column");
        }
    }

    public abstract List<DocumentField> getTableColumns() throws IOException;

    public static DocumentField getTableCol(List<DocumentField> fields, String colName) {
        for(DocumentField field : fields) {
            if(field.getCode().equals(colName)) {
                return field;
            }
        }
        return null;
    }

    public abstract void _connect(TableFimsConnectionOptions options) throws ConnectionException;

    public final void disconnect() {
        tissueCol = specimenCol = null;
        fields = null;
        taxonomyFields = null;
        columns = null;
        _disconnect();
    }

    public abstract void _disconnect();

    public DocumentField getTissueSampleDocumentField() {
        return getTableCol(fields, tissueCol);
    }

    public List<DocumentField> getCollectionAttributes() {
        return new ArrayList<DocumentField>(fields);
    }

    public List<DocumentField> getTaxonomyAttributes() {
        return new ArrayList<DocumentField>(taxonomyFields);
    }

    public List<DocumentField> getSearchAttributes() {
        ArrayList<DocumentField> fields = new ArrayList<DocumentField>(this.fields);
        fields.addAll(taxonomyFields);
        return fields;
    }

    public BiocodeUtilities.LatLong getLatLong(AnnotatedPluginDocument annotatedDocument) {
        return null;  //todo
    }

    public boolean canGetTissueIdsFromFimsTissuePlate() {
        return storePlates;
    }

    @Override
    public DocumentField getPlateDocumentField() {
        if(!storePlates) {
            return null;
        }
        return getTableCol(fields, plateCol);
    }

    @Override
    public DocumentField getWellDocumentField() {
        if(!storePlates) {
            return null;
        }
        return getTableCol(fields, wellCol);
    }

    public Map<String, String> getTissueIdsFromExtractionBarcodes(List<String> extractionIds) throws ConnectionException {
        return null;
    }

    public Map<String, String> getTissueIdsFromFimsExtractionPlate(String plateId) throws ConnectionException {
        return null;
    }
}
