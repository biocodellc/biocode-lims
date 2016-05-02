package com.biomatters.plugins.biocode.assembler.verify;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.implementations.Percentage;
import com.biomatters.geneious.publicapi.plugin.Icons;
import com.biomatters.geneious.publicapi.plugin.Options;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * @author Richard
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
    private static final String MIN_HIT_LENGTH = "minHitLengthPercent";
    private static final String MIN_IDENTITY = "minIdentity";
    private static final String MIN_CONTIG_BIN = "minContigBin";

    public VerifyBinOptions(boolean isHigh) {
        super(VerifyBinOptions.class);
        prefs = Preferences.userNodeForPackage(VerifyBinOptions.class).node(isHigh ? "highBinDefaults" : "mediumBinDefaults");

        List<OptionValue> taxonomicLevels = new ArrayList<OptionValue>();
        for (BiocodeTaxon.Level level : BiocodeTaxon.Level.values()) {
            taxonomicLevels.add(new OptionValue(level.name(), level.name().toLowerCase()));
        }
        String defaultTaxonName = prefs.get(LOWEST_TAXON, null);
        OptionValue defaultTaxon = taxonomicLevels.get(isHigh ? 8 : 4);
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
        int defaultMinLength = prefs.getInt(MIN_HIT_LENGTH, isHigh ? 80 : 70);
        minLengthOption = addIntegerOption(MIN_HIT_LENGTH, "Minimum hit length:", defaultMinLength, 0, 100);
        minLengthOption.setUnits("% (of query length)");
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
        if(result.hitDocuments == null || result.hitDocuments.size() == 0) {
            return false;
        }
        AnnotatedPluginDocument hitDocument = result.hitDocuments.get(0);
        AnnotatedPluginDocument queryDocument = result.queryDocument;
        if (100*(Integer) hitDocument.getFieldValue(DocumentField.SEQUENCE_LENGTH) / (Integer) queryDocument.getFieldValue(DocumentField.SEQUENCE_LENGTH) < minLengthOption.getValue()) {
            return false;
        }
        if (((Percentage) hitDocument.getFieldValue(DocumentField.ALIGNMENT_PERCENTAGE_IDENTICAL)).doubleValue() < minIdentityOption.getValue()) {
            return false;
        }
        Object binValue = result.queryDocument.getFieldValue(DocumentField.BIN);
        if(binValue == null) {
            return false;
        }
        Bin bin = Bin.valueOf(binValue.toString().replaceAll("<html>.*'>", "").replaceAll("</font.*html>", ""));
        Bin minimumBin = Bin.valueOf(minContigBinOption.getValue().getName());
        if (bin.getRank() < minimumBin.getRank()) {
            return false;
        }
        int matchingKeywords = 0;
        String hitDefinition = hitDocument.getFieldValue(DocumentField.DESCRIPTION_FIELD).toString().toLowerCase();
        String[] keywordsArray = keywords.split(",");
        for (String keyword : keywordsArray) {
            if (hitDefinition.contains(keyword.trim().toLowerCase())) {
                matchingKeywords ++;
            }
        }
        if (matchingKeywords < matchingKeywordsOption.getValue()) {
            return false;
        }

        Object hitTaxonObject = hitDocument.getFieldValue(DocumentField.TAXONOMY_FIELD);
        BiocodeTaxon queryTaxon = result.queryTaxon;
        if (queryTaxon == null) {
            return false;
        }
        if (hitTaxonObject != null) {
            String hitTaxon = hitTaxonObject.toString().toLowerCase();
            BiocodeTaxon.Level lowestMatchingLevel = null;
            while (queryTaxon != null) {
                if (hitTaxon.contains(queryTaxon.name.toLowerCase())) {
                    lowestMatchingLevel = queryTaxon.level;
                    break;
                }
                queryTaxon = queryTaxon.parent;
            }
            BiocodeTaxon.Level lowestRequiredLevel = BiocodeTaxon.Level.valueOf(lowestTaxonOption.getValue().getName());
            boolean lowestMatchingLevelReached = false;
            for (BiocodeTaxon.Level level : BiocodeTaxon.Level.values()) {
                if (level == lowestMatchingLevel) {
                    lowestMatchingLevelReached = true;
                } else if (level == lowestRequiredLevel) {
                    if (!lowestMatchingLevelReached) {
                        break;
                    } else {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    public enum Bin {
        High("happy.png", "High", Color.green.darker(), 3),
        Medium("ok.png", "Medium", Color.orange, 2),
        Low("sad.png", "Low", Color.red.darker(), 1);

        private final String icon;
        private String title;
        private Color color;
        private final int rank;
        private VerifyTaxonomyTableModel.IconsWithToString icons;
        private final OptionValue optionValue;

        Bin(String icon, String title, Color color, int rank) {
            this.icon = icon;
            this.title = title;
            this.color = color;
            this.rank = rank;
            this.optionValue = new OptionValue(name(), name());
        }

        public VerifyTaxonomyTableModel.IconsWithToString getIcons() {
            if (icons == null) {
                icons = new VerifyTaxonomyTableModel.IconsWithToString(title, new Icons(new ImageIcon(VerifyBinOptions.class.getResource(icon))), color, rank);
            }
            return icons;
        }

        public OptionValue getOptionValue() {
            return optionValue;
        }

        public int getRank() {
            return rank;
        }

        public Color getColor() {
            return color;
        }
    }
}
