package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

import javax.swing.table.TableModel;
import javax.swing.table.AbstractTableModel;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Steve
 */
public class TabularPlateDocumentViewerFactory extends TableDocumentViewerFactory {

    public String getName() {
        return "Tabular Plate View";
    }

    public String getDescription() {
        return "A tabular view of plate info";
    }

    public String getHelp() {
        return "A tabular view of plate info.  Right click on the column headers in the table to choose which columns you would like to display.";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(PlateDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    protected TableModel getTableModel(AnnotatedPluginDocument[] docs, Options options) {
        Set<DocumentField> validFieldSet = new LinkedHashSet<DocumentField>();
        final List<Reaction> reactions = new ArrayList<Reaction>();
        if(BiocodeService.getInstance().isLoggedIn()) {
            validFieldSet.addAll(BiocodeService.getInstance().getActiveFIMSConnection().getCollectionAttributes());
            validFieldSet.addAll(BiocodeService.getInstance().getActiveFIMSConnection().getTaxonomyAttributes());
        }
        validFieldSet.add(Reaction.PLATE_NAME_DOCUMENT_FIELD);
        validFieldSet.add(Reaction.WELL_DOCUMENT_FIELD);
        for(AnnotatedPluginDocument doc : docs) {
            PlateDocument plateDoc = (PlateDocument)doc.getDocumentOrCrash();
            Plate plate = plateDoc.getPlate();
            for(Reaction r : plate.getReactions()) {
                if(!r.isEmpty()) {
                    //noinspection unchecked
                    validFieldSet.addAll(r.getDisplayableFields());
                    reactions.add(r);
                }
            }
        }

        if(reactions.size() == 0) {
            return null;
        }

        final List<DocumentField> validFieldList = new ArrayList<DocumentField>(validFieldSet);



        return new AbstractTableModel(){
            public int getRowCount() {
                return reactions.size();
            }

            public int getColumnCount() {
                return validFieldList.size();
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                return reactions.get(rowIndex).getFieldValue(validFieldList.get(columnIndex).getCode());
            }

            @Override
            public String getColumnName(int column) {
                return validFieldList.get(column).getName();
            }
        };
    }

    @Override
    protected boolean columnVisibleByDefault(int columnIndex, AnnotatedPluginDocument[] selectedDocuments) {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            return true;
        }
        return columnIndex >= BiocodeService.getInstance().getActiveFIMSConnection().getCollectionAttributes().size() +
                BiocodeService.getInstance().getActiveFIMSConnection().getTaxonomyAttributes().size();
    }
}
