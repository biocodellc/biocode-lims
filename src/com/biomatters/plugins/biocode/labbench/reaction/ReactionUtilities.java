package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.*;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceListDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ButtonOption;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.options.NamePartOption;
import com.biomatters.plugins.biocode.options.NameSeparatorOption;
import jebl.util.ProgressListener;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

    public static final DefaultListCellRenderer DOCUMENT_FIELD_CELL_RENDERER = new DefaultListCellRenderer() {
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


    /**
     * Shows a dialog and tries to get chromatograms for each reaction by parsing the well name out of the abi file names
     * @param plate the cycle sequencing plate to modify
     * @param owner
     * @return true if the user clicked OK on the dialog
     */
    public static String bulkLoadChromatograms(final Plate plate, final JComponent owner) {
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
        chooseOption.addDependent(chooseValues[1], fieldOption, true);

        options.beginAlignHorizontally(null, false);
        Options.Option label = options.addLabel("Match:");
        label.setDescription("Separate sequences in to groups according to their names and assemble each group individually");
        NamePartOption namePartOption2 = new NamePartOption("namePart2", "");
        options.addCustomOption(namePartOption2);
        namePartOption2.setDescription("Each name is split into segments by the given separator, then the n-th segment is used to identify the sequence's well");
        options.addLabel("part of name,");
        options.addLabel(" seperated by");
        NameSeparatorOption nameSeperatorOption = new NameSeparatorOption("nameSeparator", "");
        options.addCustomOption(nameSeperatorOption);
        options.endAlignHorizontally();

        options.beginAlignHorizontally(null, false);
        Options.BooleanOption checkPlateName = options.addBooleanOption("checkPlateName", "Check plate name is correct.", false);
        Options.Option<String, ? extends JComponent> label2 = options.addLabel("Plate name is");
        checkPlateName.setDescription("Separate sequences in to groups according to their names and assemble each group individually");
        NamePartOption namePartOption = new NamePartOption("namePart", "");
        options.addCustomOption(namePartOption);
        namePartOption.setDescription("Each name is split into segments by the given separator, then the n-th segment is used to identify the sequence's plate");
        Options.Option<String, ? extends JComponent> label3 = options.addLabel("part of name");
        checkPlateName.addDependent(namePartOption,  true);
        checkPlateName.addDependent(label2, true);
        checkPlateName.addDependent(label3, true);
        options.endAlignHorizontally();

        options.addLabel(" ");

        final Options.BooleanOption plateBackwards = options.addBooleanOption("plateBackwards", "Whoops! I sequenced my plate backwards.  Please fix it!", false);
        final Options.BooleanOption fixNames = options.addBooleanOption("fixNames", "Also try to correct the well number in the trace filenames", false);
        fixNames.setDisabledValue(false);
        plateBackwards.addDependent(fixNames, true);


        if(!Dialogs.showOptionsDialog(options, "Bulk add traces", true, owner)){
            return null;
        }

        final int platePart = namePartOption.getPart();
        final int wellPart = namePartOption2.getPart();
        final boolean checkPlate = checkPlateName.getValue();
        DocumentField field = null;
        if(optionValuesAreEqual(chooseValues[1], chooseOption.getValue())) {
            field = getDocumentField(reactions.get(0).getAllDisplayableFields(), fieldOption.getValue().getName());
            assert field != null; //this shouldn't happen unless the list changes between when the options were displayed and when the user clicks ok.
            //noinspection ConstantConditions
            if(field == null) {
                return "Could not find the field "+fieldOption.getValue().getName()+" on your reactions.  Please try again with another field.";
            }
        }

        final File folder = new File(selectionOption.getValue());
        if(!folder.exists()) {
            return "The folder "+folder.getAbsolutePath()+" does not exist!";
        }
        if(!folder.isDirectory()) {
            throw new IllegalStateException(folder.getAbsolutePath()+" is not a folder!");  //leave as a crash because we have specified directories only in the file selection option
        }

        final String separatorString = nameSeperatorOption.getSeparatorString();
        final DocumentField finalField = field;
        Runnable runnable = new Runnable() {
            public void run() {
                final int count = importAndAddTraces(reactions, separatorString, platePart, wellPart, finalField, checkPlate, folder, plate.getPlateSize(), plateBackwards.getValue(), fixNames.getValue());
                Runnable runnable = new Runnable() {
                    public void run() {
                        ThreadUtilities.sleep(100);
                        Dialogs.showMessageDialog("Imported "+count+" traces", "Imported traces", owner, Dialogs.DialogIcon.INFORMATION);
                    }
                };
                ThreadUtilities.invokeNowOrLater(runnable);
            }
        };
        BiocodeService.block("Importing traces", owner, runnable);
        return null;
    }

    private static DocumentField getDocumentField(List<DocumentField> fields, String code) {
        for(DocumentField field : fields) {
            if(field.getCode().equals(code)) {
                return field;
            }
        }
        return null;
    }

    private static CycleSequencingReaction getReaction(List<CycleSequencingReaction> reactions, DocumentField field, BiocodeUtilities.Well value) {
        for(CycleSequencingReaction r : reactions) {
            if(value.toPaddedString().equals(""+r.getDisplayableValue(field)) || value.toString().equals(""+r.getDisplayableValue(field))) {
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
     * @param fieldToCheck
     * @param checkPlate
     * @param folder
     * @param plateSize
     * @param flipPlate
     * @param checkNames
     */
    private static int importAndAddTraces(List<CycleSequencingReaction> reactions, String separatorString, int platePart, int partToMatch, DocumentField fieldToCheck, boolean checkPlate, File folder, Plate.Size plateSize, boolean flipPlate, boolean checkNames) {
        try {
            BiocodeUtilities.downloadTracesForReactions(reactions, ProgressListener.EMPTY);
        } catch (SQLException e) {
            e.printStackTrace();
            Dialogs.showMessageDialog("Error reading existing sequences from database: "+e.getMessage());
            return -1;
        } catch (IOException e) {
            e.printStackTrace();
            Dialogs.showMessageDialog("Error writing temporary sequences to disk: "+e.getMessage());
            return -1;
        } catch (DocumentImportException e) {
            e.printStackTrace();
            Dialogs.showMessageDialog("Error importing existing sequences: "+e.getMessage());
            return -1;
        }

        int count = 0;

        for(File f : folder.listFiles()) {
            if(f.isHidden()) {
                continue;
            }
            if(f.getName().startsWith(".")) { //stupid macos files
                continue;
            }
            BiocodeUtilities.Well originalWell = null;
            BiocodeUtilities.Well newWell = null;
            if(f.getName().toLowerCase().endsWith(".ab1")) { //let's do some actual work...
                String[] nameParts = f.getName().split(separatorString);
                CycleSequencingReaction r = null;
                if(fieldToCheck != null && nameParts.length > partToMatch) {
                    String fieldValue = nameParts[partToMatch];

                    for(CycleSequencingReaction reaction : reactions) {
                        if(fieldValue.toString().equals(""+reaction.getDisplayableValue(fieldToCheck))) {
                            r = reaction;
                            newWell = new BiocodeUtilities.Well(r.getLocationString());
                            break;
                        }
                    }
                }
                else {
                    originalWell = BiocodeUtilities.getWellFromFileName(f.getName(), separatorString, partToMatch);
                    if (originalWell == null) continue;
                    int location = flipPlate ? plateSize.numberOfReactions()-Plate.getWellLocation(originalWell, plateSize)-1 : Plate.getWellLocation(originalWell, plateSize);
                    newWell = Plate.getWell(location, plateSize);

                    String wellString = newWell.toString();
                    r = getReaction(reactions, wellString);
                }
                if(r == null) {
                    continue;
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
                List<AnnotatedPluginDocument> annotatedDocuments;
                try {
                    annotatedDocuments = importDocuments(new File[]{f}, ProgressListener.EMPTY);
                } catch (IOException e) {
                    Dialogs.showMessageDialog("Error reading sequences: "+e.getMessage());
                    break;
                } catch (DocumentImportException e) {
                    Dialogs.showMessageDialog("Error importing sequences: "+e.getMessage());
                    break;
                }

                List<Trace> traces = new ArrayList<Trace>();
                try {
                    MemoryFile memoryFile = loadFileIntoMemory(f);
                    if(checkNames && originalWell != null && newWell != null) {
                        String name = memoryFile.getName().replace(originalWell.toPaddedString(), newWell.toPaddedString()); //prioritise padded names (eg A01) because that's what most sequencers produce
                        name = name.replace(originalWell.toString(), newWell.toString());
                        memoryFile.setName(name);
                    }
                    traces.add(new Trace(memoryFile));
                } catch (IOException e) {
                    assert false : e.getMessage();
                    //todo: handle
                    e.printStackTrace();
                }
                catch (DocumentImportException e) {
                    assert false : e.getMessage();
                    //todo: handle
                    e.printStackTrace();
                }

                count += traces.size();
                r.addSequences(traces);

            }
        }
        return count;
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

        FileInputStream in = new FileInputStream(f);
        int offset=0;
        int numRead;
        byte[] result = new byte[(int)f.length()];
        while (offset < result.length
               && (numRead=in.read(result, offset, result.length-offset)) >= 0) {
            offset += numRead;
        }

        if (offset < result.length) {
            throw new IOException("Could not completely read file "+f.getName());
        }


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


    public static void saveAbiFileFromPlate(Plate plate, JComponent owner) {
        Options options = new Options(ReactionUtilities.class);
        options.addStringOption("owner", "Owner", "");
        options.addStringOption("operator", "Operator", "");
        options.addStringOption("plateSealing", "Plate Sealing", "Septa");
        options.addStringOption("resultsGroup", "Results Group", "");
        options.addStringOption("instrumentProtocol", "Instrument Protocol", "LongSeq50");
        options.addStringOption("analysisProtocol", "Analysis Protocol", "Standard_3.1");

        if(!Dialogs.showOptionsDialog(options, "Export API config file", true, owner)) {
            return;
        }

        if(plate.getReactionType() == Reaction.Type.Extraction) {
            Dialogs.showMessageDialog("You cannot create ABI input files from extraction plates", "Error creating ABI input", owner, Dialogs.DialogIcon.WARNING);
            return;
        }

        Preferences prefs = Preferences.userNodeForPackage(ReactionUtilities.class);

        JFileChooser chooser = new JFileChooser(prefs.get("abiFileLocation", System.getProperty("user.home")));

        if(chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File outFile = chooser.getSelectedFile();

        PrintWriter out = null;
        try {
            out = new PrintWriter(outFile);
            out.println("Container Name\tPlate ID\tDescription\tContainerType\tAppType\tOwner\tOperator\tPlateSealing\tSchedulingPref");
            out.println(replaceChars(plate.getName())+"\t"+replaceChars(plate.getName())+"\t\t"+plate.getReactions().length+"-Well\tRegular\t"+replaceChars(options.getValue("owner"))+"\t"+replaceChars(options.getValue("operator"))+"\t"+replaceChars(options.getValue("plateSealing"))+"\t1234");
            out.println("AppServer\tAppInstance");
            out.println("SequencingAnalysis");
            out.println("Well\tSample Name\tComment\tResults Group 1\tInstrument Protocol 1\tAnalysis Protocol 1");
            for(Reaction r : plate.getReactions()) {
                out.println(Plate.getWell(r.getPosition(), plate.getPlateSize()).toPaddedString()+"\t"+replaceChars(r.getExtractionId())+"\t\t"+replaceChars(options.getValue("resultsGroup"))+"\t"+replaceChars(options.getValue("instrumentProtocol"))+"\t"+replaceChars(options.getValue("analysisProtocol")));
            }
            
        } catch (IOException e) {
            e.printStackTrace();  //todo
        } finally {
            if(out != null) {
                out.close();
            }
        }

    }

    private static String replaceChars(Object o) {
        String s = o.toString();
        char[] charsToReplace = "\\/:*\"<>|?' ".toCharArray();
        for(char c : charsToReplace) {
            s = s.replace(c, '.');
        }
        return s;
    }

    public static void showDisplayDialog(Plate plate, JComponent owner) {

        List<Reaction> reactions = Arrays.asList(plate.getReactions());

        JPanel fieldsPanel = new JPanel(new BorderLayout());

        Vector<DocumentField> selectedFieldsVector = new Vector<DocumentField>();
        Vector<DocumentField> availableFieldsVector = new Vector<DocumentField>();
        for(Reaction r : reactions) {//todo: may be slow
            List<DocumentField> displayableFields = r.getFieldsToDisplay();
            if(displayableFields == null || displayableFields.size() == 0) {
                displayableFields = Reaction.getDefaultDisplayedFields();
            }
            for(DocumentField df : displayableFields) {
                if(!selectedFieldsVector.contains(df)) {
                    selectedFieldsVector.add(df);
                }
            }
            List<DocumentField> availableFields = r.getAllDisplayableFields();
            for(DocumentField df : availableFields) {
                if(!availableFieldsVector.contains(df)) {
                    availableFieldsVector.add(df);
                }
            }
        }

        //make sure that all the selected fields exist in the available fields...
        List<DocumentField> invalidSelectedFields = new ArrayList<DocumentField>();
        aroundtheoutterloop:
        for(DocumentField field : selectedFieldsVector) {
            for(DocumentField field2 : availableFieldsVector) {
                if(field2.getCode().equals(field.getCode())) {
                    continue aroundtheoutterloop;
                }
            }
            invalidSelectedFields.add(field);
        }
        selectedFieldsVector.removeAll(invalidSelectedFields);

        int [] selectedIndicies = new int[selectedFieldsVector.size()];
        for(int i=0; i < selectedFieldsVector.size(); i++) {
            for(int j=0; j < availableFieldsVector.size(); j++) {
                if(availableFieldsVector.get(j).getCode().equals(selectedFieldsVector.get(i).getCode())) {
                    selectedIndicies[i] = j;
                }
            }
        }

        final SplitPaneListSelector<DocumentField> listSelector = new SplitPaneListSelector<DocumentField>(availableFieldsVector, selectedIndicies, DOCUMENT_FIELD_CELL_RENDERER);


        fieldsPanel.add(listSelector, BorderLayout.CENTER);
        fieldsPanel.setOpaque(false);
        fieldsPanel.setBorder(new EmptyBorder(10,10,10,10));


        final ColoringPanel colorPanel = getColoringPanel(availableFieldsVector, reactions);


        final TemplateSelector templateSelectorPanel = new TemplateSelector(listSelector, colorPanel, reactions.get(0).getType());
        final ChangeListener templateChangeListener = new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                DisplayFieldsTemplate selectedTemplate = (DisplayFieldsTemplate) e.getSource();
                if (selectedTemplate != null) {
                    listSelector.setSelectedFields(selectedTemplate.getDisplayedFields());
                    colorPanel.setColorer(selectedTemplate.getColorer());
                }
            }
        };
        templateSelectorPanel.addChangeListener(templateChangeListener);
        //templateChangeListener.stateChanged(new ChangeEvent(BiocodeService.getInstance().getDefaultDisplayedFieldsTemplate(reactions.get(0).getType())));

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setOpaque(false);
        mainPanel.add(templateSelectorPanel, BorderLayout.NORTH);
        mainPanel.add(fieldsPanel, BorderLayout.CENTER);
        mainPanel.add(colorPanel, BorderLayout.SOUTH);
        mainPanel.setPreferredSize(new Dimension(500, 500));

        Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[] {"OK", "Cancel"}, "Display", owner, Dialogs.DialogIcon.NO_ICON);
        dialogOptions.setMaxWidth(800);
        dialogOptions.setMaxHeight(800);
        Object choice = Dialogs.showDialog(dialogOptions, mainPanel);
        if(choice.equals("OK")) {
            for(Reaction r : reactions) {
                r.setFieldsToDisplay(new ArrayList<DocumentField>(listSelector.getSelectedFields()));
                r.setBackgroundColorer(colorPanel.getColorer());
            }
        }
    }

    public static boolean documentFieldsAreEqual(DocumentField a, DocumentField b) {
        return a != null && b != null && a.getCode().equals(b.getCode());
    }

    public static int documentFieldIndexOf(List<DocumentField> fields, DocumentField value) {
        for(int i=0; i < fields.size(); i++) {
            if(documentFieldsAreEqual(fields.get(i), value)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean editReactions(List<Reaction> reactions, JComponent owner, boolean creating) {
        if(reactions == null || reactions.size() == 0) {
            throw new IllegalArgumentException("reactions must be non-null and non-empty");
        }

        ReactionOptions options;
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
                Object optionValue = option.getValue();
                Object reactionValue = reaction.getOptions().getValue(option.getName());
                if(!optionValuesAreEqual(optionValue, reactionValue)) {
                    haveAllSameValues.put(option.getName(), false);
                }
            }
        }

        OptionsPanel displayPanel = getReactionPanel(options, haveAllSameValues, reactions.size() > 1, creating);

        Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[] {"OK", "Cancel"}, "Edit Wells", owner, Dialogs.DialogIcon.NO_ICON);
        dialogOptions.setMaxWidth(800);
        dialogOptions.setMaxHeight(800);
        Object choice = Dialogs.showDialog(dialogOptions, displayPanel);
        boolean hasChanges = false;
        if(choice.equals("OK")) {
            int changedOptionCount = 0;
            hasChanges = true; //todo: check if options actually have changed...
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
                            Options.Option reactionOption = reaction.getOptions().getOption(option.getName());
                            if(reactionOption != null) {
                                reactionOption.setValue(option.getValue());
                                changedOptionCount++;
                            }
                            else {
                                assert false : option.getName()+" didn't exist in the destination options!";
                            }
                        }
                    }
                }
            }
            if(changedOptionCount > 0) {
                String error = reactions.get(0).areReactionsValid(reactions, owner, true);
                if(error != null) {
                    Dialogs.showMessageDialog(error, "Invalid Reactions", owner, Dialogs.DialogIcon.INFORMATION);
                }
            }
            return true;
        }
        return false;
    }

    private static boolean optionValuesAreEqual(Object optionValue, Object reactionValue) {
        if(reactionValue instanceof List && optionValue instanceof List) {
            List list1 = (List)reactionValue;
            List list2 = (List)optionValue;
            if(list1.size() != list2.size()) {
                return false;
            }
            for(int i=0; i < list1.size(); i++) {
                if(!list1.get(i).equals(list2.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return reactionValue.equals(optionValue);
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

            if(!(option instanceof Options.LabelOption) && !(option instanceof ButtonOption || option instanceof Options.ButtonOption)) {
                if(!creating && options.fieldIsFinal(option.getName())) {
                    option.setEnabled(false);
                    if(!haveAllSameValues.get(option.getName())) {//just don't display the option if we're editing multiple reactions and they don't all have the same value...
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
                    checkbox.setAlignmentX(JCheckBox.RIGHT_ALIGNMENT);
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

    private static DisplayFieldsTemplate getTemplate(Vector<DocumentField> fields, List<DisplayFieldsTemplate> templates) {
        List<DocumentField> fieldsList = new ArrayList<DocumentField>(fields);
        for(DisplayFieldsTemplate template : templates) {
            if(template.fieldsMatch(fieldsList)) {
                return template;
            }
        }
        return null;
    }


    /**
     * if an existing template matches the one we're trying to create, that one is returned instead unless createNewEvenIfItMatchesExisting is true
     * @param listSelector
     * @param colorSelector
     * @param type
     * @param instruction
     * @param createNewEvenIfItMatchesExisting
     * @return
     */
    private static DisplayFieldsTemplate createNewTemplate(SplitPaneListSelector<DocumentField> listSelector, ColoringPanel colorSelector, Reaction.Type type, String instruction, boolean createNewEvenIfItMatchesExisting) {
        DisplayFieldsTemplate newTemplate = null;
        BiocodeService.getInstance().updateDisplayFieldsTemplates();
        final Reaction.BackgroundColorer newColorer = colorSelector.getColorer();     
        for(DisplayFieldsTemplate template : BiocodeService.getInstance().getDisplayedFieldTemplates(type)) {
            if(template.fieldsMatch(listSelector.getSelectedFields()) && template.colourerMatches(newColorer)) {
                BiocodeService.getInstance().setDefaultDisplayedFieldsTemplate(template);
                if(!createNewEvenIfItMatchesExisting) {
                    return template;
                }
            }
        }
        JTextField inputTextfield = new JTextField(25);
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setOpaque(false);
        panel.add(new JLabel("Template name: "));
        panel.add(inputTextfield);
        String message = instruction;
        aroundTheOutterLoop:
        while(Dialogs.showInputDialog(message, "Save template", listSelector, panel)){
            BiocodeService.getInstance().updateDisplayFieldsTemplates();
            for(DisplayFieldsTemplate template : BiocodeService.getInstance().getDisplayedFieldTemplates(type)) {
                if(template.getName().equals(inputTextfield.getText())) {
                    message = instruction+"<br><br><font style=\"color:red;\">A template with the name '"+inputTextfield.getText()+"' already exists.";

                    continue aroundTheOutterLoop;
                }
            }
            newTemplate = new DisplayFieldsTemplate(inputTextfield.getText(), type, listSelector.getSelectedFields(), colorSelector.getColorer());
            BiocodeService.getInstance().saveDisplayedFieldTemplate(newTemplate);
            break;
        }
        return newTemplate;
    }


    public static Collection getAllValues(DocumentField field, List<Reaction> reactions) {
        Set allValues = new HashSet();
        if(field != null) {
            if(field.isEnumeratedField()) {
                allValues.addAll(Arrays.asList(field.getEnumerationValues()));
            }
            else {
                for(Reaction r : reactions) {
                    Object value = r.getFieldValue(field.getCode());
                    if(value != null) {
                        allValues.add(value);
                    }
                }
            }
        }
        if(allValues.size() == 0) {
            allValues.add("");
        }

        return allValues;
    }

    private static ColoringPanel getColoringPanel(final Vector<DocumentField> availableFieldsVector, List<Reaction> reactions) {
        return new ColoringPanel(availableFieldsVector, reactions);
    }

    public static void copyReaction(Reaction srcReaction, Reaction destReaction) {
        destReaction.setExtractionId(srcReaction.getExtractionId());
        Object locus = srcReaction.getFieldValue("locus");
        if(srcReaction.getType() != Reaction.Type.Extraction && locus != null) {
            destReaction.getOptions().setValue("locus", locus);
        }
        destReaction.setFimsSample(srcReaction.getFimsSample());
        destReaction.setWorkflow(srcReaction.getWorkflow());
        if(destReaction.getType() == Reaction.Type.Extraction) {
            FimsSample fimsSample = srcReaction.getFimsSample();
            if(fimsSample != null) {
                ((ExtractionReaction) destReaction).setTissueId(fimsSample.getId());
            }
        }
        if(destReaction.getType() == srcReaction.getType()) { //copy everything
            ReactionOptions op;
            try {
                //clone it...
                op = XMLSerializer.classFromXML(XMLSerializer.classToXML("Options", srcReaction.getOptions()), ReactionOptions.class);
                destReaction.setOptions(op);
                if(srcReaction.getType() == Reaction.Type.Extraction) { //hack for extractions...
                    ReactionOptions destOptions = destReaction.getOptions();
                    destOptions.setValue("parentExtraction", destOptions.getValue("extractionId"));
                    destOptions.setValue("extractionId", "");
                    destOptions.setValue("previousPlate", srcReaction.getPlateName());
                    destOptions.setValue("previousWell", srcReaction.getLocationString());
                }
            } catch (XMLSerializationException e) {
                e.printStackTrace();
                assert false : e.getMessage(); //this shouldn't really happen since we're not actually writing anything out...
            }

        }
    }

    public static String getNewExtractionId(Set<String> extractionIds, Object tissueId) {
        int i = 1;
        while(extractionIds.contains(tissueId+"."+i)) {
            i++;
        }
        return tissueId + "." + i;
    }

    public static List<NucleotideSequenceDocument> getAllSequences(List<Trace> traces) {
        if(traces == null) {
            return Collections.emptyList();
        }
        List<NucleotideSequenceDocument> sequences = new ArrayList<NucleotideSequenceDocument>();
        for(Trace trace : traces) {
            if(trace.getSequences() != null) {
                sequences.addAll(trace.getSequences());
            }
        }
        return sequences;
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

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class DocumentFieldWrapper implements GComboBox.DescriptionProvider {
        private DocumentField documentField;

        DocumentFieldWrapper(DocumentField documentField) {
//            if(documentField == null) {
//                throw new IllegalArgumentException("You cannot wrap a null document field");
//            }
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

    private static class TemplateSelector extends JPanel {

        public TemplateSelector(final SplitPaneListSelector listSelector, final ColoringPanel colorer, final Reaction.Type type) {
            changeListeners = new ArrayList<ChangeListener>();
            final List<DisplayFieldsTemplate> templateList = BiocodeService.getInstance().getDisplayedFieldTemplates(type);
            setOpaque(false);
            final GeneiousAction.SubMenu templateSelectorDropdown = new GeneiousAction.SubMenu(new GeneiousActionOptions("Select a template"), Collections.EMPTY_LIST);
            add(new GLabel("Template: "));
            final JButton button = new JButton(templateSelectorDropdown);
            button.setIcon(IconUtilities.getIcons("dropdownArrow.png").getOriginalIcon());
            button.setHorizontalTextPosition(AbstractButton.LEFT);
            add(button);
            GButton setDefaultTemplateButton = new GButton("Save as default");
            add(setDefaultTemplateButton);


            final Runnable newTemplateRunnable = new Runnable() {
                public void run() {
                    createNewTemplate(listSelector, colorer, type, "Please enter a name for your template", true);
                    List<GeneiousAction> templateActions = getTemplateActions(BiocodeService.getInstance().getDisplayedFieldTemplates(type), listSelector, this, type);
                    templateSelectorDropdown.setSubMenuActions(templateActions);
                }
            };

            setDefaultTemplateButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    DisplayFieldsTemplate newTemplate = createNewTemplate(listSelector, colorer, type, "You must save your settings as a template before making them the defaults", false);
                    if (newTemplate != null) {
                        BiocodeService.getInstance().setDefaultDisplayedFieldsTemplate(newTemplate);
                        templateSelectorDropdown.setSubMenuActions(getTemplateActions(BiocodeService.getInstance().getDisplayedFieldTemplates(type), listSelector, newTemplateRunnable, type));
                    }
                }
            });

            List<GeneiousAction> templateActions = getTemplateActions(templateList, listSelector, newTemplateRunnable, type);
            templateSelectorDropdown.setSubMenuActions(templateActions);
        }

        private List<GeneiousAction> getTemplateActions(final List<DisplayFieldsTemplate> templateList, final SplitPaneListSelector<DocumentField> listSelector, final Runnable newTemplateRunnable, final Reaction.Type type) {
            List<GeneiousAction> templateActions = new ArrayList<GeneiousAction>();
            DisplayFieldsTemplate defaultTemplate = BiocodeService.getInstance().getDefaultDisplayedFieldsTemplate(type);
            for(final DisplayFieldsTemplate template : templateList) {
                String name = (defaultTemplate != null && template.getName().equals(defaultTemplate.getName())) ? "<html><b>"+template.getName()+"</b></html>" : template.getName();
                templateActions.add(new GeneiousAction(new GeneiousActionOptions(name)){
                    public void actionPerformed(ActionEvent e) {
                        for(ChangeListener listener : changeListeners) {
                            listener.stateChanged(new ChangeEvent(template));
                        }
                    }
                });
            }
            templateActions.add(new GeneiousAction.Divider());
            templateActions.add(new GeneiousAction("Create template..."){
                public void actionPerformed(ActionEvent e) {
                    newTemplateRunnable.run();
                }
            });
            return templateActions;
        }

        public void addChangeListener(ChangeListener l) {
            changeListeners.add(l);
        }

        public void removeChangeListener(ChangeListener l) {
            changeListeners.remove(l);
        }

        private List<ChangeListener> changeListeners;

    }


}
