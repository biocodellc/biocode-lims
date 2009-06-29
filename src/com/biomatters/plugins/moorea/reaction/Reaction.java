package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.MooreaLabBenchService;
import com.biomatters.plugins.moorea.FimsSample;
import com.biomatters.plugins.moorea.ButtonOption;
import com.biomatters.plugins.moorea.Workflow;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.jdom.Element;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 16/05/2009
 * Time: 9:10:17 AM <br>
 * Represents a single reaction (ie a well on a plate)
 */
public abstract class Reaction implements XMLSerializable{
    private boolean selected;
    private int id=-1;
    private int plate;
    private Workflow workflow;
    private int position;
    protected boolean isError = false;
    protected FimsSample fimsSample = null;
    protected Date date = new Date();
    private static int averageCharWidth = -1;
    private static int charHeight = -1;

    private FontRenderContext fontRenderContext = new FontRenderContext(new AffineTransform(), false, false); //used for calculating the preferred size

    private List<DocumentField> displayableFields;

    public static final int PADDING = 10;
    private Thermocycle thermocycle;


    public enum Type {
        Extraction,
        PCR,
        CycleSequencing
    }

    public static Reaction getNewReaction(Type type) {
        switch(type) {
            case Extraction :
                return new ExtractionReaction();
            case PCR :
                return new PCRReaction();
            case CycleSequencing :
                return new CycleSequencingReaction();
        }
        return null;
    }

    private String locationString = "";

    public Reaction() {
        setFieldsToDisplay(getDefaultDisplayedFields());
    }

    public abstract Type getType();


    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected;
    }

    public Date getCreated() {
        return date;
    }

    protected void setCreated(Date date) {
        this.date = date;
    }


    public void setLocationString(String location) {
        this.locationString = location;
    }

    private static final int LINE_SPACING = 5;

    private Font firstLabelFont = new Font("sansserif", Font.BOLD, 12);
    private Font labelFont = new Font("sansserif", Font.PLAIN, 10);

    private Rectangle location = new Rectangle(0,0,0,0);


    public abstract Options getOptions();

    public Thermocycle getThermocycle(){
        return thermocycle;
    }

    public void setThermocycle(Thermocycle tc) {
        this.thermocycle = tc;
    }

    public abstract Cocktail getCocktail();

    public List<DocumentField> getAllDisplayableFields() {
        List<DocumentField> displayableFields = new ArrayList<DocumentField>();
        displayableFields.addAll(getDisplayableFields());
        displayableFields.addAll(MooreaLabBenchService.getInstance().getActiveFIMSConnection().getSearchAttributes());
        return displayableFields;
    }

    public List<DocumentField> getDisplayableFields() {
        List<DocumentField> fields = new ArrayList<DocumentField>();
        for(Options.Option op : getOptions().getOptions()) {
            if(!(op instanceof Options.LabelOption) && !(op instanceof ButtonOption)){
                fields.add(new DocumentField(op.getLabel(), "", op.getName(), op.getValue().getClass(), true, false));
            }
        }
        return fields;
    }

    public abstract List<DocumentField> getDefaultDisplayedFields();

    public List<DocumentField> getFieldsToDisplay(){
        return displayableFields != null ? displayableFields : Collections.EMPTY_LIST;
    };

    public void setFieldsToDisplay(List<DocumentField> fields) {
        this.displayableFields = fields;
    }

    public Object getFieldValue(String fieldCode) {
        Object value = getOptions().getValue(fieldCode);
        if(value instanceof Options.OptionValue) {
            return ((Options.OptionValue)value).getLabel();
        }
        if(value instanceof Integer || value instanceof Double) {
            Options.Option option = getOptions().getOption(fieldCode);
            value = option.getLabel()+": "+option.getValue();
        }
        if(value == null && fimsSample != null) { //check the FIMS data
            value = fimsSample.getFimsAttributeValue(fieldCode);
        }
        return value == null ? "" : value.toString();
    }

    public abstract String getExtractionId();
    
    public final Color getBackgroundColor() {
        if(isError) {
            return Color.orange.brighter();
        }
        return _getBackgroundColor();
    }

    public Element toXML() {
        Element element = new Element("Reaction");
        if(getThermocycle() != null) {
            element.addContent(new Element("thermocycle").setText(""+getThermocycle().getId()));
        }
        element.addContent(getOptions().valuesToXML("values"));
        return element;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        String thermoCycleId = element.getChildText("thermocycle");
        if(thermoCycleId != null) {
            int tcId = Integer.parseInt(thermoCycleId);
            for(Thermocycle tc : MooreaLabBenchService.getInstance().getPCRThermocycles()) {
                if(tc.getId() == tcId) {
                    setThermocycle(tc);
                    break;
                }
            }
            if(thermocycle == null) {
                for(Thermocycle tc : MooreaLabBenchService.getInstance().getCycleSequencingThermocycles()) {
                    if(tc.getId() == tcId) {
                        setThermocycle(tc);
                        break;
                    }
                }
            }
        }
        getOptions().valuesFromXML(element.getChild("values"));
    }

    public abstract Color _getBackgroundColor();

    public abstract String areReactionsValid(List<Reaction> reactions);

    public Dimension getPreferredSize() {
        int y = PADDING;
        int x = 0;
        String maxLabel = " ";
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

        if(averageCharWidth < 0 || charHeight < 0) {
            String testString = "AbCdEfGhIjKlMnOpQrStUvWxYz";
            TextLayout tl = new TextLayout(testString, g.getFont(), fontRenderContext);
            averageCharWidth = (int)tl.getBounds().getWidth()/26;
            charHeight = (int)tl.getBounds().getHeight();
        }

        if(locationString != null && locationString.length() > 0) {
            g.setColor(getBackgroundColor().darker().darker());
            g.setFont(new Font("sansserif", Font.PLAIN, 12));
            g.drawString(locationString, location.x+2, location.y+(int)charHeight + 2);
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
            int textHeight = charHeight;
            int textWidth = averageCharWidth*value.length();
            g.drawString(value.toString(), location.x + 5 + (location.width - textWidth - PADDING) / 2, y + textHeight);
            y += textHeight + LINE_SPACING;
        }

        g.setColor(Color.black);
    }

    public boolean isEmpty(){
        Options options = getOptions();
        for(Options.Option option : options.getOptions()) {
            if(!option.getValue().equals(option.getDefaultValue())) {
                return false;
            }
        }
        return true;
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

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPlate() {
        return plate;
    }

    public void setPlate(int plate) {
        this.plate = plate;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public void setWorkflow(Workflow workflow) {
        this.workflow = workflow;
    }

    public Date getDate() {
        return date;
    }

    public static void saveReactions(Reaction[] reactions, Type type, Connection connection, MooreaLabBenchService.BlockingDialog progress) throws IllegalStateException, SQLException {
        switch(type) {
            case Extraction:
                String sql = "INSERT INTO extraction (method, volume, dilution, parent, sampleId, extractionId, plate) VALUES (?, ?, ?, ?, ?, ?, ?)";
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.addBatch();
                for (int i = 0; i < reactions.length; i++) {
                    Reaction reaction = reactions[i];
                    if(progress != null) {
                        progress.setMessage("Saving reaction "+(i+1)+" of "+reactions.length);
                    }
                    if (!reaction.isEmpty() && reaction.plate >= 0) {
                        Options options = reaction.getOptions();
                        statement.setString(1, options.getValueAsString("extractionMethod"));
                        statement.setInt(2, (Integer) options.getValue("volume"));
                        statement.setInt(3, (Integer) options.getValue("dilution"));
                        statement.setString(4, options.getValueAsString("parentExtraction"));
                        statement.setString(5, options.getValueAsString("sampleId"));
                        statement.setString(6, options.getValueAsString("extractionId"));
                        statement.setInt(7, reaction.getPlate());
                        statement.execute();
                    }
                }
                break;
            case PCR:
                sql = "INSERT INTO pcr (prName, prSequence, prAmount, workflow, plate, location, cocktail, progress, thermocycle, cleanupPerformed, cleanupMethod, extractionId) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                statement = connection.prepareStatement(sql);
                for (int i = 0; i < reactions.length; i++) {
                    Reaction reaction = reactions[i];
                    if(progress != null) {
                        progress.setMessage("Saving reaction "+(i+1)+" of "+reactions.length);
                    }
                    if (!reaction.isEmpty() && reaction.plate >= 0) {
                        Options options = reaction.getOptions();
                        Object value = options.getValue(PCROptions.PRIMER_OPTION_ID);
                        if(!(value instanceof PCROptions.PrimerOptionValue)) {
                            throw new SQLException("Could not save reactions - expected primer type "+PCROptions.PrimerOptionValue.class.getCanonicalName()+" but found a "+value.getClass().getCanonicalName());
                        }
                        PCROptions.PrimerOptionValue primerOptionValue = (PCROptions.PrimerOptionValue) value;
                        statement.setString(1, primerOptionValue.getLabel());
                        statement.setString(2, primerOptionValue.getSequence());
                        statement.setInt(3, (Integer)options.getValue("prAmount"));
                        if (reaction.getWorkflow() == null || reaction.getWorkflow().getId() < 0) {
                            throw new SQLException("The reaction " + reaction.getId() + " does not have a workflow set.");
                        }
                        statement.setInt(4, reaction.getWorkflow().getId());
                        statement.setInt(5, reaction.getPlate());
                        statement.setInt(6, reaction.getPosition());
                        int cocktailId = -1;
                        Options.OptionValue cocktailValue = (Options.OptionValue) options.getValue("cocktail");
                        try {
                            cocktailId = Integer.parseInt(cocktailValue.getName());
                        }
                        catch(NumberFormatException ex) {
                            throw new SQLException("The reaction " + reaction.getId() + " does not have a valid cocktail ("+ cocktailValue.getLabel()+", "+cocktailValue.getName()+").");
                        }
                        if(cocktailId < 0) {
                            throw new SQLException("The reaction " + reaction.getId() + " does not have a valid cocktail ("+cocktailValue.getName()+").");
                        }
                        statement.setInt(7, cocktailId);
                        statement.setString(8, ((Options.OptionValue)options.getValue("runStatus")).getLabel());
                        if(reaction.getThermocycle() != null) {
                            statement.setInt(9, reaction.getThermocycle().getId());
                        }
                        else {
                            statement.setInt(9, -1);
                        }
                        statement.setBoolean(10, (Boolean)options.getValue("cleanupPerformed"));
                        statement.setString(11, options.getValueAsString("cleanupMethod"));
                        statement.setString(12, reaction.getExtractionId());
                        statement.execute();
                    }
                }
                break;
            case CycleSequencing:
                sql = "INSERT INTO cycleSequencing (primerName, primerSequence, primerAmount, workflow, plate, location, cocktail, progress, thermocycle, cleanupPerformed, cleanupMethod, extractionId, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                statement = connection.prepareStatement(sql);
                for (int i = 0; i < reactions.length; i++) {
                    Reaction reaction = reactions[i];
                    if(progress != null) {
                        progress.setMessage("Saving reaction "+(i+1)+" of "+reactions.length);
                    }
                    if (!reaction.isEmpty() && reaction.plate >= 0) {
                        Options options = reaction.getOptions();
                        Object value = options.getValue(PCROptions.PRIMER_OPTION_ID);
                        if(!(value instanceof CycleSequencingOptions.PrimerOptionValue)) {
                            throw new SQLException("Could not save reactions - expected primer type "+CycleSequencingOptions.PrimerOptionValue.class.getCanonicalName()+" but found a "+value.getClass().getCanonicalName());
                        }
                        CycleSequencingOptions.PrimerOptionValue primerOptionValue = (CycleSequencingOptions.PrimerOptionValue) value;
                        statement.setString(1, primerOptionValue.getLabel());
                        statement.setString(2, primerOptionValue.getSequence());
                        statement.setInt(3, (Integer)options.getValue("prAmount"));
                        if (reaction.getWorkflow() == null || reaction.getWorkflow().getId() < 0) {
                            throw new SQLException("The reaction " + reaction.getId() + " does not have a workflow set.");
                        }
                        statement.setInt(4, reaction.getWorkflow().getId());
                        statement.setInt(5, reaction.getPlate());
                        statement.setInt(6, reaction.getPosition());
                        int cocktailId = -1;
                        Options.OptionValue cocktailValue = (Options.OptionValue) options.getValue("cocktail");
                        try {
                            cocktailId = Integer.parseInt(cocktailValue.getName());
                        }
                        catch(NumberFormatException ex) {
                            throw new SQLException("The reaction " + reaction.getId() + " does not have a valid cocktail ("+ cocktailValue.getLabel()+", "+cocktailValue.getName()+").");
                        }
                        if(cocktailId < 0) {
                            throw new SQLException("The reaction " + reaction.getId() + " does not have a valid cocktail ("+cocktailValue.getName()+").");
                        }
                        statement.setInt(7, cocktailId);
                        statement.setString(8, ((Options.OptionValue)options.getValue("runStatus")).getLabel());
                        if(reaction.getThermocycle() != null) {
                            statement.setInt(9, reaction.getThermocycle().getId());
                        }
                        else {
                            statement.setInt(9, -1);
                        }
                        statement.setBoolean(10, (Boolean)options.getValue("cleanupPerformed"));
                        statement.setString(11, options.getValueAsString("cleanupMethod"));
                        statement.setString(12, reaction.getExtractionId());
                        statement.setString(13, options.getValueAsString("notes"));
                        statement.execute();
                    }
                }
                break;
        }
    }

}