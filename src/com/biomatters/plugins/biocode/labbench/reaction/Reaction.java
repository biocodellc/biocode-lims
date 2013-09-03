package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ButtonOption;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ImageObserver;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 16/05/2009
 * Time: 9:10:17 AM <br>
 * Represents a single reaction (ie a well on a plate)
 */
public abstract class Reaction<T extends Reaction> implements XMLSerializable{
    private boolean selected;
    private int id=-1;
    private int plateId;
    private String plateName;
    private Workflow workflow;
    String extractionBarcode;
    private int position;
    public boolean isError = false;
    private FimsSample fimsSample = null;
    protected Date date = new Date();
    private static int charHeight = -1;
    private int[] fieldWidthCache = null;
    private GelImage gelImage = null;
    private BackgroundColorer backgroundColorer;
    DisplayFieldsTemplate displayFieldsTemplate;
    private static final ImageObserver imageObserver = new ImageObserver(){
        public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
            return false;
        }
    };

    private static Preferences getPreferences() {
        return Preferences.userNodeForPackage(Reaction.class);
    }

    private FontRenderContext fontRenderContext = new FontRenderContext(new AffineTransform(), false, false); //used for calculating the preferred size

    private List<DocumentField> displayableFields;

    public static final int PADDING = 10;
    private Thermocycle thermocycle;
    public static final DocumentField GEL_IMAGE_DOCUMENT_FIELD = new DocumentField("GELImage", "", "gelImage", String.class, false, false);
    public static final DocumentField PLATE_NAME_DOCUMENT_FIELD = new DocumentField("Plate", "", "_plateName_", String.class, false, false);
    public static final DocumentField WELL_DOCUMENT_FIELD = new DocumentField("Well", "", "_plateWell_", String.class, false, false);

    public abstract String getLocus();

    public void setBaseFontSize(int fontSze) {
        labelFont = new Font("sansserif", Font.PLAIN, fontSze);
        firstLabelFont = new Font("sansserif", Font.BOLD, fontSze+2);
        invalidateFieldWidthCache();
    }

    public void invalidateFieldWidthCache() {
        Runnable runnable = new Runnable() {
            public void run() {
                fieldWidthCache = null;
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }


    public enum Type {
        Extraction,
        PCR,
        CycleSequencing
    }

    public GelImage getGelImage() {
        return gelImage;
    }

    public void setGelImage(GelImage gelImage) {
        this.gelImage = gelImage;
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
        setFieldsToDisplay(BiocodeService.getInstance().getDefaultDisplayedFieldsTemplate(getType()).getDisplayedFields());
    }

    public void setFimsSample(FimsSample sample) {
        this.fimsSample = sample;
        invalidateFieldWidthCache();
        if(workflow != null) {
            workflow.setFimsSample(fimsSample);
        }
    }

    public abstract Type getType();

    protected BackgroundColorer getDefaultBackgroundColorer() {
         return BiocodeService.getInstance().getDefaultDisplayedFieldsTemplate(getType()).getColorer();
    }

    public BackgroundColorer getBackgroundColorer() {
        return backgroundColorer == null ? getDefaultBackgroundColorer() : backgroundColorer;
    }

    public void setBackgroundColorer(BackgroundColorer backgroundColorer) {
        this.backgroundColorer = backgroundColorer;
    }

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

    public String getPlateName() {
        return plateName != null ? plateName : "";
    }

    public void setPlateName(String plateName) {
        if(plateName == null) {
            throw new IllegalArgumentException("Plate names cannot be null");
        }
        this.plateName = plateName;
    }

    public String getLocationString() {
        return locationString != null ? locationString : "";
    }

    private static final int LINE_SPACING = 5;

    private Font firstLabelFont = new Font("sansserif", Font.BOLD, 12);
    private Font labelFont = new Font("sansserif", Font.PLAIN, 10);

    private Rectangle location = new Rectangle(0,0,0,0);


    public final ReactionOptions getOptions() {
        ReactionOptions options = _getOptions();
        if(options != null) {
            options.setReaction(this);
        }
        return options;
    }

    public abstract ReactionOptions _getOptions();

    public abstract void setOptions(ReactionOptions op);

    public Thermocycle getThermocycle(){
        return thermocycle;
    }

    public boolean hasError() {
        return isError;
    }

    public void setHasError(boolean b) {
        isError = b;
    }

    public void setThermocycle(Thermocycle tc) {
        this.thermocycle = tc;
    }

    public abstract Cocktail getCocktail();

    public List<DocumentField> getAllDisplayableFields() {
        List<DocumentField> displayableFields = new ArrayList<DocumentField>();
        displayableFields.addAll(getDisplayableFields());
        if(BiocodeService.getInstance().isLoggedIn()) {
            displayableFields.addAll(BiocodeService.getInstance().getActiveFIMSConnection().getCollectionAttributes());
            displayableFields.addAll(BiocodeService.getInstance().getActiveFIMSConnection().getTaxonomyAttributes());
        }
        else if(fimsSample != null) {
            displayableFields.addAll(fimsSample.getFimsAttributes());
            displayableFields.addAll(fimsSample.getTaxonomyAttributes());
        }
        return displayableFields;
    }

    public DocumentField getDisplayableField(String code) {
        for(DocumentField field : getAllDisplayableFields()) {
            if(field.getCode().equals(code)) {
                return field;
            }
        }
        return null;
    }

    public List<DocumentField> getDisplayableFields() {
        List<DocumentField> fields = new ArrayList<DocumentField>();
        fields.add(GEL_IMAGE_DOCUMENT_FIELD);
        for(Options.Option op : getOptions().getOptions()) {
            if(!(op instanceof Options.LabelOption) && !(op instanceof ButtonOption) && !(op instanceof Options.ButtonOption)){
                if(op instanceof Options.ComboBoxOption) {
                    final List possibleValues = ((Options.ComboBoxOption) op).getPossibleOptionValues();
                    String[] enumValues = new String[possibleValues.size()];
                    for (int i = 0; i < possibleValues.size(); i++) {
                        enumValues[i] = ((Options.OptionValue)possibleValues.get(i)).getLabel();

                    }
                    fields.add(DocumentField.createEnumeratedField(enumValues, op.getLabel().length() > 0 ? op.getLabel() : op.getName(), "", op.getName(), true, false));
                }
                else if(op instanceof DocumentSelectionOption) {
                    fields.add(new DocumentField(op.getLabel().length() > 0 ? op.getLabel() : op.getName(), "", op.getName(), String.class, true, false));
                }
                else {
                    fields.add(new DocumentField(op.getLabel().length() > 0 ? op.getLabel() : op.getName(), "", op.getName(), op.getValue().getClass(), true, false));
                }
            }
        }
        return fields;
    }

    public static List<DocumentField> getDefaultDisplayedFields() {
        return Collections.emptyList();//BiocodeService.getInstance().getDefaultDisplayedFieldsTemplate(getType()).getDisplayedFields();
    }

    public List<DocumentField> getFieldsToDisplay(){
        if(isEmpty()) {
            if(displayableFields != null && displayableFields.contains(GEL_IMAGE_DOCUMENT_FIELD)) {
                return Arrays.asList(GEL_IMAGE_DOCUMENT_FIELD);
            }
            DisplayFieldsTemplate displayFieldsTemplate = getDefaultDisplayedFieldsTemplate();
            if(displayFieldsTemplate != null) {
                List<DocumentField> fields = displayFieldsTemplate.getDisplayedFields();
                if(fields.contains(GEL_IMAGE_DOCUMENT_FIELD)) {
                    return Arrays.asList(GEL_IMAGE_DOCUMENT_FIELD);
                }
            }
            return Collections.emptyList();
        }
        if(displayableFields != null) {
            return displayableFields;
        }
        DisplayFieldsTemplate displayFieldsTemplate = getDefaultDisplayedFieldsTemplate();
        if(displayFieldsTemplate != null) {
            return displayFieldsTemplate.getDisplayedFields();
        }
        return Collections.emptyList();
    }

    public DisplayFieldsTemplate getDefaultDisplayedFieldsTemplate() {
        final String templateName = getPreferences().get(getType() + "_fields", null);
        if(templateName == null) {
            return BiocodeService.getInstance().getDisplayedFieldTemplate(getType(), templateName);
        }
        return null;
    }

    public void setFieldsToDisplay(List<DocumentField> fields) {
        this.displayableFields = fields;
        invalidateFieldWidthCache();
    }

    public Object getFieldValue(String fieldCode) {
        if(fieldCode.equals("testField")) {
            return "A";
        }
        Options options = getOptions();
        if(options == null) {
            return null;
        }
        Options.Option option = options.getOption(fieldCode);
        Object value = null;
        if(option != null) {
            if(option instanceof DocumentSelectionOption) {
                List<AnnotatedPluginDocument> valueList = ((DocumentSelectionOption)option).getDocuments();
                if(valueList.size() == 0) {
                    return "None";
                }
                AnnotatedPluginDocument firstValue = valueList.get(0);
                return firstValue.getName();
            }
            value = option.getValue();
            if(value instanceof Options.OptionValue) {
                return ((Options.OptionValue)value).getLabel();
            }
        }
        else if(fimsSample != null) { //check the FIMS data
            value = fimsSample.getFimsAttributeValue(fieldCode);
        }
        if(value == null) {
            if(fieldCode.equals(PLATE_NAME_DOCUMENT_FIELD.getCode())) {
                return plateName;
            }
            if(fieldCode.equals(WELL_DOCUMENT_FIELD.getCode())) {
                return locationString;
            }
        }
        return value == null ? "" : value;
    }

    public abstract String getExtractionId();

    public abstract void setExtractionId(String s);

    public String getExtractionBarcode() {
        return extractionBarcode;
    }
    
    public final Color getBackgroundColor() {
        if(isError) {
            return Color.orange.brighter();
        }
        return getBackgroundColorer().getColor(this);
    }

    public FimsSample getFimsSample() {
        return fimsSample;
    }

    public Element toXML() {
        Element element = new Element("Reaction");
        if(getThermocycle() != null) {
            element.addContent(new Element("thermocycle").setText(""+getThermocycle().getId()));
        }
        if(fimsSample != null) {
            element.addContent(XMLSerializer.classToXML("fimsSample", fimsSample));
        }
        if(getId() >= 0) {
            element.addContent(new Element("id").setText(""+getId()));
        }
        if(getPlateId() >= 0) {
            element.addContent(new Element("plate").setText(""+ getPlateId()));
        }
        if(plateName != null && plateName.length() > 0) {
            element.addContent(new Element("plateName").setText(plateName));
        }
        if(extractionBarcode != null) {
            element.addContent(new Element("extractionBarcode").setText(extractionBarcode));
        }
        if(isError) {
            element.addContent(new Element("isError").setText("true"));
        }
        synchronized (BiocodeService.XMLDateFormat) {
            element.addContent(new Element("created").setText(BiocodeService.XMLDateFormat.format(getCreated())));
        }
        element.addContent(new Element("position").setText(""+getPosition()));
        if(locationString != null) {
            element.addContent(new Element("wellLabel").setText(locationString));
        }
        if(gelImage != null) {
            element.addContent(XMLSerializer.classToXML("gelimage", gelImage));
        }
        if(displayableFields != null && displayableFields.size() > 0) {
            for(DocumentField df : displayableFields) {
                element.addContent(XMLSerializer.classToXML("displayableField", df));
            }
        }
        if(workflow != null) {
            Element workflowElement = XMLSerializer.classToXML("workflow", workflow);
            element.addContent(workflowElement);
        }
        if(backgroundColorer != null) {
            Element backgroundColorerElement = XMLSerializer.classToXML("backgroundColorer", backgroundColorer);
            element.addContent(backgroundColorerElement);
        }
        element.addContent(getOptions().valuesToXML("options"));
        return element;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        String thermoCycleId = element.getChildText("thermocycle");
        setPosition(Integer.parseInt(element.getChildText("position")));
        Element locationStringElement = element.getChild("wellLabel");
        Element plateNameElement = element.getChild("plateName");
        if(element.getChild("id") != null) {
            setId(Integer.parseInt(element.getChildText("id")));
        }
        if(element.getChild("plate") != null) {
            setPlateId(Integer.parseInt(element.getChildText("plate")));
        }
        if(locationStringElement != null) {
            locationString = locationStringElement.getText();
        }
        extractionBarcode = element.getChildText("extractionBarcode");
        if(plateNameElement != null) {
            plateName = plateNameElement.getText();
        }
        isError = element.getChild("isError") != null;
        Element fimsElement = element.getChild("fimsSample");
        if(fimsElement != null) {
            fimsSample = XMLSerializer.classFromXML(fimsElement, FimsSample.class);
        }
        Element gelImageElement = element.getChild("gelimage");
        if(gelImageElement != null) {
            gelImage = XMLSerializer.classFromXML(gelImageElement, GelImage.class);
        }
        try {
            synchronized (BiocodeService.XMLDateFormat) {
                setCreated(BiocodeService.XMLDateFormat.parse(element.getChildText("created")));
            }
        } catch (ParseException e) {
            assert false : "Could not read the date "+element.getChildText("created");
            setCreated(new Date());
        }
        if(thermoCycleId != null) {
            int tcId = Integer.parseInt(thermoCycleId);
            for(Thermocycle tc : BiocodeService.getInstance().getPCRThermocycles()) {
                if(tc.getId() == tcId) {
                    setThermocycle(tc);
                    break;
                }
            }
            if(thermocycle == null) {
                for(Thermocycle tc : BiocodeService.getInstance().getCycleSequencingThermocycles()) {
                    if(tc.getId() == tcId) {
                        setThermocycle(tc);
                        break;
                    }
                }
            }
        }
        Element workflowElement = element.getChild("workflow");
        if(workflowElement != null) {
            workflow = XMLSerializer.classFromXML(workflowElement, Workflow.class);
        }
        Element backgroundColorerElement = element.getChild("backgroundColorer");
        if(backgroundColorerElement != null) {
            backgroundColorer = XMLSerializer.classFromXML(backgroundColorerElement, BackgroundColorer.class);
        }
        displayableFields = new ArrayList<DocumentField>();
        for(Element e : element.getChildren("displayableField")) {
            displayableFields.add(XMLSerializer.classFromXML(e, DocumentField.class));
        }
        Options options = getOptions();
        options.valuesFromXML(element.getChild("options"));
        //setOptions(XMLSerializer.classFromXML(element.getChild("options"), ReactionOptions.class));
    }

    public abstract Color _getBackgroundColor();

    public abstract String areReactionsValid(List<T> reactions, JComponent dialogParent, boolean showDialogs);

    public Dimension getPreferredSize() {
        int y = PADDING+3;
        int x = 10;
        List<DocumentField> fieldsToDisplay = getFieldsToDisplay();

        if(fieldWidthCache == null || fieldWidthCache.length != fieldsToDisplay.size()) {
            initFieldWidthCache();
        }
        for (int i = 0; i < fieldsToDisplay.size(); i++) {
            DocumentField field = getFieldsToDisplay().get(i);
            if(field.equals(GEL_IMAGE_DOCUMENT_FIELD)) {
                if(gelImage != null) {
                    y += gelImage.getImage().getHeight(imageObserver)+5;
                }
                continue;
            }
            String value = getDisplayableValue(field);
            if (value.length() == 0) {
                continue;
            }
            y += charHeight + LINE_SPACING;
            if(fieldWidthCache == null) {
                assert false;
            }
            else {
                x = Math.max(x, fieldWidthCache[i]);
            }
        }
        x += PADDING;
        return new Dimension(Math.max(50,x), Math.max(30,y));
    }

    private void initFieldWidthCache() {
        assert EventQueue.isDispatchThread();
        List<DocumentField> fieldList = new ArrayList<DocumentField>(getFieldsToDisplay());
        if(fieldList.size() == 0) {
            fieldList.add(new DocumentField("a", "", "testField", String.class, false, false));
        }
        fieldWidthCache = new int[fieldList.size()];
        for(int i=0; i < fieldList.size(); i++) {
            String value = getDisplayableValue(fieldList.get(i));
            if(value.length() == 0) {
                continue;
            }
            Font font = i == 0 ? firstLabelFont : labelFont;
            TextLayout tl = new TextLayout(value, font, fontRenderContext);
            Rectangle2D layoutBounds = tl.getBounds();
            if(layoutBounds != null) {
                fieldWidthCache[i] = (int) layoutBounds.getWidth();
            }
            else {
                fieldWidthCache[i] = 60;
                assert false : "The text knows no bounds!";
            }
            charHeight = (int)tl.getBounds().getHeight();
        }
    }

    public String getDisplayableValue(DocumentField field) {
        Object value = getFieldValue(field.getCode());
        if(value == null) {
            value = "";
        }
        if(value instanceof String) {
            return (String)value;
        }
        Options.Option option = getOptions().getOption(field.getCode());
        if(option instanceof Options.IntegerOption) {
            value = value + ((Options.IntegerOption)option).getUnits();
        }
        if(option instanceof Options.DoubleOption) {
            value = value + ((Options.DoubleOption)option).getUnits();
        }
        return field.getName()+": "+value;
    }


    public void paint(Graphics2D g, boolean colorTheBackground, boolean enabled){
        fontRenderContext = g.getFontRenderContext();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(colorTheBackground && enabled ? isSelected() ? getBackgroundColor().darker() : getBackgroundColor() : Color.white);
        g.fillRect(location.x, location.y, location.width, location.height);

        if(fieldWidthCache == null) {
            initFieldWidthCache();
        }

        if(locationString != null && locationString.length() > 0) {
            g.setColor(new Color(0,0,0,128));
            g.setFont(firstLabelFont.deriveFont(Font.PLAIN));
            g.drawString(locationString, location.x+2, location.y+charHeight + 2);
        }

        g.setColor(enabled ? Color.black : Color.gray);

        int y = location.y + charHeight;
        y += (location.height - getPreferredSize().height + PADDING)/2;
        for (int i = 0; i < getFieldsToDisplay().size(); i++) {
            if(getFieldsToDisplay().get(i).equals(GEL_IMAGE_DOCUMENT_FIELD)) {
                if(gelImage != null) {
                    int x = (location.width-gelImage.getImage().getWidth(imageObserver))/2;
                    g.drawImage(gelImage.getImage(),location.x+x,y, imageObserver);
                    y += gelImage.getImage().getHeight(imageObserver)+5;
                }
                continue;
            }
            g.setFont(i == 0 ? firstLabelFont : labelFont);

            DocumentField field = getFieldsToDisplay().get(i);
            String value = getDisplayableValue(field);
            if (value.length() == 0) {
                continue;
            }
            int textHeight = charHeight;
            int textWidth = fieldWidthCache != null ? fieldWidthCache[i] : location.width;
            g.drawString(value, location.x + (location.width - textWidth) / 2, y + textHeight);
            y += textHeight + LINE_SPACING;
        }

        g.setColor(Color.black);
    }

    public boolean isEmpty(){
        Options options = getOptions();
        for(Options.Option option : options.getOptions()) {
            if(!option.getValue().equals(option.getDefaultValue()) && !(option instanceof Options.LabelOption)) {
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

    public int getPlateId() {
        return plateId;
    }

    public void setPlateId(int plate) {
        this.plateId = plate;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public void setWorkflow(Workflow workflow) {
        this.workflow = workflow;
        getOptions().setValue("workflowId", workflow != null ? workflow.getName() : "");
        if(getFimsSample() != null && workflow != null) {
            workflow.setFimsSample(getFimsSample());
        }
        //getOptions().setValue("locus", workflow != null ? workflow.getLocus() : ""); //lets not clear this - we want to be able to create new workflows for this locus...
    }

    public Date getDate() {
        return date;
    }

    public static void saveReactions(Reaction[] reactions, Type type, LIMSConnection connection, BiocodeService.BlockingProgress progress) throws IllegalStateException, SQLException {
        switch(type) {
            case Extraction:
                String insertSQL;
                String updateSQL;
                insertSQL  = "INSERT INTO extraction (method, volume, dilution, parent, sampleId, extractionId, extractionBarcode, plate, location, notes, previousPlate, previousWell, date, technician, concentrationStored, concentration, gelimage, control) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                updateSQL  = "UPDATE extraction SET method=?, volume=?, dilution=?, parent=?, sampleId=?, extractionId=?, extractionBarcode=?, plate=?, location=?, notes=?, previousPlate=?, previousWell=?, date=?, technician=?, concentrationStored=?, concentration=?, gelImage=?, control=? WHERE id=?";
                PreparedStatement insertStatement = connection.createStatement(insertSQL);
                PreparedStatement updateStatement = connection.createStatement(updateSQL);
                int insertCount = 0;
                int updateCount = 0;
                for (int i = 0; i < reactions.length; i++) {
                    Reaction reaction = reactions[i];
                    if(progress != null) {
                        progress.setMessage("Saving reaction "+(i+1)+" of "+reactions.length);
                    }
                    if (!reaction.isEmpty() && reaction.plateId >= 0) {
                        PreparedStatement statement;
                        if(reaction.getId() >= 0) { //the reaction is already in the database
                            statement = updateStatement;
                            updateCount++;
                            statement.setInt(19, reaction.getId());
                        }
                        else {
                            statement = insertStatement;
                            insertCount++;
                        }
                        ReactionOptions options = reaction.getOptions();
                        statement.setString(1, options.getValueAsString("extractionMethod"));
                        statement.setInt(2, (Integer) options.getValue("volume"));
                        statement.setInt(3, (Integer) options.getValue("dilution"));
                        statement.setString(4, options.getValueAsString("parentExtraction"));
                        statement.setString(5, options.getValueAsString("sampleId"));
                        statement.setString(6, options.getValueAsString("extractionId"));
                        statement.setString(7, options.getValueAsString("extractionBarcode"));
                        statement.setInt(8, reaction.getPlateId());
                        statement.setInt(9, reaction.getPosition());
                        statement.setString(10, options.getValueAsString("notes"));
                        statement.setString(11, options.getValueAsString("previousPlate"));
                        statement.setString(12, options.getValueAsString("previousWell"));
                        statement.setDate(13, new java.sql.Date(((Date)options.getValue("date")).getTime()));
                        statement.setString(14, options.getValueAsString("technician"));
                        statement.setInt(15, "yes".equals(options.getValueAsString("concentrationStored")) ? 1 : 0);
                        statement.setDouble(16, (Double)options.getValue("concentration"));
                        GelImage image = reaction.getGelImage();
                        statement.setBytes(17, image != null ? image.getImageBytes() : null);
                        statement.setString(18, options.getValueAsString("control"));
                        statement.addBatch();
                        //statement.execute();
                    }
                    try {
                        if(insertCount > 0) {
                            insertStatement.executeBatch();
                        }
                        if(updateCount > 0) {
                            updateStatement.executeBatch();
                        }
                    }
                    catch(SQLException e) {
                        if(!e.getMessage().toLowerCase().contains("not in batch")) { //suppress if the SQL driver doesn't support batch mode...
                            throw e;
                        }
                    }
                }
                insertStatement.close();
                updateStatement.close();
                break;
            case PCR:
                insertSQL = "INSERT INTO pcr (prName, prSequence, workflow, plate, location, cocktail, progress, thermocycle, cleanupPerformed, cleanupMethod, extractionId, notes, revPrName, revPrSequence, date, technician, gelimage) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                updateSQL = "UPDATE pcr SET prName=?, prSequence=?, workflow=?, plate=?, location=?, cocktail=?, progress=?, thermocycle=?, cleanupPerformed=?, cleanupMethod=?, extractionId=?, notes=?, revPrName=?, revPrSequence=?, date=?, technician=?, gelimage=? WHERE id=?";
                insertStatement = connection.createStatement(insertSQL);
                updateStatement = connection.createStatement(updateSQL);
                int saveCount = 0;
                for (int i = 0; i < reactions.length; i++) {
                    Reaction reaction = reactions[i];
                    if(progress != null) {
                        progress.setMessage("Saving reaction "+(i+1)+" of "+reactions.length);
                    }
                    if (!reaction.isEmpty() && reaction.plateId >= 0) {
                        PreparedStatement statement;
                        if(reaction.getId() >= 0) { //the reaction is already in the database
                            statement = updateStatement;
                            statement.setInt(18, reaction.getId());
                        }
                        else {
                            statement = insertStatement;
                        }

                        ReactionOptions options = reaction.getOptions();
                        Options.Option option = options.getOption(PCROptions.PRIMER_OPTION_ID);
                        if(!(option instanceof DocumentSelectionOption)) {
                            throw new SQLException("Could not save reactions - expected primer type "+DocumentSelectionOption.class.getCanonicalName()+" but found a "+option.getClass().getCanonicalName());
                        }
                        List<AnnotatedPluginDocument> primerOptionValue = ((DocumentSelectionOption)option).getDocuments();
                        if(primerOptionValue.size() == 0) {
                            statement.setString(1, "None");
                            statement.setString(2, "");
                        }
                        else {
                            AnnotatedPluginDocument selectedDoc = primerOptionValue.get(0);
                            NucleotideSequenceDocument sequence = (NucleotideSequenceDocument)selectedDoc.getDocumentOrThrow(SQLException.class);
                            statement.setString(1, selectedDoc.getName());
                            statement.setString(2, sequence.getSequenceString());
                        }
                        //statement.setInt(3, (Integer)options.getValue("prAmount"));

                        Options.Option option2 = options.getOption(PCROptions.PRIMER_REVERSE_OPTION_ID);
                        if(!(option2 instanceof DocumentSelectionOption)) {
                            throw new SQLException("Could not save reactions - expected primer type "+DocumentSelectionOption.class.getCanonicalName()+" but found a "+option2.getClass().getCanonicalName());
                        }
                        List<AnnotatedPluginDocument> primerOptionValue2 = ((DocumentSelectionOption)option2).getDocuments();
                        if(primerOptionValue2.size() == 0) {
                            statement.setString(13, "None");
                            statement.setString(14, "");
                        }
                        else {
                            AnnotatedPluginDocument selectedDoc = primerOptionValue2.get(0);
                            NucleotideSequenceDocument sequence = (NucleotideSequenceDocument)selectedDoc.getDocumentOrThrow(SQLException.class);
                            statement.setString(13, selectedDoc.getName());
                            statement.setString(14, sequence.getSequenceString());
                        }
                        statement.setDate(15, new java.sql.Date(((Date)options.getValue("date")).getTime()));
                        statement.setString(16, options.getValueAsString("technician"));
//                        statement.setInt(14, (Integer)options.getValue("revPrAmount"));
//                        if (reaction.getWorkflow() == null || reaction.getWorkflow().getId() < 0) {
//                            throw new SQLException("The reaction " + reaction.getId() + " does not have a workflow set.");
//                        }
                        //statement.setInt(4, reaction.getWorkflow() != null ? reaction.getWorkflow().getId() : 0);
                        if(reaction.getWorkflow() != null) {
                            statement.setInt(3, reaction.getWorkflow().getId());
                        }
                        else {
                            statement.setObject(3, null);
                        }
                        statement.setInt(4, reaction.getPlateId());
                        statement.setInt(5, reaction.getPosition());
                        int cocktailId;
                        Options.OptionValue cocktailValue = (Options.OptionValue) options.getValue("cocktail");
                        try {
                            cocktailId = Integer.parseInt(cocktailValue.getName());
                        }
                        catch(NumberFormatException ex) {
                            throw new SQLException("The reaction " + reaction.getId() + " does not have a valid cocktail ("+ cocktailValue.getLabel()+", "+cocktailValue.getName()+").");
                        }
                        if(cocktailId < 0) {
                            throw new SQLException("The reaction " + reaction.getPosition() + " does not have a valid cocktail ("+cocktailValue.getName()+").");
                        }
                        statement.setInt(6, cocktailId);
                        statement.setString(7, ((Options.OptionValue)options.getValue(ReactionOptions.RUN_STATUS)).getLabel());
                        if(reaction.getThermocycle() != null) {
                            statement.setInt(8, reaction.getThermocycle().getId());
                        }
                        else {
                            statement.setInt(8, -1);
                        }
                        statement.setInt(9, ((Options.OptionValue)options.getValue("cleanupPerformed")).getName().equals("true") ? 1 : 0);
                        statement.setString(10, options.getValueAsString("cleanupMethod"));
                        statement.setString(11, reaction.getExtractionId());
                        System.out.println(reaction.getExtractionId());
                        statement.setString(12, options.getValueAsString("notes"));
                        GelImage image = reaction.getGelImage();
                        statement.setBytes(17, image != null ? image.getImageBytes() : null);
                        statement.execute();
                        saveCount++;
                    }
                }
                insertStatement.close();
                updateStatement.close();
                System.out.println(saveCount+" reactions saved...");
                break;
            case CycleSequencing:
                insertSQL = "INSERT INTO cyclesequencing (primerName, primerSequence, direction, workflow, plate, location, cocktail, progress, thermocycle, cleanupPerformed, cleanupMethod, extractionId, notes, date, technician, gelimage) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                updateSQL = "UPDATE cyclesequencing SET primerName=?, primerSequence=?, direction=?, workflow=?, plate=?, location=?, cocktail=?, progress=?, thermocycle=?, cleanupPerformed=?, cleanupMethod=?, extractionId=?, notes=?, date=?, technician=?, gelimage=? WHERE id=?";
                String clearTracesSQL = "DELETE FROM traces WHERE id=?";
                String insertTracesSQL = "INSERT INTO traces(reaction, name, data) values(?, ?, ?)";

                insertStatement = connection.createStatement(insertSQL);
                updateStatement = connection.createStatement(updateSQL);
                PreparedStatement clearTracesStatement = connection.createStatement(clearTracesSQL);
                PreparedStatement insertTracesStatement = connection.createStatement(insertTracesSQL);
                for (int i = 0; i < reactions.length; i++) {
                    Reaction reaction = reactions[i];
                    if(progress != null) {
                        progress.setMessage("Saving reaction "+(i+1)+" of "+reactions.length);
                    }
                    if (!reaction.isEmpty() && reaction.plateId >= 0) {

                        PreparedStatement statement;
                        if(reaction.getId() >= 0) { //the reaction is already in the database
                            statement = updateStatement;
                            statement.setInt(17, reaction.getId());
                        }
                        else {
                            statement = insertStatement;
                        }

                        ReactionOptions options = reaction.getOptions();
                        Options.Option option = options.getOption(PCROptions.PRIMER_OPTION_ID);
                        if(!(option instanceof DocumentSelectionOption)) {
                            throw new SQLException("Could not save reactions - expected primer type "+DocumentSelectionOption.class.getCanonicalName()+" but found a "+option.getClass().getCanonicalName());
                        }
                        List<AnnotatedPluginDocument> primerOptionValue = ((DocumentSelectionOption)option).getDocuments();
                        if(primerOptionValue.size() == 0) {
                            statement.setString(1, "None");
                            statement.setString(2, "");
                        }
                        else {
                            AnnotatedPluginDocument selectedDoc = primerOptionValue.get(0);
                            NucleotideSequenceDocument sequence = (NucleotideSequenceDocument)selectedDoc.getDocumentOrThrow(SQLException.class);
                            statement.setString(1, selectedDoc.getName());
                            statement.setString(2, sequence.getSequenceString());
                        }
                        statement.setString(3, options.getValueAsString("direction"));
                        //statement.setInt(3, (Integer)options.getValue("prAmount"));
                        if(reaction.getWorkflow() != null) {
                            statement.setInt(4, reaction.getWorkflow().getId());
                        }
                        else {
                            statement.setObject(4, null);
                        }
                        statement.setInt(5, reaction.getPlateId());
                        statement.setInt(6, reaction.getPosition());
                        int cocktailId;
                        Options.OptionValue cocktailValue = (Options.OptionValue) options.getValue("cocktail");
                        try {
                            cocktailId = Integer.parseInt(cocktailValue.getName());
                        }
                        catch(NumberFormatException ex) {
                            throw new SQLException("The reaction " + reaction.getLocationString() + " does not have a valid cocktail ("+ cocktailValue.getLabel()+", "+cocktailValue.getName()+").");
                        }
                        if(cocktailId < 0) {
                            throw new SQLException("The reaction " + reaction.getLocationString() + " does not have a valid cocktail ("+cocktailValue.getName()+").");
                        }
                        statement.setInt(7, cocktailId);
                        statement.setString(8, ((Options.OptionValue)options.getValue(ReactionOptions.RUN_STATUS)).getLabel());
                        if(reaction.getThermocycle() != null) {
                            statement.setInt(9, reaction.getThermocycle().getId());
                        }
                        else {
                            statement.setInt(9, -1);
                        }
                        statement.setInt(10, ((Options.OptionValue)options.getValue("cleanupPerformed")).getName().equals("true") ? 1 : 0);
                        statement.setString(11, options.getValueAsString("cleanupMethod"));
                        statement.setString(12, reaction.getExtractionId());
                        statement.setString(13, options.getValueAsString("notes"));
                        statement.setDate(14, new java.sql.Date(((Date)options.getValue("date")).getTime()));
                        statement.setString(15, options.getValueAsString("technician"));
                        GelImage image = reaction.getGelImage();
                        statement.setBytes(16, image != null ? image.getImageBytes() : null);

//                        List<NucleotideSequenceDocument> sequences = ((CycleSequencingOptions)options).getTraces();
//                        String sequenceString = "";
//                        if(sequences != null && sequences.size() > 0) {
//                            DefaultSequenceListDocument sequenceList = DefaultSequenceListDocument.forNucleotideSequences(sequences);
//                            Element element = XMLSerializer.classToXML("sequences", sequenceList);
//                            XMLOutputter out = new XMLOutputter(Format.getCompactFormat());
//                            StringWriter writer = new StringWriter();
//                            try {
//                                out.output(element, writer);
//                                sequenceString = writer.toString();
//                            } catch (IOException e) {
//                                throw new SQLException("Could not write the sequences to the database: "+e.getMessage());
//                            }
//                        }
//
//                        statement.setString(14, sequenceString);
                        statement.execute();
                        if(((CycleSequencingReaction)reaction).getTraces() != null) {
                            int reactionId = reaction.getId();
                            for(Integer traceId : ((CycleSequencingReaction)reaction).getTracesToRemoveOnSave()) {
                                if(!BiocodeService.getInstance().deleteAllowed("traces")) {
                                    throw new SQLException("It appears that you do not have permission to delete traces.  Please contact your System Administrator for assistance");
                                }
                                clearTracesStatement.setInt(1, traceId);
                                clearTracesStatement.execute();
                            }
                            ((CycleSequencingReaction)reaction).clearTracesToRemoveOnSave();
                            if(reactionId < 0) {
                                reactionId = connection.getLastInsertId();
                            }

                            List<Trace> traces = ((CycleSequencingReaction)reaction).getTraces();
                            if(traces != null) {
                                for(Trace trace : traces) {
                                    if(trace.getId() >= 0) {
                                        continue; //already added these...
                                    }
                                    ReactionUtilities.MemoryFile file = trace.getFile();
                                    if(file != null) {
                                        insertTracesStatement.setInt(1, reactionId);
                                        insertTracesStatement.setString(2, file.getName());
                                        insertTracesStatement.setBytes(3, file.getData());
                                        insertTracesStatement.execute();
                                        trace.setId(connection.getLastInsertId());
                                    }
                                }
                            }
                        }
                    }
                }
                insertStatement.close();
                updateStatement.close();
                insertTracesStatement.close();
                break;
        }
    }

    public static class BackgroundColorer implements XMLSerializable{
        private DocumentField documentField;
        private Map<String, Color> color;

        public BackgroundColorer(DocumentField documentField, Map<String, Color> color) {
            this.documentField = documentField;
            this.color = color;
        }

        public BackgroundColorer(Element e) throws XMLSerializationException{
            fromXML(e);
        }

        public DocumentField getDocumentField() {
            return documentField;
        }

        public Map<String, Color> getColorMap() {
            return color;
        }

        public Color getColor(Reaction reaction) {
            if(documentField != null) {
                return getColor(reaction.getFieldValue(documentField.getCode()));
            }
            return Color.white;
        }

        public Color getColor(Object value) {
            if(value == null) {
                return Color.white;
            }
            Color valueColor = color.get(value.toString());
            return valueColor != null ? valueColor : Color.white;
        }

        public static Color getRandomColor(Object o) {
            Random r = new Random(o.hashCode());
            r = new Random(r.nextInt(Integer.MAX_VALUE));
            ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
            float[] rgbValues = cs.fromCIEXYZ(new float[]{r.nextFloat(), r.nextFloat(),r.nextFloat()});
            return new Color(rgbValues[0], rgbValues[1], rgbValues[2]);
        }

        public Element toXML() {
            Element root = new Element("backgroundColorer");
            if(documentField != null) {
                Element documentFieldElement = XMLSerializer.classToXML("documentField", documentField);
                root.addContent(documentFieldElement);
            }
            Element mapElement = new Element("valueMap");
            for(Map.Entry<String, Color> e : color.entrySet()) {
                Element entryElement = new Element("entry");
                Element keyElement = new Element("key").setText(e.getKey());
                entryElement.addContent(keyElement);
                Element valueElement = new Element("value");
                valueElement.setText(colorToString(e.getValue()));
                entryElement.addContent(valueElement);
                mapElement.addContent(entryElement);
            }
            root.addContent(mapElement);
            return root;
        }

        private static String colorToString(Color c) {
            return c.getRed()+", "+c.getGreen()+", "+c.getBlue();
        }

        private static Color colorFromString(String s) {
            String[] channels = s.split(",");
            return new Color(Integer.parseInt(channels[0].trim()), Integer.parseInt(channels[1].trim()), Integer.parseInt(channels[2].trim()));
        }

        public void fromXML(Element element) throws XMLSerializationException {
            Element documentFieldElement = element.getChild("documentField");
            if(documentFieldElement != null) {
                documentField = XMLSerializer.classFromXML(documentFieldElement, DocumentField.class);
            }
            Element mapElement = element.getChild("valueMap");
            color = new HashMap<String, Color>();
            for(Element e : mapElement.getChildren("entry")) {
                String key = e.getChildText("key");
                Color value = colorFromString(e.getChildText("value"));
                color.put(key, value);
            }
        }

        @SuppressWarnings({"RedundantIfStatement"})
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BackgroundColorer that = (BackgroundColorer) o;

            if (!color.equals(that.color)) return false;
            if (documentField != null ? !documentField.getCode().equals(that.documentField.getCode()) : that.documentField != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = documentField != null ? documentField.hashCode() : 0;
            result = 31 * result + color.hashCode();
            return result;
        }
    }
}
