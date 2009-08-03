package com.biomatters.plugins.moorea.labbench.plates;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.GeneiousActionToolbar;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.labbench.MooreaLabBenchService;
import com.biomatters.plugins.moorea.labbench.Workflow;
import com.biomatters.plugins.moorea.MooreaPlugin;
import com.biomatters.plugins.moorea.labbench.ConnectionException;
import com.biomatters.plugins.moorea.labbench.reaction.Reaction;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.text.Caret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.AdjustmentEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import java.sql.SQLException;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 17/06/2009 12:39:03 PM
 */
public class PlateBulkEditor {
    public enum Direction {
        ACROSS_AND_DOWN,
        DOWN_AND_ACROSS
    }

    public static void editPlate(final Plate p, JComponent owner) {
        JPanel platePanel = new JPanel();
        final AtomicReference<Direction> direction = new AtomicReference<Direction>(Direction.ACROSS_AND_DOWN);
        platePanel.setLayout(new BoxLayout(platePanel, BoxLayout.X_AXIS));
        List<DocumentField> defaultFields = getDefaultFields(p);
        final List<DocumentFieldEditor> editors = new ArrayList<DocumentFieldEditor>();
        final AtomicBoolean isAdjusting = new AtomicBoolean(false);
        AdjustmentListener listener = new AdjustmentListener(){
            public void adjustmentValueChanged(AdjustmentEvent e) {
                if(isAdjusting.get()) {
                    return;
                }
                isAdjusting.set(true);
                for(DocumentFieldEditor editor : editors) {
                    editor.setScrollPosition(e.getValue());
                }
                isAdjusting.set(false);
            }
        };
        for(DocumentField field : defaultFields) {
            DocumentFieldEditor editor = new DocumentFieldEditor(field, p, direction.get());
            editor.addScrollListener(listener);
            editors.add(editor);
            platePanel.add(editor);
        }

        final DocumentField workflowField = getWorkflowField(p);
        final DocumentField fieldToCheck = getFieldToCheck(p);

        GeneiousActionToolbar toolbar = new GeneiousActionToolbar(Preferences.userNodeForPackage(PlateBulkEditor.class), false, true);
        toolbar.addAction(new GeneiousAction("Swap Direction", "Swap the direction the wells are read from (between 'across then down', or 'down then across')", MooreaPlugin.getIcons("swapDirection_16.png")){
            public void actionPerformed(ActionEvent e) {
                switch(direction.get()) {
                    case ACROSS_AND_DOWN:
                        direction.set(Direction.DOWN_AND_ACROSS);
                        break;
                    case DOWN_AND_ACROSS:
                        direction.set(Direction.ACROSS_AND_DOWN);
                        break;
                }
                for(DocumentFieldEditor editor : editors) {
                    editor.setDirection(direction.get());
                }
            }
        });
        if(p.getReactionType() == Reaction.Type.Extraction) {
            toolbar.addAction(new GeneiousAction("Get Tissue Id's from Barcodes", "Use 2D barcode tube data to get tissue sample ids from the FIMS", MooreaPlugin.getIcons("barcode_16.png")) {
                public void actionPerformed(ActionEvent e) {
                    DocumentField barcodeField = new DocumentField("Tissue Barcode", "", "barcode", String.class, false, false);
                    DocumentField tissueField = new DocumentField("Tissue Sample Id", "", "sampleId", String.class, false, false);
                    final DocumentFieldEditor barcodeEditor = new DocumentFieldEditor(barcodeField, p, direction.get());
                    final DocumentFieldEditor tissueEditor = getEditorForField(editors, tissueField);
                    if(tissueEditor == null) {
                        Dialogs.showMessageDialog("Could not autodetect tissue id's for this plate - no editor set for the id field!");
                        return;
                    }
                    if(Dialogs.showOkCancelDialog(barcodeEditor, "Enter Barcode Ids", tissueEditor, Dialogs.DialogIcon.NO_ICON)) {
                        barcodeEditor.valuesFromTextView();
                        final List<String> idsToCheck = getIdsToCheck(barcodeEditor, p);
                            Runnable runnable = new Runnable() {
                                public void run() {
                                    try {
                                        Map<String, String> barcodeToId = MooreaLabBenchService.getInstance().getActiveFIMSConnection().getTissueIdsFromExtractionBarcodes(idsToCheck);
                                        putMappedValuesIntoEditor(barcodeEditor, tissueEditor, barcodeToId, p);
                                    } catch (ConnectionException e1) {
                                        Dialogs.showMessageDialog("Could not get Workflow IDs from the database: "+e1.getMessage());
                                    }
                                }
                            };
                        MooreaLabBenchService.block("Fetching tissue ID's from the FIMS database", tissueEditor, runnable);
                    }
                }
            });
        }
        if(fieldToCheck != null) {
            toolbar.addAction(new GeneiousAction("Autodetect workflows", "Autodetect workflows from the extraction id's you have entered", MooreaPlugin.getIcons("workflow_16.png")){
                public void actionPerformed(ActionEvent e) {
                    if(fieldToCheck == null) {
                        Dialogs.showMessageDialog("Could not autodetect workflows for this plate - plate type unknown!");
                        return;
                    }
                    final DocumentFieldEditor editorToCheck = getEditorForField(editors, fieldToCheck);
                    if(editorToCheck == null) {
                        Dialogs.showMessageDialog("Could not autodetect workflows for this plate - no editor set for the id field!");
                        return;
                    }
                    final DocumentFieldEditor workflowEditor = getEditorForField(editors, workflowField);
                    if(workflowEditor == null) {
                        Dialogs.showMessageDialog("Could not autodetect workflows for this plate - no editor set for the workflow field!");
                        return;
                    }
                    editorToCheck.valuesFromTextView();
                    final List<String> idsToCheck = getIdsToCheck(editorToCheck, p);
                    Runnable runnable = new Runnable() {
                        public void run() {
                            try {
                                Map<String, String> idToWorkflow = MooreaLabBenchService.getInstance().getWorkflowIds(idsToCheck, p.getReactionType());
                                putMappedValuesIntoEditor(editorToCheck, workflowEditor, idToWorkflow, p);
                            } catch (SQLException e1) {
                                Dialogs.showMessageDialog("Could not get Workflow IDs from the database: "+e1.getMessage());
                                return;
                            }
                        }
                    };
                    MooreaLabBenchService.block("Fetching workflow ID's from the database", editorToCheck, runnable);

                }
            });
        }
        JPanel holderPanel = new JPanel(new BorderLayout());
        holderPanel.add(platePanel, BorderLayout.CENTER);
        holderPanel.add(toolbar, BorderLayout.NORTH);
        if(Dialogs.showDialog(new Dialogs.DialogOptions(Dialogs.OK_CANCEL, "Edit Plate", owner), holderPanel) == Dialogs.CANCEL) {
            return;    
        }

        for(DocumentFieldEditor editor : editors) {
            editor.valuesFromTextView();
        }

        List<String> workflowIds = new ArrayList<String>();

        //get the workflows out of the database (mainly to check for validity in what the user's entered)
        for(DocumentFieldEditor editor : editors) {
            if(editor.getField().getCode().equals(workflowField.getCode())) {
                for(int row = 0; row < p.getRows(); row++) {
                    for(int col = 0; col < p.getCols(); col++) {
                        if(editor.getValue(row, col) != null && editor.getValue(row, col).toString().trim().length() > 0) {
                            workflowIds.add(editor.getValue(row, col).toString());
                        }
                    }
                }
            }
        }
        Map<String, Workflow> workflows = null;
        if(workflowIds.size() > 0) {
            try {
                workflows = MooreaLabBenchService.getInstance().getWorkflows(workflowIds);
            } catch (SQLException e) {
                Dialogs.showMessageDialog("Could not get the workflows from the database: "+e.getMessage());
            }
        }

        //put the values back in the reactions
        StringBuilder badWorkflows = new StringBuilder();
        for(int row=0; row < p.getRows(); row++) {
            for(int col=0; col < p.getCols(); col++) {
                Reaction reaction = p.getReaction(row, col);
                Options options = reaction.getOptions();


                for(DocumentFieldEditor editor : editors) {
                    Object value = editor.getValue(row, col);
                    if(value != null) {
                        if(editor.getField().getCode().equals(workflowField.getCode()) && workflows != null) {
                            Workflow workflow = workflows.get(value);
                            if(workflow == null) {
                                badWorkflows.append(value+"\n");
                            }
                            else {
                                reaction.setWorkflow(workflow);
                            }
                        }
                        if(options.getOption(editor.getField().getCode()) != null) {
                            options.setValue(editor.getField().getCode(), editor.getValue(row, col));
                        }
                    }
                }
            }
        }
        if(badWorkflows.length() > 0) {
            Dialogs.showMessageDialog("The following workflow Ids were invalid and were not set:\n"+badWorkflows.toString());
        }
    }

    private static void putMappedValuesIntoEditor(DocumentFieldEditor sourceEditor, DocumentFieldEditor destEditor, Map<String, String> mappedValues, Plate plate) {
        for(int row=0; row < plate.getRows(); row++) {
            for(int col=0; col < plate.getCols(); col++) {
                Object value = sourceEditor.getValue(row, col);
                if(value != null && value.toString().length() > 0) {
                    String destValue = mappedValues.get(value.toString());
                    if(destValue != null) {
                        destEditor.setValue(row, col, destValue);
                    }
                }
            }
        }
        destEditor.textViewFromValues();
    }

    private static List<String> getIdsToCheck(DocumentFieldEditor editorToCheck, Plate p) {
        List<String> idsToCheck = new ArrayList<String>();
        for(int row=0; row < p.getRows(); row++) {
            for(int col=0; col < p.getCols(); col++) {
                Object value = editorToCheck.getValue(row, col);
                if(value != null && value.toString().trim().length() > 0) {
                    idsToCheck.add(value.toString());
                }
            }
        }
        return idsToCheck;
    }

    private static DocumentFieldEditor getEditorForField(List<DocumentFieldEditor> editors, DocumentField field) {
        DocumentFieldEditor editorToCheck = null;
        for(DocumentFieldEditor editor : editors) {
            if(editor.getField().getCode().equals(field.getCode())){
                editorToCheck = editor;
            }
        }
        return editorToCheck;
    }

    private static List<DocumentField> getDefaultFields(Plate p) {
        switch(p.getReactionType()) {
            case Extraction:
                return Arrays.asList(
                    new DocumentField("Tissue Sample Id", "", "sampleId", String.class, false, false),
                    new DocumentField("Extraction Id", "", "extractionId", String.class, false, false),
                    new DocumentField("Parent Extraction Id", "", "parentExtraction", String.class, true, false)
                );
            case PCR://drop through
            case CycleSequencing:
                return Arrays.asList(
                    new DocumentField("Extraction Id", "", "extractionId", String.class, false, false),
                    new DocumentField("Workflow Id", "", "workflowId", String.class, false, false)
                );
            default :
                return Collections.EMPTY_LIST;
        }
    }

    private static DocumentField getFieldToCheck(Plate p) {
        switch(p.getReactionType()) {
            case Extraction:
                return null;
            case PCR:
            case CycleSequencing:
                return new DocumentField("Extraction Id", "", "extractionId", String.class, false, false);
            default :
                return null;
        }
    }

    private static DocumentField getWorkflowField(Plate p) {
        switch(p.getReactionType()) {
            default :
                return new DocumentField("Workflow Id", "", "workflowId", String.class, false, false);
        }
    }



    static class DocumentFieldEditor extends JPanel {
        private DocumentField field;
        private Plate plate;
        private String[][] values;
        Direction direction;
        private JTextArea lineNumbers;
        private JTextArea valueArea;
        private JScrollPane scroller;

        public DocumentFieldEditor(DocumentField field, Plate plate, Direction direction){
            this.field = field;
            this.plate = plate;
            this.direction = direction;
            values = new String[plate.getRows()][plate.getCols()];
            for(int row = 0; row < plate.getRows(); row++) {
                for(int col = 0; col < plate.getCols(); col++) {
                    Object value = plate.getReaction(row, col).getFieldValue(field.getCode());
                    values[row][col] = value != null ? value.toString() : "";
                }
            }
            valueArea = new JTextArea();
            lineNumbers = new JTextArea() {
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(30, valueArea.getPreferredSize().height);
                }
            };
            lineNumbers.setEditable(false);
            lineNumbers.setCaret(new Caret(){//disable selections and cursor setting on the line numbers
                public void install(javax.swing.text.JTextComponent c){}
                public void deinstall(javax.swing.text.JTextComponent c){}
                public void paint(Graphics g){}
                public void addChangeListener(ChangeListener l){}
                public void removeChangeListener(ChangeListener l){}
                public boolean isVisible(){return false;}
                public void setVisible(boolean v){}
                public boolean isSelectionVisible(){return false;}
                public void setSelectionVisible(boolean v){}
                public void setMagicCaretPosition(Point p){}
                public Point getMagicCaretPosition(){return new Point(0,0);}
                public void setBlinkRate(int rate){}
                public int getBlinkRate(){return 10000;}
                public int getDot(){return 0;}
                public int getMark(){return 0;}
                public void setDot(int dot){}
                public void moveDot(int dot){}
            });
            lineNumbers.setBackground(new Color(225,225,225));
            scroller = new JScrollPane(valueArea);
            scroller.setRowHeaderView(lineNumbers);
            textViewFromValues();
            setLayout(new BorderLayout());
            add(scroller, BorderLayout.CENTER);
            add(new JLabel(field.getName()), BorderLayout.NORTH);
        }

        public void textViewFromValues() {
            StringBuilder valuesBuilder = new StringBuilder();
            StringBuilder lineNumbersBuilder = new StringBuilder();
            if(direction == Direction.ACROSS_AND_DOWN) {
                for(int row = 0; row < plate.getRows(); row++) {
                    for(int col = 0; col < plate.getCols(); col++) {
                        valuesBuilder.append(values[row][col]+"\n");
                        lineNumbersBuilder.append(plate.getWellName(row, col)+"\n");
                    }
                }
            }
            else {
                for(int col = 0; col < plate.getCols(); col++) {
                    for(int row = 0; row < plate.getRows(); row++) {
                        valuesBuilder.append(values[row][col]+"\n");
                        lineNumbersBuilder.append(plate.getWellName(row, col)+"\n");
                    }
                }
            }

            valueArea.setText(valuesBuilder.toString());
            lineNumbers.setText(lineNumbersBuilder.toString());
        }

        public DocumentField getField() {
            return field;
        }

        public void valuesFromTextView() {
            String[] stringValues = valueArea.getText().split("\n");
            int index = 0;
            if(direction == Direction.ACROSS_AND_DOWN) {
                for(int row = 0; row < plate.getRows(); row++) {
                    for(int col = 0; col < plate.getCols(); col++) {
                        String value;
                        if(index >= stringValues.length)
                            value = "";
                        else
                            value = stringValues[index];
                        values[row][col] = value;
                        index++;
                    }
                }
            }
            else {
                for(int col = 0; col < plate.getCols(); col++) {
                    for(int row = 0; row < plate.getRows(); row++) {
                        String value;
                        if(index >= stringValues.length)
                            value = "";
                        else
                            value = stringValues[index];
                        values[row][col] = value;
                        index++;
                    }
                }
            }
        }

        public void setDirection(Direction dir) {
            if(dir != direction) {
                valuesFromTextView();
                direction = dir;
                textViewFromValues();
                repaint();
            }
        }

        public Object getValue(int row, int col) throws IllegalStateException{
            String stringValue = values[row][col];
            if(stringValue.trim().length() == 0) {
                return null;
            }
            if(Integer.class.isAssignableFrom(field.getClass())) {
                try{
                    return Integer.parseInt(stringValue);
                }
                catch(NumberFormatException ex) {
                    throw new IllegalStateException("Invalid value '"+stringValue+"' is invalid.  Expected an integer.");
                }
            }
            else if(Double.class.isAssignableFrom(field.getClass())) {
                try{
                    return Double.parseDouble(stringValue);
                }
                catch(NumberFormatException ex) {
                    throw new IllegalStateException("Invalid value '"+stringValue+"' is invalid.  Expected a floating point number.");
                }
            }
            else {
                return stringValue;
            }
        }

        public void setValue(int row, int col, String value) {
            values[row][col] = value;
        }

        public int getScrollPosition() {
            return scroller.getVerticalScrollBar().getValue();
        }

        public void setScrollPosition(int position) {
            scroller.getVerticalScrollBar().setValue(position);
        }

        public void addScrollListener(AdjustmentListener al) {
            scroller.getVerticalScrollBar().addAdjustmentListener(al);
        }
    }
}
