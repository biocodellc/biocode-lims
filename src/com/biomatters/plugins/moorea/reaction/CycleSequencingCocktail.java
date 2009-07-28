package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.TextAreaOption;
import com.biomatters.plugins.moorea.MooreaLabBenchService;

import java.util.List;
import java.sql.ResultSet;
import java.sql.SQLException;

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
        options.setValue("ddh20", resultSet.getInt("ddh2o"));
        options.setValue("buffer", resultSet.getInt("buffer"));
        options.setValue("bigDye", resultSet.getInt("bigDye"));
        options.setValue("notes", resultSet.getString("notes"));
        options.setValue("bufferConc", resultSet.getInt("bufferConc"));
        options.setValue("bigDyeConc", resultSet.getInt("bigDyeConc"));
        options.setValue("templateConc", resultSet.getInt("templateConc"));
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
            Options.IntegerOption templateConcOption = options.addIntegerOption("templateConc", "Template/target concentration", 0, 0, Integer.MAX_VALUE);
            templateConcOption.setUnits("uM");
            Options.IntegerOption ddh2oOption = options.addIntegerOption("ddh2o", "ddH2O", 1, 1, Integer.MAX_VALUE);
            ddh2oOption.setUnits("ul");
            Options.IntegerOption bufferOption = options.addIntegerOption("buffer", "5x buffer", 1, 1, Integer.MAX_VALUE);
            bufferOption.setUnits("ul");
            Options.IntegerOption bufferConcOption = options.addIntegerOption("bufferConc", "Buffer concentration", 0, 0, Integer.MAX_VALUE);
            bufferConcOption.setUnits("uM");
            Options.IntegerOption dyeOption = options.addIntegerOption("bigDye", "Big Dye", 1, 1, Integer.MAX_VALUE);
            dyeOption.setUnits("ul");
            Options.IntegerOption bigDyeConcOption = options.addIntegerOption("bigDyeConc", "Big Dye concentration", 0, 0, Integer.MAX_VALUE);
            bigDyeConcOption.setUnits("uM");
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
        String s = "INSERT INTO cyclesequencing_cocktail (name, ddh2o, buffer, bigDye, notes, bufferConc, bigDyeConc, templateConc) VALUES ('" + options.getValueAsString("name").replace("'", "''") + "', " + options.getValueAsString("ddh2o") + ", " + options.getValueAsString("buffer") + ", " + options.getValueAsString("bigDye") + ", '" + options.getValueAsString("notes").replace("'", "''") + "', "+options.getValue("bufferConc")+", "+options.getValue("bigDyeConc")+", "+options.getValue("templateConc")+")";
        System.out.println(s);
        return s;
    }
}
