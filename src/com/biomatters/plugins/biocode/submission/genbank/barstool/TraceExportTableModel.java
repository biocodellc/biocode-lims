package com.biomatters.plugins.biocode.submission.genbank.barstool;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.plugins.biocode.assembler.BatchChromatogramExportOperation;
import com.biomatters.plugins.biocode.assembler.SetReadDirectionOperation;

import java.util.ArrayList;
import java.util.Map;

/**
 * @author Richard
 * @version $Id$
 */
public class TraceExportTableModel extends TabDelimitedExport.ExportTableModel {

    private static final String[] COLUMN_NAMES = {"Template_ID", "Trace_file", "Trace_format", "Center_Project", "Program_ID", "Trace_End"};
    private final Map<AnnotatedPluginDocument, AnnotatedPluginDocument> contigsMap;
    private final ExportForBarstoolOptions options;
    private final BatchChromatogramExportOperation chromatogramExportOperation;
    private final String noReadDirectionValue;

    /**
     *
     * @param contigsMap map of trace docs to contigs they came from
     * @param options
     * @param chromatogramExportOperation
     * @param noReadDirectionValue
     */
    public TraceExportTableModel(Map<AnnotatedPluginDocument, AnnotatedPluginDocument> contigsMap, ExportForBarstoolOptions options,
                                 BatchChromatogramExportOperation chromatogramExportOperation, String noReadDirectionValue) {
        super(new ArrayList<AnnotatedPluginDocument>(contigsMap.keySet()));
        this.contigsMap = contigsMap;
        this.options = options;
        this.chromatogramExportOperation = chromatogramExportOperation;
        this.noReadDirectionValue = noReadDirectionValue;
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
                return options.getSequenceId(contigsMap.get(doc));
            case 1:
                return options.getTracesFolderName() + "/" + chromatogramExportOperation.getFileNameUsedFor(doc);
            case 2:
                return chromatogramExportOperation.getFormatUsedFor(doc);
            case 3:
                return options.getProjectName();
            case 4:
                return options.getBaseCaller();
            case 5:
                Object isForwardValue = doc.getFieldValue(SetReadDirectionOperation.IS_FORWARD_FIELD);
                if (isForwardValue == null) {
                    return noReadDirectionValue;
                } else {
                    return (Boolean)isForwardValue ? "F" : "R";
                }
            default:
                return null;
        }
    }
}
