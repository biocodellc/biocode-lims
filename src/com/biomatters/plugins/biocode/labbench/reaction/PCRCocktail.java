package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.TextAreaOption;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 9/06/2009 11:18:12 AM
 */
public class PCRCocktail extends Cocktail{

    int id = -1;
    private Options options;

    public PCRCocktail(ResultSet resultSet) throws SQLException{
        this();
        id = resultSet.getInt("id");
        options.setValue("name", resultSet.getString("name"));
        options.setValue("ddH20", resultSet.getDouble("ddH20"));
        options.setValue("Buffer", resultSet.getDouble("buffer"));
        options.setValue("Mg", resultSet.getDouble("mg"));
        options.setValue("BSA", resultSet.getDouble("bsa"));
        options.setValue("dNTP", resultSet.getDouble("dNTP"));
        options.setValue("TAQ", resultSet.getDouble("taq"));
        options.setValue("notes", resultSet.getString("notes"));
        options.setValue("BufferConc", resultSet.getDouble("bufferConc"));
        options.setValue("MgConc", resultSet.getDouble("mgConc"));
        options.setValue("BSAConc", resultSet.getDouble("bsaConc"));
        options.setValue("dNTPConc", resultSet.getDouble("dNTPConc"));
        options.setValue("TAQConc", resultSet.getDouble("taqConc"));
        options.setValue("template Conc", resultSet.getDouble("templateConc"));
        options.setValue("fwd PrimerConc", resultSet.getDouble("fwPrConc"));
        options.setValue("fwd Primer", resultSet.getDouble("fwPrAmount"));
        options.setValue("rev PrimerConc", resultSet.getDouble("revPrConc"));
        options.setValue("rev Primer", resultSet.getDouble("revPrAmount"));
        options.setValue("extraItem", resultSet.getString("extraItem"));
        options.setValue("extraItemAmount", resultSet.getDouble("extraItemAmount"));
    }

    public PCRCocktail() {
        options = new Options(this.getClass());
        Options.StringOption nameOption = options.addStringOption("name", "Name", "");
        Options.DoubleOption tempateConcOption = options.addDoubleOption("template Conc", "Template/target Concentration", 0.0, 0.0, Double.MAX_VALUE);
        tempateConcOption.setUnits("uM");
        options.beginAlignHorizontally("Forward Primer", false);
        Options.DoubleOption fwPrimerConc = options.addDoubleOption("fwd PrimerConc", "", 1.0, 0.0, Double.MAX_VALUE);
        fwPrimerConc.setUnits("uM");
        Options.DoubleOption fwPrimerOption = options.addDoubleOption("fwd Primer", "", 1.0, 0.0, Double.MAX_VALUE);
        fwPrimerOption.setUnits("uL");
        options.endAlignHorizontally();
        options.beginAlignHorizontally("Reverse Primer", false);
        Options.DoubleOption revPrimerConc = options.addDoubleOption("rev PrimerConc", "", 1.0, 0.0, Double.MAX_VALUE);
        revPrimerConc.setUnits("uM");
        Options.DoubleOption revPrimerOption = options.addDoubleOption("rev Primer", "", 1.0, 0.0, Double.MAX_VALUE);
        revPrimerOption.setUnits("uL");
        options.endAlignHorizontally();
        Options.DoubleOption ddh2oOption = options.addDoubleOption("ddH20", "ddH20", 1.0, 0.0, Double.MAX_VALUE);
        ddh2oOption.setUnits("uL");
        options.beginAlignHorizontally("10x PCR Buffer", false);
        Options.DoubleOption bufferConcOption = options.addDoubleOption("BufferConc", "", 0.0, 0.0, Double.MAX_VALUE);
        bufferConcOption.setUnits("uM");
        Options.DoubleOption bufferOption = options.addDoubleOption("Buffer", "", 1.0, 0.0, Double.MAX_VALUE);
        bufferOption.setUnits("uL");
        options.endAlignHorizontally();
        options.beginAlignHorizontally("Mg", false);
        Options.DoubleOption mgConcOption = options.addDoubleOption("MgConc", "", 25.0, 0.0, Double.MAX_VALUE);
        mgConcOption.setUnits("mM");
        Options.DoubleOption mgOption = options.addDoubleOption("Mg", "", 1.0, 0.0, Double.MAX_VALUE);
        mgOption.setUnits("uL");
        options.endAlignHorizontally();
        options.beginAlignHorizontally("BSA", false);
        Options.DoubleOption bsaConcOption = options.addDoubleOption("BSAConc", "", 0.0, 0.0, Double.MAX_VALUE);
        bsaConcOption.setUnits("uM");
        Options.DoubleOption bsaOption = options.addDoubleOption("BSA", "", 1.0, 0.0, Double.MAX_VALUE);
        bsaOption.setUnits("uL");
        options.endAlignHorizontally();
        options.beginAlignHorizontally("dNTPs", false);
        Options.DoubleOption dntpConcOption = options.addDoubleOption("dNTPConc", "", 10.0, 0.0, Double.MAX_VALUE);
        dntpConcOption.setUnits("mM");
        Options.DoubleOption dntpOption = options.addDoubleOption("dNTP", "", 1.0, 0.0, Double.MAX_VALUE);
        dntpOption.setUnits("uL");
        options.endAlignHorizontally();
        options.beginAlignHorizontally("TAQ", false);
        Options.DoubleOption taqConcOption = options.addDoubleOption("TAQConc", "", 5.0, 0.0, Double.MAX_VALUE);
        taqConcOption.setUnits("Units/uL");
        Options.DoubleOption taqOption = options.addDoubleOption("TAQ", "", 1.0, 0.0, Double.MAX_VALUE);
        taqOption.setUnits("uL");
        options.endAlignHorizontally();
        options.beginAlignHorizontally("Extra Ingredient", false);
        options.addStringOption("extraItem", "", "");
        Options.DoubleOption extraItem = options.addDoubleOption("extraItemAmount", "", 0.0, 0.0, Double.MAX_VALUE);
        extraItem.setUnits("uL");
        options.endAlignHorizontally();
        TextAreaOption areaOption = new TextAreaOption("notes", "Notes", "");
        options.addCustomOption(areaOption);
    }

    public String getSQLString() {
        return "INSERT INTO pcr_cocktail (name, ddH20, buffer, mg, bsa, dNTP, taq, notes, bufferConc, mgConc, dNTPConc, taqConc, templateConc, bsaConc, fwPrAmount, fwPrConc, revPrAmount, revPrConc, extraItem, extraItemAmount) VALUES ('"+options.getValueAsString("name").replace("'", "''")+"', "+options.getValueAsString("ddH20")+", "+options.getValueAsString("Buffer")+", "+options.getValueAsString("Mg")+", "+options.getValueAsString("BSA")+", "+options.getValueAsString("dNTP")+", "+options.getValueAsString("TAQ")+", '"+options.getValueAsString("notes")+"', "+options.getValue("BufferConc")+", "+options.getValue("MgConc")+", "+options.getValue("dNTPConc")+", "+options.getValue("TAQConc")+", "+options.getValue("template Conc")+", "+options.getValue("BSAConc")+", "+options.getValue("fwd Primer")+", "+options.getValue("fwd PrimerConc")+", "+options.getValue("rev Primer")+", "+options.getValue("rev PrimerConc")+", '"+options.getValueAsString("extraItem")+"', "+options.getValue("extraItemAmount")+")";
    }

    public int getId() {
        return id;
    }

    public PCRCocktail(String name) {
        this();
        options.setValue("name", name);
    }

    public Options getOptions() {
        return options;
    }

    protected void setOptions(Options options) {
        this.options = options;
    }

    protected void setId(int id) {
        this.id = id;
    }

    protected void setName(String name) {
        if(options != null) {
            options.setValue("name", name);
        }
    }

    public String getName() {
        return options == null ? "Untitled" : options.getValueAsString("name");
    }

    public Cocktail createNewCocktail() {
        return new PCRCocktail();
    }
}
