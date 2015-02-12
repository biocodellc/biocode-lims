package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ButtonOption;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.rest.client.ServerLimsConnection;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ImageObserver;
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
    protected String extractionBarcode;
    protected Integer databaseIdOfExtraction = null;
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
    public static final DocumentField EXTRACTION_PLATE_NAME_DOCUMENT_FIELD = new DocumentField("extraction_plate", "", "_plateName_", String.class, false, false);
    public static final DocumentField EXTRACTION_WELL_DOCUMENT_FIELD = new DocumentField("extraction_Well", "", "_plateWell_", String.class, false, false);

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
        this.date = new Date(date.getTime());
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
        Collections.sort(displayableFields, new Comparator<DocumentField>() {
            @Override
            public int compare(DocumentField o1, DocumentField o2) {
                return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
            }
        });
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
        if(getType() != Type.Extraction) {
            // Extraction reactions have the barcode as an editable option.  For the other types we'll just make it a displayable field.
            fields.add(LIMSConnection.EXTRACTION_BARCODE_FIELD);
        }
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
        fields.add(LIMSConnection.EXTRACTION_BCID_FIELD);
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
            DisplayFieldsTemplate displayFieldsTemplate = BiocodeService.getInstance().getDefaultDisplayedFieldsTemplate(getType());
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
        DisplayFieldsTemplate displayFieldsTemplate = BiocodeService.getInstance().getDefaultDisplayedFieldsTemplate(getType());
        if(displayFieldsTemplate != null) {
            return displayFieldsTemplate.getDisplayedFields();
        }
        return Collections.emptyList();
    }

    public void setFieldsToDisplay(List<DocumentField> fields) {
        this.displayableFields = fields;
        invalidateFieldWidthCache();
    }

    /**
     * Get the value of a particular field.  Similar to {@link com.biomatters.geneious.publicapi.documents.PluginDocument#getFieldValue(String)}
     *
     * @param fieldCode field code. This should be the
     *      {@link com.biomatters.geneious.publicapi.documents.DocumentField#getCode() code} of one of the fields
     *      returned from {@link #getDisplayableFields()}.
     * @return value for a field or null if this document does not have a field with the given field code.
     *          The class of the returned value must be the {@link DocumentField#getValueType()} (or a subclass) of the corresponding DocumentField returned from {@link #getDisplayableFields()}.
     */
    public Object getFieldValue(String fieldCode) {
        if(LIMSConnection.EXTRACTION_BARCODE_FIELD.getCode().equals(fieldCode)) {
            return getExtractionBarcode();
        } else if (LIMSConnection.EXTRACTION_BCID_FIELD.getCode().equals(fieldCode)) {
            LIMSConnection limsConnection;
            try {
                limsConnection = BiocodeService.getInstance().getActiveLIMSConnection();
                if (!(limsConnection instanceof ServerLimsConnection)) {
                    return "";
                }
                String extractionBCIDRoot = ((ServerLimsConnection) limsConnection).getBCIDRoots().get("extraction");
                Integer extractionId = getDatabaseIdOfExtraction();
                if (extractionBCIDRoot == null || extractionBCIDRoot.isEmpty() || extractionId == null || extractionId == -1) {
                    return "";
                }
                return extractionBCIDRoot + extractionId;
            } catch (DatabaseServiceException e) {
                return "";
            }
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

    /**
     * Get the database ID of the extraction associated with this reaction.  For an extraction this would be it's ID.
     * Not displayed to the user.  The user identifies extractions by a more human friendly identifier that can be
     * renamed.
     * ie MBIO3589.1.2 rather than it's database ID.
     * <br/><br/>
     * <strong>Note</strong>: The value returned from this method is used to create the extraction BCID defined by
     * {@link com.biomatters.plugins.biocode.labbench.lims.LIMSConnection#EXTRACTION_BCID_FIELD}
     *
     * @return The ID of this extraction in the database.
     */
    protected Integer getDatabaseIdOfExtraction() {
        return databaseIdOfExtraction;
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

    private static final String EXTRACTION_DATABASE_ID = "databaseIdOfExtraction";

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
        if(databaseIdOfExtraction != null) {
            element.addContent(new Element(EXTRACTION_DATABASE_ID).setText(String.valueOf(databaseIdOfExtraction)));
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
        String databaseExtractionIdString = element.getChildText(EXTRACTION_DATABASE_ID);
        if(databaseExtractionIdString != null) {
            try {
                databaseIdOfExtraction = Integer.parseInt(databaseExtractionIdString);
            } catch (NumberFormatException e) {
                throw new XMLSerializationException("Bad value for " + EXTRACTION_DATABASE_ID + ", expected an integer but was: " + databaseExtractionIdString, e);
            }
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
        List<Element> displayableFieldElements = element.getChildren("displayableField");
        if(!displayableFieldElements.isEmpty()) {
            displayableFields = new ArrayList<DocumentField>();
            for(Element e : displayableFieldElements) {
                displayableFields.add(XMLSerializer.classFromXML(e, DocumentField.class));
            }
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

    /**
     * @param field The field to retrieve the value of
     * @return A formatted String interpretation of the value returned from {@link #getFieldValue(String)} intended for users.
     */
    final String getDisplayableValue(DocumentField field) {
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
