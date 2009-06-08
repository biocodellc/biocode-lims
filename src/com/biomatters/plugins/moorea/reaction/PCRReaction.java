package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.*;
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
        options.addStringOption("sampleId", "Sample Name", "");

        Options.OptionValue[] passedValues = new Options.OptionValue[] {
                new Options.OptionValue("none", "not run"),
                new Options.OptionValue("passed", "passed"),
                new Options.OptionValue("failed", "failed"),
        };
        options.addComboBoxOption("runStatus", "Reaction state", passedValues, passedValues[0]);

        options.addLabel("");

        Options.IntegerOption ddh2oOption = options.addIntegerOption("ddh20", "ddH20 Amount", 1, 0, Integer.MAX_VALUE);
        Options.IntegerOption bufferOption = options.addIntegerOption("buffer", "10x PCR Buffer Amount", 1, 0, Integer.MAX_VALUE);
        Options.IntegerOption mgOption = options.addIntegerOption("mg", "Mg Amount", 1, 0, Integer.MAX_VALUE);
        Options.IntegerOption bsaOption = options.addIntegerOption("bsa", "BSA Amount", 1, 0, Integer.MAX_VALUE);
        Options.IntegerOption dntpOption = options.addIntegerOption("dntp", "dNTPs Amount", 1, 0, Integer.MAX_VALUE);

        options.beginAlignHorizontally("forward primer", false);
        Options.OptionValue[] values = new Options.OptionValue[] {new Options.OptionValue("myPrimer1", "My Primer 1"), new Options.OptionValue("myPrimer2", "My Primer 2")};
        options.addComboBoxOption("fwPrimer", "", values, values[0]);
        //options.addStringOption("fwPrimer", "Name", "");
        //options.addStringOption("fwPrimerBases", "", "");
        options.addIntegerOption("fwAmount", "amount", 1, 0, Integer.MAX_VALUE);
        options.endAlignHorizontally();

        options.beginAlignHorizontally("reverse primer", false);
        options.addComboBoxOption("revPrimer", "", values, values[0]);
        //options.addStringOption("revPrimer", "Name", "");
        //options.addStringOption("revPrimerBases", "", "");
        options.addIntegerOption("revAmount", "amount", 1, 0, Integer.MAX_VALUE);
        options.endAlignHorizontally();

        options.addIntegerOption("taq", "TAQ", 1, 0, Integer.MAX_VALUE);

        final Options.Option<String, ? extends JComponent> labelOption = options.addLabel("Total Volume of Reaction: 0uL");

        for(Options.Option o : options.getOptions()) {
            if(o instanceof Options.IntegerOption) {
                o.addChangeListener(new SimpleListener(){
                    public void objectChanged() {
                        int sum = 0;
                        for(Options.Option o : options.getOptions()) {
                            if(o instanceof Options.IntegerOption) {
                                sum += (Integer)o.getValue();
                            }
                        }
                        labelOption.setValue("Total Volume of Reaction: "+sum+"uL");
                    }
                });
            }
        }


    }



    public Options getOptions() {
        return options;
    }

    public List<DocumentField> getDisplayableFields() {

        List<DocumentField> fields = new ArrayList<DocumentField>();
        for(Options.Option op : getOptions().getOptions()) {
            if(!(op instanceof Options.LabelOption)){
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
