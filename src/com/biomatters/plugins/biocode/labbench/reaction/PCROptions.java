package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
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
import java.util.List;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 23/06/2009 12:45:32 PM
 */
public class PCROptions extends ReactionOptions {

    private ButtonOption cocktailButton;
    private Option<String, ? extends JComponent> labelOption;
    private ComboBoxOption cocktailOption;

    public static final String PRIMER_OPTION_ID = "primer";
    public static final String PRIMER_REVERSE_OPTION_ID = "revPrimer";
    static final String COCKTAIL_BUTTON_ID = "cocktailEdit";
    static final String LABEL_OPTION_ID = "label";
    static final String COCKTAIL_OPTION_ID = "cocktail";


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
        return "extractionId".equals(fieldCode) || "workflowId".equals(fieldCode);
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

        ActionListener cocktailButtonListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final List<? extends Cocktail> newCocktails = Cocktail.editCocktails(Cocktail.getAllCocktailsOfType(Reaction.Type.PCR), PCRCocktail.class, cocktailOption.getComponent());
                if (newCocktails.size() > 0) {
                    Runnable runnable = new Runnable() {
                        public void run() {
                            try {
                                BiocodeService.block("Adding Cocktails", cocktailButton.getComponent());
                                BiocodeService.getInstance().addNewPCRCocktails(newCocktails);
                                List<OptionValue> cocktails = getCocktails();

                                if(cocktails.size() > 0) {
                                    cocktailOption.setPossibleValues(cocktails);
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
                updateCocktailOption(cocktailOption);
            }
        };
        cocktailButton.addActionListener(cocktailButtonListener);

        SimpleListener labelListener = new SimpleListener() {
            public void objectChanged() {
                int sum = 0;
                for (Option o : getOptions()) {
//                    if (o instanceof IntegerOption) {
//                        sum += (Integer) o.getValue();
//                    }
                    if(o.getName().equals("cocktail")) {
                        Integer cocktailId = Integer.parseInt(((Options.OptionValue)o.getValue()).getName());
                        List<Cocktail> cocktailList = BiocodeService.getInstance().getPCRCocktails();
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
        PrimerOption primerOption = new PrimerOption(PRIMER_OPTION_ID, "Forward Primer");
        addCustomOption(primerOption);
        //IntegerOption primerAmountOption = addIntegerOption("prAmount", "Primer Amount", 1, 0, Integer.MAX_VALUE);
        //primerAmountOption.setUnits("uL");
        PrimerOption revPrimerOption = new PrimerOption(PRIMER_REVERSE_OPTION_ID, "Reverse Primer");
        addCustomOption(revPrimerOption);
        //IntegerOption revPrimerAmountOption = addIntegerOption("revPrAmount", "Primer Amount", 1, 0, Integer.MAX_VALUE);
        //revPrimerAmountOption.setUnits("uL");


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
        cleanupOption.addDependent(cleanupMethodOption, cleanupValues[0]);
        TextAreaOption notes = new TextAreaOption("notes", "Notes", "");
        addCustomOption(notes);

        labelOption = new LabelOption(LABEL_OPTION_ID, "Total Volume of Reaction: 0uL");
        addCustomOption(labelOption);
    }

    private List<OptionValue> getCocktails() {
        List<OptionValue> cocktails = new ArrayList<OptionValue>();
        List<? extends Cocktail> cocktailList = Cocktail.getAllCocktailsOfType(Reaction.Type.PCR);
        for (int i = 0; i < cocktailList.size(); i++) {
            Cocktail cocktail = cocktailList.get(i);
            cocktails.add(new OptionValue(""+cocktail.getId(), cocktail.getName()));
        }
        if(cocktailList.size() == 0) {
            cocktails.add(new OptionValue("-1", "No available cocktails"));
        }
        return cocktails;
    }

    private void updateCocktailOption(ComboBoxOption<OptionValue> cocktailOption) {
        List<OptionValue> cocktails = new ArrayList<OptionValue>();
        List<? extends Cocktail> cocktailList = Cocktail.getAllCocktailsOfType(Reaction.Type.PCR);
        for (int i = 0; i < cocktailList.size(); i++) {
            Cocktail cocktail = cocktailList.get(i);
            cocktails.add(new OptionValue(""+cocktail.getId(), cocktail.getName()));
        }
        if(cocktails.size() == 0) {
            cocktails.add(new OptionValue("-1", "No available cocktails"));
        }
        cocktailOption.setPossibleValues(cocktails);
    }
    
}
