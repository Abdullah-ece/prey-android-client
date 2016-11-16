/*******************************************************************************
 * Created by Carlos Yaconi
 * Copyright 2015 Prey Inc. All rights reserved.
 * License: GPLv3
 * Full license at "/LICENSE"
 ******************************************************************************/
package com.prey.net;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;



import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;

import com.google.android.gms.wearable.DataMap;
import com.prey.FileConfigReader;
import com.prey.PreyAccountData;
import com.prey.PreyConfig;
import com.prey.PreyLogger;
import com.prey.PreyPhone;
import com.prey.PreyPhone.Hardware;
import com.prey.PreyPhone.Wifi;

import com.prey.actions.HttpDataService;
import com.prey.actions.fileretrieval.FileretrievalDto;
import com.prey.actions.location.PreyLocation;
import com.prey.actions.observer.ActionsController;
import com.prey.backwardcompatibility.AboveCupcakeSupport;
import com.prey.dto.Device;
import com.prey.events.Event;
import com.prey.exceptions.NoMoreDevicesAllowedException;
import com.prey.exceptions.PreyException;
import com.prey.json.parser.JSONParser;
import com.prey.net.http.EntityFile;
import com.prey.R;

public class PreyWebServices {

    private static PreyWebServices _instance = null;

    private PreyWebServices() {

    }

    public static PreyWebServices getInstance() {
        if (_instance == null)
            _instance = new PreyWebServices();
        return _instance;
    }




    /**
     * Register a new account and get the API_KEY as return In case email is
     * already registered, this service will return an error.
     *
     * @throws PreyException
     */
    public PreyAccountData registerNewAccount(Context ctx, String name, String email, String password, String deviceType) throws PreyException {


        HashMap<String, String> parameters = new HashMap<String, String>();

        parameters.put("name", name);
        parameters.put("email", email);
        parameters.put("password", password);
        parameters.put("password_confirmation", password);
        parameters.put("country_name", Locale.getDefault().getDisplayCountry());


        PreyHttpResponse response = null;
        String xml = "";
        try {
            String apiv2 = FileConfigReader.getInstance(ctx).getApiV2();
            String url = PreyConfig.getPreyConfig(ctx).getPreyUrl().concat(apiv2).concat("signup.json");

            response = PreyRestHttpClient.getInstance(ctx).post(url, parameters);
            xml = response.getResponseAsString();
        } catch (Exception e) {
            PreyLogger.e("error: "+e.getMessage(),e);
            throw new PreyException(ctx.getText(R.string.error_communication_exception).toString(), e);
        }

        String apiKey = "";
        if (xml.contains("\"key\"")) {
            try {
                JSONObject jsnobject = new JSONObject(xml);
                apiKey = jsnobject.getString("key");
            } catch (Exception e) {

            }
        } else {

            if (response != null && response.getStatusCode() > 299) {
                if (response.getStatusCode() == 422 && xml.indexOf("already") > 0) {
                    throw new PreyException(ctx.getString(R.string.error_already_register));
                }
                throw new PreyException(ctx.getString(R.string.error_cant_add_this_device, "[" + response.getStatusCode() + "]"));
            } else {
                throw new PreyException(ctx.getString(R.string.error_cant_add_this_device, ""));
            }
        }

        PreyHttpResponse responseDevice = registerNewDevice(ctx, apiKey, deviceType);
        String xmlDeviceId = responseDevice.getResponseAsString();
        String deviceId = null;
        if (xmlDeviceId.contains("{\"key\"")) {
            try {
                JSONObject jsnobject = new JSONObject(xmlDeviceId);
                deviceId = jsnobject.getString("key");
            } catch (Exception e) {

            }
        } else {
            throw new PreyException(ctx.getString(R.string.error_cant_add_this_device, ""));
        }

        PreyAccountData newAccount = new PreyAccountData();
        newAccount.setApiKey(apiKey);
        newAccount.setDeviceId(deviceId);
        newAccount.setEmail(email);
        newAccount.setPassword(password);
        newAccount.setName(name);
        return newAccount;
    }


    /**
     * Register a new device for a given API_KEY, needed just after obtain the
     * new API_KEY.
     *
     * @throws PreyException
     */
    private PreyHttpResponse registerNewDevice(Context ctx, String api_key, String deviceType) throws PreyException {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);

        String model = Build.MODEL;
        String vendor = "Google";
        try {
            vendor = AboveCupcakeSupport.getDeviceVendor();
        }catch(Exception e){
        }
        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("api_key", api_key);
        parameters.put("title", vendor + " " + model);
        parameters.put("device_type", deviceType);
        parameters.put("os", "Android");
        parameters.put("os_version", Build.VERSION.RELEASE);
        parameters.put("referer_device_id", "");
        parameters.put("plan", "free");
        parameters.put("model_name", model);
        parameters.put("vendor_name", vendor);

        parameters = increaseData(ctx, parameters);
        TelephonyManager mTelephonyMgr = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        //String imsi = mTelephonyMgr.getSubscriberId();
        String imei = new PreyPhone(ctx).getHardware().getAndroidDeviceId();
        parameters.put("physical_address", imei);

        PreyHttpResponse response = null;
        try {
            String apiv2 = FileConfigReader.getInstance(ctx).getApiV2();
            String url = PreyConfig.getPreyConfig(ctx).getPreyUrl().concat(apiv2).concat("devices.json");
            PreyLogger.d("url:" + url);
            response = PreyRestHttpClient.getInstance(ctx).post(url, parameters);
            if (response == null) {
                throw new PreyException(ctx.getString(R.string.error_cant_add_this_device, "[" + -1 + "]"));
            } else {
                PreyLogger.d("response:" + response.getStatusCode() + " " + response.getResponseAsString());
                // No more devices allowed

                if ((response.getStatusCode() == 302) || (response.getStatusCode() == 422) || (response.getStatusCode() == 403)) {
                    throw new NoMoreDevicesAllowedException(ctx.getText(R.string.set_old_user_no_more_devices_text).toString());
                }
                if (response.getStatusCode() > 299) {
                    throw new PreyException(ctx.getString(R.string.error_cant_add_this_device, "[" + response.getStatusCode() + "]"));
                }
            }
        } catch (Exception e) {
            PreyLogger.e("error:"+e.getMessage(),e);
            throw new PreyException(ctx.getText(R.string.error_communication_exception).toString(), e);
        }

        return response;
    }

    public PreyAccountData registerNewDeviceToAccount(Context ctx, String email, String password, String deviceType) throws PreyException {
        PreyLogger.d("ws email:" + email + " password:" + password);
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);
        HashMap<String, String> parameters = new HashMap<String, String>();
        PreyHttpResponse response = null;
        String xml;
        try {
            String apiv2 = FileConfigReader.getInstance(ctx).getApiV2();
            String url = PreyConfig.getPreyConfig(ctx).getPreyUrl().concat(apiv2).concat("profile.xml");
            PreyLogger.d("_____url:" + url);
            response = PreyRestHttpClient.getInstance(ctx).get(url, parameters, email, password);
            xml = response.getResponseAsString();
            PreyLogger.d("xml:" + xml);
        } catch (Exception e) {
            PreyLogger.e("Error!"+e.getMessage(), e);
            throw new PreyException(ctx.getText(R.string.error_communication_exception).toString(), e);
        }
        String status = "";
        if (response != null  ) {
            status = "[" + response.getStatusCode() + "]";
        }
        if (!xml.contains("<key")) {
            throw new PreyException(ctx.getString(R.string.error_cant_add_this_device, status));
        }

        int from;
        int to;
        String apiKey;
        try {
            from = xml.indexOf("<key>") + 5;
            to = xml.indexOf("</key>");
            apiKey = xml.substring(from, to);
        } catch (Exception e) {
            throw new PreyException(ctx.getString(R.string.error_cant_add_this_device, status));
        }
        String deviceId = null;
        PreyHttpResponse responseDevice = registerNewDevice(ctx, apiKey, deviceType);
        String xmlDeviceId = responseDevice.getResponseAsString();
        //if json
        if (xmlDeviceId.contains("{\"key\"")) {
            try {
                JSONObject jsnobject = new JSONObject(xmlDeviceId);
                deviceId = jsnobject.getString("key");
            } catch (Exception e) {

            }
        }
        PreyAccountData newAccount = new PreyAccountData();
        newAccount.setApiKey(apiKey);
        newAccount.setDeviceId(deviceId);
        newAccount.setEmail(email);
        newAccount.setPassword(password);
        return newAccount;

    }

    public PreyAccountData registerNewDeviceWithApiKeyEmail(Context ctx, String apiKey, String email, String deviceType) throws PreyException {
        String deviceId = "";
        PreyHttpResponse responseDevice = registerNewDevice(ctx, apiKey, deviceType);
        String xmlDeviceId = responseDevice.getResponseAsString();
        //if json
        if (xmlDeviceId.contains("{\"key\"")) {
            try {
                JSONObject jsnobject = new JSONObject(xmlDeviceId);
                deviceId = jsnobject.getString("key");
            } catch (Exception e) {
            }
        }
        PreyLogger.i("deviceId:"+deviceId);
        PreyAccountData newAccount =null;
        if (deviceId!=null&&!"".equals(deviceId)) {
            newAccount = new PreyAccountData();
            newAccount.setApiKey(apiKey);
            newAccount.setDeviceId(deviceId);
            newAccount.setEmail(email);
            newAccount.setPassword("");
        }
        return newAccount;

    }

    public PreyHttpResponse setPushRegistrationId(Context ctx, String regId) {
        //this.updateDeviceAttribute(ctx, "notification_id", regId);
        HttpDataService data = new HttpDataService("notification_id");
        data.setList(false);
        data.setKey("notification_id");
        data.setSingleData(regId);
        ArrayList<HttpDataService> dataToBeSent = new ArrayList<HttpDataService>();
        dataToBeSent.add(data);
        PreyHttpResponse response = PreyWebServices.getInstance().sendPreyHttpData(ctx, dataToBeSent);
        if (response != null && response.getStatusCode() == 200) {
            PreyLogger.d("c2dm registry id set succesfully");
        }
        return response;
    }

    public boolean checkPassword(Context ctx, String apikey, String password) throws PreyException {
        String xml = this.checkPassword(apikey, password, ctx);
        return xml.contains("<key");
    }

    public String checkPassword(String apikey, String password, Context ctx) throws PreyException {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);
        HashMap<String, String> parameters = new HashMap<String, String>();
        String xml=null;

        try {
            String uri=PreyConfig.getPreyConfig(ctx).getPreyUrl().concat("profile.xml");
            PreyHttpResponse response = PreyRestHttpClient.getInstance(ctx).get(uri, parameters, apikey, password);
            xml=response.getResponseAsString();
        } catch (Exception e) {
            throw new PreyException(ctx.getText(R.string.error_communication_exception).toString(), e);
        }
        try {
            PreyLogger.d("____[token]_________________apikey:"+apikey+" password:"+password);
            String apiv2 = FileConfigReader.getInstance(ctx).getApiV2();
            String uri2=PreyConfig.getPreyConfig(ctx).getPreyUrl().concat(apiv2).concat("get_token.json");
            PreyHttpResponse response = PreyRestHttpClient.getInstance(ctx).get(uri2, parameters, apikey, password,"application/json");
            if(response!=null) {
                JSONObject jsnobject = new JSONObject(response.getResponseAsString());
                String tokenJwt = jsnobject.getString("token");
                PreyLogger.d("tokenJwt:" + tokenJwt);
                PreyConfig.getPreyConfig(ctx).setTokenJwt(tokenJwt);
            }else{
                PreyLogger.d("token: nulo");
            }

        } catch (Exception e) {

        }
        PreyLogger.d("____[token]_________________xml:"+xml);
        return xml;
    }



    public String deleteDevice(Context ctx) throws PreyException {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);
        HashMap<String, String> parameters = new HashMap<String, String>();
        String xml;
        try {
            String url = this.getDeviceWebControlPanelUiUrl(ctx);
            PreyHttpResponse response = PreyRestHttpClient.getInstance(ctx)
                    .delete(url, parameters);
            PreyLogger.d(response.toString());
            xml = response.getResponseAsString();

        } catch (Exception e) {
            throw new PreyException(ctx.getText(R.string.error_communication_exception).toString(), e);
        }
        return xml;
    }

    public boolean forgotPassword(Context ctx) throws PreyException {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);
        String URL = PreyConfig.getPreyConfig(ctx).getPreyUrl().concat("forgot");
        HashMap<String, String> parameters = new HashMap<String, String>();

        parameters.put("user[email]", preyConfig.getEmail());

        try {
            PreyHttpResponse response = PreyRestHttpClient.getInstance(ctx).post(URL, parameters);
            if (response.getStatusCode() != 302) {
                throw new PreyException(ctx.getText(R.string.error_cant_report_forgotten_password).toString());
            }
        } catch (Exception e) {
            throw new PreyException(ctx.getText(R.string.error_cant_report_forgotten_password).toString(), e);
        }

        return true;
    }

    public static String getDeviceWebControlPanelUrl(Context ctx) throws PreyException {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);
        String deviceKey = preyConfig.getDeviceId();
        if (deviceKey == null || deviceKey == "")
            throw new PreyException("Device key not found on the configuration");
        String apiv2 = FileConfigReader.getInstance(ctx).getApiV2();
        //apiv2="";
        return PreyConfig.getPreyConfig(ctx).getPreyUrl().concat(apiv2).concat("devices/").concat(deviceKey);
    }

    public String getDeviceWebControlPanelUiUrl(Context ctx) throws PreyException {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);
        String deviceKey = preyConfig.getDeviceId();
        if (deviceKey == null || deviceKey == "")
            throw new PreyException("Device key not found on the configuration");
        String apiv2 = FileConfigReader.getInstance(ctx).getApiV2();
        return PreyConfig.getPreyConfig(ctx).getPreyUrl().concat(apiv2).concat("devices/").concat(deviceKey);
    }


    private String getDeviceUrlJson(Context ctx) throws PreyException {
        return getDeviceUrlApiv2(ctx).concat(".json");
    }

    private String getVerifyUrl(Context ctx) throws PreyException {
        return getDeviceUrlApiv2(ctx).concat("/verify.json");
    }

    private String getReportUrlJson(Context ctx) throws PreyException {
        return getDeviceUrlApiv2(ctx).concat("/reports.json");
    }

    public String getFileUrlJson(Context ctx) throws PreyException {
        return getDeviceUrlApiv2(ctx).concat("/files");
    }

    public String getDataUrlJson(Context ctx) throws PreyException {
        return getDeviceUrlApiv2(ctx).concat("/data.json");
    }

    private String getEventsUrlJson(Context ctx) throws PreyException {
        return getDeviceUrlApiv2(ctx).concat("/events");
    }

    private String getResponseUrlJson(Context ctx) throws PreyException {
        return getDeviceUrlApiv2(ctx).concat("/response");
    }

    private String getDeviceUrlApiv2(Context ctx) throws PreyException {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);
        String deviceKey = preyConfig.getDeviceId();
        if (deviceKey == null || deviceKey == "")
            throw new PreyException("Device key not found on the configuration");
        String apiv2 = FileConfigReader.getInstance(ctx).getApiV2();
        String url = PreyConfig.getPreyConfig(ctx).getPreyUrl().concat(apiv2).concat("devices/").concat(deviceKey);
        return url;
    }

    public String getDeviceUrlV2(Context ctx) throws PreyException {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);
        String deviceKey = preyConfig.getDeviceId();
        if (deviceKey == null || deviceKey == "")
            throw new PreyException("Device key not found on the configuration");
        String apiv2 = FileConfigReader.getInstance(ctx).getApiV2();
        String url = PreyConfig.getPreyConfig(ctx).getPreyUrl().concat(apiv2).concat("devices/").concat(deviceKey);
        return url;
    }

    public String getDeviceUrl(Context ctx) throws PreyException {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);
        String deviceKey = preyConfig.getDeviceId();
        if (deviceKey == null || deviceKey == "")
            throw new PreyException("Device key not found on the configuration");

        String url = PreyConfig.getPreyConfig(ctx).getPreyUrl().concat("devices/").concat(deviceKey);
        return url;
    }


    public HashMap<String, String> increaseData(Context ctx, HashMap<String, String> parameters) {
        PreyPhone phone = new PreyPhone(ctx);
        Hardware hardware = phone.getHardware();
        String prefix = "hardware_attributes";
        parameters.put(prefix + "[uuid]", hardware.getUuid());
        parameters.put(prefix + "[bios_vendor]", hardware.getBiosVendor());
        parameters.put(prefix + "[bios_version]", hardware.getBiosVersion());
        parameters.put(prefix + "[mb_vendor]", hardware.getMbVendor());
        parameters.put(prefix + "[mb_serial]", hardware.getMbSerial());
        parameters.put(prefix + "[mb_model]", hardware.getMbModel());
        // parameters.put(prefix + "[mb_version]", hardware.getMbVersion());
        parameters.put(prefix + "[cpu_model]", hardware.getCpuModel());
        parameters.put(prefix + "[cpu_speed]", hardware.getCpuSpeed());
        parameters.put(prefix + "[cpu_cores]", hardware.getCpuCores());
        parameters.put(prefix + "[ram_size]", "" + hardware.getTotalMemory());
        parameters.put(prefix + "[serial_number]", hardware.getSerialNumber());
        // parameters.put(prefix + "[ram_modules]", hardware.getRamModules());
        int nic = 0;
        Wifi wifi = phone.getWifi();
        if (wifi != null) {
            prefix = "hardware_attributes[network]";
            parameters.put(prefix + "[nic_" + nic + "][name]", wifi.getName());
            parameters.put(prefix + "[nic_" + nic + "][interface_type]", wifi.getInterfaceType());
            // parameters.put(prefix + "[nic_" + nic + "][model]", wifi.getModel());
            // parameters.put(prefix + "[nic_" + nic + "][vendor]", wifi.getVendor());
            parameters.put(prefix + "[nic_" + nic + "][ip_address]", wifi.getIpAddress());
            parameters.put(prefix + "[nic_" + nic + "][gateway_ip]", wifi.getGatewayIp());
            parameters.put(prefix + "[nic_" + nic + "][netmask]", wifi.getNetmask());
            parameters.put(prefix + "[nic_" + nic + "][mac_address]", wifi.getMacAddress());
        }
        return parameters;
    }


    public PreyHttpResponse sendPreyHttpData(Context ctx, ArrayList<HttpDataService> dataToSend) {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);

        Map<String, String> parameters = new HashMap<String, String>();
        List<EntityFile> entityFiles = new ArrayList<EntityFile>();
        for (HttpDataService httpDataService : dataToSend) {
            if (httpDataService != null) {
                parameters.putAll(httpDataService.getDataAsParameters());
                if (httpDataService.getEntityFiles() != null && httpDataService.getEntityFiles().size() > 0) {
                    entityFiles.addAll(httpDataService.getEntityFiles());
                }
            }
        }
        Hardware hardware = new PreyPhone(ctx).getHardware();
        if (!PreyConfig.getPreyConfig(ctx).isSendData() && hardware.getTotalMemory() > 0) {
            PreyConfig.getPreyConfig(ctx).setSendData(true);
            parameters.put("hardware_attributes[ram_size]", "" + hardware.getTotalMemory());
        }

        if(!"".equals(hardware.getUuid())&&!PreyConfig.getPreyConfig(ctx).isSentUuidSerialNumber()) {
            parameters.put("hardware_attributes[uuid]", hardware.getUuid());
            parameters.put("hardware_attributes[serial_number]", hardware.getSerialNumber());
            PreyConfig.getPreyConfig(ctx).setSentUuidSerialNumber(true);
        }



        //	parameters.put("notification_id", preyConfig.getNotificationId());


        PreyHttpResponse preyHttpResponse = null;
        try {
            String url = getDataUrlJson(ctx);
            PreyLogger.d("URL:" + url);
            PreyConfig.postUrl = null;


            if (entityFiles==null||entityFiles.size() == 0)
                preyHttpResponse = PreyRestHttpClient.getInstance(ctx).postAutentication(url, parameters);
            else
                preyHttpResponse = PreyRestHttpClient.getInstance(ctx).postAutentication(url, parameters, entityFiles);
            PreyLogger.d("Data sent_: " + (preyHttpResponse==null?"":preyHttpResponse.getResponseAsString()));
        } catch (Exception e) {
            PreyLogger.e("Data wasn't send", e);
        }
        return preyHttpResponse;
    }


    public boolean verify(Context ctx) throws Exception {
        boolean result = false;
        String url = getVerifyUrl(ctx);
        //PreyLogger.i("verify url:"+url);
        PreyHttpResponse preyHttpResponse = null;
        PreyConfig config = PreyConfig.getPreyConfig(ctx);
        preyHttpResponse = PreyRestHttpClient.getInstance(ctx).getAutentication(url,null);
        PreyLogger.d("status:"+preyHttpResponse.getStatusCode());
        result = (preyHttpResponse.getStatusCode() == 200);
        return result;
    }

    public void sendPreyHttpEvent(Context ctx, Event event, JSONObject jsonObject) {
        try {
            String url = getEventsUrlJson(ctx) + ".json";
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("name", event.getName());
            parameters.put("info", event.getInfo());

            PreyLogger.d("sendPreyHttpEvent url:" + url);
            PreyLogger.d("name:" + event.getName() + " info:" + event.getInfo());
            PreyLogger.d("status:" + jsonObject.toString());
            String status = jsonObject.toString();
            PreyHttpResponse preyHttpResponse = PreyRestHttpClient.getInstance(ctx).postStatusAutentication(url, status, parameters);
            String jsonString = preyHttpResponse.getResponseAsString();
            if (jsonString != null && jsonString.length() > 0) {
                List<JSONObject> jsonObjectList = new JSONParser().getJSONFromTxt(ctx, jsonString.toString());
                if (jsonObjectList != null && jsonObjectList.size() > 0) {
                    ActionsController.getInstance(ctx).runActionJson(ctx, jsonObjectList);
                }
            }
        } catch (Exception e) {
            PreyLogger.i("message:" + e.getMessage());
            PreyLogger.e("Event wasn't send", e);
        }
    }






    public String sendNotifyActionResultPreyHttp(Context ctx, Map<String, String> params) {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);
        String response = null;
        try {
            String url = getResponseUrlJson(ctx);
            PreyConfig.postUrl = null;
            PreyHttpResponse httpResponse = PreyRestHttpClient.getInstance(ctx).postAutentication(url, params);
            response = httpResponse.toString();
            PreyLogger.d("Notify Action Result sent: " + response);
        } catch (Exception e) {
            //PreyLogger.e("Notify Action Result wasn't send",e);
        }
        return response;
    }

    public String sendNotifyActionResultPreyHttp(Context ctx, String correlationId, Map<String, String> params) {
        return sendNotifyActionResultPreyHttp(ctx,null,correlationId,params);
    }
    public String sendNotifyActionResultPreyHttp(Context ctx, String status,String correlationId, Map<String, String> params) {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);
        String response = null;
        try {
            String url = getResponseUrlJson(ctx);
            PreyConfig.postUrl = null;
            PreyHttpResponse httpResponse = PreyRestHttpClient.getInstance(ctx).postAutenticationCorrelationId(url, status,correlationId,params);
            response = httpResponse.toString();
            PreyLogger.d("Notify Action Result sent: " + response);
        } catch (Exception e) {
            //PreyLogger.e("Notify Action Result wasn't send",e);
        }
        return response;
    }

    public PreyHttpResponse sendPreyHttpReport(Context ctx, List<HttpDataService> dataToSend) {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);

        HashMap<String, String> parameters = new HashMap<String, String>();
        List<EntityFile> entityFiles = new ArrayList<EntityFile>();
        for (HttpDataService httpDataService : dataToSend) {
            if (httpDataService != null) {
                parameters.putAll(httpDataService.getReportAsParameters());
                if (httpDataService.getEntityFiles() != null && httpDataService.getEntityFiles().size() > 0) {
                    entityFiles.addAll(httpDataService.getEntityFiles());
                }
            }
        }

        PreyHttpResponse preyHttpResponse = null;
        try {
            String url = getReportUrlJson(ctx);
            PreyConfig.postUrl = null;
            PreyLogger.d("report url:" + url);


            if (entityFiles == null || entityFiles.size() == 0)
                preyHttpResponse = PreyRestHttpClient.getInstance(ctx).postAutenticationTimeout(url, parameters);
            else
                preyHttpResponse = PreyRestHttpClient.getInstance(ctx).postAutentication(url, parameters, entityFiles);
            PreyLogger.i("Report sent: " + (preyHttpResponse==null?"":preyHttpResponse.getResponseAsString()));
        } catch (Exception e) {
            PreyLogger.e("Report wasn't send:" + e.getMessage(), e);
        }
        return preyHttpResponse;
    }

    public List<JSONObject> getActionsJsonToPerform(Context ctx) throws PreyException {
        String url = getDeviceUrlJson(ctx);
        //PreyLogger.i("url:"+url);
        List<JSONObject> lista = new JSONParser().getJSONFromUrl(ctx, url);

        return lista;
    }

    public PreyHttpResponse registerNewDeviceRemote(Context ctx, String mail, String notificationId, String deviceType) throws PreyException {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);

        String model = Build.MODEL;
        String vendor = "Google";
        if (!PreyConfig.getPreyConfig(ctx).isCupcakeOrAbove())
            vendor = AboveCupcakeSupport.getDeviceVendor();


        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("device[notification_id]", notificationId);
        parameters.put("device[remote_email]", mail);
        parameters.put("device[title]", vendor + " " + model);
        parameters.put("device[device_type]", deviceType);
        parameters.put("device[os]", "Android");
        parameters.put("device[os_version]", Build.VERSION.RELEASE);
        parameters.put("device[referer_device_id]", "");
        parameters.put("device[plan]", "free");
        parameters.put("device[model_name]", model);
        parameters.put("device[vendor_name]", vendor);

        parameters = increaseData(ctx, parameters);
        TelephonyManager mTelephonyMgr = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        String imei = mTelephonyMgr.getDeviceId();
        parameters.put("device[physical_address]", imei);

        PreyHttpResponse response = null;
        try {
            String url = "https://panel.preyapp.com/api/v2/remote.json";
            response = PreyRestHttpClient.getInstance(ctx).post(url, parameters);
        } catch (Exception e) {
            throw new PreyException(ctx.getText(R.string.error_communication_exception).toString(), e);
        }

        return response;
    }


    public PreyHttpResponse sendContact(Context ctx, HashMap<String, String> parameters) {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);


        PreyHttpResponse preyHttpResponse = null;
        try {

            String url = getDeviceUrlApiv2(ctx).concat("/contacts");

            PreyConfig.postUrl = null;


            preyHttpResponse = PreyRestHttpClient.getInstance(ctx).postAutentication(url, parameters);


        } catch (Exception e) {
            PreyLogger.e("Contact wasn't send", e);
        }
        return preyHttpResponse;
    }

    public PreyHttpResponse sendBrowser(Context ctx, HashMap<String, String> parameters) {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);
        PreyHttpResponse preyHttpResponse = null;
        try {
            String url = getDeviceUrlApiv2(ctx).concat("/browser");
            PreyConfig.postUrl = null;

            preyHttpResponse = PreyRestHttpClient.getInstance(ctx).postAutentication(url, parameters);


        } catch (Exception e) {
            PreyLogger.e("Contact wasn't send", e);
        }
        return preyHttpResponse;
    }

    public PreyHttpResponse getContact(Context ctx) {
        PreyConfig preyConfig = PreyConfig.getPreyConfig(ctx);
        PreyHttpResponse preyHttpResponse = null;
        try {
            HashMap<String, String> parameters = new HashMap<String, String>();
            String url = getDeviceUrlApiv2(ctx).concat("/contacts.json");
            PreyLogger.d("url:" + url);
            preyHttpResponse = PreyRestHttpClient.getInstance(ctx).getAutentication(url, parameters);
        } catch (Exception e) {
            PreyLogger.e("Contact wasn't send", e);
        }

        return preyHttpResponse;
    }

    public String getIPAddress(Context ctx)throws Exception {
        String uri="http://ifconfig.me/ip";
        PreyHttpResponse response = PreyRestHttpClient.getInstance(ctx).get(uri,null);
        String responseAsString = response.getResponseAsString();
        PreyLogger.d("responseAsString:" + responseAsString);
        return responseAsString;
    }

    public PreyLocation getLocation(Context ctx, List<Wifi> listWifi) throws Exception {
        PreyLocation location = null;
        String url = googleLookup(listWifi);
        PreyLogger.d("location url:" + url);
        PreyHttpResponse response = PreyRestHttpClient.getInstance(ctx).get(url,null);
        String responseAsString = response.getResponseAsString();
        PreyLogger.d("location resp:" + responseAsString);
        if (response.getStatusCode() == 200) {
            if (responseAsString != null && responseAsString.indexOf("OK") >= 0) {
                location = new PreyLocation();
                JSONObject jsnobject = new JSONObject(response.getResponseAsString());
                String accuracy = jsnobject.getString("accuracy");
                JSONObject jsnobjectLocation = jsnobject.getJSONObject("location");
                String lat = jsnobjectLocation.getString("lat");
                String lng = jsnobjectLocation.getString("lng");
                location.setLat(Double.parseDouble(lat));
                location.setLng(Double.parseDouble(lng));
                location.setAccuracy(Float.parseFloat(accuracy));
                location.setMethod("wifi");
            }
        }
        return location;
    }

    private String googleLookup(List<Wifi> listwifi) {
        String queryString = "https://maps.googleapis.com/maps/api/browserlocation/json?browser=firefox&sensor=true";
        try {
            for (int i = 0; listwifi != null && i < listwifi.size(); i++) {
                String ssid = listwifi.get(i).getSsid();
                ssid = ssid.replaceAll(" ", "%20");
                queryString += "&wifi=mac:";
                queryString += listwifi.get(i).getMacAddress();
                queryString += "%7C";
                queryString += "ssid:";
                queryString += ssid;
                queryString += "%7C";
                queryString += "ss:";
                queryString += listwifi.get(i).getSignalStrength();

            }
        } catch (Exception e) {
        }
        return queryString;
    }

    public String geofencing(Context ctx) throws PreyException {
        String url = getDeviceUrlApiv2(ctx).concat("/geofencing.json");
        PreyLogger.d("url:"+url);
        String sb=null;
        PreyRestHttpClient preyRestHttpClient=PreyRestHttpClient.getInstance(ctx);
        try{
            Map<String, String> params=null;
            PreyHttpResponse response=PreyRestHttpClient.getInstance(ctx).getAutentication(url, params);
            sb=response.getResponseAsString();
            if (sb!=null)
                sb = sb.trim();
        }catch(Exception e){
            PreyLogger.e("Error, causa:" + e.getMessage(), e);
            return null;
        }
        PreyLogger.d("cmd:" + sb);
        return sb;
    }

    public void sendEvent(final Context ctx,final int id  ) {
        new Thread() {
            public void run() {


                PreyPhone phone=new PreyPhone(ctx);
                String serialNumber=phone.getHardware().getSerialNumber();

                String version=PreyConfig.getPreyConfig(ctx).getPreyVersion();
                String sid=PreyConfig.getPreyConfig(ctx).getSessionId();

                String time = "" + new Date().getTime();
                try {
                    String page = FileConfigReader.getInstance(ctx).getPreyEventsLogs();;
                    PreyLogger.d("URL:"+page);
                    JSONObject jsonParam = new JSONObject();
                    jsonParam.put("enum", id);

                    JSONArray properties=new JSONArray();

                    JSONObject jsonSid = new JSONObject();
                    jsonSid.put("name", "sid");
                    jsonSid.put("value", sid);
                    properties.put(jsonSid);

                    JSONObject jsonSerial = new JSONObject();
                    jsonSerial.put("name", "sn");
                    jsonSerial.put("value", serialNumber);
                    properties.put(jsonSerial);


                    JSONObject jsonVersion = new JSONObject();
                    jsonVersion.put("name", "version");
                    jsonVersion.put("value", version);
                    properties.put(jsonVersion);

                    jsonParam.put("properties",properties);

                    PreyLogger.d("__________jsonParam:"+jsonParam.toString());

                    PreyRestHttpClient.getInstance(ctx).postJson(page, jsonParam);
                } catch (Exception e) {
                    PreyLogger.e("Error:" + e.getMessage(), e);
                }
            }
        }.start();
    }

    public void sendTree(final Context ctx,JSONObject json  ) throws PreyException{
        String uri = getDeviceUrlApiv2(ctx).concat("/data.json");
        PreyRestHttpClient.getInstance(ctx).postJsonAutentication(uri, json);
    }

    public int uploadFile(Context ctx, File file,String uploadID,long total)  throws PreyException{
        String uri = PreyConfig.getPreyConfig(ctx).getPreyUrl() + "upload/upload?uploadID=" + uploadID;
        return PreyRestHttpClient.getInstance(ctx).uploadFile(ctx,uri,file,total);
    }

    public FileretrievalDto uploadStatus(Context ctx,String uploadID)  throws Exception {
        FileretrievalDto dto=null;
        String uri = PreyConfig.getPreyConfig(ctx).getPreyUrl() + "upload/upload?uploadID=" + uploadID;
        PreyHttpResponse response = PreyRestHttpClient.getInstance(ctx).get(uri,null);
        String responseAsString = response.getResponseAsString();
        PreyLogger.d("uploadStatus resp:" + responseAsString);
        if (response.getStatusCode() == 200) {
            if (responseAsString != null ) {
                JSONObject jsnobject = new JSONObject(response.getResponseAsString());
                String id = jsnobject.getString("ID");
                String name = jsnobject.getString("Name");
                String size = jsnobject.getString("Size");
                String total = jsnobject.getString("Total");
                String status = jsnobject.getString("Status");
                String path = jsnobject.getString("Path");
                dto=new FileretrievalDto();
                dto.setFileId(id);
                dto.setName(name);
                dto.setSize(Long.parseLong(size));
                dto.setTotal(Long.parseLong(total));
                dto.setStatus(Integer.parseInt(status));
                dto.setPath(path);
            }
        }
        if (response.getStatusCode() == 404) {
            dto=new FileretrievalDto();
            dto.setStatus(response.getStatusCode());
        }
        return dto;
    }

    public String googlePlayVersion(Context ctx) {
        try {
            String uri = PreyConfig.getPreyConfig(ctx).getPreyGooglePlay();
            PreyHttpResponse response = PreyRestHttpClient.getInstance(ctx).get(uri, null);
            String responseAsString = response.getResponseAsString();
            int po = responseAsString.indexOf("softwareVersion\">");
            responseAsString = responseAsString.substring(po + 17);
            po = responseAsString.indexOf("</");
            responseAsString = responseAsString.substring(0, po);
            return responseAsString.trim();
        } catch (Exception e) {
            return null;
        }
    }

    public List<Device> devicesList(Context ctx){
        List <Device> list=new ArrayList<Device>();
        try {
            //String uri="http://localhost:8080/prey/json/devices_list.json";
            //PreyHttpResponse response = PreyRestHttpClient.getInstance(ctx).get(uri, null);
            //String responseAsString = response.getResponseAsString();
            String responseAsString = "[{\"name\":\"Michelsons-MacBook-Pro-PREY\",\"key\":\"fbcd56\",\"type\":\"Laptop\",\"description\":\"Apple MacBook, OS X Yosemite (10.10.3)\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":36,\"state\":\"ok\",\"connection_status_desc\":\"No requests yet\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Mac\",\"os_version\":\"10.10.3\",\"client_version\":null,\"icon\":\"mac-laptop\",\"reports_count\":44,\"delay\":2},{\"name\":\"Google SM-G900M\",\"key\":\"5e6ce6\",\"type\":\"Phone\",\"description\":\"Google SM-G900M\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":0,\"state\":\"deleted\",\"connection_status_desc\":\"Unreachable\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Android\",\"os_version\":\"5.0\",\"client_version\":null,\"icon\":\"android-phone\",\"reports_count\":0,\"delay\":25},{\"name\":\"mbair\",\"key\":\"a0b6c3\",\"type\":\"Laptop\",\"description\":\"Apple MacBook, OS X Yosemite (10.10.5)\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":1,\"state\":\"deleted\",\"connection_status_desc\":\"No requests yet\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Mac\",\"os_version\":\"10.10.5\",\"client_version\":null,\"icon\":\"mac-laptop\",\"reports_count\":1,\"delay\":25},{\"name\":\"cyh\",\"key\":\"1e443e\",\"type\":\"Laptop\",\"description\":\"Apple MacBook, OS X Yosemite (10.10.5)\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":20,\"state\":\"deleted\",\"connection_status_desc\":\"No requests yet\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Mac\",\"os_version\":\"10.10.5\",\"client_version\":null,\"icon\":\"mac-laptop\",\"reports_count\":23,\"delay\":2},{\"name\":\"Google SM-G900M\",\"key\":\"1d3be2\",\"type\":\"Phone\",\"description\":\"Google SM-G900M\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":0,\"state\":\"deleted\",\"connection_status_desc\":\"Unreachable\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Android\",\"os_version\":\"5.0\",\"client_version\":null,\"icon\":\"android-phone\",\"reports_count\":0,\"delay\":25},{\"name\":\"Google SM-G920I\",\"key\":\"cd820e\",\"type\":\"Phone\",\"description\":\"Google SM-G920I\",\"missing\":false,\"unreachable\":false,\"location\":null,\"unread_report\":0,\"state\":\"ok\",\"connection_status_desc\":\"Ready\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Android\",\"os_version\":\"5.1.1\",\"client_version\":null,\"icon\":\"android-phone\",\"reports_count\":0,\"delay\":25},{\"name\":\"Google XT1058\",\"key\":\"86040e\",\"type\":\"Phone\",\"description\":\"Google XT1058\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":0,\"state\":\"deleted\",\"connection_status_desc\":\"Unreachable\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Android\",\"os_version\":\"5.1\",\"client_version\":null,\"icon\":\"android-phone\",\"reports_count\":0,\"delay\":25},{\"name\":\"nyx\",\"key\":\"3c894f\",\"type\":\"Laptop\",\"description\":\"Apple MacBook, OS X Yosemite (10.10.5)\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":0,\"state\":\"deleted\",\"connection_status_desc\":\"No requests yet\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Mac\",\"os_version\":\"10.10.5\",\"client_version\":null,\"icon\":\"mac-laptop\",\"reports_count\":0,\"delay\":25},{\"name\":\"Google XT1058\",\"key\":\"1d1c40\",\"type\":\"Phone\",\"description\":\"Google XT1058\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":0,\"state\":\"deleted\",\"connection_status_desc\":\"Unreachable\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Android\",\"os_version\":\"5.1\",\"client_version\":null,\"icon\":\"android-phone\",\"reports_count\":0,\"delay\":25},{\"name\":\"Google XT1058\",\"key\":\"ed29c6\",\"type\":\"Phone\",\"description\":\"Google XT1058\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":0,\"state\":\"deleted\",\"connection_status_desc\":\"Unreachable\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Android\",\"os_version\":\"5.1\",\"client_version\":null,\"icon\":\"android-phone\",\"reports_count\":0,\"delay\":25}]";
            PreyLogger.d("devicesList resp:" + responseAsString);
            //if (response.getStatusCode() == 200) {
            if (true) {
                    if (responseAsString != null ) {
                        String json="{\"prey\":"+responseAsString+"}";
                        JSONObject jsonObject = new JSONObject(json);
                        JSONArray jsonArray = jsonObject.getJSONArray("prey");
                        for (int i = 0; i < jsonArray.length(); i++) {
                            String jsonCommand= jsonArray.get(i).toString();
                            JSONObject jsnobject =new JSONObject(jsonCommand);
                            String name = jsnobject.getString("name");
                            String key = jsnobject.getString("key");
                            String type = jsnobject.getString("type");
                            String description = jsnobject.getString("description");
                            String missing = jsnobject.getString("missing");
                            String unreachable = jsnobject.getString("unreachable");
                            String location = jsnobject.getString("location");
                            String unreadReport = jsnobject.getString("unread_report");
                            String state = jsnobject.getString("state");
                            String os = jsnobject.getString("os");
                            String osVersion = jsnobject.getString("os_version");
                            String clientVersion = jsnobject.getString("client_version");
                            String icon = jsnobject.getString("icon");
                            String reportsCount = jsnobject.getString("reports_count");
                            String delay = jsnobject.getString("delay");
                            Device device=new Device();
                            device.setName(name);
                            device.setKey(key);
                            device.setType(type);
                            device.setDescription(description);
                            device.setMissing(missing);
                            device.setUnreachable(unreachable);
                            device.setLocation(location);
                            device.setUnreadReport(unreadReport);
                            device.setState(state);
                            device.setOs(os);
                            device.setOsVersion(osVersion);
                            device.setClientVersion(clientVersion);
                            device.setIcon(icon);
                            device.setReportsCount(reportsCount);
                            device.setDelay(delay);
                            list.add(device);
                        }
                    }

            }
        } catch (Exception e) {
        }
        return list;
    }

    public ArrayList<DataMap> devicesListMap(Context ctx){
        ArrayList <DataMap> list=new ArrayList<DataMap>();
        try {
            //String uri="http://localhost:8080/prey/json/devices_list.json";
            //PreyHttpResponse response = PreyRestHttpClient.getInstance(ctx).get(uri, null);
            //String responseAsString = response.getResponseAsString();
            String responseAsString = "[{\"name\":\"Michelsons-MacBook-Pro-PREY\",\"key\":\"fbcd56\",\"type\":\"Laptop\",\"description\":\"Apple MacBook, OS X Yosemite (10.10.3)\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":36,\"state\":\"ok\",\"connection_status_desc\":\"No requests yet\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Mac\",\"os_version\":\"10.10.3\",\"client_version\":\"Prey 1.2.3\",\"icon\":\"mac-laptop\",\"reports_count\":44,\"delay\":2},{\"name\":\"Google SM-G900M\",\"key\":\"5e6ce6\",\"type\":\"Phone\",\"description\":\"Google SM-G900M\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":0,\"state\":\"deleted\",\"connection_status_desc\":\"Unreachable\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Android\",\"os_version\":\"5.0\",\"client_version\":\"Prey 1.2.2\",\"icon\":\"android-phone\",\"reports_count\":0,\"delay\":25},{\"name\":\"mbair\",\"key\":\"a0b6c3\",\"type\":\"Laptop\",\"description\":\"Apple MacBook, OS X Yosemite (10.10.5)\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":1,\"state\":\"deleted\",\"connection_status_desc\":\"No requests yet\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Mac\",\"os_version\":\"10.10.5\",\"client_version\":\"Prey 1.2.5\",\"icon\":\"mac-laptop\",\"reports_count\":1,\"delay\":25},{\"name\":\"cyh\",\"key\":\"1e443e\",\"type\":\"Laptop\",\"description\":\"Apple MacBook, OS X Yosemite (10.10.5)\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":20,\"state\":\"deleted\",\"connection_status_desc\":\"No requests yet\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Mac\",\"os_version\":\"10.10.5\",\"client_version\":\"Prey 1.2.3\",\"icon\":\"mac-laptop\",\"reports_count\":23,\"delay\":2},{\"name\":\"Google SM-G900M\",\"key\":\"1d3be2\",\"type\":\"Phone\",\"description\":\"Google SM-G900M\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":0,\"state\":\"deleted\",\"connection_status_desc\":\"Unreachable\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Android\",\"os_version\":\"5.0\",\"client_version\":\"Prey 1.2.3\",\"icon\":\"android-phone\",\"reports_count\":0,\"delay\":25},{\"name\":\"Google SM-G920I\",\"key\":\"cd820e\",\"type\":\"Phone\",\"description\":\"Google SM-G920I\",\"missing\":false,\"unreachable\":false,\"location\":null,\"unread_report\":0,\"state\":\"ok\",\"connection_status_desc\":\"Ready\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Android\",\"os_version\":\"5.1.1\",\"client_version\":\"Prey 1.2.3\",\"icon\":\"android-phone\",\"reports_count\":0,\"delay\":25},{\"name\":\"Google XT1058\",\"key\":\"86040e\",\"type\":\"Phone\",\"description\":\"Google XT1058\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":0,\"state\":\"deleted\",\"connection_status_desc\":\"Unreachable\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Android\",\"os_version\":\"5.1\",\"client_version\":\"Prey 1.2.3\",\"icon\":\"android-phone\",\"reports_count\":0,\"delay\":25},{\"name\":\"nyx\",\"key\":\"3c894f\",\"type\":\"Laptop\",\"description\":\"Apple MacBook, OS X Yosemite (10.10.5)\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":0,\"state\":\"deleted\",\"connection_status_desc\":\"No requests yet\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Mac\",\"os_version\":\"10.10.5\",\"client_version\":\"Prey 1.2.3\",\"icon\":\"mac-laptop\",\"reports_count\":0,\"delay\":25},{\"name\":\"Google XT1058\",\"key\":\"1d1c40\",\"type\":\"Phone\",\"description\":\"Google XT1058\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":0,\"state\":\"deleted\",\"connection_status_desc\":\"Unreachable\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Android\",\"os_version\":\"5.1\",\"client_version\":\"Prey 1.2.3\",\"icon\":\"android-phone\",\"reports_count\":0,\"delay\":25},{\"name\":\"Google XT1058\",\"key\":\"ed29c6\",\"type\":\"Phone\",\"description\":\"Google XT1058\",\"missing\":false,\"unreachable\":true,\"location\":null,\"unread_report\":0,\"state\":\"deleted\",\"connection_status_desc\":\"Unreachable\",\"status_info\":{},\"client_outdated\":true,\"last_checked_in\":null,\"last_checked_in_from_now\":null,\"os\":\"Android\",\"os_version\":\"5.1\",\"client_version\":\"Prey 1.2.3\",\"icon\":\"android-phone\",\"reports_count\":0,\"delay\":25}]";
            PreyLogger.d("devicesList resp:" + responseAsString);
            //if (response.getStatusCode() == 200) {
            if (true) {
                if (responseAsString != null ) {
                    String json="{\"prey\":"+responseAsString+"}";
                    JSONObject jsonObject = new JSONObject(json);
                    JSONArray jsonArray = jsonObject.getJSONArray("prey");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        String jsonCommand= jsonArray.get(i).toString();
                        JSONObject jsnobject =new JSONObject(jsonCommand);
                        String name = jsnobject.getString("name");
                        String key = jsnobject.getString("key");
                        String type = jsnobject.getString("type");
                        String description = jsnobject.getString("description");
                        String missing = jsnobject.getString("missing");
                        String unreachable = jsnobject.getString("unreachable");
                        String location = jsnobject.getString("location");
                        String unreadReport = jsnobject.getString("unread_report");
                        String state = jsnobject.getString("state");
                        String os = jsnobject.getString("os");
                        String osVersion = jsnobject.getString("os_version");
                        String clientVersion = jsnobject.getString("client_version");
                        String icon = jsnobject.getString("icon");
                        String reportsCount = jsnobject.getString("reports_count");
                        String delay = jsnobject.getString("delay");
                        DataMap device=new DataMap();
                        device.putString("name",name);
                        device.putString("key",key);
                        device.putString("type",type);
                        device.putString("description",description);
                        device.putString("missing",missing);
                        device.putString("unreachable",unreachable);
                        device.putString("location",location);
                        device.putString("unreadReport",unreadReport);
                        device.putString("state",state);
                        device.putString("os",os);
                        device.putString("osVersion",osVersion);
                        device.putString("clientVersion",clientVersion);
                        device.putString("icon",icon);
                        device.putString("reportsCount",reportsCount);
                        device.putString("delay",delay);
                        list.add(device);
                    }
                }

            }
        } catch (Exception e) {
        }
        return list;
    }
}