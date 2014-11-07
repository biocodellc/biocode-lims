package com.biomatters.plugins.biocode.options;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.plates.Plate;

import javax.swing.*;
import java.util.Collection;

/**
 *
 * @author Frank Lee
 */
public class PlateSizeOption extends Options.ComboBoxOption<Options.OptionValue> {

    private static final Options.OptionValue[] VALUES;

    static {
        Collection<Plate.Size> sizeList = Plate.Size.getSizeList();
        VALUES = new Options.OptionValue[sizeList.size() + 1];
        VALUES[0] = new Options.OptionValue("None", "None");

        int i = 0;
        for (Plate.Size size : sizeList) {
            String lable = "" + size.numberOfReactions();
            VALUES[++i] = new Options.OptionValue(lable, lable);
        }
    }

    public PlateSizeOption(String name, String label) {
        super(name, label, VALUES, VALUES[0]);
    }

    @Override
    protected JComboBox createComponent() {
        JComboBox box = super.createComponent();
        box.setPrototypeDisplayValue("1stab");
        return box;
    }
}
