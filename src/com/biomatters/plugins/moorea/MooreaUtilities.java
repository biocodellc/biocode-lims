package com.biomatters.plugins.moorea;

/**
 * @author Richard
 * @version $Id$
 */
public class MooreaUtilities {

    public static final class Well {
        public final char letter;
        public final int number;

        public Well(char letter, int number) {
            this.letter = letter;
            this.number = number;
        }

        /**
         *
         * @return eg. "A1"
         */
        @Override
        public String toString() {
            return "" + letter + number;
        }

        /**
         *
         * @return eg. "A01"
         */
        public String toPaddedString() {
            String number = "" + this.number;
            return "" + letter + (number.length() < 2 ? "0" : "") + number;
        }
    }

    /**
     * Take an ab1 filename and attempt to pull out the well name
     *
     * @param fileName
     * @param separator
     * @param partNumber
     * @return well or null if couldn't parse out well name
     */
    public static Well getWellString(String fileName, String separator, int partNumber) {
        String[] nameParts = fileName.split(separator);
        if(partNumber >= nameParts.length) {
            return null;
        }

        String wellStringBig = nameParts[partNumber];
        int count = 1;
        int wellNumber = -1;
        String wellNumberString = "";
        while(true) {
            if(count >= wellStringBig.length()) {
                break;
            }
            char numberChar = wellStringBig.charAt(count);
            try{
                wellNumber = Integer.parseInt(wellNumberString+numberChar);
            }
            catch(NumberFormatException ex) {
                break;
            }
            wellNumberString = wellNumberString+numberChar;
            count++;
        }
        return new Well(wellStringBig.toUpperCase().charAt(0), wellNumber);
    }
}
