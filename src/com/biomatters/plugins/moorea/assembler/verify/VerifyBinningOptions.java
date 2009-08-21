package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.components.GButton;
import com.biomatters.geneious.publicapi.plugin.Options;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.prefs.Preferences;

/**
 * @author Richard
 * @version $Id$
 */
public class VerifyBinningOptions extends Options {

    private final VerifyBinOptions highBinOptions = new VerifyBinOptions(true);
    private final VerifyBinOptions mediumBinOptions = new VerifyBinOptions(false);
    private final StringOption keywordsOption;

    private final Preferences prefs = Preferences.userNodeForPackage(VerifyBinningOptions.class);
    private static final String KEYWORDS = "keywords";

    public VerifyBinningOptions() {
        super(VerifyBinningOptions.class);
        String defaultKeywords = prefs.get(KEYWORDS, "COI, cytochrome");
        addLabel("Keywords (comma-separated):", false, true);
        beginAlignHorizontally(null, false);
        keywordsOption = addStringOption(KEYWORDS, "", defaultKeywords);
        endAlignHorizontally();
        addLabel(" ");
        addChildOptions("highBin", "High Bin", null, highBinOptions);
        addChildOptions("mediumBin", "Medium Bin", null, mediumBinOptions);
        addLabel(" ");
        addCustomOption(new SetDefaultsButtonOption());
    }

    public VerifyBinOptions getHighBinOptions() {
        return highBinOptions;
    }

    public VerifyBinOptions getMediumBinOptions() {
        return mediumBinOptions;
    }

    public String getKeywords() {
        return keywordsOption.getValue();
    }

    private final class SetDefaultsButtonOption extends Options.Option<Object, JButton> {

        private SetDefaultsButtonOption() {
            super("setDefaults", "Save as Global Defaults", new Object());
        }

        public Object getValueFromString(String value) {
            return new Object();
        }

        protected void setValueOnComponent(JButton component, Object value) {
        }

        @Override
        public String getDisplayedLabel() {
            return null;
        }

        protected JButton createComponent() {
            return new GButton(new AbstractAction("Save as Global Defaults") {
                public void actionPerformed(ActionEvent e) {
                    highBinOptions.saveCurrentValuesAsDefaults();
                    mediumBinOptions.saveCurrentValuesAsDefaults();
                    prefs.put(KEYWORDS, keywordsOption.getValue());
                    keywordsOption.setDefaultValue(keywordsOption.getValue());
                }
            });
        }
    }
}
