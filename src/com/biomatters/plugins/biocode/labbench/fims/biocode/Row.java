package com.biomatters.plugins.biocode.labbench.fims.biocode;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import javax.xml.bind.annotation.XmlElement;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 3/02/14 8:24 PM
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Row {
    @XmlElement(name = "row")public List<String> rowItems;
}
