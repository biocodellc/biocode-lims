package com.biomatters.plugins.moorea;

import com.biomatters.plugins.moorea.reaction.Reaction;

import java.awt.*;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 10/06/2009 11:38:24 AM
 */
public class Plate {
    private int rows;
    private int cols;
    private Reaction[] reactions;
    private Reaction.Type type;
    private Size plateSize;

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

    private void init(int rows, int cols, Reaction.Type type) {
        this.rows = rows;
        this.cols = cols;
        this.type = type;

        reactions = new Reaction[rows*cols];
        for(int i=0; i < rows; i++) {
            for(int j = 0; j < cols; j++) {
                final Reaction reaction = Reaction.getNewReaction(type);
                if(type != null) {
                    reaction.setLocationString(""+(char)(65+i)+(1+j));
                }
                Dimension preferredSize = reaction.getPreferredSize();
                reaction.setBounds(new Rectangle(1+(preferredSize.width+1)*j, 1+(preferredSize.height+1)*i, preferredSize.width, preferredSize.height));

                reactions[cols*i + j] = reaction;
            }
        }
    }

}
