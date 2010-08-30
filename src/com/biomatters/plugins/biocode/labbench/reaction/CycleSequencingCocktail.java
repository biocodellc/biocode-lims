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
 *          Created on 24/06/2009 7:00:56 PM
 */
public class CycleSequencingCocktail extends Cocktail{
    int id = -1;
    private Options options;

    public CycleSequencingCocktail() {
        getOptions();
    }

    public CycleSequencingCocktail(String name) {
        this();
        options.setValue("name", name);
    }

    public CycleSequencingCocktail(ResultSet resultSet) throws SQLException{
        this();
        id = resultSet.getInt("id");
        options.setValue("name", resultSet.getString("name"));
        options.setValue("ddH2O", resultSet.getDouble("ddh2o"));
        options.setValue("BufferX", resultSet.getDouble("buffer"));
        options.setValue("Big Dye", resultSet.getDouble("bigDye"));
        options.setValue("notes", resultSet.getString("notes"));
        options.setValue("BufferVol", resultSet.getDouble("bufferConc"));
        options.setValue("Big DyeConc", resultSet.getDouble("bigDyeConc"));
        options.setValue("Template Conc", resultSet.getDouble("templateConc"));
        options.setValue("Template", resultSet.getDouble("templateAmount"));
        options.setValue("extraItem", resultSet.getString("extraItem"));
        options.setValue("extraItemAmount", resultSet.getDouble("extraItemAmount"));
        options.setValue("Primer", resultSet.getDouble("primerAmount"));
        options.setValue("PrimerConc", resultSet.getDouble("primerConc"));
        options.setValue("extraItem", resultSet.getString("extraItem"));
        options.setValue("extraItemAmount", resultSet.getDouble("extraItemAmount"));
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return options == null ? "Untitled" : options.getValueAsString("name");
    }

    public Options getOptions() {
        if(options == null) {
            options = new Options(this.getClass());
            Options.StringOption nameOption = options.addStringOption("name", "Name", "");
            options.beginAlignHorizontally("Template/Target", true);
            Options.DoubleOption templateConcOption = options.addDoubleOption("Template Conc", "", 0.0, 0.0, Double.MAX_VALUE);
            templateConcOption.setUnits("ng/킠");
            Options.DoubleOption templateOption = options.addDoubleOption("Template", "", 0.0, 0.0, Double.MAX_VALUE);
            templateOption.setUnits("킠");
            options.endAlignHorizontally();
            options.beginAlignHorizontally("Primer", true);
            Options.DoubleOption primerConcentrationOption = options.addDoubleOption("PrimerConc", "", 0.0, 0.0, Double.MAX_VALUE);
            primerConcentrationOption.setUnits("mM");
            Options.DoubleOption primerOption = options.addDoubleOption("Primer", "", 0.0, 0.0, Double.MAX_VALUE);
            primerOption.setUnits("킠");
            options.endAlignHorizontally();
            options.beginAlignHorizontally("ddH2O", true);
            options.addLabel(" ");
            Options.DoubleOption ddh2oOption = options.addDoubleOption("ddH2O", "", 0.0, 0.0, Double.MAX_VALUE);
            ddh2oOption.setUnits("킠");
            options.endAlignHorizontally();
            options.beginAlignHorizontally("Buffer", true);
            Options.DoubleOption bufferOption = options.addDoubleOption("BufferX", "", 0.0, 0.0, Double.MAX_VALUE);
            bufferOption.setUnits("X");
            Options.DoubleOption bufferConcOption = options.addDoubleOption("BufferVol", "", 0.0, 0.0, Double.MAX_VALUE);
            bufferConcOption.setUnits("킠");
            options.endAlignHorizontally();
            options.beginAlignHorizontally("Big Dye", true);
            Options.DoubleOption bigDyeConcOption = options.addDoubleOption("Big DyeConc", "", 0.0, 0.0, Double.MAX_VALUE);
            bigDyeConcOption.setUnits("킡");
            Options.DoubleOption dyeOption = options.addDoubleOption("Big Dye", "", 0.0, 0.0, Double.MAX_VALUE);
            dyeOption.setUnits("킠");
            options.endAlignHorizontally();
            options.beginAlignHorizontally("Extra Ingredient", true);
            options.addStringOption("extraItem", "", "");
            Options.DoubleOption extraIngredientAmount = options.addDoubleOption("extraItemAmount", "", 0.0, 0.0, Double.MAX_VALUE);
            extraIngredientAmount.setUnits("킠");
            options.endAlignHorizontally();
            TextAreaOption areaOption = new TextAreaOption("notes", "Notes", "");
            options.addCustomOption(areaOption);
        }
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

    public Cocktail createNewCocktail() {
        return new CycleSequencingCocktail();
    }

    public String getSQLString() {
        String s = "INSERT INTO cyclesequencing_cocktail (name, ddh2o, buffer, bigDye, notes, bufferConc, bigDyeConc, templateConc, templateAmount, extraItem, extraItemAmount, primerAmount, primerConc) VALUES ('"+options.getValueAsString("name").replace("'", "''")+"', "+options.getValueAsString("ddH2O")+", "+options.getValueAsString("BufferX")+", "+options.getValueAsString("Big Dye")+", '"+options.getValueAsString("notes").replace("'", "''")+"', "+options.getValueAsString("BufferVol")+",  "+options.getValueAsString("Big DyeConc")+", "+options.getValueAsString("Template Conc")+", '"+options.getValueAsString("Template")+"', '"+options.getValueAsString("extraItem")+"', "+options.getValueAsString("extraItemAmount")+", '"+options.getValueAsString("Primer")+"', "+options.getValueAsString("PrimerConc")+")";
        System.out.println(s);
        return s;
    }
}
