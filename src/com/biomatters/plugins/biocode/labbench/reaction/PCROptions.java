package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.TextAreaOption;
import com.biomatters.plugins.biocode.labbench.TransactionException;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 23/06/2009 12:45:32 PM
 */
public class PCROptions extends ReactionOptions<PCRReaction> {

    private ButtonOption cocktailButton;
    private Option<String, ? extends JComponent> labelOption;
    private ComboBoxOption cocktailOption;

    public static final String PRIMER_OPTION_ID = "primer";
    public static final String PRIMER_REVERSE_OPTION_ID = "revPrimer";
    static final String COCKTAIL_BUTTON_ID = "cocktailEdit";
    static final String LABEL_OPTION_ID = "label";
    static final String ADD_PRIMER_TO_LOCAL_ID = "addPrimers";
    private ButtonOption addPrimersButton;


    public PCROptions(Class c) {
        super(c);
        init();
        initListeners();
    }
    
    public PCROptions(Element e) throws XMLSerializationException {
        super(e);
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
        List<? extends Cocktail> cocktailList = Cocktail.getAllCocktailsOfType(Reaction.Type.PCR);
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
        cocktailOption = (ComboBoxOption)getOption(COCKTAIL_OPTION_ID);
        addPrimersButton = (ButtonOption)getOption(ADD_PRIMER_TO_LOCAL_ID);

        ActionListener cocktailButtonListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final CocktailEditor<PCRCocktail> editor = new CocktailEditor<PCRCocktail>();
                if(!editor.editCocktails(BiocodeService.getInstance().getPCRCocktails(), PCRCocktail.class, cocktailOption.getComponent())) {
                    return;
                }
                Runnable runnable = new Runnable() {
                    public void run() {
                        ProgressFrame progressFrame = BiocodeUtilities.getBlockingProgressFrame("Adding Cocktails", cocktailButton.getComponent());
                        try {
                            BiocodeService.getInstance().addNewCocktails(editor.getNewCocktails());
                            BiocodeService.getInstance().removeCocktails(editor.getDeletedCocktails());
                            final List<OptionValue> cocktails = getCocktails();

                            if(cocktails.size() > 0) {
                                Runnable runnable = new Runnable() {
                                    public void run() {
                                        cocktailOption.setPossibleValues(cocktails);
                                    }
                                };
                                ThreadUtilities.invokeNowOrLater(runnable);
                            }
                        } catch (final DatabaseServiceException e1) {
                            Runnable runnable = new Runnable() {
                                public void run() {
                                    Dialogs.showDialog(new Dialogs.DialogOptions(Dialogs.OK_ONLY, "Error saving cocktails", cocktailButton.getComponent()), e1.getMessage());
                                }
                            };
                            ThreadUtilities.invokeNowOrLater(runnable);
                        } finally {
                            progressFrame.setComplete();
                        }
                    }
                };
                new Thread(runnable).start();
                updateCocktailOption(cocktailOption);
            }
        };
        cocktailButton.addActionListener(cocktailButtonListener);

        SimpleListener labelListener = new SimpleListener() {
            public void objectChanged() {
                double sum = 0;
                for (Option o : getOptions()) {
//                    if (o instanceof IntegerOption) {
//                        sum += (Integer) o.getValue();
//                    }
                    if(o.getName().equals("cocktail")) {
                        Integer cocktailId = Integer.parseInt(((Options.OptionValue)o.getValue()).getName());
                        List<PCRCocktail> cocktailList = BiocodeService.getInstance().getPCRCocktails();
                        for(PCRCocktail cocktail : cocktailList) {
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

        addPrimersButton.addActionListener(new SaveMyPrimersActionListener(){
            public List<DocumentSelectionOption> getPrimerOptions() {
                return Arrays.asList((DocumentSelectionOption)getOption(PRIMER_OPTION_ID), (DocumentSelectionOption)getOption(PRIMER_REVERSE_OPTION_ID));
            }
        });
    }

    public void init() {
        //todo interface for user to pick the sample
        addStringOption("extractionId", "Extraction ID", "");
        addStringOption(WORKFLOW_ID, "Workflow ID", "");
        String[] sampleLoci = new String[] {"None", "COI", "16s", "18s", "ITS", "ITS1", "ITS2", "28S", "12S", "rbcl", "matK", "trnH-psba"};
        addEditableComboBoxOption(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), "Locus", "None", sampleLoci);
        addDateOption("date", "Date", new Date());


        addComboBoxOption(RUN_STATUS, "Reaction state", STATUS_VALUES, STATUS_VALUES[0]);

        addLabel("");
        addPrimerSelectionOption(PRIMER_OPTION_ID, "Forward Primer", DocumentSelectionOption.FolderOrDocuments.EMPTY, false, Collections.<AnnotatedPluginDocument>emptyList());
        addPrimerSelectionOption(PRIMER_REVERSE_OPTION_ID, "Reverse Primer", DocumentSelectionOption.FolderOrDocuments.EMPTY, false, Collections.<AnnotatedPluginDocument>emptyList());
        addPrimersButton = addButtonOption(ADD_PRIMER_TO_LOCAL_ID, "", "Add primers to my local database");


        List<OptionValue> cocktails = getCocktails();

        cocktailOption = addComboBoxOption(COCKTAIL_OPTION_ID, "Reaction Cocktail", cocktails, cocktails.get(0));

        updateCocktailOption(cocktailOption);

        cocktailButton = new ButtonOption(COCKTAIL_BUTTON_ID, "", "Edit Cocktails");
        cocktailButton.setSpanningComponent(true);
        addCustomOption(cocktailButton);
        Options.OptionValue[] cleanupValues = new OptionValue[] {new OptionValue("true", "Yes"), new OptionValue("false", "No")};
        ComboBoxOption cleanupOption = addComboBoxOption("cleanupPerformed", "Cleanup performed", cleanupValues, cleanupValues[1]);
        StringOption cleanupMethodOption = addStringOption("cleanupMethod", "Cleanup method", "");
        cleanupMethodOption.setDisabledValue("");
        addStringOption("technician", "Technician", "", "May be blank");
        cleanupOption.addDependent(cleanupMethodOption, cleanupValues[0]);
        TextAreaOption notes = new TextAreaOption("notes", "Notes", "");
        addCustomOption(notes);

        labelOption = new LabelOption(LABEL_OPTION_ID, "Total Volume of Reaction: 0uL");
        addCustomOption(labelOption);
    }

    private List<OptionValue> getCocktails() {
        List<OptionValue> cocktails = new ArrayList<OptionValue>();
        List<? extends Cocktail> cocktailList = Cocktail.getAllCocktailsOfType(Reaction.Type.PCR);
        for (Cocktail cocktail : cocktailList) {
            cocktails.add(new OptionValue("" + cocktail.getId(), cocktail.getName()));
        }
        if(cocktailList.size() == 0) {
            cocktails.add(new OptionValue("-1", "No available cocktails"));
        }
        return cocktails;
    }

    private void updateCocktailOption(ComboBoxOption<OptionValue> cocktailOption) {
        List<OptionValue> cocktails = new ArrayList<OptionValue>();
        List<? extends Cocktail> cocktailList = Cocktail.getAllCocktailsOfType(Reaction.Type.PCR);
        for (Cocktail cocktail : cocktailList) {
            cocktails.add(new OptionValue("" + cocktail.getId(), cocktail.getName()));
        }
        if(cocktails.size() == 0) {
            cocktails.add(new OptionValue("-1", "No available cocktails"));
        }
        cocktailOption.setPossibleValues(cocktails);
    }
    
}
