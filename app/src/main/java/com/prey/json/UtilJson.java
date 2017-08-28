/*******************************************************************************
 * Created by Orlando Aliaga
 * Copyright 2015 Prey Inc. All rights reserved.
 * License: GPLv3
 * Full license at "/LICENSE"
 ******************************************************************************/
package com.prey.json;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

public class UtilJson {

    public static JSONObject makeJsonResponse(String command,String target,String status){
        JSONObject json=new JSONObject();
        try {
            json.put("command", command);
            json.put("target", target);
            json.put("status", status);
        } catch (JSONException e) {
        }
        return json;
    }

    public static JSONObject makeJsonResponse(String command,String target,String status,String reason){
        JSONObject json=makeJsonResponse(command, target, status);
        try {
            json.put("reason", reason);
        } catch (JSONException e) {
        }
        return json;
    }

    public static  Map<String, Object> makeMapParam(String command,String target,String status){
        Map<String, Object> map=new HashMap<String, Object>();
        map.put("command", command);
        map.put("target", target);
        map.put("status", status);
        return map;
    }

    public static  Map<String, Object> makeMapParam(String command,String target,String status,String reason){
        Map<String, Object> map=makeMapParam(command, target, status);
        if(reason!=null)
            map.put("reason", reason);
        return map;
    }


}
