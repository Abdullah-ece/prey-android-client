/*******************************************************************************
 * Created by Carlos Yaconi
 * Copyright 2015 Prey Inc. All rights reserved.
 * License: GPLv3
 * Full license at "/LICENSE"
 ******************************************************************************/
package com.prey.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import com.prey.PreyLogger;

public class PreyHttpResponse {

    private int statusCode;
    private String responseAsString;
    private HttpURLConnection response;


    public PreyHttpResponse(HttpURLConnection connection) {

        try {
                this.response=connection;
                this.statusCode = connection.getResponseCode();
                this.responseAsString = convertStreamToString(connection.getInputStream());
             PreyLogger.d("responseAsString:"+responseAsString);
        } catch (IOException e) {
            PreyLogger.d("Can't receive body stream from http connection, setting response string as ''");
            this.responseAsString = "";
        }
    }

    public PreyHttpResponse(int  statusCode,String responseAsString) {

        try {
            PreyLogger.d("statusCode:"+statusCode+" responseAsString:"+responseAsString);
            this.response=null;
            this.statusCode = statusCode;
            this.responseAsString = responseAsString;

        } catch (Exception e) {
            PreyLogger.d("Can't receive body stream from http connection, setting response string as ''");
            this.responseAsString = "";
        }
    }


    private String convertStreamToString(InputStream is) {
        String out=null;
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuffer response = new StringBuffer();
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();
            out=response.toString();
        }catch(Exception e){

        }
        return out;

    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseAsString() {
        return responseAsString;
    }

    @Override
    public String toString() {

        return statusCode + " " + responseAsString;

    }

    public HttpURLConnection getResponse() {
        return response;
    }

    public void setResponse(HttpURLConnection response) {
        this.response = response;
    }

}
