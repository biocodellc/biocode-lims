package com.biomatters.plugins.biocode.labbench.plates;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import org.jdom.Element;

import java.awt.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Date;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 10/06/2009 11:38:24 AM
 */
public class Plate implements XMLSerializable {
    private int id=-1;
    private int rows;
    private int cols;
    private Date lastModified = new Date();
    private String name = "";
    private Reaction[] reactions;
    private Reaction.Type type;
    private Size plateSize;
    private Thermocycle thermocycle;
    private List<GelImage> images;
    private boolean isDeleted = false;
    private int thermocycleId = -1;

    public enum Size {
        w1("1", 1),
        w2("2", 2),
        w3("3", 3),
        w4("4", 4),
        w5("5", 5),
        w6("6", 6),
        w7("7", 7),
        w8("8", 8),
        w9("9", 9),
        w10("10", 10),
        w11("11", 11),
        w12("12", 12),
        w13("13", 13),
        w14("14", 14),
        w15("15", 15),
        w16("16", 16),
        w17("17", 17),
        w18("18", 18),
        w19("19", 19),
        w20("20", 20),
        w21("21", 21),
        w22("22", 22),
        w23("23", 23),
        w24("24", 24),
        w25("25", 25),
        w26("26", 26),
        w32("32", 32),
        w40("40", 40),
        w48("48", 48),
        w96("96", 96),
        w384("384", 384);
        private String niceName;
        private int size;

        private Size(String s, int size) {
            this.niceName = s;
            this.size = size;
        }

        @Override
        public String toString() {
            return niceName;
        }

        public int numberOfReactions() {
            return size;
        }
    }

    public Plate(Element e) throws XMLSerializationException{
        fromXML(e);
    }

    public Plate(ResultSet resultSet) throws SQLException{
        String typeString = resultSet.getString("plate.type");
        Reaction.Type type = Reaction.Type.valueOf(typeString);
        int size = resultSet.getInt("plate.size");
        this.id = resultSet.getInt("plate.id");
        plateSize = getSizeEnum(size);
        name = resultSet.getString("plate.name");
        lastModified = resultSet.getDate("plate.date");
        if(plateSize != null) {
            init(plateSize, type, false);
        }
        else {
            init(size, type, false);
        }
        int thermocycleId = resultSet.getInt("plate.thermocycle");
        setThermocycleFromId(thermocycleId);
    }

    private void setThermocycleFromId(int thermocycleId) {
        this.thermocycleId = thermocycleId;
        if(thermocycleId >= 0) {
            for(Thermocycle tc : BiocodeService.getInstance().getPCRThermocycles()) {
                if(tc.getId() == thermocycleId) {
                    setThermocycle(tc);
                    break;
                }
            }
            for(Thermocycle tc : BiocodeService.getInstance().getCycleSequencingThermocycles()) {
                if(tc.getId() == thermocycleId) {
                    setThermocycle(tc);
                    break;
                }
            }
        }
    }

    public static Size getSizeEnum(int size) {
        Size sizeToReturn = null;

        for (Size sizeValue : Size.values()) {
            if (sizeValue.size == size) {
                sizeToReturn = sizeValue;
                break;
            }
        }

        return sizeToReturn;
    }

    public Plate(int numberOfWells, Reaction.Type type) {
        init(numberOfWells, type, true);
    }

    private void init(int numberOfWells, Reaction.Type type, boolean initializeReactions) {
        if (numberOfWells % 8 == 0) {
            init(8, numberOfWells / 8, type, initializeReactions);
        } else {
            init(1, numberOfWells, type, initializeReactions);
        }
    }


    public Plate(Plate.Size size, Reaction.Type type) {
        this.type = type;
        this.plateSize = size;
        init(size, type, true);
    }

    public Plate(Plate.Size size, Reaction.Type type, Date lastModified) {
        this.type = type;
        this.plateSize = size;
        this.lastModified = lastModified;
        init(size, type, true);
    }

    private void init(Size size, Reaction.Type type, boolean initialiseReactions) {
        switch(size) {
            case w8:
                init(8, 1, type, initialiseReactions);
                break;
            case w16:
                init(8, 2, type, initialiseReactions);
                break;
            case w24:
                init(8, 3, type, initialiseReactions);
                break;
            case w32:
                init(8, 4, type, initialiseReactions);
                break;
            case w40:
                init(8, 5, type, initialiseReactions);
                break;
            case w48 :
                init(8, 6, type, initialiseReactions);
                break;
            case w96 :
                init(8, 12, type, initialiseReactions);
                break;
            case w384 :
                init(16, 24, type, initialiseReactions);
        }
    }

    public List<GelImage> getImages() {
        return images != null ? images : Collections.EMPTY_LIST;
    }

    public boolean gelImagesHaveBeenDownloaded() {
        return images != null;
    }

    public void setImages(List<GelImage> images) {
        this.images = images;
        for(GelImage image : images) {
            image.setPlate(id);
        }
    }

    public Reaction.Type getReactionType() {
        return type;
    }

    public Size getPlateSize() {
        return plateSize;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public Reaction[] getReactions() {
        return reactions;
    }

    public void setReactions(Reaction[] newReactions) {
        if(newReactions.length != reactions.length) {
            throw new IllegalArgumentException("You cannot change the size of an existing plate!  Expected "+reactions.length+" reactions, but you set "+newReactions.length);
        }
        for(Reaction r : newReactions) {
            if(r.getType() != getReactionType()) {
                throw new IllegalArgumentException("You cannot change the type of an existing plate!  Expected "+getReactionType()+" but you set at least one reaction that is "+r.getType());
            }
        }
        reactions = newReactions;
    }


    public Thermocycle getThermocycle() {
        return thermocycle;
    }

    public int getThermocycleId() {
        return thermocycleId;
    }

    public void setThermocycle(Thermocycle thermocycle) {
        this.thermocycle = thermocycle;
        this.thermocycleId = thermocycle != null ? thermocycle.getId() : -1;
        for(Reaction r : getReactions()) {
            if(r != null) {
                r.setThermocycle(thermocycle);
            }
        }
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    private void init(int rows, int cols, Reaction.Type type, boolean initialiseReactions) {
        this.rows = rows;
        this.cols = cols;
        this.type = type;

        //images = new ArrayList<GelImage>();

        reactions = new Reaction[rows*cols];
        if(initialiseReactions) {
            initialiseReactions();
        }
    }

    /**
     * If you have created the plate from a resultSet, you should call this method before doing anything with the plate.
     */
    public void initialiseReactions() {
        for(int i=0; i < rows; i++) {
            for(int j = 0; j < cols; j++) {
                final int index = cols * i + j;
                if(reactions[index] != null) {
                    continue;
                }
                final Reaction reaction = Reaction.getNewReaction(type);
                final String wellName = getWellName(i, j);
                final int j1 = j;
                final int i1 = i;
                Runnable runnable = new Runnable() {
                    public void run() {
                        reaction.setPlateId(id);
                        reaction.setPosition(index);
                        reaction.setPlateName(getName());
                        if(type != null) {
                            reaction.setLocationString(wellName);
                        }
                        Dimension preferredSize = reaction.getPreferredSize();
                        reaction.setBounds(new Rectangle(1+(preferredSize.width+1)* j1, 1+(preferredSize.height+1)* i1, preferredSize.width, preferredSize.height));
                        reaction.setThermocycle(getThermocycle());
                    }
                };
                ThreadUtilities.invokeNowOrWait(runnable);

                reactions[index] = reaction;
            }
        }
    }

    public Reaction getReaction(int row, int col) {
        if (row < 0 || row >= rows) {
            throw new IllegalArgumentException("Row index out of bounds: " + row + ". Valid range: 0 - " + (rows - 1) + ".");
        }

        if (col < 0 || col >= cols) {
            throw new IllegalArgumentException("Column index out of bounds: " + col + ". Valid range: 0 - " + (cols - 1) + ".");
        }

        return getReaction(cols * row + col);
    }

    public BiocodeUtilities.Well getWell(int row, int col) {
        return new BiocodeUtilities.Well(getWellName(row, col));
    }

    public Reaction getReaction(BiocodeUtilities.Well well) {
        int index = cols * well.row() + well.col();
        Reaction reaction = getReaction(index);
        if(reaction == null) {
            System.out.println("Reaction does not exist for well " + well);
        }
        return reaction;
    }

    private Reaction getReaction(int index) {
        if(index < reactions.length) {
            Reaction reaction = reactions[index];
            reaction.setPosition(index);
            return reaction;
        } else {
            System.out.println("Index " + index + " does not exist in plate " + getName() + "!");
            return null;
        }
    }

    public static String getWellName(int row, int col) {
        return ""+(char)(65+row)+(1+col);
    }

//    public static String getWellName(int position, Plate.Size size) {
//        BiocodeUtilities.Well well = getWell(position, size);
//        return getWellName(well.row(), well.col(), size != null);
//    }

    public static BiocodeUtilities.Well getWell(int position, Size size) {
        int cols;
        if(size != null) {
            switch(size) {
                case w8:
                    cols = 1;
                    break;
                case w16:
                    cols = 2;
                    break;
                case w24:
                    cols = 3;
                    break;
                case w32:
                    cols = 4;
                    break;
                case w40:
                    cols = 5;
                    break;
                case w48 :
                    cols = 6;
                    break;
                case w96 :
                    cols = 12;
                    break;
                case w384 :
                default :
                    cols = 24;
            }
        }
        else {
            cols = 1+position;
        }
        int row = position / cols;
        int col = position % cols;
        return new BiocodeUtilities.Well((char)(65+row), 1+col);
    }

    public Date lastModified() {
        return lastModified;
    }

    /**
     * wellName must be in the form A1, or A01
     * @param well
     * @param size
     * @return
     */
    public static int getWellLocation(BiocodeUtilities.Well well, Size size) {
        int cols;
        if(size != null) {
            switch(size) {
                case w8:
                    cols = 1;
                    break;
                case w16:
                    cols = 2;
                    break;
                case w24:
                    cols = 3;
                    break;
                case w32:
                    cols = 4;
                    break;
                case w40:
                    cols = 5;
                    break;
                case w48 :
                    cols = 6;
                    break;
                case w96 :
                    cols = 12;
                    break;
                case w384 :
                default :
                    cols = 24;
            }
        }
        else {
            cols = 1;
        }

        return (well.letter-65)*cols + well.number-1;
    }

    public Reaction addReaction(ResultSet resultSet) throws SQLException{
        Reaction r;
        switch(type) {
            case Extraction:
                if(resultSet.getObject("extraction.id") == null) {
                    return null;
                }
                r = new ExtractionReaction(resultSet);
                break;
            case PCR:
                if(resultSet.getObject("pcr.id") == null) {
                    return null;
                }
                r = new PCRReaction(resultSet);
                break;
            case CycleSequencing:
            default:
                if(resultSet.getObject("cyclesequencing.id") == null) {
                    return null;
                }
                r = new CycleSequencingReaction(resultSet);
                break;
        }
        r.setPlateId(this.id);
        r.setPlateName(getName());

        if(reactions[r.getPosition()] == null || reactions[r.getPosition()].isEmpty()) {
            reactions[r.getPosition()] = r;
        } else if(r instanceof CycleSequencingReaction && !((CycleSequencingReaction)r).getSequencingResults().isEmpty()){
            if(reactions[r.getPosition()] instanceof CycleSequencingReaction) {
                ((CycleSequencingReaction)reactions[r.getPosition()]).addSequencingResults(((CycleSequencingReaction) r).getSequencingResults());
            }
        }
        r.setLocationString(getWell(r.getPosition(), plateSize).toString());
        return r;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
        for(Reaction reaction : reactions) {
            reaction.setPlateId(id);
        }
        if(images != null) {
            for(GelImage image : images) {
                image.setPlate(id);
            }
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        setName(element.getChildText("name"));
        id = Integer.parseInt(element.getChildText("id"));
        rows = Integer.parseInt(element.getChildText("rows"));
        cols = Integer.parseInt(element.getChildText("cols"));
        lastModified = new Date(Long.parseLong(element.getChildText("lastModified")));
        if(element.getChild("plateSize") != null) {
            plateSize = Size.valueOf(element.getChildText("plateSize"));
        }
        type = Reaction.Type.valueOf(element.getChildText("type"));
        int size = Integer.parseInt(element.getChildText("size"));
        isDeleted = "true".equals(element.getChildText("isDeleted"));
        Size sizeEnum = getSizeEnum(size);
        if(sizeEnum != null) {
            init(sizeEnum, type, false);
        }
        else {
            init(size, type, false);
        }
        for(Element e : element.getChildren("reaction")) {
            Reaction r = XMLSerializer.classFromXML(e, Reaction.class);
            reactions[r.getPosition()] = r;
        }
        initialiseReactions();
        String thermocycleId = element.getChildText("thermocycle");
        if(thermocycleId != null) {
            setThermocycleFromId(Integer.parseInt(thermocycleId));     
        }
        List<Element> imagesList = element.getChildren("gelImage");
        if(imagesList != null && imagesList.size() > 0) {
            images = new ArrayList<GelImage>();
            for(Element gelImageElement : imagesList) {
                images.add(XMLSerializer.classFromXML(gelImageElement, GelImage.class));
            }
        }
    }


//    private int id=-1;
//    private int rows;
//    private int cols;
//    private String name;
//    private Reaction[] reactions;
//    private Reaction.Type type;
//    private Size plateSize;
//    private Thermocycle thermocycle;
//    private List<GelImage> images;

    public Element toXML() {
        Element plateElement = new Element("Plate");
        plateElement.addContent(new Element("id").setText(""+id));
        plateElement.addContent(new Element("name").setText(getName()));
        plateElement.addContent(new Element("type").setText(type.name()));
        plateElement.addContent(new Element("size").setText(""+reactions.length));
        plateElement.addContent(new Element("lastModified").setText(""+lastModified.getTime()));
        String rowString = "" + rows;
        Element rowElement = new Element("rows");
        rowElement.setText(rowString);
        plateElement.addContent(rowElement);
        plateElement.addContent(new Element("cols").setText(""+cols));
        if(plateSize != null) {
            plateElement.addContent(new Element("plateSize").setText(plateSize.name()));
        }
        plateElement.addContent(new Element("isDeleted").setText(""+isDeleted));
        if(getThermocycle() != null) {
            plateElement.addContent(new Element("thermocycle").setText(""+getThermocycle().getId()));
        }
        for(Reaction r : reactions) {
            plateElement.addContent(XMLSerializer.classToXML("reaction",r));
        }
        if(images != null) {
            for(GelImage gi : images) {
                plateElement.addContent(XMLSerializer.classToXML("gelImage", gi));
            }
        }

        

        return plateElement;
    }
}
