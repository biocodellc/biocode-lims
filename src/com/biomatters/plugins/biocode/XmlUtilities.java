package com.biomatters.plugins.biocode;

import org.jdom.Verifier;

/**
 * User: Steve
 * Date: 4/02/2010
 * Time: 1:10:43 PM
 */
public class XmlUtilities {


    /**
     * NO attempt has been made to put robust error checking in these methods - they will work as long as the only strings passed to the decode method are strings that have been passed through the encode method first.
     * @param input
     * @return
     */
    public static String encodeXMLChars(String input) {
        StringBuilder outputBuilder = new StringBuilder();
        for(char c : input.toCharArray()) {
            if(!Verifier.isXMLCharacter(c) || c == '&') {
                outputBuilder.append(getEncoding(c));
            }
            else {
                outputBuilder.append(c);
            }
        }
        return outputBuilder.toString();
    }


    public static String decodeXMLChars(String input) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < input.toCharArray().length; i++) {
            char c = input.toCharArray()[i];
            if(c == '&') {
                int endIndex = input.indexOf(";", i);
                String data = input.substring(i, endIndex+1);
                builder.append(decode(data));
                i = endIndex;
            }
            else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String getEncoding(char c) {
        return "&"+(int)c+";";
    }

    private static char decode(String charCode) {
        try {
        int charValue = Integer.parseInt(charCode.substring(1, charCode.length()-1));
        return (char)charValue;
        }
        catch(NumberFormatException ex) {
            return 'a';
        }
    }

}
