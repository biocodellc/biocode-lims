package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 9/06/2009 11:18:12 AM
 */
public class PCRCocktail extends Cocktail{

    private Options options;

    public PCRCocktail() {
        options = new Options(this.getClass());
        Options.StringOption nameOption = options.addStringOption("name", "Name", "");
        Options.IntegerOption ddh2oOption = options.addIntegerOption("ddh20", "ddH20 Amount", 1, 0, Integer.MAX_VALUE);
        Options.IntegerOption bufferOption = options.addIntegerOption("buffer", "10x PCR Buffer Amount", 1, 0, Integer.MAX_VALUE);
        Options.IntegerOption mgOption = options.addIntegerOption("mg", "Mg Amount", 1, 0, Integer.MAX_VALUE);
        Options.IntegerOption bsaOption = options.addIntegerOption("bsa", "BSA Amount", 1, 0, Integer.MAX_VALUE);
        Options.IntegerOption dntpOption = options.addIntegerOption("dntp", "dNTPs Amount", 1, 0, Integer.MAX_VALUE);
        Options.IntegerOption taqOption = options.addIntegerOption("taq", "TAQ", 1, 0, Integer.MAX_VALUE);
    }

    public PCRCocktail(String name) {
        this();
        options.setValue("name", name);
    }

    public Options getOptions() {
        return options;
    }

    public String getName() {
        return options.getValueAsString("name");
    }

    public int getReactionVolume(Options options) {
        int sum = 0;
        for (Options.Option o : options.getOptions()) {
            if (o instanceof Options.IntegerOption) {
                sum += (Integer) o.getValue();
            }
        }
        return sum;
    }

    public List<Cocktail> getAllCocktailsOfType() {
        //todo: retrieve from database
        List<Cocktail> cocktails = new ArrayList<Cocktail>();
        cocktails.add(new PCRCocktail("Steve's Cocktail 1"));
        cocktails.add(new PCRCocktail("Steve's Cocktail 2"));
        return cocktails;
    }

    public Cocktail createNewCocktail() {
        return new PCRCocktail();
    }
}
