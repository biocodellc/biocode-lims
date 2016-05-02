package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.CherryPickingDocument;
import com.biomatters.plugins.biocode.labbench.TableDocumentViewerFactory;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * @author steve
 */
public class CherryPickingDocumentViewerFactory extends TableDocumentViewerFactory {

    @Override
    public String getName() {
        return "Reactions";
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public String getHelp() {
        return null;
    }

    @Override
    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {new DocumentSelectionSignature(CherryPickingDocument.class, 1, Integer.MAX_VALUE)};
    }

    @Override
    public TableModel getTableModel(AnnotatedPluginDocument[] docs, Options options) {
        final List<Reaction> allReactions = new ArrayList<Reaction>();
        Set<DocumentField> fimsFields = new LinkedHashSet<DocumentField>();
        for(AnnotatedPluginDocument doc : docs) {
            CherryPickingDocument cDoc = (CherryPickingDocument)doc.getDocumentOrCrash();
            allReactions.addAll(cDoc.getReactions());
            fimsFields.addAll(cDoc.getFimsFields());
        }
        final List<DocumentField> fimsFieldsList = new ArrayList<DocumentField>(fimsFields);

        return new AbstractTableModel(){
            public int getRowCount() {
                return allReactions.size();
            }

            public int getColumnCount() {
                return 3+fimsFieldsList.size();
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                Reaction reaction = allReactions.get(rowIndex);
                switch (columnIndex) {
                    case 0:
                        return reaction.getPlateName();
                    case 1:
                        return reaction.getLocationString();
                    case 2:
                        return reaction.getExtractionId();
                    case 3:
                        return reaction.getExtractionBarcode();

                }
                return reaction.getFieldValue(fimsFieldsList.get(columnIndex-4).getCode());
            }
            @Override
            public String getColumnName(int column) {
                if(column < 4) {
                    return new String[] {
                            "Plate",
                            "Well",
                            "Extraction ID",
                            "Extraction Barcode"
                    }[column];
                }
                return fimsFieldsList.get(column-4).getName();
            }


        };
    }

    @Override
    protected boolean columnVisibleByDefault(int columnIndex, AnnotatedPluginDocument[] selectedDocuments) {
        return columnIndex < 3;
    }
}
