package com.biomatters.plugins.biocode.labbench.fims.biocode;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Matthew Cheung
 *         Created on 6/01/15 2:36 PM
 */
@XmlRootElement
public class ErrorResponse {

    @XmlElement public String usrMessage;
    @XmlElement public String developerMessage;
    @XmlElement public int httpStatusCode;
    @XmlElement public String time;
}
