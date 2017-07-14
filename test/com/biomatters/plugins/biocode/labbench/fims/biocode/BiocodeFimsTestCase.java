package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.plugins.biocode.utilities.SharedCookieHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import java.net.MalformedURLException;
import java.net.URL;

public abstract class BiocodeFimsTestCase extends Assert {

    protected BiocodeFIMSClient client;

    @Before
    public void shareSessionsForBiSciCol() throws MalformedURLException {
        SharedCookieHandler.registerHost(getHostname());
    }

    @Before
    public void createClient() {
        client = new BiocodeFIMSClient(BiocodeFIMSConnection.BISCICOL_URL);
    }
    @After
    public void logoutAfterTestDone() throws MalformedURLException {
        SharedCookieHandler.unregisterHost(getHostname());
    }

    private String getHostname() throws MalformedURLException {
        return new URL(BiocodeFIMSConnection.BISCICOL_URL).getHost();
    }
}
