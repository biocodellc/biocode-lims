package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.plugin.ExtendedPrintable;
import com.biomatters.plugins.moorea.plates.Plate;
import com.biomatters.plugins.moorea.plates.PlateView;
import com.biomatters.plugins.moorea.reaction.Reaction;
import com.biomatters.plugins.moorea.reaction.Cocktail;

import java.util.List;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.awt.*;

import org.jdom.Element;

import javax.swing.*;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 18/06/2009 4:10:35 PM
 */
public class PlateDocument extends MuitiPartDocument {

    private PlatePart platePart;

    public PlateDocument() {}

    public PlateDocument(Element e) throws XMLSerializationException{
        fromXML(e);
    }

    public PlateDocument(Plate p) {
        platePart = new PlatePart(p);
    }

    public List<DocumentField> getDisplayableFields() {
        return null;
    }

    public Object getFieldValue(String fieldCodeName) {
        return null;
    }

    public String getName() {
        return platePart.getName();
    }

    public URN getURN() {
        return null;
    }

    public Date getCreationDate() {
        return null;
    }

    public String getDescription() {
        return "A "+platePart.getPlate().getReactionType().toString()+" plate.";
    }

    public String toHTML() {
        return null;
    }

    public Element toXML() {
        return platePart.getPlate().toXML();
    }

    public void fromXML(Element element) throws XMLSerializationException {
        Plate plate = new Plate(element);
        platePart = new PlatePart(plate);
    }

    public int getNumberOfParts() {
        return 1;
    }

    public Part getPart(int index) {
        return platePart;
    }


    public static class PlatePart extends Part {
        private Plate plate;
        private PlateView pView;

        public PlatePart(Plate plate) {
            this.plate = plate;
            pView = new PlateView(plate);
            Map<Cocktail, Integer> cocktailCount = new HashMap<Cocktail, Integer>();
            for(Reaction r : plate.getReactions()) {
                Cocktail c = r.getCocktail();
                if(c != null) {
                    int count = 0;
                    Integer existing = cocktailCount.get(c);
                }
            }
            setLayout(new BorderLayout());
            add(new JScrollPane(pView), BorderLayout.CENTER);
        }


        public String getName() {
            return plate.getName();
        }

        public ExtendedPrintable getExtendedPrintable() {
            return null;
        }

        public boolean hasChanges() {
            return false;
        }

        public Plate getPlate() {
            return plate;
        }
    }
}
