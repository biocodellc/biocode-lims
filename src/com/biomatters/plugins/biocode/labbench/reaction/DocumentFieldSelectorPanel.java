package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.GComboBox;
import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.documents.DocumentField;

import javax.swing.*;
import java.awt.*;
import java.util.Vector;

/**
 * @author Matthew Cheung
 *         Created on 1/12/15 11:38 AM
 */
public class DocumentFieldSelectorPanel extends GPanel {
    private final Vector<DocumentField> availableFieldsVector;
    protected final GComboBox fieldCombo;

    public DocumentFieldSelectorPanel(String label, Vector<DocumentField> availableFieldsVector) {
        super(new BorderLayout());
        this.availableFieldsVector = availableFieldsVector;
        Vector<ReactionUtilities.DocumentFieldWrapper> cbValues = getDocumentFields();
        fieldCombo = new GComboBox(cbValues);

        JPanel cbPanel = new JPanel();
        cbPanel.setOpaque(false);
        JLabel jLabel = new JLabel(label);
        jLabel.setOpaque(false);
        cbPanel.add(jLabel);
        cbPanel.add(fieldCombo);
        add(cbPanel, BorderLayout.NORTH);
    }

    private Vector<ReactionUtilities.DocumentFieldWrapper> getDocumentFields() {
        Vector<ReactionUtilities.DocumentFieldWrapper> cbValues = new Vector<ReactionUtilities.DocumentFieldWrapper>();
        cbValues.add(new ReactionUtilities.DocumentFieldWrapper(null));
        for(DocumentField field : availableFieldsVector) {
            cbValues.add(new ReactionUtilities.DocumentFieldWrapper(field));
        }
        return cbValues;
    }

    public void setDocumentField(DocumentField newField) {
        final Vector<ReactionUtilities.DocumentFieldWrapper> documentFields = getDocumentFields();
        int index = 0;
        for(int i=0; i < documentFields.size(); i++) {
            DocumentField candidate = documentFields.get(i).getDocumentField();
            if(newField != null && candidate != null && newField.getCode().equals(candidate.getCode())) {
                index = i;
            }
        }
        fieldCombo.setSelectedIndex(index);
    }

    public DocumentField getDocumentField() {
        ReactionUtilities.DocumentFieldWrapper wrapper = (ReactionUtilities.DocumentFieldWrapper)fieldCombo.getSelectedItem();
        return wrapper.getDocumentField();
    }
}
