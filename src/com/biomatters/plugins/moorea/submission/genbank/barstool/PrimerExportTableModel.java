package com.biomatters.plugins.moorea.submission.genbank.barstool;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;

import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class PrimerExportTableModel extends TabDelimitedExport.ExportTableModel {

    private static final String[] COLUMN_NAMES = {"Sequence_ID", "fwd_primer_seq", "rev_primer_seq", "fwd_primer_name", "rev_primer_name"};

    private final ExportForBarstoolOptions options;

    public PrimerExportTableModel(List<AnnotatedPluginDocument> docs, ExportForBarstoolOptions exportForBarstoolOptions) {
        super(docs);
        options = exportForBarstoolOptions;
    }

    @Override
    int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    String getColumnName(int columnIndex) {
        return COLUMN_NAMES[columnIndex];
    }

    @Override
    Object getValue(AnnotatedPluginDocument doc, int columnIndex) {
        switch (columnIndex) {
            case 0:
                return options.getSequenceId(doc);
            case 1:
                //todo
            case 2:
                //todo
            case 3:
                //todo
            case 4:
                //todo
                return columnIndex;
            default:
                return null;
        }
    }
}
