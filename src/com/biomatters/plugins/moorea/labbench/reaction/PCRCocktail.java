package com.biomatters.plugins.moorea.labbench.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.labbench.MooreaLabBenchService;
import com.biomatters.plugins.moorea.labbench.TextAreaOption;

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
        options.setValue("ddh20", resultSet.getDouble("ddH20"));
        options.setValue("buffer", resultSet.getDouble("buffer"));
        options.setValue("mg", resultSet.getDouble("mg"));
        options.setValue("bsa", resultSet.getDouble("bsa"));
        options.setValue("dntp", resultSet.getDouble("dNTP"));
        options.setValue("taq", resultSet.getDouble("taq"));
        options.setValue("notes", resultSet.getString("notes"));
        options.setValue("bufferConc", resultSet.getDouble("bufferConc"));
        options.setValue("mgConc", resultSet.getDouble("mgConc"));
        options.setValue("bsaConc", resultSet.getDouble("bsaConc"));
        options.setValue("dntpConc", resultSet.getDouble("dNTPConc"));
        options.setValue("taqConc", resultSet.getDouble("taqConc"));
        options.setValue("templateConc", resultSet.getDouble("templateConc"));
        options.setValue("fwPrConc", resultSet.getDouble("fwPrConc"));
        options.setValue("fwPrAmount", resultSet.getDouble("fwPrAmount"));
        options.setValue("revPrConc", resultSet.getDouble("revPrConc"));
        options.setValue("revPrAmount", resultSet.getDouble("revPrAmount"));
        options.setValue("extraItem", resultSet.getString("extraItem"));
        options.setValue("extraItemAmount", resultSet.getDouble("extraItemAmount"));
    }

    public PCRCocktail() {
        options = new Options(this.getClass());
        Options.StringOption nameOption = options.addStringOption("name", "Name", "");
        Options.DoubleOption tempateConcOption = options.addDoubleOption("templateConc", "Template/target Concentration", 0.0, 0.0, Double.MAX_VALUE);
        tempateConcOption.setUnits("uM");
        options.beginAlignHorizontally("Forward Primer", false);
        Options.DoubleOption fwPrimerConc = options.addDoubleOption("fwPrConc", "", 1.0, 0.0, Double.MAX_VALUE);
        fwPrimerConc.setUnits("uM");
        Options.DoubleOption fwPrimerOption = options.addDoubleOption("fwPrAmount", "", 1.0, 0.0, Double.MAX_VALUE);
        fwPrimerOption.setUnits("ul");
        options.endAlignHorizontally();
        options.beginAlignHorizontally("Reverse Primer", false);
        Options.DoubleOption revPrimerConc = options.addDoubleOption("revPrConc", "", 1.0, 0.0, Double.MAX_VALUE);
        revPrimerConc.setUnits("uM");
        Options.DoubleOption revPrimerOption = options.addDoubleOption("revPrAmount", "", 1.0, 0.0, Double.MAX_VALUE);
        revPrimerOption.setUnits("ul");
        options.endAlignHorizontally();
        Options.DoubleOption ddh2oOption = options.addDoubleOption("ddh20", "ddH20", 1.0, 0.0, Double.MAX_VALUE);
        ddh2oOption.setUnits("ul");
        options.beginAlignHorizontally("10x PCR Buffer", false);
        Options.DoubleOption bufferConcOption = options.addDoubleOption("bufferConc", "", 0.0, 0.0, Double.MAX_VALUE);
        bufferConcOption.setUnits("uM");
        Options.DoubleOption bufferOption = options.addDoubleOption("buffer", "", 1.0, 0.0, Double.MAX_VALUE);
        bufferOption.setUnits("ul");
        options.endAlignHorizontally();
        options.beginAlignHorizontally("Mg", false);
        Options.DoubleOption mgConcOption = options.addDoubleOption("mgConc", "", 25.0, 0.0, Double.MAX_VALUE);
        mgConcOption.setUnits("mM");
        Options.DoubleOption mgOption = options.addDoubleOption("mg", "", 1.0, 0.0, Double.MAX_VALUE);
        mgOption.setUnits("ul");
        options.endAlignHorizontally();
        options.beginAlignHorizontally("BSA", false);
        Options.DoubleOption bsaConcOption = options.addDoubleOption("bsaConc", "", 0.0, 0.0, Double.MAX_VALUE);
        bsaConcOption.setUnits("uM");
        Options.DoubleOption bsaOption = options.addDoubleOption("bsa", "", 1.0, 0.0, Double.MAX_VALUE);
        bsaOption.setUnits("ul");
        options.endAlignHorizontally();
        options.beginAlignHorizontally("dNTPs", false);
        Options.DoubleOption dntpConcOption = options.addDoubleOption("dntpConc", "", 10.0, 0.0, Double.MAX_VALUE);
        dntpConcOption.setUnits("mM");
        Options.DoubleOption dntpOption = options.addDoubleOption("dntp", "", 1.0, 0.0, Double.MAX_VALUE);
        dntpOption.setUnits("ul");
        options.endAlignHorizontally();
        options.beginAlignHorizontally("TAQ", false);
        Options.DoubleOption taqConcOption = options.addDoubleOption("taqConc", "", 5.0, 0.0, Double.MAX_VALUE);
        taqConcOption.setUnits("Units/ul");
        Options.DoubleOption taqOption = options.addDoubleOption("taq", "", 1.0, 0.0, Double.MAX_VALUE);
        taqOption.setUnits("ul");
        options.endAlignHorizontally();
        options.beginAlignHorizontally("Extra Ingredient", false);
        options.addStringOption("extraItem", "", "");
        Options.DoubleOption extraItem = options.addDoubleOption("extraItemAmount", "", 0.0, 0.0, Double.MAX_VALUE);
        extraItem.setUnits("ul");
        options.endAlignHorizontally();
        TextAreaOption areaOption = new TextAreaOption("notes", "Notes", "");
        options.addCustomOption(areaOption);
    }

    public String getSQLString() {
        return "INSERT INTO pcr_cocktail (name, ddH20, buffer, mg, bsa, dNTP, taq, notes, bufferConc, mgConc, dNTPConc, taqConc, templateConc, bsaConc, fwPrAmount, fwPrConc, revPrAmount, revPrConc, extraItem, extraItemAmount) VALUES ('"+options.getValueAsString("name").replace("'", "''")+"', "+options.getValueAsString("ddh20")+", "+options.getValueAsString("buffer")+", "+options.getValueAsString("mg")+", "+options.getValueAsString("bsa")+", "+options.getValueAsString("dntp")+", "+options.getValueAsString("taq")+", '"+options.getValueAsString("notes")+"', "+options.getValue("bufferConc")+", "+options.getValue("mgConc")+", "+options.getValue("dntpConc")+", "+options.getValue("taqConc")+", "+options.getValue("templateConc")+", "+options.getValue("bsaConc")+", "+options.getValue("fwPrAmount")+", "+options.getValue("fwPrConc")+", "+options.getValue("revPrAmount")+", "+options.getValue("revPrConc")+", '"+options.getValueAsString("extraItem")+"', "+options.getValue("extraItemAmount")+")";
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

    public List<Cocktail> getAllCocktailsOfType() {
        return MooreaLabBenchService.getInstance().getPCRCocktails();
    }

    public Cocktail createNewCocktail() {
        return new PCRCocktail();
    }
}
