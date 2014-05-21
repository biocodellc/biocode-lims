package com.biomatters.plugins.biocode.utilities;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.URI;
import java.util.*;

/**
 * Provides an easy way for cookies to be shared within the JVM for a given host.
 * <p>Using {@link #get()} will instantiate
 * the handler and set it to the default {@link java.net.CookieHandler}.</p>
 * <p>Registering a host using {@link #registerHost(String)} will cause all cookies received and sent from/to the host
 * to be shared between any HTTP connections.</p>
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 16/04/14 9:42 AM
 */
public class SharedCookieHandler extends java.net.CookieHandler {
    // Try to preserve the same functionality when dealing with servers that have not been registered
    private java.net.CookieHandler oldDefault;

    private SharedCookieHandler() {
        super();
        oldDefault = java.net.CookieHandler.getDefault();
    }

    private static SharedCookieHandler singleton;

    /**
     *
     * @return A singleton instance of the {@link com.biomatters.plugins.biocode.utilities.SharedCookieHandler}
     */
    private static synchronized SharedCookieHandler get() {
        if(singleton == null) {
            singleton = new SharedCookieHandler();
            CookieHandler.setDefault(singleton);
        }
        return singleton;
    }

    private List<String> hostsWeShouldUseSameCookieFor = new ArrayList<String>();
    public static void registerHost(String host) {
        get().hostsWeShouldUseSameCookieFor.add(host);
    }
    public static void unregisterHost(String host) {
        get().hostsWeShouldUseSameCookieFor.remove(host);
        get().cookies.remove(host);

    }

    Map<String, List<String>> cookies = new HashMap<String, List<String>>();

    public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
        if(hostsWeShouldUseSameCookieFor.contains(uri.getHost())) {
            List<String> cookiesForHost = cookies.get(uri.getHost());
            if(cookiesForHost != null) {
                return Collections.singletonMap("Cookie", cookiesForHost);
            }
        } else if(oldDefault != null) {
            return oldDefault.get(uri, requestHeaders);
        }
        return Collections.emptyMap();
    }

    public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
        if(hostsWeShouldUseSameCookieFor.contains(uri.getHost())) {
            for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                if(entry.getKey() != null && entry.getKey().startsWith("Set-Cookie")) {
                    cookies.put(uri.getHost(), entry.getValue());
                }
            }
        } else if(oldDefault != null) {
            oldDefault.put(uri, responseHeaders);
        }
    }
}
