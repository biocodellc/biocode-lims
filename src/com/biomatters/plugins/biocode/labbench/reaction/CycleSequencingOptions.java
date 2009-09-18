package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultSequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentImportException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.PluginUtilities;
import com.biomatters.geneious.publicapi.utilities.Base64Coder;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ButtonOption;
import com.biomatters.plugins.biocode.labbench.TextAreaOption;
import com.biomatters.plugins.biocode.labbench.TransactionException;
import jebl.util.ProgressListener;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 24/06/2009 7:35:20 PM
 */
public class CycleSequencingOptions extends ReactionOptions {
    private ButtonOption cocktailButton;
    private Option<String, ? extends JComponent> labelOption;
    private ButtonOption tracesButton;

    public static final String PRIMER_OPTION_ID = "primer";
    static final String COCKTAIL_OPTION_ID = "cocktail";
    static final String COCKTAIL_BUTTON_ID = "cocktailEdit";
    static final String LABEL_OPTION_ID = "label";
    static final String TRACES_BUTTON_ID = "traces";

    private List<NucleotideSequenceDocument> sequences;
    private List<ReactionUtilities.MemoryFile> rawTraces;

    public static final String FORWARD_VALUE = "forward";
    public static final String DIRECTION = "direction";

    public CycleSequencingOptions(Class c) {
        super(c);
        init();
        initListeners();
    }

    public CycleSequencingOptions(Element e) throws XMLSerializationException {
        super(e);
        Element sequencesElement = e.getChild("sequences");
        Element rawTracesElement = e.getChild("rawTraces");
        if(sequencesElement != null && rawTracesElement != null) {
            sequences = XMLSerializer.classFromXML(sequencesElement, DefaultSequenceListDocument.class).getNucleotideSequences();
            rawTraces = new ArrayList<ReactionUtilities.MemoryFile>();
            for(Element el : rawTracesElement.getChildren("trace")){
                rawTraces.add(new ReactionUtilities.MemoryFile(el.getAttributeValue("name"), Base64Coder.decode(el.getText().toCharArray())));
            }
        }
        initListeners();
    }

    public boolean fieldIsFinal(String fieldCode) {
        return "extractionId".equals(fieldCode) || "workflowId".equals(fieldCode);
    }

    public void refreshValuesFromCaches() {
        final ComboBoxOption cocktailsOption = (ComboBoxOption)getOption(COCKTAIL_OPTION_ID);
        cocktailsOption.setPossibleValues(getCocktails());
    }

    public Cocktail getCocktail() {
        List<? extends Cocktail> cocktailList = Cocktail.getAllCocktailsOfType(Reaction.Type.CycleSequencing);
        Option cocktailOption = getOption(COCKTAIL_OPTION_ID);
        OptionValue cocktailValue = (OptionValue)cocktailOption.getValue();

        int cocktailId = Integer.parseInt(cocktailValue.getName());

        for(Cocktail cocktail : cocktailList) {
            if(cocktail.getId() == cocktailId) {
                return cocktail;
            }
        }
        return null;
    }

    public void initListeners() {
        cocktailButton = (ButtonOption)getOption(COCKTAIL_BUTTON_ID);
        labelOption = (LabelOption)getOption(LABEL_OPTION_ID);
        tracesButton = (ButtonOption)getOption(TRACES_BUTTON_ID);
        final ComboBoxOption cocktailsOption = (ComboBoxOption)getOption(COCKTAIL_OPTION_ID);


        ActionListener cocktailButtonListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final List<? extends Cocktail> newCocktails = Cocktail.editCocktails(Cocktail.getAllCocktailsOfType(Reaction.Type.CycleSequencing), CycleSequencingCocktail.class, cocktailButton.getComponent());
                if (newCocktails.size() > 0) {
                    Runnable runnable = new Runnable() {
                        public void run() {
                            try {
                                BiocodeService.block("Adding Cocktails", cocktailButton.getComponent());
                                BiocodeService.getInstance().addNewCycleSequencingCocktails(newCocktails);
                                List<OptionValue> cocktails = getCocktails();

                                if(cocktails.size() > 0) {
                                    cocktailsOption.setPossibleValues(cocktails);
                                }
                            } catch (final TransactionException e1) {
                                Runnable runnable = new Runnable() {
                                    public void run() {
                                        Dialogs.showDialog(new Dialogs.DialogOptions(Dialogs.OK_ONLY, "Error saving cocktails", getPanel()), e1.getMessage());
                                    }
                                };
                                ThreadUtilities.invokeNowOrLater(runnable);
                            } finally {
                                BiocodeService.unBlock();
                            }
                        }
                    };
                    new Thread(runnable).start();
                }
            }
        };
        cocktailButton.addActionListener(cocktailButtonListener);

        tracesButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                if(reaction == null) {
                    Dialogs.showMessageDialog("These options are not linked to a reaction, so you cannot view the traces.", "Cannot view traces", tracesButton.getComponent(), Dialogs.DialogIcon.INFORMATION);
                    return;
                }
                if(reaction.getId() > 0) {
                    if(sequences == null || rawTraces == null) {
//                        if(!Dialogs.showYesNoDialog("You have not downloaded the sequences for this reaction from the database yet.  Would you like to do so now?", "Download sequences", tracesButton.getComponent(), Dialogs.DialogIcon.QUESTION)) {
//                            return;
//                        }
                        Runnable r = new Runnable() {
                            public void run() {
                                getChromats();
                            }
                        };
                        BiocodeService.block("Downloading sequences", tracesButton.getComponent(), r);

                    }
                }
                TracesEditor editor = new TracesEditor(sequences==null ? Collections.EMPTY_LIST : sequences, rawTraces==null ? Collections.EMPTY_LIST : rawTraces, getValueAsString("extractionId"));
                if(editor.showDialog(tracesButton.getComponent())) {
                    sequences = editor.getSequences();
                    rawTraces = editor.getRawTraces();
                }

            }
        });

        SimpleListener labelListener = new SimpleListener() {
            public void objectChanged() {
                int sum = 0;
                for (Option o : getOptions()) {
//                    if (o instanceof IntegerOption) {
//                        sum += (Integer) o.getValue();
//                    }
                    if(o.getName().equals("cocktail")) {
                        Integer cocktailId = Integer.parseInt(((Options.OptionValue)o.getValue()).getName());
                        List<Cocktail> cocktailList = BiocodeService.getInstance().getCycleSequencingCocktails();
                        for(Cocktail cocktail : cocktailList) {
                            if(cocktail.getId() == cocktailId) {
                                sum += cocktail.getReactionVolume(cocktail.getOptions());    
                            }
                        }

                    }
                }
                labelOption.setValue("Total Volume of Reaction: " + sum + "uL");
            }
        };

        for(Option o : getOptions()) {
            if(o instanceof IntegerOption || o.getName().equals("cocktail")) {
                o.addChangeListener(labelListener);
            }
        }
        labelListener.objectChanged();
    }

    public void addChromats(List<ReactionUtilities.MemoryFile> files) throws IOException, DocumentImportException {
        List<AnnotatedPluginDocument> docs = new ArrayList<AnnotatedPluginDocument>();
        File tempFolder = null;
        if(rawTraces == null) {
            rawTraces = new ArrayList<ReactionUtilities.MemoryFile>();
        }
        if(sequences == null) {
            sequences = new ArrayList<NucleotideSequenceDocument>();
        }

        for(ReactionUtilities.MemoryFile mFile : files) {
            if(tempFolder == null) {
                tempFolder = File.createTempFile("biocode_" + getValueAsString("extractionId"), "");
                if(tempFolder.exists()) {
                    tempFolder.delete();
                }
                if(!tempFolder.mkdir()){
                    throw new IOException("could not create the temp dir!");
                }
            }

            //write the data to a temp file (because Geneious file importers can't read an in-memory stream
            File abiFile = new File(tempFolder, mFile.getName());
            FileOutputStream out = new FileOutputStream(abiFile);
            out.write(mFile.getData());
            out.close();

            //import the file
            List<AnnotatedPluginDocument> pluginDocuments = PluginUtilities.importDocuments(abiFile, ProgressListener.EMPTY);
            docs.addAll(pluginDocuments);
            rawTraces.add(mFile);
            if(!abiFile.delete()){
                abiFile.deleteOnExit();
            }           
        }
        if(tempFolder != null && !tempFolder.delete()){
            tempFolder.deleteOnExit();
        }

        for(AnnotatedPluginDocument adoc : docs) {
            PluginDocument doc = adoc.getDocumentOrNull();
            if(doc == null) {
                //todo: handle
            }
            if(!(doc instanceof NucleotideSequenceDocument)) {
                //todo: handle
            }
            sequences.add((NucleotideSequenceDocument)doc);
        }
    }

    private void getChromats() {
        try {
            List<ReactionUtilities.MemoryFile> chromatFiles = ((CycleSequencingReaction) reaction).getChromats();
            rawTraces = new ArrayList<ReactionUtilities.MemoryFile>();
            sequences = new ArrayList<NucleotideSequenceDocument>();
            addChromats(chromatFiles);

        } catch (SQLException e1) {
            Dialogs.showMessageDialog("Could not get the sequences: "+e1.getMessage());
        } catch (IOException e1) {
            Dialogs.showMessageDialog("Could not import the sequences: "+e1.getMessage());
        } catch (DocumentImportException e1) {
            Dialogs.showMessageDialog("Could not import the sequences: "+e1.getMessage());
        }
    }

    public void init() {
        //todo interface for user to pick the sample
        addStringOption("extractionId", "Extraction ID", "");
        addStringOption("workflowId", "Workflow ID", "");


        OptionValue[] statusValues = new OptionValue[] { NOT_RUN_VALUE, PASSED_VALUE, FAILED_VALUE };
        addComboBoxOption(RUN_STATUS, "Reaction state", statusValues, statusValues[0]);

        addLabel("");
        PrimerOption primerOption = new PrimerOption(PRIMER_OPTION_ID, "Primer");
        addCustomOption(primerOption);

        OptionValue[] directionValues = new OptionValue[] {new OptionValue(FORWARD_VALUE, "Forward"), new OptionValue("reverse", "Reverse")};
        addComboBoxOption(DIRECTION, "Direction", directionValues, directionValues[0]);


//        IntegerOption primerAmountOption = addIntegerOption("prAmount", "Primer Amount", 1, 0, Integer.MAX_VALUE);
//        primerAmountOption.setUnits("uL");


        List<OptionValue> cocktails = getCocktails();

        if(cocktails.size() > 0) {
        addComboBoxOption(COCKTAIL_OPTION_ID, "Reaction Cocktail",  cocktails, cocktails.get(0));
        }

        cocktailButton = new ButtonOption(COCKTAIL_BUTTON_ID, "", "Edit Cocktails");
        cocktailButton.setSpanningComponent(true);
        addCustomOption(cocktailButton);
        BooleanOption cleanupOption = addBooleanOption("cleanupPerformed", "Cleanup performed", false);
        StringOption cleanupMethodOption = addStringOption("cleanupMethod", "Cleanup method", "");
        cleanupMethodOption.setDisabledValue("");
        cleanupOption.addDependent(cleanupMethodOption, true);
        tracesButton = new ButtonOption("traces", "", "Add/Edit Traces", false);
        addCustomOption(tracesButton);
        TextAreaOption notesOption = new TextAreaOption("notes", "Notes", "");
        addCustomOption(notesOption);

        labelOption = new LabelOption(LABEL_OPTION_ID, "Total Volume of Reaction: 0uL");
        addCustomOption(labelOption);
    }

    private List<OptionValue> getCocktails() {
        List<OptionValue> cocktails = new ArrayList<OptionValue>();
        List<? extends Cocktail> cycleSequencingCocktails = Cocktail.getAllCocktailsOfType(Reaction.Type.CycleSequencing);
        for (int i = 0; i < cycleSequencingCocktails.size(); i++) {
            Cocktail cocktail = cycleSequencingCocktails.get(i);
            cocktails.add(new OptionValue(""+cocktail.getId(), cocktail.getName()));
        }
        if(cocktails.size() == 0) {
            cocktails.add(new OptionValue("-1", "No available cocktails"));
        }
        return cocktails;
    }

    public List<NucleotideSequenceDocument> getSequences() {
        return sequences;
    }

    public List<ReactionUtilities.MemoryFile> getRawTraces() {
        return rawTraces;
    }

    public void addSequences(List<NucleotideSequenceDocument> sequences) {
        if(this.sequences == null) {
            this.sequences = new ArrayList<NucleotideSequenceDocument>(sequences);
        }
        else {
            this.sequences.addAll(sequences);
        }
    }

    public void addRawTraces(List<ReactionUtilities.MemoryFile> files) {
        if(this.rawTraces == null) {
            this.rawTraces = new ArrayList<ReactionUtilities.MemoryFile>();
        }
        this.rawTraces.addAll(files);
    }

    private List<Options.OptionValue> getOptionValues(List<AnnotatedPluginDocument> documents) {
        ArrayList<Options.OptionValue> primerList = new ArrayList<Options.OptionValue>();
        for(AnnotatedPluginDocument doc : documents) {
            OligoSequenceDocument seq = (OligoSequenceDocument)doc.getDocumentOrCrash();
            primerList.add(new OptionValue(doc.getName(), doc.getName(), seq.getSequenceString()));
        }
        return primerList;
    }


    @Override
    public Element toXML() {
        Element element = super.toXML();
        if(sequences != null) {
            DefaultSequenceListDocument list = DefaultSequenceListDocument.forNucleotideSequences(sequences);
            element.addContent(XMLSerializer.classToXML("sequences", list));
        }
        if(rawTraces != null) {
            Element tracesElement = new Element("rawTraces");
            for(ReactionUtilities.MemoryFile file : rawTraces) {
                Element traceElement = new Element("trace");
                traceElement.setAttribute("name", file.getName());
                traceElement.setText(new String(Base64Coder.encode(file.getData())));
                tracesElement.addContent(traceElement);
            }
            element.addContent(tracesElement);
        }
        return element;
    }
}
