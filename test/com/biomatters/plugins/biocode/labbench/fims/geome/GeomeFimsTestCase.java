package com.biomatters.plugins.biocode.labbench.fims.geome;

import com.biomatters.plugins.biocode.labbench.fims.biocode.BiocodeFIMSClient;
import com.biomatters.plugins.biocode.labbench.fims.biocode.BiocodeFIMSConnection;
import com.biomatters.plugins.biocode.utilities.SharedCookieHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import java.net.MalformedURLException;
import java.net.URL;

public abstract class GeomeFimsTestCase extends Assert {

    protected geomeFIMSClient client;

    @Before
    public void shareSessionsForBiSciCol() throws MalformedURLException {
        SharedCookieHandler.registerHost(getHostname());
    }

    @Before
    public void createClient() {
        client = new geomeFIMSClient(geomeFIMSConnection.GEOME_URL);
    }
    @After
    public void logoutAfterTestDone() throws MalformedURLException {
        SharedCookieHandler.unregisterHost(getHostname());
    }

    private String getHostname() throws MalformedURLException {
        return new URL(geomeFIMSConnection.GEOME_URL).getHost();
    }
}
