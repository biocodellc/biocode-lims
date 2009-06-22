package com.biomatters.plugins.moorea.plates;

import com.biomatters.plugins.moorea.reaction.Reaction;
import com.biomatters.plugins.moorea.reaction.Thermocycle;
import com.biomatters.plugins.moorea.plates.GelImage;

import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 10/06/2009 11:38:24 AM
 */
public class Plate {
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

    public Plate(int numberOfWells, Reaction.Type type) {
        init(numberOfWells, 1, type);
    }


    public Plate(Plate.Size size, Reaction.Type type) {
        this.type = type;
        this.plateSize = size;
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
        PreparedStatement statement = connection.prepareStatement("INSERT INTO plate (name, size, type) VALUES (?, ?, ?)");
        statement.setString(1, getName());
        statement.setInt(2, reactions.length);
        statement.setString(3, type.toString());
        return statement;
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
}
