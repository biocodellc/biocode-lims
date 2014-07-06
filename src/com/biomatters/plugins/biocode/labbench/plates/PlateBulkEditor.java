package com.biomatters.plugins.biocode.labbench.plates;

import com.biomatters.geneious.publicapi.components.*;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.plugin.GeneiousActionOptions;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.*;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.MooreaFimsConnection;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionOptions;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reaction.ReactionUtilities;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 17/06/2009 12:39:03 PM
 */
@SuppressWarnings({"ConstantConditions", "ConstantConditions"})
public class PlateBulkEditor {
    private Plate plate;
    private boolean newPlate;
    private SwapAction swapAction;
    private GeneiousAction archivePlateAction;
    private GeneiousAction importBarcodes;
    private GeneiousAction getExtractionsFromBarcodes;
    private GeneiousAction specNumCollector;
    private GeneiousAction autoGenerateIds;
    private GeneiousAction autodetectAction;


    private List<DocumentField> defaultFields;
    List<DocumentField> autoFillFields = getAutofillFields();


    public enum Direction {
        ACROSS_AND_DOWN,
        DOWN_AND_ACROSS
    }

    public PlateBulkEditor(Plate p, boolean newPlate) {
        plate = p;
        this.newPlate = newPlate;
        defaultFields = getDefaultFields(p, newPlate);
    }

    /**
     * displays an editing dialog
     * @param owner a JComponent for the displayed dialog to be modal over
     * @return  true if the user clicked ok, false if they clicked cancel
     */
    public boolean editPlate(JComponent owner) {
        final JPanel platePanel = new JPanel();
        platePanel.setLayout(new BoxLayout(platePanel, BoxLayout.X_AXIS));
        final List<DocumentFieldEditor> editors = new ArrayList<DocumentFieldEditor>();

        //link the scrolling of all editors together
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
            final AtomicReference<DocumentFieldEditor> editor = new AtomicReference<DocumentFieldEditor>();
            if(autoFillFields.contains(field)) {
                JButton setAllButton = new JButton("Set All", IconUtilities.getIcons("expanded.png").getIcon16());
                setAllButton.setHorizontalTextPosition(SwingConstants.LEFT);
                setAllButton.setBorderPainted(false);
                setAllButton.setBorder(new EmptyBorder(1, 5, 1, 5));
                setAllButton.addActionListener(new ActionListener(){
                    public void actionPerformed(ActionEvent e) {
                        OptionsPanel messagePanel = new OptionsPanel();
                        JTextField textField = new JTextField(25);
                        messagePanel.addComponent(new JLabel(" "), true);
                        messagePanel.addComponentWithLabel("Value for all wells:", textField, false);
                        JCheckBox doWorkflows = new JCheckBox("Also refresh workflows", true);
                        doWorkflows.setToolTipText("Updates the workflow ids to the correct workflow for the new locus");
                        messagePanel.addComponent(doWorkflows, false);
                        if(Dialogs.showOkCancelDialog(messagePanel, "Enter Value", editor.get())) {
                            for (DocumentFieldEditor editorToCheck : editors) {
                                editorToCheck.valuesFromTextView();  // Get values from current text
                            }

                            for(int i=0; i < editor.get().values.length; i++) {
                                for(int j=0; j < editor.get().values[0].length; j++) {
                                    boolean blank = true;
                                    for (DocumentFieldEditor editorToCheck : editors) {
                                        if(editorToCheck == editor.get()) {
                                            continue;
                                        }
                                        Object value = editorToCheck.getValue(i, j);
                                        if(value != null && value.toString().trim().length() > 0 && !value.equals("None")) {
                                            blank = false;
                                        }
                                    }
                                    if(!blank) {
                                        editor.get().values[i][j] = textField.getText();
                                    }
                                }
                            }
                            editor.get().textViewFromValues();
                            if(doWorkflows.isSelected() && autodetectAction != null) {
                                autodetectAction.actionPerformed(null);
                            }
                        }
                    }
                });
                editor.set(new DocumentFieldEditor(field, plate, Direction.ACROSS_AND_DOWN, setAllButton));
            }
            else {
                editor.set(new DocumentFieldEditor(field, plate, Direction.ACROSS_AND_DOWN, null));
            }
            editor.get().addScrollListener(listener);
            editors.add(editor.get());
            platePanel.add(editor.get());
        }

        final DocumentField workflowField = getWorkflowField(plate);
        final DocumentField fieldToCheck = getFieldToCheck(plate);
        final DocumentField lociField = getLociField(plate);

        GeneiousActionToolbar toolbar = new GeneiousActionToolbar(Preferences.userNodeForPackage(PlateBulkEditor.class), false, true);
        swapAction = new SwapAction(editors);
        toolbar.addAction(swapAction);
        List<GeneiousAction> toolsActions = new ArrayList<GeneiousAction>();
        boolean compatiblePlate = plate.getPlateSize() != null || plate.getReactions().length < 96;
        if(plate.getReactionType() == Reaction.Type.Extraction && compatiblePlate && BiocodeService.getInstance().getActiveFIMSConnection().storesPlateAndWellInformation()) {
            archivePlateAction = new GeneiousAction("Get Tissue Id's from archive plate", "Use 2D barcode tube data to get tissue sample ids from the FIMS", IconUtilities.getIcons("database16.png")) {
                public void actionPerformed(ActionEvent e) {
                    //the holder for the textfields
                    List<JTextField> jTextFields = new ArrayList<JTextField>();
                    JPanel textFieldPanel = new JPanel();

                    final boolean size384 = plate.getPlateSize() == Plate.Size.w384;
                    if (size384) {
                        textFieldPanel.setLayout(new GridLayout(2, 2, 2, 2));
                        JTextField tf1 = new GTextField(); //top left
                        JTextField tf2 = new GTextField(); //top right
                        JTextField tf3 = new GTextField(); //bottom left
                        JTextField tf4 = new GTextField(); //bottom right
                        jTextFields.add(tf1);
                        jTextFields.add(tf2);
                        jTextFields.add(tf3);
                        jTextFields.add(tf4);
                        textFieldPanel.add(tf1);
                        textFieldPanel.add(tf2);
                        textFieldPanel.add(tf3);
                        textFieldPanel.add(tf4);
                    } else {
                        textFieldPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
                        textFieldPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
                        JTextField tf1 = new JTextField(15);
                        jTextFields.add(tf1);
                        textFieldPanel.add(tf1);
                    }
                    if (Dialogs.showInputDialog("", "Get FIMS plate", platePanel, new JLabel(" "), new JLabel("Enter the plate ID" + (size384 ? "s" : "")), textFieldPanel)) {
                        final List<String> plateIds = new ArrayList<String>();
                        for (JTextField field : jTextFields) {
                            plateIds.add(field.getText());
                        }
                        DocumentField tissueField = new DocumentField("Tissue Sample Id", "", ExtractionOptions.TISSUE_ID, String.class, false, false);
                        final DocumentFieldEditor tissueEditor = getEditorForField(editors, tissueField);


                        Runnable runnable = new Runnable() {
                            public void run() {
                                try {
                                    List<Map<String, String>> tissueIds = new ArrayList<Map<String, String>>();
                                    for (String plateId : plateIds) {
                                        FIMSConnection connection = BiocodeService.getInstance().getActiveFIMSConnection();
                                        if(connection == null) {
                                            Dialogs.showMessageDialog(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE, "FIMS Connection is unavailable", tissueEditor, Dialogs.DialogIcon.INFORMATION);
                                            return;
                                        }

                                        tissueIds.add(connection.getTissueIdsFromFimsTissuePlate(plateId));
                                    }

                                    for (int i = 0; i < tissueIds.size(); i++) {
                                        if (tissueIds.get(i).size() == 0 && plateIds.get(i).length() > 0) {
                                            Dialogs.showMessageDialog("Plate " + plateIds.get(i) + " not found in the database.", "Plate not found", tissueEditor, Dialogs.DialogIcon.INFORMATION);
                                            return;
                                        }
                                    }

                                    if (size384) {
                                        populateWells384(tissueIds, tissueEditor, plate);
                                    } else {
                                        populateWells96(tissueIds.get(0), tissueEditor, plate, plateIds.get(0));
                                    }

                                } catch (ConnectionException e1) {
                                    e1.printStackTrace();
                                    Dialogs.showMessageDialog("Could not get Tissue IDs from the FIMS database: " + e1.getMessage());
                                }
                            }
                        };
                        BiocodeService.block("Fetching tissue ID's from the FIMS database", tissueEditor, runnable, null);
                    }
                }
            };
            toolsActions.add(archivePlateAction);
        }
        if(plate.getReactionType() == Reaction.Type.Extraction) {
            importBarcodes = new GeneiousAction("Import Extraction Barcodes", "Import extraction barcodes from a barcode scanner file", BiocodePlugin.getIcons("barcode_16.png")) {
                public void actionPerformed(ActionEvent e) {
                    File inputFile = FileUtilities.getUserSelectedFile("Select Barcode File", new FilenameFilter(){
                        public boolean accept(File dir, String name) {
                            return true;
                        }
                    }, JFileChooser.FILES_ONLY);

                    if(inputFile != null) {
                        DocumentField extractionBarcodeField = new DocumentField("Extraction Barcode", "", "extractionBarcode", String.class, false, false);
                        final DocumentFieldEditor barcodeEditor = getEditorForField(editors, extractionBarcodeField);
                        //barcodeEditor.values = new String[barcodeEditor.values.length][barcodeEditor.values[0].length];//todo: make this tidier
                        try {
                            BufferedReader in = new BufferedReader(new FileReader(inputFile));
                            String s;
                            while((s = in.readLine()) != null) {
                                String[] data = s.split("\\t");
                                if(data.length != 2) {
                                    continue;
                                }
                                String wellString = data[0].trim();
                                String barcode = data[1].trim();
                                if(wellString.charAt(wellString.length()-1) == ';') {
                                    wellString = wellString.substring(0, wellString.length()-1);
                                }
                                BiocodeUtilities.Well well;
                                try {
                                    well = new BiocodeUtilities.Well(wellString);
                                }
                                catch(IllegalArgumentException ex) {
                                    continue;
                                }
                                barcodeEditor.setValue(well.row(), well.col(), barcode);
                            }
                            in.close();
                            barcodeEditor.textViewFromValues();
                        }
                        catch(IOException ex) {
                            Dialogs.showMessageDialog("Could not read the input file! "+ex.getMessage());
                        }
                    }
                }
            };
            toolsActions.add(importBarcodes);

            getExtractionsFromBarcodes = new GeneiousAction("Fetch extractions from barcodes", "Fetch extractons that already exist in your database, based on the extraction barcodes you have entered in this plate") {
                public void actionPerformed(ActionEvent e) {
                    DocumentField extractionBarcodeField = new DocumentField("Extraction Barcode", "", "extractionBarcode", String.class, false, false);
                    final DocumentFieldEditor barcodeEditor = getEditorForField(editors, extractionBarcodeField);
                    barcodeEditor.valuesFromTextView();
                    final List<String> barcodes = new ArrayList<String>();
                    for(int i=0; i < plate.getRows(); i++) {
                        for(int j=0; j < plate.getCols(); j++) {
                            final Object value = barcodeEditor.getValue(i, j);
                            if(value != null) {
                                barcodes.add(value.toString());
                            }
                        }
                    }


                    Runnable runnable = new ExtractionFetcherRunnable(barcodes, editors, plate, barcodeEditor);
                    BiocodeService.block("Fetching Extractions from the database", barcodeEditor, runnable, null);
                }
            };
            toolsActions.add(getExtractionsFromBarcodes);

            if(BiocodeService.getInstance().getActiveFIMSConnection() instanceof MooreaFimsConnection) {
                specNumCollector = new GeneiousAction("Map Spec_Num_Collectors", "Convert Spec_Num_Collector values to Mbio numbers") {
                    public void actionPerformed(ActionEvent e) {
                        DocumentField tissueField = new DocumentField("Tissue Sample Id", "", ExtractionOptions.TISSUE_ID, String.class, false, false);
                        final DocumentFieldEditor tissueEditor = getEditorForField(editors, tissueField);
                        tissueEditor.valuesFromTextView();

                        List<String> tissueIds = getIdsToCheck(tissueEditor, plate);



                        try {
                            Map<String, String> mapping = BiocodeService.getSpecNumToMbioMapping(tissueIds);
                            putMappedValuesIntoEditor(tissueEditor, tissueEditor, mapping, plate, false);
                        } catch (ConnectionException e1) {
                            Dialogs.showMessageDialog(e1.getMessage());
                            e1.printStackTrace();
                        }
                    }
                };
                toolsActions.add(specNumCollector);
            }

            autoGenerateIds = new GeneiousAction("Generate Extraction Ids", "Automatically generate extraction ids based on the tissue ids you have entered") {
                public void actionPerformed(ActionEvent e) {
                    DocumentField tissueField = new DocumentField("Tissue Sample Id", "", ExtractionOptions.TISSUE_ID, String.class, false, false);
                    final DocumentFieldEditor tissueEditor = getEditorForField(editors, tissueField);
                    tissueEditor.valuesFromTextView();

                    DocumentField extractionField = new DocumentField("Extraction Id", "", "extractionId", String.class, false, false);
                    final DocumentFieldEditor extractionEditor = getEditorForField(editors, extractionField);
                    extractionEditor.valuesFromTextView();

                    DocumentField extractionBarcodeField = new DocumentField("Extraction Barcode", "", "extractionBarcode", String.class, false, false);
                    final DocumentFieldEditor extractionBarcodeEditor = getEditorForField(editors, extractionBarcodeField);
                    extractionBarcodeEditor.valuesFromTextView();

                    List<String> tissueIds = getIdsToCheck(tissueEditor, plate);

                    List<String> existingExtractionIds = getIdsToCheck(extractionEditor, plate);

                    boolean fillAllWells = false;
                    if(existingExtractionIds.size() > 1) {
                        fillAllWells = Dialogs.showYesNoDialog("There are already extraction ID's on this plate. \nDo you want to overwrite these values (choosing no will generate extraction id's for nonempty wells that don't already have one)", "Extraction IDs already exist", tissueEditor, Dialogs.DialogIcon.QUESTION);
                    }

                    try {
                        Set<String> extractionIds = BiocodeService.getInstance().getActiveLIMSConnection().getAllExtractionIdsForTissueIds(tissueIds);
                        extractionIds.addAll(existingExtractionIds);
                        for(int row=0; row < plate.getRows(); row++) {
                            for(int col=0; col < plate.getCols(); col++) {
                                Object existingValue = extractionEditor.getValue(row, col);
                                Object value = tissueEditor.getValue(row, col);
                                Object barcodeValue = extractionBarcodeEditor.getValue(row, col);
                                if(existingValue != null && existingValue.toString().length() > 0) {
                                    extractionIds.add(existingValue.toString());
                                    if(!fillAllWells) {
                                        continue;
                                    }
                                }
                                if(value != null && value.toString().trim().length() > 0) {
                                    String valueString = ReactionUtilities.getNewExtractionId(extractionIds, value);
                                    extractionEditor.setValue(row, col, valueString);
                                    extractionIds.add(valueString);
                                }
                                else if(barcodeValue != null && barcodeValue.toString().length() > 0) {
                                    String emptyValue = "noTissue";
                                    int i = 1;
                                    while(extractionIds.contains(emptyValue+i)) {
                                        i++;
                                    }
                                    String valueString = emptyValue + i;
                                    extractionEditor.setValue(row, col, valueString);
                                    extractionIds.add(valueString);
                                }
                            }
                        }
                        extractionEditor.textViewFromValues();

                    } catch (DatabaseServiceException e1) {
                        //todo: handle
                        //todo: multithread
                    }
                }
            };
            toolsActions.add(autoGenerateIds);
        }
        else {
            getExtractionsFromBarcodes = new GeneiousAction("Fetch extractions from barcodes", "Fetch extractons that already exist in your database, based on the extraction barcodes you have entered in this plate") {
                public void actionPerformed(ActionEvent e) {
                    DocumentField extractionBarcodeField = new DocumentField("Extraction Barcode", "", "extractionBarcode", String.class, false, false);
                    final DocumentFieldEditor barcodeEditor =new DocumentFieldEditor(extractionBarcodeField, plate, swapAction.getDirection(), null);
                    final AtomicBoolean response = new AtomicBoolean(false);
                    Runnable barcodeEnterRunnable = new Runnable() {
                        public void run() {
                            GPanel panel = new GPanel(new BorderLayout());
                            JToolBar innerToolbar = new JToolBar();
                            innerToolbar.setFloatable(false);
                            innerToolbar.add(new SwapAction(Arrays.asList(barcodeEditor)));
                            for(Component component : innerToolbar.getComponents()) {
                                if(component instanceof JButton) {
                                    ((JButton)component).putClientProperty("hideActionText", Boolean.FALSE);
                                    ((JButton)component).setHorizontalTextPosition(JButton.RIGHT);
                                }
                            }
                            panel.add(innerToolbar, BorderLayout.NORTH);
                            panel.add(barcodeEditor, BorderLayout.CENTER);
                            response.set(Dialogs.showDialog(new Dialogs.DialogOptions(Dialogs.OK_CANCEL, "Enter your extraction Barcodes", editors.get(0)), panel).equals(Dialogs.OK));
                        }
                    };
                    ThreadUtilities.invokeNowOrWait(barcodeEnterRunnable);
                    if(!response.get()) {
                        return;
                    }
                    barcodeEditor.valuesFromTextView();
                    final List<String> barcodes = new ArrayList<String>();
                    for(int i=0; i < plate.getRows(); i++) {
                        for(int j=0; j < plate.getCols(); j++) {
                            final Object value = barcodeEditor.getValue(i, j);
                            if(value != null) {
                                barcodes.add(value.toString());
                            }
                        }
                    }

                    Runnable runnable = new ExtractionFetcherRunnable(barcodes, editors, plate, barcodeEditor);

                    BiocodeService.block("Fetching Extractions from the database", barcodeEditor, runnable, null);
                }
            };
            toolsActions.add(getExtractionsFromBarcodes);
        }
        if(toolsActions.size() > 0) {
            GeneiousAction.SubMenu toolsAction = new GeneiousAction.SubMenu(new GeneiousActionOptions("Tools", "", IconUtilities.getIcons("tools16.png")), toolsActions);
            toolbar.addAction(toolsAction);
        }
        if(fieldToCheck != null) {
            autodetectAction = new GeneiousAction("Autodetect workflows", "Autodetect workflows from the extraction id's you have entered", BiocodePlugin.getIcons("workflow_16.png")) {
                public void actionPerformed(ActionEvent e) {
                    if (fieldToCheck == null) {
                        Dialogs.showMessageDialog("Could not autodetect workflows for this plate - plate type unknown!");
                        return;
                    }
                    final DocumentFieldEditor editorToCheck = getEditorForField(editors, fieldToCheck);
                    if (editorToCheck == null) {
                        Dialogs.showMessageDialog("Could not autodetect workflows for this plate - no editor set for the id field!");
                        return;
                    }
                    final DocumentFieldEditor lociEditor = getEditorForField(editors, lociField);
                    if (lociEditor == null) {
                        Dialogs.showMessageDialog("Could not autodetect workflows for this plate - no editor set for the locus field!");
                        return;
                    }
                    final DocumentFieldEditor workflowEditor = getEditorForField(editors, workflowField);
                    if (workflowEditor == null) {
                        Dialogs.showMessageDialog("Could not autodetect workflows for this plate - no editor set for the workflow field!");
                        return;
                    }
                    editorToCheck.valuesFromTextView();
                    final List<String> idsToCheck = new ArrayList<String>();
                    final List<String> loci = new ArrayList<String>();
                    //todo "noTissue"?
                    for(int row=0; row < plate.getRows(); row++) {
                        for(int col=0; col < plate.getCols(); col++) {
                            Object value = editorToCheck.getValue(row, col);
                            if(value != null && value.toString().trim().length() > 0) {
                                idsToCheck.add(value.toString().trim());
                                Object lociValue = lociEditor.getValue(row, col);
                                loci.add(lociValue != null ? lociValue.toString() : null);
                            }
                        }
                    }
                    Runnable runnable = new Runnable() {
                        public void run() {
                            try {
                                boolean noLociSet = true;
                                for (String l : loci) {
                                    if(l != null && !l.equals("None")) {
                                        noLociSet = false;
                                    }
                                }
                                Map<String, String> idToWorkflow = BiocodeService.getInstance().getWorkflowIds(idsToCheck, loci, plate.getReactionType());
                                if(idToWorkflow.isEmpty()) {
                                    Dialogs.showMessageDialog("<html>Did not find any workflows that match your extraction IDs and locus. " +
                                            (noLociSet ? "Have you set the locus for your plate?" : "") + "<br><br><u><b>Note</b></u>: If you save this plate without any workflows, " +
                                            "Geneious will generate new workflows for your reactions.</html>");
                                } else {
                                    putMappedValuesIntoEditor(editorToCheck, workflowEditor, idToWorkflow, plate, true);
                                }
                            } catch (DatabaseServiceException e1) {
                                Dialogs.showMessageDialog("Could not get Workflow IDs from the database: " + e1.getMessage());
                            }
                        }
                    };
                    BiocodeService.block("Fetching workflow ID's from the database", editorToCheck, runnable, null);

                }
            };
            toolbar.addAction(autodetectAction);
        }
        platePanel.setPreferredSize(new Dimension(600, platePanel.getPreferredSize().height));
        JPanel holderPanel = new JPanel(new BorderLayout());
        holderPanel.add(platePanel, BorderLayout.CENTER);
        holderPanel.add(toolbar, BorderLayout.NORTH);
        //swapAction.actionPerformed(null);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                for (DocumentFieldEditor editor : editors) {
                    editor.setCaretPosition(0);
                }
            }
        });
        if (Dialogs.showDialog(new Dialogs.DialogOptions(new String[] {"OK", "Cancel"}, "Edit Plate", owner), holderPanel).equals("Cancel")) {
            return false;
        }

        for(DocumentFieldEditor editor : editors) {
            editor.valuesFromTextView();
        }

        List<String> workflowIds = new ArrayList<String>();

        //check that the user hasn't forgotten to click the autodetect workflows button
        int workflowCount = 0;
        for(DocumentFieldEditor editor : editors) {
            if(editor.getField().getCode().equals(workflowField.getCode())) {
                for(int row = 0; row < plate.getRows(); row++) {
                    for(int col = 0; col < plate.getCols(); col++) {
                        if(editor.getValue(row, col) != null && editor.getValue(row, col).toString().trim().length() > 0) {
                            workflowCount++;
                        }
                    }
                }
            }
        }
        if(workflowCount == 0 && fieldToCheck != null && autodetectAction != null) {
            if(Dialogs.showYesNoDialog("You have not entered any workflows.  You should only enter no workflows if you are intending to start new workflows with these reactions (for example if you are sequencing a new locus).  <br><br>Do you want to autodetect the workflows? (If you have forgotten to click Autodetect Workflows, click yes)", "No workflows", owner, Dialogs.DialogIcon.QUESTION)){
                autodetectAction.actionPerformed(null);
            }
        }
        //get the workflows out of the database (mainly to check for validity in what the user's entered)
        for(DocumentFieldEditor editor : editors) {
            if(editor.getField().getCode().equals(workflowField.getCode())) {
                for(int row = 0; row < plate.getRows(); row++) {
                    for(int col = 0; col < plate.getCols(); col++) {
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
                workflows = BiocodeService.getInstance().getWorkflows(workflowIds);
            } catch (DatabaseServiceException e) {
                Dialogs.showMessageDialog("Could not get the workflows from the database: "+e.getMessage());
            }
        }
        //put the values back in the reactions
        StringBuilder badWorkflows = new StringBuilder();
        for(int row=0; row < plate.getRows(); row++) {
            for(int col=0; col < plate.getCols(); col++) {
                Reaction reaction = plate.getReaction(row, col);
                Options options = reaction.getOptions();


                for (int i = editors.size()-1; i >= 0; i--) {
                    DocumentFieldEditor editor = editors.get(i);
                    Object value = editor.getValue(row, col);
                    options.setValue(editor.getField().getCode(), ""); //erase records if the user has blanked out the line...
                    if (value != null) {
                        if (editor.getField().getCode().equals(workflowField.getCode()) && workflows != null) {
                            Workflow workflow = workflows.get(value.toString());
                            if (workflow == null) {
                                //noinspection StringConcatenationInsideStringBufferAppend
                                badWorkflows.append(value + "\n");
                            } else {
                                //noinspection StringConcatenationInsideStringBufferAppend
                                reaction.setWorkflow(workflow);
                            }
                        }
                        if (options.getOption(editor.getField().getCode()) != null) {
                            options.setValue(editor.getField().getCode(), editor.getValue(row, col));
                        }
                    }
                }
            }
        }
        if(badWorkflows.length() > 0) {
            Dialogs.showMessageDialog("The following workflow Ids were invalid and were not set:\n"+badWorkflows.toString());
        }
        return true;
    }

    private static void populateWells384(final List<Map<String, String>> ids, final DocumentFieldEditor editorField, Plate p){
        if(ids.size() != 4) {
            throw new IllegalArgumentException("You must have 4 maps!");
        }

        Runnable runnable = new Runnable() {
            public void run() {
                editorField.setText("");
                editorField.valuesFromTextView();

                for(int i=0; i < ids.size(); i++) {
                    int xoffset = i % 2 == 0 ? 0 : 1;
                    int yOffset = i > 1 ? 1 : 0;
                    for(Map.Entry<String, String> entry : ids.get(i).entrySet()) {
                        BiocodeUtilities.Well well = new BiocodeUtilities.Well(entry.getKey());
                        try {
                            editorField.setValue(well.row()*2 + yOffset, well.col()*2 + xoffset, entry.getValue());
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    }
                    editorField.textViewFromValues();
                }
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }

    private static void populateWells96(final Map<String, String> ids, final DocumentFieldEditor editorField, Plate p, String plateId) {
        Runnable runnable = new Runnable() {
            public void run() {
                editorField.setText("");
                editorField.valuesFromTextView();
                for(Map.Entry<String, String> entry : ids.entrySet()) {
                    BiocodeUtilities.Well well;
                    try {
                        well = new BiocodeUtilities.Well(entry.getKey());
                    } catch (IllegalArgumentException e) {
                        Dialogs.showMessageDialog(e.getMessage());
                        return;
                    }
                    try {
                        editorField.setValue(well.row(), well.col(), entry.getValue());
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                }
                editorField.textViewFromValues();
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }

    private static void putMappedValuesIntoEditor(DocumentFieldEditor sourceEditor, DocumentFieldEditor destEditor, Map<String, String> mappedValues, Plate plate, boolean blankUnmappedRows) {
        for(int row=0; row < plate.getRows(); row++) {
            for(int col=0; col < plate.getCols(); col++) {
                Object value = sourceEditor.getValue(row, col);
                if(value != null && value.toString().length() > 0) {
                    String destValue = mappedValues.get(value.toString());
                    if(destValue != null) {
                        destEditor.setValue(row, col, destValue);
                    }
                    else if(blankUnmappedRows) {
                        destEditor.setValue(row, col, "");
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
                    idsToCheck.add(value.toString().trim());
                }
            }
        }
        idsToCheck.add("noTissue");
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

    private static List<DocumentField> getAutofillFields() {
        return Arrays.asList(
                LIMSConnection.WORKFLOW_LOCUS_FIELD
        );
    }

    private static List<DocumentField> getDefaultFields(Plate p, boolean newPlate) {
        switch(p.getReactionType()) {
            case Extraction:
                return Arrays.asList(
                    new DocumentField("Tissue Sample Id", "", ExtractionOptions.TISSUE_ID, String.class, false, false),
                    new DocumentField("Extraction Id", "", "extractionId", String.class, false, false),
                    new DocumentField("Extraction Barcode", "", "extractionBarcode", String.class, false, false),
                    new DocumentField("Parent Extraction Id", "", "parentExtraction", String.class, true, false)
                );
            case PCR://drop through
            case CycleSequencing:
                if(newPlate) {
                    return Arrays.asList(
                        new DocumentField("Extraction Id", "", "extractionId", String.class, false, false),
                        LIMSConnection.WORKFLOW_LOCUS_FIELD,
                        new DocumentField("Workflow Id", "", "workflowId", String.class, false, false)
                    );
                }
                else {
                    return Arrays.asList(
                        new DocumentField("Extraction Id", "", "extractionId", String.class, false, false),
                        LIMSConnection.WORKFLOW_LOCUS_FIELD,
                        new DocumentField("Workflow Id", "", "workflowId", String.class, false, false)
                    );
                }
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

    private static DocumentField getLociField(Plate p) {
        switch(p.getReactionType()) {
            case Extraction:
                return null;
            case PCR:
            case CycleSequencing:
                return LIMSConnection.WORKFLOW_LOCUS_FIELD;
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
        private JButton setAllButton;
        private LineNumbers lineNumbers;
        private JTextArea valueArea;
        private JScrollPane scroller;

        public DocumentFieldEditor(DocumentField field, Plate plate, Direction direction, JButton setAllButton) {
            this.field = field;
            this.plate = plate;
            this.direction = direction;
            this.setAllButton = setAllButton;
            values = new String[plate.getRows()][plate.getCols()];
            for(int row = 0; row < plate.getRows(); row++) {
                for(int col = 0; col < plate.getCols(); col++) {
                    Object value = plate.getReaction(row, col).getFieldValue(field.getCode());
                    values[row][col] = value != null ? value.toString() : "";
                }
            }
            valueArea = new JTextArea() {
                private int minimumRows = DocumentFieldEditor.this.plate.getCols()*DocumentFieldEditor.this.plate.getRows();

                @Override
                public Dimension getPreferredSize() {
                    Dimension regularPrefSize = super.getPreferredSize();
                    int height = Math.max(minimumRows * getRowHeight(), regularPrefSize.height);
                    return new Dimension(regularPrefSize.width, height);
                }
            };
            GuiUtilities.addUndoSupport(valueArea);
            GuiUtilities.addEditMenuSupport(valueArea);
            GuiUtilities.addPopupEditMenu(valueArea);
            valueArea.setTransferHandler(new PasteHandler(valueArea.getTransferHandler()));
            lineNumbers = new LineNumbers() {
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(30, valueArea.getPreferredSize().height);
                }
            };

            scroller = new JScrollPane(valueArea);
            scroller.setRowHeaderView(lineNumbers);

            textViewFromValues();
            setLayout(new BorderLayout());
            add(scroller, BorderLayout.CENTER);

            JPanel topPanel = new GPanel(new BorderLayout());
            topPanel.setBorder(new EmptyBorder(0,5,1,10));
            topPanel.add(new JLabel(field.getName()), BorderLayout.CENTER);
            
            add(topPanel, BorderLayout.NORTH);
            if(setAllButton != null) {
                topPanel.add(setAllButton, BorderLayout.EAST);
            }
            else {
                JPanel spacer = new GPanel();
                spacer.setPreferredSize(new Dimension(1, 15));
                topPanel.add(spacer, BorderLayout.EAST);
            }

            valueArea.getDocument().addDocumentListener(lineNumbers);

            String triggerKey = "ENTER";
            KeyStroke keystroke = KeyStroke.getKeyStroke(triggerKey);
            Object originalMapKey = valueArea.getInputMap().get(keystroke);
            ActionMap actionMap = valueArea.getActionMap();
            final Action originalAction = originalMapKey == null ? null : actionMap.get(originalMapKey);
            Object mapKeyToUse = originalMapKey == null ? triggerKey : originalMapKey;
            actionMap.put(mapKeyToUse, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int caretPosition = valueArea.getCaretPosition();
                    if (caretPosition <= valueArea.getText().length() - 1) {
                        char c = valueArea.getText().charAt(caretPosition);
                        if (c == '\n') {
                            valueArea.setCaretPosition(caretPosition + 1);
                            return;
                        }
                    }
                    if (originalAction != null) {
                        originalAction.actionPerformed(e);
                    }
                }
            });
            valueArea.getInputMap().put(keystroke, mapKeyToUse);
        }

        public void setText(String text) {
            valueArea.setText(text);
        }

        public void textViewFromValues() {
            final int scrollPosition = scroller.getVerticalScrollBar().getValue();
            final StringBuilder valuesBuilder = new StringBuilder();
            final List<String> lineNumbersBuilder = new ArrayList<String>();
            if(direction == Direction.DOWN_AND_ACROSS) {
                for(int row = 0; row < plate.getRows(); row++) {
                    for(int col = 0; col < plate.getCols(); col++) {
                        if(col != 0 || row != 0) {
                            valuesBuilder.append("\n");
                        }
                        valuesBuilder.append(values[row][col]);
                        lineNumbersBuilder.add(Plate.getWellName(row, col));
                    }
                }
            }
            else {
                for(int col = 0; col < plate.getCols(); col++) {
                    for(int row = 0; row < plate.getRows(); row++) {
                        if(col != 0 || row != 0) {
                            valuesBuilder.append("\n");
                        }
                        valuesBuilder.append(values[row][col]);
                        lineNumbersBuilder.add(Plate.getWellName(row, col));
                    }
                }
            }

            Runnable runnable = new Runnable() {
                public void run() {
                    valueArea.setText(valuesBuilder.toString());
                    scroller.getVerticalScrollBar().setValue(scrollPosition);
                    for (int i = 0; i < lineNumbersBuilder.size(); i++) {
                        lineNumbers.setValue(i, lineNumbersBuilder.get(i));
                    }
                }
            };
            ThreadUtilities.invokeNowOrLater(runnable);
        }

        public DocumentField getField() {
            return field;
        }

        public void valuesFromTextView() {
            String[] stringValues = valueArea.getText().split("\n");
            int index = 0;
            if(direction == Direction.DOWN_AND_ACROSS) {
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
            if(row >= values.length || col >= values[0].length) {
                return;
            }
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

        /**
         * newPos must be between 0 and the length of text inclusive.
         * @param newPos
         */
        public void setCaretPosition(int newPos) {
            valueArea.setCaretPosition(newPos);
        }

        public int getCaretPosition() {
            return valueArea.getCaretPosition();
        }

        private class PasteHandler extends TransferHandler {
            private TransferHandler original;
            public PasteHandler(TransferHandler transferHandler) {
                original = transferHandler;
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    DataFlavor textFlavour = DataFlavor.selectBestTextFlavor(support.getTransferable().getTransferDataFlavors());
                    if(textFlavour == null) {
                        return original.importData(support);  // Let the original implementation try
                    }
                    Object textData = support.getTransferable().getTransferData(textFlavour);
                    if(textData == null) {
                        return original.importData(support);  // Let the original implementation try
                    }

                    String text = support.getTransferable().getTransferData(DataFlavor.stringFlavor).toString();
                    String[] newLines = getLines(text);

                    String originalText = valueArea.getText();
                    int endOfLineAfterSelection = originalText.indexOf("\n", valueArea.getSelectionEnd());
                    String trailingLineAfterPaste = originalText.substring(valueArea.getSelectionEnd(),
                            endOfLineAfterSelection == -1 ? originalText.length() : endOfLineAfterSelection);

                    int pasteStart = valueArea.getSelectionStart();
                    String remainingText = originalText.substring(pasteStart);
                    String[] oldLines = remainingText.split("\n", -1);

                    String selection = valueArea.getSelectedText();
                    int selectedLines = selection == null ? 1 : 1 + CharSequenceUtilities.count('\n', selection);
                    int extraEmptyRowsBeingOverwritten = 0;
                    if(newLines.length > 1) {
                        for(int i=selectedLines; i<newLines.length && i<oldLines.length; i++) {
                            if(oldLines[i].trim().length() > 0) {
                                extraEmptyRowsBeingOverwritten++;
                            }
                        }
                    }

                    if(extraEmptyRowsBeingOverwritten > 0) {
                        if (displayOverwritingWarning(newLines, selectedLines, extraEmptyRowsBeingOverwritten))
                            return false;
                    }


                    StringBuilder newText = new StringBuilder();
                    newText.append(originalText.substring(0, pasteStart));
                    for(int i=0; i<oldLines.length || i<newLines.length; i++) {
                        if(i>0) {
                            newText.append("\n");
                        }
                        if(i < newLines.length) {
                            newText.append(newLines[i]);
                            if(selectedLines == newLines.length && i == newLines.length-1) {
                                newText.append(trailingLineAfterPaste);
                            }
                        } else {
                            newText.append(oldLines[i]);
                        }
                    }
                    valueArea.setText(newText.toString());
                    int position = pasteStart + text.length();
                    if(position > newText.length()) {
                        position = newText.length();
                    }
                    valueArea.setCaretPosition(position);

                    return true;
                } catch (UnsupportedFlavorException e) {
                    e.printStackTrace();
                    return false;
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }

            private String[] getLines(String text) throws IOException {
                BufferedReader reader = new BufferedReader(new StringReader(text));

                List<String> lines = new ArrayList<String>();
                String current;
                while((current = reader.readLine()) != null) {
                    lines.add(current);
                }
                reader.close();
                return lines.toArray(new String[lines.size()]);
            }

            private boolean displayOverwritingWarning(String[] newLines, int selectedLines, int extraNonEmptyRowsBeingOverwritten) {
                StringBuilder message = new StringBuilder("<html>You are pasting <strong>" + newLines.length + "</strong> lines");
                if(selectedLines > 1) {
                    message.append(", but have only selected <strong>").append(selectedLines).append("</strong> lines");
                }
                message.append(". This will overwrite <font color=\"red\"><strong>").append(extraNonEmptyRowsBeingOverwritten).append(
                        "</strong></font> existing well");
                if(extraNonEmptyRowsBeingOverwritten > 1) {
                    message.append("s");
                }
                if(selectedLines > 1) {
                    message.append(" outside of your selection");
                } else {
                    message.append(" below the current well");
                }
                message.append(".");

                if(!Dialogs.showYesNoDialog(message + "\n\nContinue?</html>", "Overwriting Existing Data", null, Dialogs.DialogIcon.WARNING)) {
                    return true;
                }
                return false;
            }

            @Override
            public void exportAsDrag(JComponent comp, InputEvent e, int action) {
                original.exportAsDrag(comp, e, action);
            }

            @Override
            public void exportToClipboard(JComponent comp, Clipboard clip, int action) throws IllegalStateException {
                original.exportToClipboard(comp, clip, action);
            }

            @Override
            public boolean importData(JComponent comp, Transferable t) {
                return original.importData(comp, t);
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return original.canImport(support);
            }

            @Override
            public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
                return original.canImport(comp, transferFlavors);
            }

            @Override
            public int getSourceActions(JComponent c) {
                return original.getSourceActions(c);
            }

            @Override
            public Icon getVisualRepresentation(Transferable t) {
                return original.getVisualRepresentation(t);
            }
        }

        private class LineNumbers extends JPanel implements DocumentListener {
            private JLabel[] rowComponents;

            private LineNumbers() {
                setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
                setBackground(new Color(225, 225, 225));
                rowComponents = new JLabel[plate.getRows() * plate.getCols()];
                for(int i=0; i< rowComponents.length; i++) {
                    JLabel label = new JLabel();
                    label.setFont(valueArea.getFont().deriveFont(Font.PLAIN));
                    add(label);
                    rowComponents[i] = label;
                }
            }

            void setValue(int i, String value) {
                rowComponents[i].setText(value);
            }

            private void docChanged(DocumentEvent changeEvent) {
                try {
                    String contents = changeEvent.getDocument().getText(0, changeEvent.getDocument().getLength());
                    String[] parts = contents.split("\n");

                    for(int i=0; i<rowComponents.length; i++) {
                        String oldValue = getPlateValue(i);
                        String newValue = i < parts.length ? parts[i] : "";
                        boolean changed = !oldValue.equals(newValue);
                        rowComponents[i].setForeground(changed ? Color.BLUE : Color.BLACK);
                        rowComponents[i].setFont(rowComponents[i].getFont().deriveFont(
                                changed ? Font.BOLD : Font.PLAIN));
                    }

                } catch (BadLocationException e) {
                    throw new IllegalStateException("Unable to get document contents.  Should not happen.", e);
                }
            }

            /**
             *
             * @param rowIndex The row index in the line numbers component
             * @return field value or "" if it does not exist.  Never null.
             */
            private String getPlateValue(int rowIndex) {
                int row;
                int col;
                if(direction == Direction.DOWN_AND_ACROSS) {
                    row = rowIndex / plate.getCols();
                    col = rowIndex % plate.getCols();
                } else {
                    col = rowIndex / plate.getRows();
                    row = rowIndex % plate.getRows();
                }
                Reaction reaction = plate.getReaction(row, col);
                if(reaction == null) {
                    return "";
                }
                Object fieldValue = reaction.getFieldValue(field.getCode());
                return fieldValue == null ? "" : fieldValue.toString();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                docChanged(e);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                docChanged(e);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                docChanged(e);
            }
        }
    }

    private static class SwapAction extends GeneiousAction{
        private List<DocumentFieldEditor> editors;
        private Direction direction = Direction.ACROSS_AND_DOWN;

        public SwapAction(final List<DocumentFieldEditor> editors) {
            super("Swap Direction", "Swap the direction the wells are read from (between 'across then down', or 'down then across')", BiocodePlugin.getIcons("swapDirection_16.png"));

            this.editors = editors;
        }

        public void actionPerformed(ActionEvent e) {
            switch (direction) {
                case ACROSS_AND_DOWN:
                    direction = Direction.DOWN_AND_ACROSS;
                    break;
                case DOWN_AND_ACROSS:
                    direction = Direction.ACROSS_AND_DOWN;
                    break;
            }
            for (DocumentFieldEditor editor : editors) {
                editor.setDirection(direction);
            }
        }

        public Direction getDirection() {
            return direction;
        }
    }

    private static class ExtractionFetcherRunnable implements Runnable{
        private List<String> barcodes;
        private List<DocumentFieldEditor> editors;
        private Plate plate;
        private DocumentFieldEditor barcodeEditor;

        private ExtractionFetcherRunnable(List<String> barcodes, List<DocumentFieldEditor> editors, Plate plate, DocumentFieldEditor barcodeEditor) {
            this.barcodes = barcodes;
            this.editors = editors;
            this.plate = plate;
            this.barcodeEditor = barcodeEditor;
        }

        public void run() {
            DocumentField extractionField = new DocumentField("Extraction Id", "", "extractionId", String.class, false, false);
            DocumentField parentExtractionField = new DocumentField("Parent Extraction", "", "parentExtraction", String.class, false, false);
            DocumentField tissueField = new DocumentField("Tissue Sample Id", "", ExtractionOptions.TISSUE_ID, String.class, false, false);
            DocumentFieldEditor tissueEditor = getEditorForField(editors, tissueField);
            DocumentFieldEditor extractionIdEditor = getEditorForField(editors, extractionField);
            DocumentFieldEditor parentExtractionEditor = getEditorForField(editors, parentExtractionField);

            try {
                List<ExtractionReaction> temp = BiocodeService.getInstance().getActiveLIMSConnection().getExtractionsFromBarcodes(barcodes);
                Map<String, ExtractionReaction> extractions = new HashMap<String, ExtractionReaction>();
                for (ExtractionReaction reaction : temp) {
                    extractions.put(reaction.getExtractionBarcode(), reaction);
                }
                for(int i=0; i < plate.getRows(); i++) {
                    for(int j=0; j < plate.getCols(); j++) {
                        Object barcode = barcodeEditor.getValue(i,j);

                        //get the extraction
                        ExtractionReaction reaction = null;
                        if(barcode != null) {
                            reaction = extractions.get(barcode.toString());
                        }

                        //fill in the values
                        if(reaction != null) {
                            if(extractionIdEditor != null)
                                extractionIdEditor.setValue(i,j,reaction.getExtractionId());
                            if(parentExtractionEditor != null)
                                parentExtractionEditor.setValue(i,j,""+reaction.getFieldValue("parentExtraction"));
                            if(tissueEditor != null)
                                tissueEditor.setValue(i,j,reaction.getTissueId());
                            //todo: original plate
                        }
                        else {
                            if(extractionIdEditor != null)
                                extractionIdEditor.setValue(i,j,"");
                            if(parentExtractionEditor != null)
                                parentExtractionEditor.setValue(i,j,"");
                            if(tissueEditor != null)
                                tissueEditor.setValue(i,j,"");
                        }

                    }
                    if(extractionIdEditor != null)
                        extractionIdEditor.textViewFromValues();
                    if(parentExtractionEditor != null)
                        parentExtractionEditor.textViewFromValues();
                    if(tissueEditor != null)
                        tissueEditor.textViewFromValues();
                }
            } catch (DatabaseServiceException e1) {
                Dialogs.showMessageDialog("Could not get Workflow IDs from the database: " + e1.getMessage());
                return;
            }
        }
    }
}