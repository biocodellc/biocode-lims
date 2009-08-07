package com.biomatters.plugins.moorea.assembler.annotate;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.options.NamePartOption;
import com.biomatters.plugins.moorea.options.NameSeparatorOption;

/**
 * @author Richard
 * @version $Id$
 */
public class AnnotateFimsDataOptions extends Options {

    private final NamePartOption namePartOption = new NamePartOption("namePart", "");
    private final NameSeparatorOption nameSeparatorOption = new NameSeparatorOption("nameSeparator", "");
    private final StringOption plateNameOption;
    private final ComboBoxOption<OptionValue> idType;

    private static final OptionValue WELL_NUMBER = new OptionValue("wellNumber", "Well number");
    private static final OptionValue BARCODE = new OptionValue("barcode", "Barcode");

    public AnnotateFimsDataOptions() {
        super(AnnotateFimsDataOptions.class);
        plateNameOption = addStringOption("plateName", "FIMS Plate Name:", "");
        plateNameOption.setDescription("eg. M001");
        beginAlignHorizontally(null, false);
        idType = addComboBoxOption("idType", "", new OptionValue[] {WELL_NUMBER, BARCODE}, WELL_NUMBER);
        addLabel("is");
        addCustomOption(namePartOption);
        addLabel("part of name, after");
        addCustomOption(nameSeparatorOption);
        endAlignHorizontally();
    }

    public String getPlateName() {
        return plateNameOption.getValue();
    }

    public int getNamePart() {
        return namePartOption.getPart();
    }

    public String getNameSeaparator() {
        return nameSeparatorOption.getSeparatorString();
    }

    public boolean isByBarcode() {
        return idType.getValue() == BARCODE;
    }
}
