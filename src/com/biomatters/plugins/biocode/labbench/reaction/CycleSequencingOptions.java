package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultSequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.ButtonOption;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.TextAreaOption;
import com.biomatters.plugins.biocode.labbench.TransactionException;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

    public CycleSequencingOptions(Class c) {
        super(c);
        init();
        initListeners();
    }

    public CycleSequencingOptions(Element e) throws XMLSerializationException {
        super(e);
        Element sequencesElement = e.getChild("sequences");
        if(sequencesElement != null) {
            sequences = XMLSerializer.classFromXML(sequencesElement, DefaultSequenceListDocument.class).getNucleotideSequences();
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
        List<Cocktail> cocktailList = new PCRCocktail().getAllCocktailsOfType();
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
                final List<? extends Cocktail> newCocktails = Cocktail.editCocktails(new CycleSequencingCocktail().getAllCocktailsOfType(), CycleSequencingCocktail.class, cocktailButton.getComponent());
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
                TracesEditor editor = new TracesEditor(sequences==null ? Collections.EMPTY_LIST : sequences, getValueAsString("extractionId"));
                if(editor.showDialog(tracesButton.getComponent())) {
                    sequences = editor.getSequences();
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

    public void init() {
        //todo interface for user to pick the sample
        addStringOption("extractionId", "Extraction ID", "");
        addStringOption("workflowId", "Workflow ID", "");


        OptionValue[] statusValues = new OptionValue[] { NOT_RUN_VALUE, PASSED_VALUE, FAILED_VALUE };
        addComboBoxOption(RUN_STATUS, "Reaction state", statusValues, statusValues[0]);

        addLabel("");
        PrimerOption primerOption = new PrimerOption(PRIMER_OPTION_ID, "Primer");
        addCustomOption(primerOption);

        OptionValue[] directionValues = new OptionValue[] {new OptionValue("forward", "Forward"), new OptionValue("reverse", "Reverse")};
        addComboBoxOption("direction", "Direction", directionValues, directionValues[0]);


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
        for (int i = 0; i < new CycleSequencingCocktail().getAllCocktailsOfType().size(); i++) {
            Cocktail cocktail = new CycleSequencingCocktail().getAllCocktailsOfType().get(i);
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

    public void setSequences(List<NucleotideSequenceDocument> sequences) {
        this.sequences = sequences;
    }

    public void addSequences(List<NucleotideSequenceDocument> sequences) {
        if(this.sequences == null) {
            this.sequences = new ArrayList<NucleotideSequenceDocument>(sequences);
        }
        else {
            this.sequences.addAll(sequences);
        }
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
        return element;
    }
}