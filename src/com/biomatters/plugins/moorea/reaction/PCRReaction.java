package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.ButtonOption;
import com.biomatters.plugins.moorea.MooreaLabBenchService;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 16/05/2009
 * Time: 10:56:30 AM
 * To change this template use File | Settings | File Templates.
 */
public class PCRReaction extends Reaction {

    private Options options;

    public PCRReaction() {
        options = new Options(this.getClass());

        //todo interface for user to pick the sample
        options.addStringOption("sampleId", "Tissue ID", "");

        Options.OptionValue[] passedValues = new Options.OptionValue[] {
                new Options.OptionValue("none", "not run"),
                new Options.OptionValue("passed", "passed"),
                new Options.OptionValue("failed", "failed"),
        };
        options.addComboBoxOption("runStatus", "Reaction state", passedValues, passedValues[0]);

        options.addLabel("");
        options.beginAlignHorizontally("forward primer", false);
        Options.OptionValue[] values = new Options.OptionValue[] {new Options.OptionValue("myPrimer1", "My Primer 1"), new Options.OptionValue("myPrimer2", "My Primer 2")};
        options.addComboBoxOption("primer", "Primer", values, values[0]);
        options.addIntegerOption("prAmount", "Primer Amount", 1, 0, Integer.MAX_VALUE);
        options.endAlignHorizontally();


        List<Options.OptionValue> cocktails = new ArrayList<Options.OptionValue>();
        for (int i = 0; i < new PCRCocktail().getAllCocktailsOfType().size(); i++) {
            Cocktail cocktail = new PCRCocktail().getAllCocktailsOfType().get(i);
            cocktails.add(new Options.OptionValue(""+i, cocktail.getName()));
        }

        options.addComboBoxOption("cocktail", "Reaction Cocktail",  cocktails, cocktails.get(0));

        ButtonOption cocktailButton = new ButtonOption("cocktailEdit", "", "Edit Cocktails");
        cocktailButton.setSpanningComponent(true);
        options.addCustomOption(cocktailButton);
        cocktailButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                List<? extends Cocktail> newCocktails = Cocktail.editCocktails(new PCRCocktail().getAllCocktailsOfType(), null);
                MooreaLabBenchService.getInstance().addNewPCRCocktails(newCocktails);
            }
        });

        final Options.Option<String, ? extends JComponent> labelOption = options.addLabel("Total Volume of Reaction: 0uL");

        SimpleListener labelListener = new SimpleListener() {
            public void objectChanged() {
                int sum = 0;
                for (Options.Option o : options.getOptions()) {
                    if (o instanceof Options.IntegerOption) {
                        sum += (Integer) o.getValue();
                    }
                }
                labelOption.setValue("Total Volume of Reaction: " + sum + "uL");
            }
        };

        for(Options.Option o : options.getOptions()) {
            if(o instanceof Options.IntegerOption) {
                o.addChangeListener(labelListener);
            }
        }
        labelListener.objectChanged();


    }



    public Options getOptions() {
        return options;
    }

    public List<DocumentField> getDisplayableFields() {

        List<DocumentField> fields = new ArrayList<DocumentField>();
        for(Options.Option op : getOptions().getOptions()) {
            if(!(op instanceof Options.LabelOption) && !(op instanceof ButtonOption) && !op.getName().equals("sampleId")){
                fields.add(new DocumentField(op.getLabel(), "", op.getName(), op.getValue().getClass(), true, false));    
            }
        }
        return fields;
    }

    public Object getFieldValue(String fieldCode) {
        return options.getValueAsString(fieldCode);
    }

    public Color getBackgroundColor() {
        String runStatus = options.getValueAsString("runStatus");
        if(runStatus.equals("none"))
                return Color.white;
        else if(runStatus.equals("passed"))
                return Color.green.darker();
        else if(runStatus.equals("failed"))
            return Color.red.darker();
        return Color.white;
    }
}
