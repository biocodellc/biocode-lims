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
 *          Created on 24/06/2009 7:00:56 PM
 */
public class CycleSequencingCocktail extends Cocktail{
    int id = -1;
    private Options options;

    public CycleSequencingCocktail() {
        getOptions();
    }

    public CycleSequencingCocktail(ResultSet resultSet) throws SQLException{
        this();
        id = resultSet.getInt("id");
        options.setValue("name", resultSet.getString("name"));
        options.setValue("ddh2o", resultSet.getDouble("ddh2o"));
        options.setValue("buffer", resultSet.getDouble("buffer"));
        options.setValue("bigDye", resultSet.getDouble("bigDye"));
        options.setValue("notes", resultSet.getString("notes"));
        options.setValue("bufferConc", resultSet.getDouble("bufferConc"));
        options.setValue("bigDyeConc", resultSet.getDouble("bigDyeConc"));
        options.setValue("templateConc", resultSet.getDouble("templateConc"));
        options.setValue("extraItem", resultSet.getString("extraItem"));
        options.setValue("extraItemAmount", resultSet.getDouble("extraItemAmount"));
        options.setValue("primerAmount", resultSet.getDouble("primerAmount"));
        options.setValue("primerConc", resultSet.getDouble("primerConc"));
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
            Options.DoubleOption templateConcOption = options.addDoubleOption("templateConc", "Template/target concentration", 0.0, 0.0, Double.MAX_VALUE);
            templateConcOption.setUnits("uM");
            options.beginAlignHorizontally("Primer", false);
            Options.DoubleOption primerConcentrationOption = options.addDoubleOption("primerConc", "", 0.0, 0.0, Double.MAX_VALUE);
            primerConcentrationOption.setUnits("uM");
            Options.DoubleOption primerOption = options.addDoubleOption("primerAmount", "", 0.0, 0.0, Double.MAX_VALUE);
            primerOption.setUnits("ul");
            options.endAlignHorizontally();
            Options.DoubleOption ddh2oOption = options.addDoubleOption("ddh2o", "ddH2O", 0.0, 0.0, Double.MAX_VALUE);
            ddh2oOption.setUnits("ul");
            options.beginAlignHorizontally("5x buffer", false);
            Options.DoubleOption bufferConcOption = options.addDoubleOption("bufferConc", "", 0.0, 0.0, Double.MAX_VALUE);
            bufferConcOption.setUnits("uM");
            Options.DoubleOption bufferOption = options.addDoubleOption("buffer", "", 0.0, 0.0, Double.MAX_VALUE);
            bufferOption.setUnits("ul");
            options.endAlignHorizontally();
            options.beginAlignHorizontally("Big Dye", false);
            Options.DoubleOption bigDyeConcOption = options.addDoubleOption("bigDyeConc", "", 0.0, 0.0, Double.MAX_VALUE);
            bigDyeConcOption.setUnits("uM");
            Options.DoubleOption dyeOption = options.addDoubleOption("bigDye", "", 0.0, 0.0, Double.MAX_VALUE);
            dyeOption.setUnits("ul");
            options.endAlignHorizontally();
            options.beginAlignHorizontally("Extra Ingredient", false);
            options.addStringOption("extraItem", "", "");
            Options.DoubleOption extraIngredientAmount = options.addDoubleOption("extraItemAmount", "", 0.0, 0.0, Double.MAX_VALUE);
            extraIngredientAmount.setUnits("ul");
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

    public List<? extends Cocktail> getAllCocktailsOfType() {
        return MooreaLabBenchService.getInstance().getCycleSequencingCocktails();
    }

    public Cocktail createNewCocktail() {
        return new CycleSequencingCocktail();
    }

    public String getSQLString() {
        String s = "INSERT INTO cyclesequencing_cocktail (name, ddh2o, buffer, bigDye, notes, bufferConc, bigDyeConc, templateConc, primerAmount, primerConc, extraItem, extraItemAmount) VALUES ('" + options.getValueAsString("name").replace("'", "''") + "', " + options.getValueAsString("ddh2o") + ", " + options.getValueAsString("buffer") + ", " + options.getValueAsString("bigDye") + ", '" + options.getValueAsString("notes").replace("'", "''") + "', "+options.getValue("bufferConc")+", "+options.getValue("bigDyeConc")+", "+options.getValue("templateConc")+", "+options.getValue("primerAmount")+", "+options.getValue("primerConc")+", '"+options.getValue("extraItem")+"', "+options.getValue("extraItemAmount")+")";
        System.out.println(s);
        return s;
    }
}
