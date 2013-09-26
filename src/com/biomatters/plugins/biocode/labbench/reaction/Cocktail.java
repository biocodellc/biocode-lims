package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 9/06/2009 11:17:43 AM
 */
public abstract class Cocktail implements XMLSerializable {

    public abstract int getId();

    public abstract String getName();

    public abstract Options getOptions();

    protected abstract void setOptions(Options options);

    protected abstract void setId(int id);

    protected abstract void setName(String name);

    public abstract String getTableName();

    public abstract Reaction.Type getReactionType();

    public double getReactionVolume(Options options) {
        double sum = 0;
        for (Options.Option o : options.getOptions()) {
            if (o instanceof Options.DoubleOption && !o.getName().toLowerCase().contains("conc") && !o.getName().toLowerCase().contains("template")) {
                sum += (Double) o.getValue();
            }
        }
        return sum;
    }

    public static List<? extends Cocktail> getAllCocktailsOfType(Reaction.Type type) {
        if(type == Reaction.Type.PCR) {
            return BiocodeService.getInstance().getPCRCocktails();
        }
        else if(type == Reaction.Type.CycleSequencing) {
            return BiocodeService.getInstance().getCycleSequencingCocktails();
        }
        else {
            throw new IllegalArgumentException("Only PCR and Cycle Sequencing reactions have cocktails");
        }
    }

    public abstract Cocktail createNewCocktail();

    public abstract String getSQLString();

    

    public boolean equals(Object o) {
        if(o instanceof Cocktail) {
            return ((Cocktail)o).getId() == getId();
        }
        return false;
    }

    public int hashCode() {
        return getId();
    }

    public Element toXML() {
        Element e = new Element("cocktail");
        e.addContent(new Element("name").setText(getName()));
        e.addContent(new Element("id").setText(""+getId()));
        e.addContent(XMLSerializer.classToXML("options", getOptions()));
        return e;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        setOptions(XMLSerializer.classFromXML(element.getChild("options"), Options.class));
        setName(element.getChildText("name"));
        setId(Integer.parseInt(element.getChildText("id")));
    }
}
