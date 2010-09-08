package com.biomatters.plugins.biocode.options;

import com.biomatters.geneious.publicapi.plugin.Options;

import javax.swing.*;

/**
 * Combo box contain 1st, 2nd, 3rd etc
 *
 * @author Richard
 * @version $Id$
 */
public class NamePartOption extends Options.ComboBoxOption<Options.OptionValue> {

    private static final Options.OptionValue[] VALUES = new Options.OptionValue[] {
            new Options.OptionValue("0", "1st"),
            new Options.OptionValue("1", "2nd"),
            new Options.OptionValue("2", "3rd"),
            new Options.OptionValue("3", "4th"),
            new Options.OptionValue("4", "5th"),
            new Options.OptionValue("5", "6th")
    };

    public NamePartOption(String name, String label) {
        super(name, label, VALUES, VALUES[0]);
    }

    @Override
    protected JComboBox createComponent() {
        JComboBox box = super.createComponent();
        box.setPrototypeDisplayValue("1stab");
        return box;
    }

    /**
     *
     * @return part of name to use, from 0 to 5
     */
    public int getPart() {
        return Integer.parseInt(getValue().getName());
    }
}
