package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.MooreaLabBenchService;
import com.biomatters.plugins.moorea.TextAreaOption;

import java.util.List;
import java.util.ArrayList;
import java.sql.ResultSet;
import java.sql.SQLException;

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
        options.setValue("ddh20", resultSet.getInt("ddH20"));
        options.setValue("buffer", resultSet.getInt("buffer"));
        options.setValue("mg", resultSet.getInt("mg"));
        options.setValue("bsa", resultSet.getInt("bsa"));
        options.setValue("dntp", resultSet.getInt("dNTP"));
        options.setValue("taq", resultSet.getInt("taq"));
        options.setValue("notes", resultSet.getString("notes"));
        options.setValue("bufferConc", resultSet.getInt("bufferConc"));
        options.setValue("mgConc", resultSet.getInt("mgConc"));
        options.setValue("bsaConc", resultSet.getInt("bsaConc"));
        options.setValue("dntpConc", resultSet.getInt("dNTPConc"));
        options.setValue("taqConc", resultSet.getInt("taqConc"));
        options.setValue("templateConc", resultSet.getInt("templateConc"));
    }

    public PCRCocktail() {
        options = new Options(this.getClass());
        Options.StringOption nameOption = options.addStringOption("name", "Name", "");
        Options.IntegerOption tempateConcOption = options.addIntegerOption("templateConc", "Template/target Concentration", 0, 0, Integer.MAX_VALUE);
        tempateConcOption.setUnits("uM");
        Options.IntegerOption ddh2oOption = options.addIntegerOption("ddh20", "ddH20", 1, 0, Integer.MAX_VALUE);
        ddh2oOption.setUnits("ul");
        Options.IntegerOption bufferOption = options.addIntegerOption("buffer", "10x PCR Buffer", 1, 0, Integer.MAX_VALUE);
        bufferOption.setUnits("ul");
        Options.IntegerOption bufferConcOption = options.addIntegerOption("bufferConc", "buffer Concentration", 0, 0, Integer.MAX_VALUE);
        bufferConcOption.setUnits("uM");
        Options.IntegerOption mgOption = options.addIntegerOption("mg", "Mg", 1, 0, Integer.MAX_VALUE);
        mgOption.setUnits("ul");
        Options.IntegerOption mgConcOption = options.addIntegerOption("mgConc", "Mg Concentration", 0, 0, Integer.MAX_VALUE);
        mgConcOption.setUnits("uM");
        Options.IntegerOption bsaOption = options.addIntegerOption("bsa", "BSA", 1, 0, Integer.MAX_VALUE);
        bsaOption.setUnits("ul");
        Options.IntegerOption bsaConcOption = options.addIntegerOption("bsaConc", "bsa Concentration", 0, 0, Integer.MAX_VALUE);
        bsaConcOption.setUnits("uM");
        Options.IntegerOption dntpOption = options.addIntegerOption("dntp", "dNTPs", 1, 0, Integer.MAX_VALUE);
        dntpOption.setUnits("ul");
        Options.IntegerOption dntpConcOption = options.addIntegerOption("dntpConc", "dntp Concentration", 0, 0, Integer.MAX_VALUE);
        dntpConcOption.setUnits("uM");
        Options.IntegerOption taqOption = options.addIntegerOption("taq", "TAQ", 1, 0, Integer.MAX_VALUE);
        taqOption.setUnits("ul");
        Options.IntegerOption taqConcOption = options.addIntegerOption("taqConc", "taq Concentration", 0, 0, Integer.MAX_VALUE);
        taqConcOption.setUnits("uM");
        TextAreaOption areaOption = new TextAreaOption("notes", "Notes", "");
        options.addCustomOption(areaOption);
    }

    public String getSQLString() {
        return "INSERT INTO pcr_cocktail (name, ddH20, buffer, mg, bsa, dNTP, taq, notes, bufferConc, mgConc, dNTPConc, taqConc, templateConc, bsaConc) VALUES ('"+options.getValueAsString("name").replace("'", "''")+"', "+options.getValueAsString("ddh20")+", "+options.getValueAsString("buffer")+", "+options.getValueAsString("mg")+", "+options.getValueAsString("bsa")+", "+options.getValueAsString("dntp")+", "+options.getValueAsString("taq")+", '"+options.getValueAsString("notes")+"', "+options.getValue("bufferConc")+", "+options.getValue("mgConc")+", "+options.getValue("dntpConc")+", "+options.getValue("taqConc")+", "+options.getValue("templateConc")+", "+options.getValue("bsaConc")+")";
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
