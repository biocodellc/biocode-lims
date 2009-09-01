package com.biomatters.plugins.moorea.submission.genbank.barstool;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.plugins.moorea.assembler.verify.Pair;
import com.biomatters.plugins.moorea.labbench.MooreaLabBenchService;
import com.biomatters.plugins.moorea.labbench.fims.FIMSConnection;

import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class SourceExportTableModel extends TabDelimitedExport.ExportTableModel {

    private final List<Pair<String,String>> sourceFields;
    private final List<Pair<String,String>> fixedSourceFields;
    private final FIMSConnection fimsConnection;
    private final boolean includeLatLong;

    public SourceExportTableModel(List<AnnotatedPluginDocument> docs, ExportForBarstoolOptions options) {
        super(docs);
        sourceFields = options.getSourceFields();
        fixedSourceFields = options.getFixedSourceFields();
        includeLatLong = options.isIncludeLatLong();
        fimsConnection = MooreaLabBenchService.getInstance().getActiveFIMSConnection();
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
            return value;
        } else if (columnIndex < sourceFields.size() + fixedSourceFields.size()) {
            return fixedSourceFields.get(columnIndex - sourceFields.size()).getItemB();
        } else {
            return fimsConnection.getLatLong(doc).toBarstoolFormat();
        }
    }
}
