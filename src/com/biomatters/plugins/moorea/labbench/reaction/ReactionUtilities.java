package com.biomatters.plugins.moorea.labbench.reaction;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceListDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.plugins.moorea.MooreaUtilities;
import com.biomatters.plugins.moorea.labbench.ButtonOption;
import com.biomatters.plugins.moorea.labbench.MooreaLabBenchService;
import com.biomatters.plugins.moorea.labbench.plates.Plate;
import com.biomatters.plugins.moorea.options.NamePartOption;
import com.biomatters.plugins.moorea.options.NameSeparatorOption;
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
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;

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
        Options.FileSelectionOption selectionOption = options.addFileSelectionOption("inputFolder", "Folder containing chromats", "", new String[0], "Browse...", new FilenameFilter() {
            public boolean accept(File dir, String name) {
                File file = new File(dir, name);
                return file.exists() && file.isDirectory();
            }
        });


        options.beginAlignHorizontally(null, false);
        Options.Option label = options.addLabel("Well number is:");
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

        final File folder = new File(selectionOption.getValue());
        if(!folder.exists()) {
            throw new IllegalStateException(folder.getAbsolutePath()+" does not exist!");
        }
        if(!folder.isDirectory()) {
            throw new IllegalStateException(folder.getAbsolutePath()+" is not a folder!");
        }

        final String separatorString = nameSeperatorOption.getSeparatorString();
        Runnable runnable = new Runnable() {
            public void run() {
                importAndAddTraces(reactions, separatorString, platePart, wellPart, checkPlate, folder);
            }
        };
        MooreaLabBenchService.block("Importing traces", owner, runnable);

        return true;
    }

    /**
     *
     * @param reactions
     * @param separatorString
     * @param platePart
     * @param wellPart
     * @param checkPlate
     * @param folder
     */
    private static void importAndAddTraces(List<CycleSequencingReaction> reactions, String separatorString, int platePart, int wellPart, boolean checkPlate, File folder) {
        for(File f : folder.listFiles()) {
            if(f.isHidden()) {
                continue;
            }
            if(f.getName().startsWith(".")) { //stupid macos files
                continue;
            }
            if(f.getName().toLowerCase().endsWith(".ab1")) { //let's do some actual work...
                String[] nameParts = f.getName().split(separatorString);
                MooreaUtilities.Well well = MooreaUtilities.getWellFromFileName(f.getName(), separatorString, wellPart);
                if (well == null) continue;
                String wellString = well.toString();
                if(wellString.equals("A1")) {
                    System.out.println(f.getAbsolutePath());
                }
                for(CycleSequencingReaction r : reactions) {
                    if(wellString.equalsIgnoreCase(r.getLocationString())) {
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
                        List<NucleotideSequenceDocument> sequences = getSequencesFromAnnotatedPluginDocuments(annotatedDocuments);
                        r.addSequences(sequences);
                    }
                }
            }
        }
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

    public static void editReactions(List<Reaction> reactions, boolean justEditDisplayableFields, Component owner, boolean justEditOptions, boolean creating) {
        if(reactions == null || reactions.size() == 0) {
            throw new IllegalArgumentException("reactions must be non-null and non-empty");
        }

        ReactionOptions options = null;
        try {
            options = XMLSerializer.clone(reactions.get(0).getOptions());
            options.refreshValuesFromCaches();
        } catch (XMLSerializationException e) {
            //assert false : e.getMessage(); //there's no way I can see that this would happen, so I'm making it an assert
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
        JComponent componentToDisplay;
        if(justEditDisplayableFields) {
            componentToDisplay = fieldsPanel;
        }
        else if(justEditOptions) {
            componentToDisplay = displayPanel;
        }
        else {
            JTabbedPane tabs = new JTabbedPane();
            tabs.add("Reaction",displayPanel);
            tabs.add("Display", fieldsPanel);
            componentToDisplay = tabs;
        }

        if(Dialogs.showOkCancelDialog(componentToDisplay, "Well Options", owner, Dialogs.DialogIcon.NO_ICON)) {
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
            }
            if(changedOptionCount > 0) {
                String error = reactions.get(0).areReactionsValid(reactions);
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
                jComponent = new JLabel(option.getValue().toString());
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

        JSplitPane fieldsSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, availableListBoxPanel, selectedListBoxPanel);
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
        fieldsPanel.setOpaque(false);
        fieldsPanel.setBorder(new EmptyBorder(10,10,10,10));

        return fieldsPanel;
    }

}
