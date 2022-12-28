package com.biomatters.plugins.biocode;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author John Deck
 * tools for working with well names
 */
public class wellUtilities {
    public final char letter;
    public final int number;
    public String wellName;

    public wellUtilities(String wellName) {
            if (wellName == null || wellName.length() < 2) {
                throw new IllegalArgumentException("wellName must be in the form 'A1', or 'A01', but your well name was '" + wellName + "'");
            }
            wellName = wellName.toUpperCase();
            char letter = wellName.toCharArray()[0];
            if (letter < 65 || letter > 90) {
                throw new IllegalArgumentException("wellName must be in the form 'A1', or 'A01', but your well name was '" + wellName + "'");
            }
            this.letter = letter;

            int number;
            try {
                number = Integer.parseInt(wellName.substring(1));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("wellName must be in the form 'A1', or 'A01', but your well name was '" + wellName + "'");
            }
            this.number = number;
        }

    public wellUtilities(char letter, int number) {
        this.letter = letter;
        this.number = number;
        this.wellName = this.letter + Integer.toString(this.number);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BiocodeUtilities.Well well = (BiocodeUtilities.Well) o;

        if (letter != well.letter) return false;
        //noinspection RedundantIfStatement
        if (number != well.number) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (int) letter;
        result = 31 * result + number;
        return result;
    }


    /**
     * zero indexed...
     *
     * @return
     */
    private int row() {
        return ((int) letter) - 65;
    }

    /**
     * zero indexed...
     *
     * @return
     */
    private int col() {
        return number - 1;
    }

    /**
     * @return eg. "A1"
     */
    @Override
    public String toString() {
        return "" + letter + number;
    }

    /**
     * @return eg. "A01"
     */
    public String toPaddedString() {
        String number = "" + this.number;
        return "" + letter + (number.length() < 2 ? "0" : "") + number;
    }

    /**
     * Numerical Index for displaying a well by its sorted order in the plate
     * that a machine expects
     * @return
     */
    public Integer wellIndex() {
        int cols = 8;
        //return (this.letter - 65) *  cols + this.number - 1;
        int letter_position = this.letter -65;
        int number_position  = this.number;
        //return (this.letter - 65) * cols + this.number;
        return (number_position * cols)-cols +  (letter_position) + 1;
    }

    public static void main(String[] args) {
        // An example of using order function to order a list
        List<wellUtilities> list = Arrays.asList(
                new wellUtilities("A1"),
                new wellUtilities("A02"),
                new wellUtilities("B1"),
                new wellUtilities("B2"),
                new wellUtilities("E12"),
                new wellUtilities("H12"),
                new wellUtilities("H1"));

        // The list in its original order
        System.out.println("natural order: " + list);
        // run the sorting
        wellUtilities.machineFriendlyOrdering(list);
        // display the list as machine friendly sorting
        System.out.println("sorted list: " + list);

        // Demonstrate some of the features of this class
        System.out.println("well utilities:");
        for (wellUtilities well : list) {
            System.out.println(well.wellIndex() +":" + well.toString() +": " +well.toPaddedString());
        }
    }

    /**
     * Sort a list of wellNaming wells in a machine friendly way
     * @param wells
     */
    public static void machineFriendlyOrdering(java.util.List<wellUtilities> wells) {
        Collections.sort(wells, new Comparator() {
            public int compare(Object o1, Object o2) {
                Integer x1 =(Integer)((wellUtilities) o1).number;
                Integer x2 =(Integer)((wellUtilities) o2).number;

                int sComp = x1.compareTo(x2);
                if (sComp != 0) {
                   return sComp;
                }
                String y1 = Character.toString(((wellUtilities) o1).letter);
                String y2 = Character.toString(((wellUtilities) o2).letter);
                return y1.compareTo(y2);
        }});
    }
}


