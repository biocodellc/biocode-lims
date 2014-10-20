package com.biomatters.plugins.biocode.server.utilities;

import com.biomatters.geneious.publicapi.utilities.StringUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Routines for dealing with String arguments. Non-instantiable.
 *
 * @author Gen Li
 *         Created on 15/10/14 9:23 AM
 */
public class StringArgumentUtilities {
    private StringArgumentUtilities() {
    }

    public static void checkForNULLOrEmptyStringArguments(Map<String, String> argumentIdentifierToArgument) throws IllegalArgumentException {
        StringBuilder exceptionMessageBuilder = new StringBuilder();

        exceptionMessageBuilder.append(createStringArgumentsAreNULLMessage(getNULLStrings(argumentIdentifierToArgument)))
                               .append(" ")
                               .append(createStringArgumentsAreEmptyMessage(getEmptyStrings(argumentIdentifierToArgument)));

        if (exceptionMessageBuilder.length() > 1) {
            throw new IllegalArgumentException(exceptionMessageBuilder.toString());
        }
    }

    public static void checkForNULLStringArguments(Map<String, String> argumentIdentifierToArgument) throws IllegalArgumentException {
        String exceptionMessage = createStringArgumentsAreNULLMessage(getNULLStrings(argumentIdentifierToArgument));

        if (!exceptionMessage.isEmpty()) {
            throw new IllegalArgumentException(exceptionMessage);
        }
    }

    public static void checkForEmptyStringArguments(Map<String, String> argumentIdentifierToArgument) throws IllegalArgumentException {
        String exceptionMessage = createStringArgumentsAreEmptyMessage(getEmptyStrings(argumentIdentifierToArgument));

        if (!exceptionMessage.isEmpty()) {
            throw new IllegalArgumentException(exceptionMessage);
        }
    }

    public static boolean isStringNULLOrEmpty(String string) {
        return isStringNULL(string) ||string.isEmpty();
    }

    public static boolean isStringNULL(String string) {
        return string == null;
    }

    private static List<String> getNULLStrings(Map<String, String> stringIdentifierToString) {
        List<String> identifiersOfNULLStrings = new ArrayList<String>();

        for (Map.Entry<String, String> stringIdentifierAndString : stringIdentifierToString.entrySet()) {
            if (isStringNULL(stringIdentifierAndString.getValue())) {
                identifiersOfNULLStrings.add(stringIdentifierAndString.getKey());
            }
        }

        return identifiersOfNULLStrings;
    }

    private static List<String> getEmptyStrings(Map<String, String> stringIdentifierToString) {
        List<String> identifiersOfEmptyStrings = new ArrayList<String>();

        for (Map.Entry<String, String> stringIdentifierAndString : stringIdentifierToString.entrySet()) {
            String string = stringIdentifierAndString.getValue();
            if (string != null && string.isEmpty()) {
                identifiersOfEmptyStrings.add(stringIdentifierAndString.getKey());
            }
        }

        return identifiersOfEmptyStrings;
    }

    private static String createStringArgumentsAreNULLMessage(List<String> identifiersOfNULLStrings) {
        if (identifiersOfNULLStrings.isEmpty()) {
            return "";
        }

        return "NULL arguments: " + StringUtilities.join(", ", identifiersOfNULLStrings) + ".";
    }

    private static String createStringArgumentsAreEmptyMessage(List<String> identifiersOfEmptyStrings) {
        if (identifiersOfEmptyStrings.isEmpty()) {
            return "";
        }

        return "Empty String arguments: " + StringUtilities.join(", ", identifiersOfEmptyStrings) + ".";
    }
}