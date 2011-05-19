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
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.PluginUtilities;
import com.biomatters.geneious.publicapi.utilities.Base64Coder;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.TextAreaOption;
import com.biomatters.plugins.biocode.labbench.TransactionException;
import jebl.util.ProgressListener;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.lang.ref.WeakReference;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 24/06/2009 7:35:20 PM
 */
public class CycleSequencingOptions extends ReactionOptions {
    private ButtonOption cocktailButton;
    private Option<String, ? extends JComponent> labelOption;
    private com.biomatters.plugins.biocode.labbench.ButtonOption tracesButton;

    public static final String PRIMER_OPTION_ID = "primer";
    static final String COCKTAIL_OPTION_ID = "cocktail";
    static final String COCKTAIL_BUTTON_ID = "cocktailEdit";
    static final String LABEL_OPTION_ID = "label";
    static final String TRACES_BUTTON_ID = "traces";
    static final String ADD_PRIMER_TO_LOCAL_ID = "addPrimers";
    private ButtonOption addPrimersButton;

    private WeakReference<List<Trace>> traces;
    private List<Trace> tracesStrongReference;

    public static final String FORWARD_VALUE = "forward";
    public static final String DIRECTION = "direction";

    public CycleSequencingOptions(Class c) {
        super(c);
        init();
        initListeners();
    }

    public CycleSequencingOptions(Element e) throws XMLSerializationException {
        super(e);
        List<Element> traceElements = e.getChildren("trace");
        if(traceElements.size() > 0) {
            List<Trace> traces = new ArrayList<Trace>();
            for(Element traceElement : traceElements) {
                traces.add(XMLSerializer.classFromXML(traceElement, Trace.class));
            }
            this.traces = new WeakReference<List<Trace>>(traces);
        }
        initListeners();
    }

    public boolean fieldIsFinal(String fieldCode) {
        return "extractionId".equals(fieldCode) || "workflowId".equals(fieldCode) || "locus".equals(fieldCode);
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
        tracesButton = (com.biomatters.plugins.biocode.labbench.ButtonOption)getOption(TRACES_BUTTON_ID);
        final ComboBoxOption cocktailsOption = (ComboBoxOption)getOption(COCKTAIL_OPTION_ID);
        addPrimersButton = (ButtonOption)getOption(ADD_PRIMER_TO_LOCAL_ID);


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
                    if(traces == null || traces.get() == null) {
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
                TracesEditor editor = new TracesEditor((traces==null || traces.get() == null) ? Collections.EMPTY_LIST : traces.get(), getValueAsString("extractionId"));
                if(editor.showDialog(tracesButton.getComponent())) {
                    List<Trace> traces = editor.getTraces();
                    CycleSequencingOptions.this.traces = new WeakReference<List<Trace>>(traces);
                    tracesStrongReference = editor.getTraces();
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

        addPrimersButton.addActionListener(new SaveMyPrimersActionListener(){
            public List<DocumentSelectionOption> getPrimerOptions() {
                return Arrays.asList((DocumentSelectionOption)getOption(PRIMER_OPTION_ID));
            }
        });

        for(Option o : getOptions()) {
            if(o instanceof IntegerOption || o.getName().equals("cocktail")) {
                o.addChangeListener(labelListener);
            }
        }
        labelListener.objectChanged();
    }

    public void setChromats(List<ReactionUtilities.MemoryFile> files) throws IOException, DocumentImportException {
        if (tracesStrongReference == null) {
            tracesStrongReference = new ArrayList<Trace>();
        } else {
            tracesStrongReference.clear();
        }
        if (traces == null) {
            traces = new WeakReference<List<Trace>>(tracesStrongReference);
        }
        if(files == null) {
            return;
        }

        convertRawTracesToTraceDocuments(files);
    }

    public void addChromats(List<ReactionUtilities.MemoryFile> files) throws IOException, DocumentImportException {
        if(tracesStrongReference == null) {
            if(traces != null && traces.get() != null) {
                tracesStrongReference = traces.get();
            }
            else {
                getChromats();
            }
        }
        if(traces == null) {
            traces = new WeakReference<List<Trace>>(tracesStrongReference);
        }


        convertRawTracesToTraceDocuments(files);
    }

    private void convertRawTracesToTraceDocuments(List<ReactionUtilities.MemoryFile> files) throws IOException, DocumentImportException {
        List<AnnotatedPluginDocument> docs = new ArrayList<AnnotatedPluginDocument>();
        File tempFolder = null;
        for(ReactionUtilities.MemoryFile mFile : files) {
            tracesStrongReference.add(new Trace(mFile));
        }
    }

    private void getChromats() {
        try {
            List<Trace> chromatFiles = ((CycleSequencingReaction) reaction).getChromats();
            tracesStrongReference = new ArrayList<Trace>();
            traces = new WeakReference<List<Trace>>(tracesStrongReference);
            addTraces(chromatFiles);

        } catch (SQLException e1) {
            Dialogs.showMessageDialog("Could not get the sequences: "+e1.getMessage());
        } catch (IOException e1) {
            Dialogs.showMessageDialog("Could not write temp files to disk: "+e1.getMessage());
        } catch (DocumentImportException e1) {
            Dialogs.showMessageDialog("Could not import the sequences.  Perhaps your traces have become corrupted in the LIMS database?: "+e1.getMessage());
        }
    }

    public void init() {
        //todo interface for user to pick the sample
        addStringOption("extractionId", "Extraction ID", "");
        addStringOption("workflowId", "Workflow ID", "");
        String[] sampleLoci = new String[] {"None", "COI", "16s", "18s", "ITS", "ITS1", "ITS2", "28S", "12S", "rbcl", "matK", "trnH-psba"};
        addEditableComboBoxOption("locus", "Locus", "None", sampleLoci);
        addDateOption("date", "Date", new Date());


        OptionValue[] statusValues = new OptionValue[] { NOT_RUN_VALUE, RUN_VALUE, PASSED_VALUE, FAILED_VALUE };
        addComboBoxOption(RUN_STATUS, "Reaction state", statusValues, statusValues[0]);

        addLabel("");
        addPrimerSelectionOption(PRIMER_OPTION_ID, "Primer", DocumentSelectionOption.FolderOrDocuments.EMPTY, false, Collections.<AnnotatedPluginDocument>emptyList());//new PrimerOption(PRIMER_OPTION_ID, "Primer");

        OptionValue[] directionValues = new OptionValue[] {new OptionValue(FORWARD_VALUE, "Forward"), new OptionValue("reverse", "Reverse")};
        addComboBoxOption(DIRECTION, "Direction", directionValues, directionValues[0]);
        addPrimersButton = addButtonOption(ADD_PRIMER_TO_LOCAL_ID, "", "Add primer to my local database");



        List<OptionValue> cocktails = getCocktails();

        if(cocktails.size() > 0) {
        addComboBoxOption(COCKTAIL_OPTION_ID, "Reaction Cocktail",  cocktails, cocktails.get(0));
        }

        cocktailButton = new ButtonOption(COCKTAIL_BUTTON_ID, "", "Edit Cocktails");
        cocktailButton.setSpanningComponent(true);
        addCustomOption(cocktailButton);
        Options.OptionValue[] cleanupValues = new OptionValue[] {new OptionValue("true", "Yes"), new OptionValue("false", "No")};
        ComboBoxOption cleanupOption = addComboBoxOption("cleanupPerformed", "Cleanup performed", cleanupValues, cleanupValues[1]);
        StringOption cleanupMethodOption = addStringOption("cleanupMethod", "Cleanup method", "");
        cleanupMethodOption.setDisabledValue("");
        cleanupOption.addDependent(cleanupMethodOption, cleanupValues[0]);
        tracesButton = new com.biomatters.plugins.biocode.labbench.ButtonOption("traces", "", "Add/Edit Traces", false);
        addCustomOption(tracesButton);
        addStringOption("technician", "Technician", "", "May be blank");
        TextAreaOption notesOption = new TextAreaOption("notes", "Notes", "");
        addCustomOption(notesOption);

        labelOption = new LabelOption(LABEL_OPTION_ID, "Total Volume of Reaction: 0uL");
        addCustomOption(labelOption);
    }

    private List<OptionValue> getCocktails() {
        List<OptionValue> cocktails = new ArrayList<OptionValue>();
        List<? extends Cocktail> cycleSequencingCocktails = Cocktail.getAllCocktailsOfType(Reaction.Type.CycleSequencing);
        for (Cocktail cocktail : cycleSequencingCocktails) {
            cocktails.add(new OptionValue("" + cocktail.getId(), cocktail.getName()));
        }
        if(cocktails.size() == 0) {
            cocktails.add(new OptionValue("-1", "No available cocktails"));
        }
        return cocktails;
    }

    public List<Trace> getTraces() {
        return traces == null ? null : traces.get();
    }

    public void addTraces(List<Trace> traces) {
        if(this.traces == null || this.traces.get() == null) {
            tracesStrongReference = new ArrayList<Trace>();
            this.traces = new WeakReference<List<Trace>>(tracesStrongReference);
        }
        this.traces.get().addAll(traces);
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
        if(traces != null && traces.get() != null) {
            for(Trace trace : traces.get()) {
                element.addContent(XMLSerializer.classToXML("trace", trace));
            }
        }
        return element;
    }

    /**
     * nullify the strong reference to trace documents to free up memory!.
     */
    public void purgeChromats() {
        if(tracesStrongReference != null) {
            tracesStrongReference = null;
        }
    }
}
