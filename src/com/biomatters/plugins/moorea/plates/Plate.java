package com.biomatters.plugins.moorea.plates;

import com.biomatters.plugins.moorea.reaction.*;
import com.biomatters.plugins.moorea.plates.GelImage;
import com.biomatters.plugins.moorea.MooreaLabBenchService;
import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;

import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;

import org.jdom.Element;

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
    private String name;
    private Reaction[] reactions;
    private Reaction.Type type;
    private Size plateSize;
    private Thermocycle thermocycle;
    private List<GelImage> images;

    public enum Size {
        w48,
        w96,
        w384
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
        if(plateSize != null) {
            init(plateSize, type);
        }
        else {
            init(size, 1, type);
        }
        int thermocycleId = resultSet.getInt("plate.thermocycle");
        setThermocycleFromId(thermocycleId);
    }

    private void setThermocycleFromId(int thermocycleId) {
        if(thermocycleId >= 0) {
            for(Thermocycle tc : MooreaLabBenchService.getInstance().getPCRThermocycles()) {
                if(tc.getId() == thermocycleId) {
                    setThermocycle(tc);
                    break;
                }
            }
            for(Thermocycle tc : MooreaLabBenchService.getInstance().getCycleSequencingThermocycles()) {
                if(tc.getId() == thermocycleId) {
                    setThermocycle(tc);
                    break;
                }
            }
        }
    }

    private Size getSizeEnum(int size) {
        if(size == 48) {
            return Size.w48;
        }
        else if(size == 96) {
            return Size.w96;
        }
        else if(size == 384) {
            return Size.w384;
        }
        return null;
    }

    public Plate(int numberOfWells, Reaction.Type type) {
        init(numberOfWells, 1, type);
    }


    public Plate(Plate.Size size, Reaction.Type type) {
        this.type = type;
        this.plateSize = size;
        init(size, type);
    }

    private void init(Size size, Reaction.Type type) {
        switch(size) {
            case w48 :
                init(8, 6, type);
                break;
            case w96 :
                init(8, 12, type);
                break;
            case w384 :
                init(16, 24, type);
        }
    }

    public List<GelImage> getImages() {
        return images;
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

    public Thermocycle getThermocycle() {
        return thermocycle;
    }

    public void setThermocycle(Thermocycle thermocycle) {
        this.thermocycle = thermocycle;
        for(Reaction r : getReactions()) {
            r.setThermocycle(thermocycle);
        }
    }

    private void init(int rows, int cols, Reaction.Type type) {
        this.rows = rows;
        this.cols = cols;
        this.type = type;

        images = new ArrayList<GelImage>();

        reactions = new Reaction[rows*cols];
        for(int i=0; i < rows; i++) {
            for(int j = 0; j < cols; j++) {
                int index = cols * i + j;
                final Reaction reaction = Reaction.getNewReaction(type);
                reaction.setPlate(id);
                reaction.setPosition(index);
                if(type != null) {
                    reaction.setLocationString(getWellName(i,j));
                }
                Dimension preferredSize = reaction.getPreferredSize();
                reaction.setBounds(new Rectangle(1+(preferredSize.width+1)*j, 1+(preferredSize.height+1)*i, preferredSize.width, preferredSize.height));

                reactions[index] = reaction;
            }
        }
    }

    public Reaction getReaction(int row, int col) {
        return reactions[cols * row + col];    
    }

    public String getWellName(int row, int col) {
        return ""+(char)(65+row)+(1+col);
    }

    public PreparedStatement toSQL(Connection connection) throws SQLException{
        if(name == null || name.trim().length() == 0) {
            throw new SQLException("Plates cannot have empty names");
        }
        PreparedStatement statement = connection.prepareStatement("INSERT INTO plate (name, size, type, thermocycle) VALUES (?, ?, ?, ?)");
        statement.setString(1, getName());
        statement.setInt(2, reactions.length);
        statement.setString(3, type.toString());
        Thermocycle tc = getThermocycle();
        if(tc != null) {
            statement.setInt(4, tc.getId());
        }
        else {
            statement.setInt(4, -1);
        }
        return statement;
    }

    public Reaction addReaction(ResultSet resultSet) throws SQLException{
        Reaction r;
        switch(type) {
            case Extraction:
                r = new ExtractionReaction(resultSet);
                break;
            case PCR:
                r = new PCRReaction(resultSet);
                break;
            case CycleSequencing:
            default:
                r = new CycleSequencingReaction(resultSet);
                break;
        }
        reactions[r.getPosition()] = r;
        int row = r.getPosition() / cols;
        int col = r.getPosition() % cols;
        r.setLocationString(getWellName(row, col));
        return r;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
        for(Reaction reaction : reactions) {
            reaction.setPlate(id);
        }
        for(GelImage image : images) {
            image.setPlate(id);
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
        type = Reaction.Type.valueOf(element.getChildText("type"));
        int size = Integer.parseInt(element.getChildText("size"));
        Size sizeEnum = getSizeEnum(size);
        if(sizeEnum != null) {
            init(sizeEnum, type);
        }
        else {
            init(size, 1, type);
        }
        for(Element e : element.getChildren("reaction")) {
            Reaction r = XMLSerializer.classFromXML(e, Reaction.class);
            reactions[r.getPosition()] = r;
        }
        String thermocycleId = element.getChildText("thermocycle");
        if(thermocycleId != null) {
            setThermocycleFromId(Integer.parseInt(thermocycleId));     
        }
        images = new ArrayList<GelImage>();
        for(Element gelImageElement : element.getChildren("gelImage")) {
            images.add(XMLSerializer.classFromXML(gelImageElement, GelImage.class));
        }
    }

    public Element toXML() {
        Element plateElement = new Element("Plate");
        plateElement.addContent(new Element("name").setText(getName()));
        plateElement.addContent(new Element("type").setText(type.toString()));
        plateElement.addContent(new Element("size").setText(""+reactions.length));
        if(getThermocycle() != null) {
            plateElement.addContent(new Element("thermocycle").setText(""+getThermocycle().getId()));
        }
        for(Reaction r : reactions) {
            plateElement.addContent(XMLSerializer.classToXML("reaction",r));
        }
        for(GelImage gi : images) {
            plateElement.addContent(XMLSerializer.classToXML("gelImage", gi));
        }

        

        return plateElement;
    }
}
