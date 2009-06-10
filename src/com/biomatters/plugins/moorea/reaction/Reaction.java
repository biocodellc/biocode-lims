package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.MooreaLabBenchService;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 16/05/2009
 * Time: 9:10:17 AM <br>
 * Represents a single reaction (ie a well on a plate)
 */
public abstract class Reaction {
    private boolean selected;

    private FontRenderContext fontRenderContext = new FontRenderContext(new AffineTransform(), false, false); //used for calculating the preferred size

    private List<DocumentField> displayableFields;

    public static final int PADDING = 10;


    public enum Type {
        Extraction,
        PCR,
        CycleSequencing
    }

    public static Reaction getNewReaction(Type type) {
        switch(type) {
            case Extraction :
                break;
            case PCR :
                return new PCRReaction();
            case CycleSequencing :
                break;
        }
        return null;
    }

    private String locationString = "";

    public Reaction() {
        setFieldsToDisplay(getDefaultDisplayedFields());
    }


    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected;
    }


    public void setLocationString(String location) {
        this.locationString = location;
    }

    private static final int LINE_SPACING = 5;

    private Font firstLabelFont = new Font("sansserif", Font.BOLD, 12);
    private Font labelFont = new Font("sansserif", Font.PLAIN, 10);

    private Rectangle location = new Rectangle(0,0,0,0);


    public abstract Options getOptions();

    public List<DocumentField> getAllDisplayableFields() {
        List<DocumentField> displayableFields = new ArrayList<DocumentField>();
        displayableFields.addAll(getDisplayableFields());
        displayableFields.addAll(MooreaLabBenchService.getInstance().getActiveFIMSConnection().getSearchAttributes());
        return displayableFields;
    }

    public abstract List<DocumentField> getDisplayableFields();

    public abstract List<DocumentField> getDefaultDisplayedFields();

    public List<DocumentField> getFieldsToDisplay(){
        return displayableFields != null ? displayableFields : Collections.EMPTY_LIST;
    };

    public void setFieldsToDisplay(List<DocumentField> fields) {
        this.displayableFields = fields;
    }

    public abstract Object getFieldValue(String fieldCode);

    public abstract Color getBackgroundColor();

    public Dimension getPreferredSize() {
        int y = PADDING;
        int x = 0;
        String maxLabel = "";
        for(DocumentField field : getFieldsToDisplay()) {
            String value = getDisplayableValue(field);
            if(value.length() > maxLabel.length()) {
                maxLabel = value;
            }
        }
        TextLayout tl = new TextLayout(maxLabel, firstLabelFont, fontRenderContext);       
        for(DocumentField field : getFieldsToDisplay()) {
            String value = getFieldValue(field.getCode()).toString();
            if(value.length() == 0) {
                continue;
            }
            y += (int) tl.getBounds().getHeight()+LINE_SPACING;
            x = Math.max(x, (int)tl.getBounds().getWidth());
        }
        x += PADDING;
        return new Dimension(Math.max(50,x), Math.max(30,y));
    }

    public String getDisplayableValue(DocumentField field) {
        Object value = getFieldValue(field.getCode());
        if(value instanceof String) {
            return (String)value;
        }
        return field.getName()+": "+value;
    }


    public void paint(Graphics2D g){
        fontRenderContext = g.getFontRenderContext();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(isSelected() ? getBackgroundColor().darker() : getBackgroundColor());
        g.fillRect(location.x, location.y, location.width, location.height);

        if(locationString != null && locationString.length() > 0) {
            g.setColor(getBackgroundColor().darker().darker());
            g.setFont(new Font("sansserif", Font.PLAIN, 12));
            TextLayout tl = new TextLayout(locationString, g.getFont(), fontRenderContext);
            g.drawString(locationString, location.x+2, location.y+(int)tl.getBounds().getHeight() + 2);
        }

        g.setColor(Color.black);

        int y = location.y + 5;
        y += (location.height - getPreferredSize().height + PADDING)/2;
        for (int i = 0; i < getFieldsToDisplay().size(); i++) {
            g.setFont(i == 0 ? firstLabelFont : labelFont);

            DocumentField field = getFieldsToDisplay().get(i);
            String value = getFieldValue(field.getCode()).toString();
            if (value.length() == 0) {
                continue;
            }
            TextLayout tl = new TextLayout(value, g.getFont(), fontRenderContext);
            int textHeight = (int) tl.getBounds().getHeight();
            int textWidth = (int) tl.getBounds().getWidth();
            g.drawString(value.toString(), location.x + 5 + (location.width - textWidth - PADDING) / 2, y + textHeight);
            y += textHeight + LINE_SPACING;
        }

        g.setColor(Color.black);
    }

    public void setBounds(Rectangle r) {
        this.location = new Rectangle(r);
    }

    public Rectangle getBounds() {
        return new Rectangle(location);
    }

    public void setLocaton(Point p) {
        this.location.setLocation(p);
    }

    public Point getLocation() {
        return location.getLocation();
    }


    /*static abstract class Options {


    }


    static abstract class Option<T, V extends JComponent> {



        public final JPanel getOptionPanel() {

        }

        protected abstract V getEditableComponent();

        public abstract boolean isMultipleValues();

    }*/


}
