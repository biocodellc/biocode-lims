package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.geneious.publicapi.components.GComboBox;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceListDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ButtonOption;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.options.NamePartOption;
import com.biomatters.plugins.biocode.options.NameSeparatorOption;
import jebl.util.ProgressListener;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import java.sql.SQLException;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 9/07/2009 6:22:53 PM
 */
public class ReactionUtilities {
    private static String PRO_VERSION_INFO = "<html><b>All Fields</b></html>";
    private static String FREE_VERSION_INFO = "Editing in Geneious Pro only";


    /**
     * Shows a dialog and tries to get chromatograms for each reaction by parsing the well name out of the abi file names
     * @param plate the cycle sequencing plate to modify
     * @param owner
     * @return true if the user clicked OK on the dialog
     */
    public static boolean bulkLoadChromatograms(Plate plate, JComponent owner) {
        if(plate == null || plate.getReactionType() != Reaction.Type.CycleSequencing) {
            throw new IllegalArgumentException("You may only call this method with Cycle Sequencing plates");
        }
        final List<CycleSequencingReaction> reactions = new ArrayList<CycleSequencingReaction>();
        for(Reaction r : plate.getReactions()) {
            if(r instanceof CycleSequencingReaction) {
                reactions.add((CycleSequencingReaction)r);
            }
        }

        Options options = new Options(ReactionUtilities.class);
        Options.FileSelectionOption selectionOption = options.addFileSelectionOption("inputFolder", "Folder containing chromats", "", new String[0], "Browse...");
        selectionOption.setSelectionType(JFileChooser.DIRECTORIES_ONLY);


        List<Options.OptionValue> fieldValues = new ArrayList<Options.OptionValue>();
        for(DocumentField field : reactions.get(0).getAllDisplayableFields()) {
            fieldValues.add(new Options.OptionValue(field.getCode(), field.getName(), field.getDescription()));
        }
        Options.OptionValue[] chooseValues = new Options.OptionValue[] {
                new Options.OptionValue("well", "Well number"),
                new Options.OptionValue("field", "Field")
        };
        Options.RadioOption<Options.OptionValue> chooseOption = options.addRadioOption("choose", "Match", chooseValues, chooseValues[0], Options.Alignment.VERTICAL_ALIGN);
        Options.ComboBoxOption<Options.OptionValue> fieldOption = options.addComboBoxOption("field", "", fieldValues, fieldValues.get(0));
        chooseOption.setDependentPosition(Options.RadioOption.DependentPosition.RIGHT);
        chooseOption.addDependent(fieldOption, chooseValues[1]);


        options.beginAlignHorizontally(null, false);
        Options.Option label = options.addLabel("Match:");
        label.setDescription("Separate sequences in to groups according to their names and assemble each group individually");
        NamePartOption namePartOption2 = new NamePartOption("namePart2", "");
        options.addCustomOption(namePartOption2);
        namePartOption2.setDescription("Each name is split into segments by the given separator, then the n-th segment is used to identify the sequence's well");
        options.addLabel("part of name,");
        options.endAlignHorizontally();
        options.beginAlignHorizontally(null, false);
        Options.BooleanOption checkPlateName = options.addBooleanOption("checkPlateName", "", false);
        Options.Option<String, ? extends JComponent> label2 = options.addLabel("Check plate name is correct, where plate name is:");
        checkPlateName.setDescription("Separate sequences in to groups according to their names and assemble each group individually");
        NamePartOption namePartOption = new NamePartOption("namePart", "");
        options.addCustomOption(namePartOption);
        namePartOption.setDescription("Each name is split into segments by the given separator, then the n-th segment is used to identify the sequence's plate");
        Options.Option<String, ? extends JComponent> label3 = options.addLabel("part of name,");
        checkPlateName.addDependent(namePartOption,  true);
        checkPlateName.addDependent(label2, true);
        checkPlateName.addDependent(label3, true);
        options.endAlignHorizontally();
        options.beginAlignHorizontally(null, false);
        options.addLabel(" seperated by");
        NameSeparatorOption nameSeperatorOption = new NameSeparatorOption("nameSeparator", "");
        options.addCustomOption(nameSeperatorOption);
        options.endAlignHorizontally();

        if(!Dialogs.showOptionsDialog(options, "Bulk add chromatograms", true, owner)){
            return false;    
        }

        final int platePart = namePartOption.getPart();
        final int wellPart = namePartOption2.getPart();
        final boolean checkPlate = checkPlateName.getValue();
        DocumentField field = null;
        if(chooseOption.getValue().equals(chooseValues[1])) {
            field = getDocumentField(reactions.get(0).getAllDisplayableFields(), fieldOption.getValue().getName());
            assert field != null; //this shouldn't happen unless the list changes between when the options were displayed and when the user clicks ok.
            if(field == null) {
                return false;
            }
        }

        final File folder = new File(selectionOption.getValue());
        if(!folder.exists()) {
            throw new IllegalStateException(folder.getAbsolutePath()+" does not exist!");
        }
        if(!folder.isDirectory()) {
            throw new IllegalStateException(folder.getAbsolutePath()+" is not a folder!");
        }

        final String separatorString = nameSeperatorOption.getSeparatorString();
        final DocumentField finalField = field;
        Runnable runnable = new Runnable() {
            public void run() {
                importAndAddTraces(reactions, separatorString, platePart, wellPart, finalField, checkPlate, folder);
            }
        };
        BiocodeService.block("Importing traces", owner, runnable);

        return true;
    }

    private static DocumentField getDocumentField(List<DocumentField> fields, String code) {
        for(DocumentField field : fields) {
            if(field.getCode().equals(code)) {
                return field;
            }
        }
        return null;
    }

    private static CycleSequencingReaction getReaction(List<CycleSequencingReaction> reactions, DocumentField field, String value) {
        for(CycleSequencingReaction r : reactions) {
            if(value.equals(""+r.getDisplayableValue(field))) {
                return r;
            }
        }
        return null;
    }

    private static CycleSequencingReaction getReaction(List<CycleSequencingReaction> reactions, String wellString) {
        for(CycleSequencingReaction r : reactions) {
            if(wellString.equalsIgnoreCase(r.getLocationString())) {
                return r;
            }
        }
        return null;
    }

    /**
     *
     * @param reactions
     * @param separatorString
     * @param platePart
     * @param partToMatch
     * @param checkPlate
     * @param folder
     */
    private static void importAndAddTraces(List<CycleSequencingReaction> reactions, String separatorString, int platePart, int partToMatch, DocumentField fieldToCheck, boolean checkPlate, File folder) {
        try {
            BiocodeUtilities.downloadTracesForReactions(reactions, ProgressListener.EMPTY);
        } catch (SQLException e) {
            Dialogs.showMessageDialog("Error reading existing sequences from database: "+e.getMessage());
            return;
        } catch (IOException e) {
            Dialogs.showMessageDialog("Error writing temporary sequences to disk: "+e.getMessage());
            return;
        } catch (DocumentImportException e) {
            Dialogs.showMessageDialog("Error importing existing sequences: "+e.getMessage());
            return;
        }

        for(File f : folder.listFiles()) {
            if(f.isHidden()) {
                continue;
            }
            if(f.getName().startsWith(".")) { //stupid macos files
                continue;
            }
            if(f.getName().toLowerCase().endsWith(".ab1")) { //let's do some actual work...
                String[] nameParts = f.getName().split(separatorString);
                CycleSequencingReaction r;
                if(fieldToCheck != null && nameParts.length > partToMatch) {
                    r = getReaction(reactions, fieldToCheck, nameParts[partToMatch]);
                    if(r == null) {
                        continue;
                    }
                }
                else {
                    BiocodeUtilities.Well well = BiocodeUtilities.getWellFromFileName(f.getName(), separatorString, partToMatch);
                    if (well == null) continue;
                    String wellString = well.toString();
                    r = getReaction(reactions, wellString);
                    if(r == null) {
                        continue;
                    }
                }
                if(checkPlate) {
                    if(nameParts.length >= platePart) {
                        break;
                    }
                    String plateName = nameParts[platePart];
                    if(!plateName.equals(r.getPlateName())) {
                        break;
                    }
                }
                List<AnnotatedPluginDocument> annotatedDocuments = null;
                try {
                    annotatedDocuments = importDocuments(new File[]{f}, ProgressListener.EMPTY);
                } catch (IOException e) {
                    Dialogs.showMessageDialog("Error reading sequences: "+e.getMessage());
                    break;
                } catch (DocumentImportException e) {
                    Dialogs.showMessageDialog("Error importing sequences: "+e.getMessage());
                    break;
                }
                List<NucleotideSequenceDocument> sequences = null;
                try {
                    sequences = getSequencesFromAnnotatedPluginDocuments(annotatedDocuments);
                } catch (Exception e) {
                    continue;
                }
                List<MemoryFile> files = new ArrayList<MemoryFile>();

                try {
                    files.add(loadFileIntoMemory(f));
                } catch (IOException e) {
                    assert false : e.getMessage();
                    //todo: handle
                    e.printStackTrace();
                }


                r.addSequences(sequences, files);

            }
        }
    }

    public static MemoryFile loadFileIntoMemory(File f) throws IOException{
        return new MemoryFile(f.getName(), getBytesFromFile(f));
    }

    public static byte[] getBytesFromFile(File f) throws IOException{
        if(!f.exists()) {
            throw new IllegalArgumentException("The file does not exist!");
        }
        if(f.isDirectory()) {
            throw new IllegalArgumentException("The file is a directory!");
        }
        if(f.length() > (long)Integer.MAX_VALUE) {
            throw new IllegalArgumentException("The file is too long!");
        }


        byte[] result = new byte[(int)f.length()];
        FileInputStream in = new FileInputStream(f);
        in.read(result);
        in.close();
        return result;
    }

    public static List<AnnotatedPluginDocument> importDocuments(File[] sequenceFiles, ProgressListener progress) throws IOException, DocumentImportException {
        List<AnnotatedPluginDocument> pluginDocuments = new ArrayList<AnnotatedPluginDocument>();
        for (int i = 0; i < sequenceFiles.length && !progress.isCanceled(); i++) {
            File f = sequenceFiles[i];
            List<AnnotatedPluginDocument> docs = PluginUtilities.importDocuments(f, ProgressListener.EMPTY);
            pluginDocuments.addAll(docs);
        }
        return pluginDocuments;
    }

    public static List<NucleotideSequenceDocument> getSequencesFromAnnotatedPluginDocuments(List<AnnotatedPluginDocument> pluginDocuments) {
        List<NucleotideSequenceDocument> nucleotideDocuments = new ArrayList<NucleotideSequenceDocument>();
        if(pluginDocuments == null || pluginDocuments.size() == 0) {
            throw new IllegalArgumentException("No documents were imported!");
        }
        for(AnnotatedPluginDocument doc : pluginDocuments) {
            try {
                if(SequenceListDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                    nucleotideDocuments.addAll(((SequenceListDocument)doc.getDocument()).getNucleotideSequences());
                }
                else if(NucleotideSequenceDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                        nucleotideDocuments.add((NucleotideSequenceDocument)doc.getDocument());
                }
                else {
                    throw new IllegalArgumentException("You can only import nucleotide sequences.  The document "+doc.getName()+" was not a nucleotide sequence.");
                }
            } catch (DocumentOperationException e1) {
                throw new IllegalArgumentException(e1.getMessage());
            }
        }
        return nucleotideDocuments;
    }


    public static boolean saveAbiFileFromPlate(Plate plate, JComponent owner) {
        Options options = new Options(ReactionUtilities.class);
        options.addStringOption("owner", "Owner", "");
        options.addStringOption("operator", "Operator", "");
        options.addStringOption("plateSealing", "Plate Sealing", "Septa");
        options.addStringOption("resultsGroup", "Results Group", "");
        options.addStringOption("instrumentProtocol", "Instrument Protocol", "LongSeq50");
        options.addStringOption("analysisProtocol", "Analysis Protocol", "Standard_3.1");

        if(!Dialogs.showOptionsDialog(options, "Export API config file", true, owner)) {
            return false;
        }

        if(plate.getReactionType() == Reaction.Type.Extraction) {
            Dialogs.showMessageDialog("You cannot create ABI input files from extraction plates", "Error creating ABI input", owner, Dialogs.DialogIcon.WARNING);
            return false;
        }

        Preferences prefs = Preferences.userNodeForPackage(ReactionUtilities.class);

        JFileChooser chooser = new JFileChooser(prefs.get("abiFileLocation", System.getProperty("user.home")));

        if(chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return false;
        }

        File outFile = chooser.getSelectedFile();

        PrintWriter out = null;
        try {
            out = new PrintWriter(outFile);
            out.println("Container Name\tPlate ID\tDescription\tContainerType\tAppType\tOwner\tOperator\tPlateSealing\tSchedulingPref");
            out.println(plate.getName()+"\t"+plate.getName()+"\t\t"+plate.getReactions().length+"-Well\tRegular\t"+options.getValue("owner")+"\t"+options.getValue("operator")+"\t"+options.getValue("plateSealing")+"\t1234");
            out.println("AppServer\tAppInstance");
            out.println("SequencingAnalysis");
            out.println("Well\tSample Name\tComment\tResults Group 1\tInstrument Protocol 1\tAnalysis Protocol 1");
            for(Reaction r : plate.getReactions()) {
                out.println(Plate.getWell(r.getPosition(), plate.getPlateSize()).toPaddedString()+"\t"+r.getExtractionId()+"\t\t"+options.getValue("resultsGroup")+"\t"+options.getValue("instrumentProtocol")+"\t"+options.getValue("analysisProtocol"));
            }
            
        } catch (IOException e) {
            e.printStackTrace();  //todo
        } finally {
            if(out != null) {
                out.close();
            }
        }

        return true;

    }

    public static void editReactions(List<Reaction> reactions, boolean justEditDisplayableFields, JComponent owner, boolean justEditOptions, boolean creating) {
        if(reactions == null || reactions.size() == 0) {
            throw new IllegalArgumentException("reactions must be non-null and non-empty");
        }

        ReactionOptions options = null;
        try {
            options = XMLSerializer.clone(reactions.get(0).getOptions());
            options.refreshValuesFromCaches();
            options.setReaction(reactions.get(0));//hack for cycle sequencing traces
        } catch (XMLSerializationException e) {
            //e.printStackTrace();
            //options = reactions.get(0).getOptions();
            throw new RuntimeException(e);
        }

        Map<String, Boolean> haveAllSameValues = new HashMap<String, Boolean>();
        //fill in the master options based on the values in all the reactions
        for(Options.Option option : options.getOptions()) {
            haveAllSameValues.put(option.getName(), true);
            for(Reaction reaction : reactions) {
                if(!reaction.getOptions().getValue(option.getName()).equals(option.getValue())) {
                    haveAllSameValues.put(option.getName(), false);
                    continue;
                }
            }
        }

        OptionsPanel displayPanel = getReactionPanel(options, haveAllSameValues, reactions.size() > 1, creating);
        Vector<DocumentField> selectedFieldsVector = new Vector<DocumentField>();
        Vector<DocumentField> availableFieldsVector = new Vector<DocumentField>();
        for(Reaction r : reactions) {//todo: may be slow
            List<DocumentField> displayableFields = r.getFieldsToDisplay();
            if(displayableFields == null || displayableFields.size() == 0) {
                displayableFields = r.getDefaultDisplayedFields();
            }
            for(DocumentField df : displayableFields) {
                if(!selectedFieldsVector.contains(df)) {
                    selectedFieldsVector.add(df);
                }
            }
            List<DocumentField> availableFields = r.getAllDisplayableFields();
            for(DocumentField df : availableFields) {
                if(!selectedFieldsVector.contains(df) && !availableFieldsVector.contains(df)) {
                    availableFieldsVector.add(df);
                }
            }
        }


        JPanel fieldsPanel = getFieldsPanel(availableFieldsVector,selectedFieldsVector);
        ColoringPanel colorPanel = getColoringPanel(availableFieldsVector, reactions);
        JPanel fieldsAndColorPanel = new JPanel(new BorderLayout());
        fieldsAndColorPanel.setOpaque(false);
        fieldsAndColorPanel.add(fieldsPanel, BorderLayout.CENTER);
        fieldsAndColorPanel.add(colorPanel, BorderLayout.SOUTH);
        JComponent componentToDisplay;
        if(justEditDisplayableFields) {
            componentToDisplay = fieldsAndColorPanel;
        }
        else if(justEditOptions) {
            componentToDisplay = displayPanel;
        }
        else {
            JTabbedPane tabs = new JTabbedPane();
            tabs.add("Reaction",displayPanel);
            tabs.add("Display", fieldsAndColorPanel);
            componentToDisplay = tabs;
        }

        Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[] {"OK", "Cancel"}, "Well Options", owner, Dialogs.DialogIcon.NO_ICON);
        dialogOptions.setMaxWidth(800);
        dialogOptions.setMaxHeight(800);
        Object choice = Dialogs.showDialog(dialogOptions, componentToDisplay);
        if(choice.equals("OK")) {
            int changedOptionCount = 0;
            if(!justEditDisplayableFields || justEditOptions) {
                Element optionsElement = XMLSerializer.classToXML("options", options);
                if(reactions.size() == 1) {
                    changedOptionCount = 1;
                    Reaction reaction = reactions.get(0);
                    try {
                        reaction.setOptions(XMLSerializer.classFromXML(optionsElement, ReactionOptions.class));
                    } catch (XMLSerializationException e) {
                        Dialogs.showMessageDialog("Could not save your options: "+e.getMessage());
                    }
                }
                else {
                    for(Reaction reaction : reactions) {
                        for(final Options.Option option : options.getOptions()) {
                            if(option.isEnabled() && !(option instanceof Options.LabelOption)) {
                                reaction.getOptions().refreshValuesFromCaches();
                                reaction.getOptions().setValue(option.getName(), option.getValue());
                                changedOptionCount++;
                            }
                        }
                    }
                }
            }
            for(Reaction r : reactions) {
                r.setFieldsToDisplay(new ArrayList<DocumentField>(selectedFieldsVector));
                r.setBackgroundColorer(colorPanel.getColorer());
            }
            if(changedOptionCount > 0) {
                String error = reactions.get(0).areReactionsValid(reactions, owner, true);
                if(error != null) {
                    Dialogs.showMessageDialog(error);
                }
            }
        }

    }

    private static OptionsPanel getReactionPanel(ReactionOptions options, Map<String, Boolean> haveAllSameValues, boolean multiOptions, boolean creating) {
        OptionsPanel displayPanel = new OptionsPanel();
        final JCheckBox selectAllBox = new JCheckBox(License.isProVersion() ? PRO_VERSION_INFO : FREE_VERSION_INFO, false);
        selectAllBox.setOpaque(false);
        selectAllBox.setEnabled(License.isProVersion());
        final AtomicBoolean selectAllValue = new AtomicBoolean(selectAllBox.isSelected());
        displayPanel.addTwoComponents(selectAllBox, new JLabel(), true, false);
        final List<JCheckBox> checkboxes = new ArrayList<JCheckBox>();
        for(final Options.Option option : options.getOptions()) {
            JComponent leftComponent;
            if(option instanceof ButtonOption && !((ButtonOption)option).displayInMultiOptions() && multiOptions) {
                continue;
            }

            if(!(option instanceof Options.LabelOption) && !(option instanceof ButtonOption)) {
                if(!creating && options.fieldIsFinal(option.getName())) {
                    option.setEnabled(false);
                    if(multiOptions) {//just don't display the option if we're editing multiple reactions
                        continue;
                    }
                    leftComponent = new JLabel(option.getLabel());
                }
                else {
                    final JCheckBox checkbox = new JCheckBox(option.getLabel(), haveAllSameValues.get(option.getName()));
                    if(!License.isProVersion()) {
                        checkbox.setEnabled(false);
                        checkbox.setSelected(false);
                    }
                    checkbox.setAlignmentY(JCheckBox.RIGHT_ALIGNMENT);
                    checkboxes.add(checkbox);
                    ChangeListener listener = new ChangeListener() {
                        public void stateChanged(ChangeEvent e) {
                            option.setEnabled(checkbox.isSelected());
                        }
                    };
                    checkbox.addChangeListener(listener);
                    listener.stateChanged(null);
                    leftComponent = checkbox;
                }
            }
            else {
                leftComponent = new JLabel(option.getLabel());
                if(!License.isProVersion()) {
                    option.setEnabled(false);
                }
            }
            leftComponent.setOpaque(false);
            selectAllBox.addChangeListener(new ChangeListener(){
                public void stateChanged(ChangeEvent e) {
                    if(selectAllBox.isSelected() != selectAllValue.getAndSet(selectAllBox.isSelected())) {
                        for(JCheckBox cb : checkboxes) {
                            cb.setSelected(selectAllBox.isSelected());
                        }
                    }
                }
            });
            JComponent jComponent;
            if(!creating && options.fieldIsFinal(option.getName())) {
                option.setEnabled(false);
                JTextField field = new JTextField(30);
                field.setText(option.getValue().toString());
                field.setEditable(false);
                field.setBorder(new EmptyBorder(field.getBorder().getBorderInsets(field)));
                field.setOpaque(false);
                jComponent = field;
            }
            else {
                jComponent = getOptionComponent(option);
            }
            displayPanel.addTwoComponents(leftComponent, jComponent, true, false);

        }
        return displayPanel;
    }

    private static JComponent getOptionComponent(final Options.Option option) {
        JComponent comp = option.getComponent();
        if(option instanceof Options.IntegerOption || option instanceof Options.DoubleOption) {
            String units;
            if(option instanceof Options.IntegerOption) {
                units = ((Options.IntegerOption)option).getUnits();
            }
            else {
                units = ((Options.DoubleOption)option).getUnits();
            }
            if(units.length() > 0) {
                JPanel panel = new JPanel(new BorderLayout(2,0));
                panel.setOpaque(false);
                panel.add(comp, BorderLayout.CENTER);
                final JLabel unitsLabel = new JLabel(units);
                panel.add(unitsLabel, BorderLayout.EAST);
                SimpleListener listener = new SimpleListener() {
                    public void objectChanged() {
                        unitsLabel.setEnabled(option.isEnabled());
                    }
                };
                option.addChangeListener(listener);
                listener.objectChanged();
                comp = panel;
            }
        }
        return comp;
    }

    private static JPanel getFieldsPanel(final Vector<DocumentField> availableFieldsVector, final Vector<DocumentField> selectedFieldsVector) {
        JPanel fieldsPanel = new JPanel(new BorderLayout());

        //List<DocumentField> displayableFields = reactions.get(0).getAllDisplayableFields();
        //final Vector<DocumentField> displayableFieldsVector = new Vector(displayableFields);
        final JList availableListBox = new JList(availableFieldsVector);
        availableListBox.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        final JList selectedListBox = new JList(selectedFieldsVector);

        DefaultListCellRenderer cellRenderer = new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component superComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);    //To change body of overridden methods use File | Settings | File Templates.
                if (superComponent instanceof JLabel) {
                    JLabel label = (JLabel) superComponent;
                    DocumentField field = (DocumentField) value;
                    label.setText(field.getName());
                }
                return superComponent;
            }
        };
        availableListBox.setCellRenderer(cellRenderer);
        selectedListBox.setCellRenderer(cellRenderer);



        final JButton addButton = new JButton(IconUtilities.getIcons("arrow_right.png").getIcon16());
        addButton.setOpaque(false);
        addButton.setPreferredSize(new Dimension(addButton.getPreferredSize().height, addButton.getPreferredSize().height));
        addButton.setCursor(Cursor.getDefaultCursor());
        final JButton removeButton = new JButton(IconUtilities.getIcons("arrow_left.png").getIcon16());
        removeButton.setOpaque(false);
        removeButton.setCursor(Cursor.getDefaultCursor());
        removeButton.setPreferredSize(new Dimension(removeButton.getPreferredSize().height, removeButton.getPreferredSize().height));

        final JButton moveUpButton = new JButton(IconUtilities.getIcons("arrow_up.png").getIcon16());
        moveUpButton.setOpaque(false);
        moveUpButton.setPreferredSize(new Dimension(moveUpButton.getPreferredSize().height, moveUpButton.getPreferredSize().height));
        final JButton moveDownButton = new JButton(IconUtilities.getIcons("arrow_down.png").getIcon16());
        moveDownButton.setOpaque(false);
        moveDownButton.setPreferredSize(new Dimension(moveDownButton.getPreferredSize().height, moveDownButton.getPreferredSize().height));

        ListSelectionListener selectionListener = new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent e) {
                addButton.setEnabled(availableListBox.getSelectedIndices().length > 0);
                removeButton.setEnabled(selectedListBox.getSelectedIndices().length > 0);
            }
        };
        availableListBox.addListSelectionListener(selectionListener);
        selectedListBox.addListSelectionListener(selectionListener);

        final ActionListener addAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int offset = 0;
                int[] indices = availableListBox.getSelectedIndices();
                for (int i = 0; i < indices.length; i++) {
                    int index = indices[i]-offset;
                    selectedFieldsVector.add(availableFieldsVector.get(index));
                    availableFieldsVector.remove(index);
                    offset++;
                }
                availableListBox.clearSelection();
                selectedListBox.clearSelection();
                for (ListDataListener listener : ((AbstractListModel) availableListBox.getModel()).getListDataListeners()) {
                    listener.contentsChanged(new ListDataEvent(availableListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, availableFieldsVector.size() - 1));
                }
                for (ListDataListener listener : ((AbstractListModel) selectedListBox.getModel()).getListDataListeners()) {
                    listener.contentsChanged(new ListDataEvent(selectedListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, selectedFieldsVector.size() - 1));
                }
                availableListBox.revalidate();
                selectedListBox.revalidate();
            }
        };

        final ActionListener removeAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int offset = 0;
                int[] indices = selectedListBox.getSelectedIndices();
                for (int i = 0; i < indices.length; i++) {
                    int index = indices[i - offset];
                    availableFieldsVector.add(selectedFieldsVector.get(index));
                    selectedFieldsVector.remove(index);
                    offset++;
                }
                selectedListBox.clearSelection();
                availableListBox.clearSelection();
                for (ListDataListener listener : ((AbstractListModel) selectedListBox.getModel()).getListDataListeners()) {
                    listener.contentsChanged(new ListDataEvent(selectedListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, selectedFieldsVector.size() - 1));
                }
                for (ListDataListener listener : ((AbstractListModel) availableListBox.getModel()).getListDataListeners()) {
                    listener.contentsChanged(new ListDataEvent(availableListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, availableFieldsVector.size() - 1));
                }
                availableListBox.revalidate();
                selectedListBox.revalidate();
            }
        };

        final ActionListener sortUpAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List selectedValues = Arrays.asList(selectedListBox.getSelectedValues());
                Vector<DocumentField> newValues = new Vector<DocumentField>();
                DocumentField current = null;
                for(int i=0; i < selectedFieldsVector.size(); i++) {
                    DocumentField currentField = selectedFieldsVector.get(i);
                    if(selectedValues.contains(currentField)) {
                        newValues.add(currentField);
                    }
                    else {
                        if(current != null) {
                            newValues.add(current);
                        }
                        current = currentField;
                    }
                }
                if(current != null) {
                    newValues.add(current);
                }
                selectedFieldsVector.clear();
                selectedFieldsVector.addAll(newValues);
                for (ListDataListener listener : ((AbstractListModel) selectedListBox.getModel()).getListDataListeners()) {
                    listener.contentsChanged(new ListDataEvent(selectedListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, selectedFieldsVector.size() - 1));
                }
                selectedListBox.revalidate();
                int[] indices = selectedListBox.getSelectedIndices();
                boolean contiguousFirstBlock = true;
                for(int i=0; i < indices.length; i++) {
                    if(contiguousFirstBlock && indices[i] == i) {
                        continue;
                    }
                    contiguousFirstBlock = false;
                    indices[i] --;
                }
                selectedListBox.setSelectedIndices(indices);

            }
        };

        final ActionListener sortDownAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List selectedValues = Arrays.asList(selectedListBox.getSelectedValues());
                Vector<DocumentField> newValues = new Vector<DocumentField>();
                DocumentField current = null;
                for(int i=selectedFieldsVector.size()-1; i >= 0; i--) {
                    DocumentField currentField = selectedFieldsVector.get(i);
                    if(selectedValues.contains(currentField)) {
                        newValues.add(0,currentField);
                    }
                    else {
                        if(current != null) {
                            newValues.add(0,current);
                        }
                        current = currentField;
                    }
                }
                if(current != null) {
                    newValues.add(0,current);
                }
                selectedFieldsVector.clear();
                selectedFieldsVector.addAll(newValues);
                for (ListDataListener listener : ((AbstractListModel) selectedListBox.getModel()).getListDataListeners()) {
                    listener.contentsChanged(new ListDataEvent(selectedListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, selectedFieldsVector.size() - 1));
                }
                selectedListBox.revalidate();
                int[] indices = selectedListBox.getSelectedIndices();
                boolean contiguousFirstBlock = true;
                for(int i=0; i < indices.length; i++) {
                    if(contiguousFirstBlock && indices[indices.length-1-i] == selectedFieldsVector.size()-1-i) {
                        continue;
                    }
                    contiguousFirstBlock = false;
                    indices[indices.length-1-i] ++;
                }
                selectedListBox.setSelectedIndices(indices);

            }
        };

        removeButton.addActionListener(removeAction);
        addButton.addActionListener(addAction);

        moveUpButton.addActionListener(sortUpAction);
        moveDownButton.addActionListener(sortDownAction);

        availableListBox.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() == 2) {
                    addAction.actionPerformed(null);
                }
            }
        });

        selectedListBox.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() == 2) {
                    removeAction.actionPerformed(null);
                }
            }
        });

        final JPanel addRemovePanel = new JPanel(new GridLayout(2,1));
        addRemovePanel.add(addButton);
        addRemovePanel.add(removeButton);
        addRemovePanel.setMaximumSize(addRemovePanel.getPreferredSize());
        addRemovePanel.setMinimumSize(addRemovePanel.getPreferredSize());
        addRemovePanel.setOpaque(false);

        final JPanel movePanel = new JPanel();
        movePanel.setOpaque(false);
        movePanel.add(moveUpButton);
        movePanel.add(moveDownButton);
        movePanel.setLayout(new BoxLayout(movePanel, BoxLayout.Y_AXIS));
        movePanel.add(moveUpButton);
        movePanel.add(moveDownButton);

        JPanel availableListBoxPanel = new JPanel(new BorderLayout());
        availableListBoxPanel.add(new JScrollPane(availableListBox), BorderLayout.CENTER);
        JLabel label1 = new JLabel("Available");
        label1.setOpaque(false);
        availableListBoxPanel.add(label1, BorderLayout.NORTH);
        availableListBoxPanel.setOpaque(false);

        JPanel selectedListBoxPanel = new JPanel(new BorderLayout());
        selectedListBoxPanel.add(new JScrollPane(selectedListBox), BorderLayout.CENTER);
        selectedListBoxPanel.add(movePanel, BorderLayout.EAST);
        JLabel label2 = new JLabel("Selected");
        label2.setOpaque(false);
        selectedListBoxPanel.add(label2, BorderLayout.NORTH);
        selectedListBoxPanel.setOpaque(false);

        final JSplitPane fieldsSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, availableListBoxPanel, selectedListBoxPanel);
        fieldsSplit.setBorder(new EmptyBorder(0,0,0,0));
        fieldsSplit.setUI(new BasicSplitPaneUI(){
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {

                BasicSplitPaneDivider divider = new BasicSplitPaneDivider(this){
                    @Override
                    public int getDividerSize() {
                        return addRemovePanel.getPreferredSize().width;
                    }

                    public void setBorder(Border b) {}
                };
                divider.setLayout(new BoxLayout(divider, BoxLayout.X_AXIS));
                divider.add(addRemovePanel);
                return divider;
            }
        });

        fieldsSplit.setOpaque(false);
        fieldsSplit.setContinuousLayout(true);
        fieldsPanel.add(fieldsSplit);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                //this doesn't have an effect unless the split pane is showing
                fieldsSplit.setDividerLocation(0.5);
            }
        });
        fieldsSplit.setResizeWeight(0.5);
        fieldsPanel.setOpaque(false);
        fieldsPanel.setBorder(new EmptyBorder(10,10,10,10));

        return fieldsPanel;
    }

    public static Collection getAllValues(DocumentField field, List<Reaction> reactions) {
        Set allValues = new HashSet();
        if(field != null) {
            for(Reaction r : reactions) {
                Object value = r.getFieldValue(field.getCode());
                if(value != null) {
                    allValues.add(value);
                }
            }
        }
        return allValues;
    }

    private static ColoringPanel getColoringPanel(final Vector<DocumentField> availableFieldsVector, List<Reaction> reactions) {
        return new ColoringPanel(availableFieldsVector, reactions);
    }

    public static class MemoryFile{
        private String name;
        private byte[] data;

        public MemoryFile(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }

        public String getName() {
            return name;
        }

        public byte[] getData() {
            return data;
        }
    }

    public static class DocumentFieldWrapper implements GComboBox.DescriptionProvider{
            private DocumentField documentField;

            DocumentFieldWrapper(DocumentField documentField) {
                this.documentField = documentField;
            }

            public String getDescription() {
                return documentField == null ? null : documentField.getDescription();
            }

            public String toString() {
                return documentField == null ? "None..." : documentField.getName();
            }

            public DocumentField getDocumentField() {
                return documentField;
            }
        }


}
