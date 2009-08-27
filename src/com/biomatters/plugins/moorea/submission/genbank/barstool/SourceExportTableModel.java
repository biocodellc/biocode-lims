package com.biomatters.plugins.moorea.submission.genbank.barstool;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;

import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class SourceExportTableModel extends TabDelimitedExport.ExportTableModel {

    private final ExportForBarstoolOptions options;

    public SourceExportTableModel(List<AnnotatedPluginDocument> docs, ExportForBarstoolOptions options) {
        super(docs);
        this.options = options;
    }

    int getColumnCount() {
        return options.getSourceFields().size() + options.getFixedSourceFields().size();
    }

    String getColumnName(int columnIndex) {
        if (columnIndex < options.getSourceFields().size()) {
            return options.getSourceFields().get(columnIndex).getItemA();
        } else {
            return options.getFixedSourceFields().get(columnIndex - options.getSourceFields().size()).getItemA();
        }
    }

    Object getValue(AnnotatedPluginDocument doc, int columnIndex) {
        if (columnIndex < options.getSourceFields().size()) {
            return doc.getFieldValue(options.getSourceFields().get(columnIndex).getItemB());
        } else {
            return options.getFixedSourceFields().get(columnIndex - options.getSourceFields().size()).getItemB();
        }
    }
}
