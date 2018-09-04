package com.biomatters.plugins.biocode.labbench.fims.geome;

import java.util.Calendar;
import java.util.Date;

public class AccessToken {


    private final Date createdAt;

    private String access_token;

    private String refresh_token;

    private String token_type;

    private int expires_in;

    public AccessToken() {
        createdAt = new Date();
    }


    public String getAccess_token() {
        return access_token;
    }

    public void setAccess_token(String access_token) {
        this.access_token = access_token;
    }

    public String getRefresh_token() {
        return refresh_token;
    }

    public void setRefresh_token(String refresh_token) {
        this.refresh_token = refresh_token;
    }

    public String getToken_type() {
        return token_type;
    }

    public void setToken_type(String token_type) {
        this.token_type = token_type;
    }

    public int getExpires_in() {
        return expires_in;
    }

    public void setExpires_in(int expires_in) {
        this.expires_in = expires_in;
    }

    public Date expiryTime() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(createdAt);
        cal.add(Calendar.SECOND, expires_in);
        return cal.getTime();
    }

    @Override
    public String toString() {
        return access_token;
    }
}
