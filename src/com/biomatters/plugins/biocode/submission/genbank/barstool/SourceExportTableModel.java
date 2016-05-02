package com.biomatters.plugins.biocode.submission.genbank.barstool;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.verify.Pair;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

/**
 * @author Richard
 */
public class SourceExportTableModel extends TabDelimitedExport.ExportTableModel {

    private final List<Pair<String,String>> sourceFields;
    private final List<Pair<String,String>> fixedSourceFields;
    private final FIMSConnection fimsConnection;
    private final boolean includeLatLong;
    private final DateFormat dateFormat;

    public SourceExportTableModel(List<AnnotatedPluginDocument> docs, ExportForBarstoolOptions options) throws DocumentOperationException{
        super(docs);
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        sourceFields = options.getSourceFields();
        fixedSourceFields = options.getFixedSourceFields();
        includeLatLong = options.isIncludeLatLong();
        fimsConnection = BiocodeService.getInstance().getActiveFIMSConnection();
        dateFormat = options.getDateFormat();
    }

    int getColumnCount() {
        return sourceFields.size() + fixedSourceFields.size() + (includeLatLong ? 1 : 0);
    }

    String getColumnName(int columnIndex) {
        if (columnIndex < sourceFields.size()) {
            return sourceFields.get(columnIndex).getItemA();
        } else if (columnIndex < sourceFields.size() + fixedSourceFields.size()) {
            return fixedSourceFields.get(columnIndex - sourceFields.size()).getItemA();
        } else {
            return "Lat_Lon";
        }
    }

    Object getValue(AnnotatedPluginDocument doc, int columnIndex) {
        if (columnIndex < sourceFields.size()) {
            Object value = doc.getFieldValue(sourceFields.get(columnIndex).getItemB());

            if (value == null && sourceFields.get(columnIndex).getItemA().equals(ExportForBarstoolOptions.NOTE)) {
                //allowed to be null
                value = "";
            }
            if (value != null && sourceFields.get(columnIndex).getItemA().toLowerCase().contains("date") && !(value instanceof Date)) {
                try {
                    value = dateFormat.parse(value.toString());
                } catch (ParseException e) {
                    value = null;
                }
            }
            return value;
        } else if (columnIndex < sourceFields.size() + fixedSourceFields.size()) {
            return fixedSourceFields.get(columnIndex - sourceFields.size()).getItemB();
        } else {
            BiocodeUtilities.LatLong latLong = fimsConnection.getLatLong(doc);
            if (latLong != null) {
                return latLong.toBarstoolFormat();
            } else {
                return "";
            }
        }
    }
}
