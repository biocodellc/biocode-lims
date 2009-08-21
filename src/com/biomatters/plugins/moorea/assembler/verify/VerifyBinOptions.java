package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.types.TaxonomyDocument;
import com.biomatters.geneious.publicapi.implementations.Percentage;
import com.biomatters.geneious.publicapi.plugin.Icons;
import com.biomatters.geneious.publicapi.plugin.Options;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * @author Richard
 * @version $Id$
 */
public class VerifyBinOptions extends Options {

    private final ComboBoxOption<OptionValue> lowestTaxonOption;
    private final IntegerOption matchingKeywordsOption;
    private final IntegerOption minLengthOption;
    private final IntegerOption minIdentityOption;
    private final ComboBoxOption<OptionValue> minContigBinOption;

    private final Preferences prefs;
    private static final String LOWEST_TAXON = "lowestTaxon";
    private static final String MIN_MATCHING_KEYWORDS = "minMatchingKeywords";
    private static final String MIN_HIT_LENGTH = "minHitLength";
    private static final String MIN_IDENTITY = "minIdentity";
    private static final String MIN_CONTIG_BIN = "minContigBin";

    public VerifyBinOptions(boolean isHigh) {
        super(VerifyBinOptions.class);
        prefs = Preferences.userNodeForPackage(VerifyBinOptions.class).node(isHigh ? "highBinDefaults" : "mediumBinDefaults");

        List<OptionValue> taxonomicLevels = new ArrayList<OptionValue>();
        for (int i = 1; i < TaxonomyDocument.TaxonomicLevel.values().length; i++) {
            TaxonomyDocument.TaxonomicLevel taxonomicLevel = TaxonomyDocument.TaxonomicLevel.values()[i];
            taxonomicLevels.add(new OptionValue(taxonomicLevel.name(), taxonomicLevel.name()));
        }
        String defaultTaxonName = prefs.get(LOWEST_TAXON, null);
        OptionValue defaultTaxon = taxonomicLevels.get(isHigh ? 16 : 10);
        if (defaultTaxonName != null) {
            for (OptionValue taxonomicLevel : taxonomicLevels) {
                if (taxonomicLevel.getName().equals(defaultTaxonName)) {
                    defaultTaxon = taxonomicLevel;
                }
            }
        }
        lowestTaxonOption = addComboBoxOption(LOWEST_TAXON, "Lowest taxon that must match:", taxonomicLevels, defaultTaxon);

        int defaultKeywordCount = prefs.getInt(MIN_MATCHING_KEYWORDS, 1);
        matchingKeywordsOption = addIntegerOption(MIN_MATCHING_KEYWORDS, "Minimum keywords that must match:", defaultKeywordCount, 0, Integer.MAX_VALUE);
        int defaultMinLength = prefs.getInt(MIN_HIT_LENGTH, isHigh ? 700 : 600);
        minLengthOption = addIntegerOption(MIN_HIT_LENGTH, "Minimum hit length:", defaultMinLength, 0, Integer.MAX_VALUE);
        int defaultMinIdentity = prefs.getInt(MIN_IDENTITY, isHigh ? 80 : 70);
        minIdentityOption = addIntegerOption(MIN_IDENTITY, "Minimum hit identity:", defaultMinIdentity, 0, 100);
        minIdentityOption.setUnits("%");

        List<OptionValue> binOptionValues = Arrays.asList(Bin.High.getOptionValue(), Bin.Medium.getOptionValue(), Bin.Low.getOptionValue());
        String defaultBin = prefs.get(MIN_CONTIG_BIN, isHigh ? Bin.High.name() : Bin.Medium.name());
        minContigBinOption = addComboBoxOption(MIN_CONTIG_BIN, "Minimum assembly bin:", binOptionValues, Bin.valueOf(defaultBin).getOptionValue());
    }

    public void saveCurrentValuesAsDefaults() {
        for (Option option : getOptions()) {
            prefs.put(option.getName(), option.getValue().toString());
            //noinspection unchecked
            option.setDefaultValue(option.getValue());
        }
    }

    public int getMinLength() {
        return minLengthOption.getValue();
    }

    public int getMinIdentity() {
        return minIdentityOption.getValue();
    }

    public boolean isMetBy(VerifyResult result, String keywords) {
        if ((Integer)result.hitDocuments.get(0).getFieldValue(DocumentField.SEQUENCE_LENGTH) < minLengthOption.getValue()) {
            return false;
        }
        if (((Percentage)result.hitDocuments.get(0).getFieldValue(DocumentField.ALIGNMENT_PERCENTAGE_IDENTICAL)).doubleValue() < minIdentityOption.getValue()) {
            return false;
        }
        Bin bin = Bin.valueOf(result.queryDocument.getFieldValue(DocumentField.BIN).toString().replaceAll("<html>.*'>", "").replaceAll("</font.*html>", ""));
        Bin minimumBin = Bin.valueOf(minContigBinOption.getValue().getName());
        if (bin.getRank() < minimumBin.getRank()) {
            return false;
        }
        int matchingKeywords = 0;
        String hitDefinition = result.hitDocuments.get(0).getFieldValue(DocumentField.DESCRIPTION_FIELD).toString().toLowerCase();
        String[] keywordsArray = keywords.split(",");
        for (String keyword : keywordsArray) {
            if (hitDefinition.contains(keyword.trim().toLowerCase())) {
                matchingKeywords ++;
            }
        }
        if (matchingKeywords < matchingKeywordsOption.getValue()) {
            return false;
        }
        //todo match taxon
        return true;
    }

    public enum Bin {
        High("happy.png", 3),
        Medium("ok.png", 2),
        Low("sad.png", 1);

        private final String icon;
        private final int rank;
        private Icons icons;
        private final OptionValue optionValue;

        Bin(String icon, int rank) {
            this.icon = icon;
            this.rank = rank;
            this.optionValue = new OptionValue(name(), name());
        }

        public Icons getIcons() {
            if (icons == null) {
                icons = new Icons(new ImageIcon(VerifyBinOptions.class.getResource(icon)));
            }
            return icons;
        }

        public OptionValue getOptionValue() {
            return optionValue;
        }

        public int getRank() {
            return rank;
        }
    }
}
