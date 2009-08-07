package com.biomatters.plugins.moorea;

/**
 * @author Richard
 * @version $Id$
 */
public class MooreaUtilities {

    /**
     * Take an ab1 filename and attempt to pull out the well name
     *
     * @param fileName
     * @param separator
     * @param partNumber
     * @return eg. "A1" or null if couldn't parse out well name
     */
    public static String getWellString(String fileName, String separator, int partNumber) {
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
        return""+wellStringBig.toUpperCase().charAt(0)+wellNumber;
    }
}
