package smile.identity.core;

//export package -tbd
//package com.smileidentity.services.WebApi

import java.util.HashMap;
import java.util.Map;

// json converter
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;

// apache http client
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

// zip file
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;

import java.io.File;
import java.io.FileInputStream;

import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.Header;

public class WebApi {
  private String partner_id;
  private String api_key;

  private JSONObject partnerParams;
  private JSONArray images;
  private JSONObject idInfo;

  private Boolean returnJobStatus;
  private Boolean returnHistory;
  private Boolean returnImages;

  private String url;
  private Integer sid_server;
  private String callbackUrl;
  private String sec_key;
  private long timestamp;

  private Utilities utilitiesConnection;

  public WebApi(String partner_id, String default_callback, String api_key, Integer sid_server) {
    try {
      this.partner_id = partner_id.toString();
      this.callbackUrl = default_callback.trim();
      this.api_key = api_key;

      if(sid_server == 0) {
        url = "https://3eydmgh10d.execute-api.us-west-2.amazonaws.com/test";
      } else if (sid_server == 1) {
        url = "https://la7am6gdm8.execute-api.us-west-2.amazonaws.com/prod";
      }

      this.sid_server = sid_server;
      this.url = url;

    } catch (Exception e) {
      throw e;
    }
  }

  public String submit_job(String partner_params, String images_params, String id_info_params, String options_params) throws Exception {
    try {

      JSONParser parser = new JSONParser();
      JSONObject partnerParams = (JSONObject) parser.parse(partner_params);

      JSONObject idInfo;
      if(id_info_params != null && !id_info_params.trim().isEmpty()) {
        idInfo = (JSONObject) parser.parse(id_info_params);
      } else {
        idInfo = fillInIdInfo();
      }

      Long job_type = (Long) partnerParams.get("job_type");
      if(job_type == 5) {
        return callIDApi(partnerParams, idInfo);
      }

      JSONArray images = (JSONArray) parser.parse(images_params);
      if(options_params != null && !options_params.trim().isEmpty()) {
        JSONObject options = (JSONObject) parser.parse(options_params);
        extractOptions(options);
      } else {
        fillInOptions();
      }

      validateImages(images);

      if(job_type == 1) {
        validateEnrollWithId(images, idInfo);
      }

      validateReturnData();

      this.partnerParams = partnerParams;
      this.images = images;
      this.idInfo = idInfo;

      this.timestamp = System.currentTimeMillis();
      this.sec_key = determineSecKey();

      return setupRequests();
    } catch (Exception e) {
      throw e;
    }
  }

  public String submit_job(String partner_params, String id_info_params) throws Exception {
    String response = null;
    try {

      JSONParser parser = new JSONParser();
      JSONObject partnerParams = (JSONObject) parser.parse(partner_params);

      JSONObject idInfo;
      if(id_info_params == null && id_info_params.trim().isEmpty()) {
        throw new IllegalArgumentException("You need to send through a vaild ID Information payload");
      } else {
        idInfo = (JSONObject) parser.parse(id_info_params);
      }

      Long job_type = (Long) partnerParams.get("job_type");
      if(job_type == 5) {
        response = callIDApi(partnerParams, idInfo);
      } else {
        throw new IllegalArgumentException("You need to send through more parameters");
      }

      this.partnerParams = partnerParams;
      this.idInfo = idInfo;

      this.timestamp = System.currentTimeMillis();
      this.sec_key = determineSecKey();

    } catch (Exception e) {
      throw e;
    }
     return response;
  }

  public String get_job_status(String partner_params, String options) throws Exception {
    String response = null;

    try {
      JSONParser parser = new JSONParser();
      JSONObject partnerParams = (JSONObject) parser.parse(partner_params);

      String user_id = (String) partnerParams.get("user_id");
      String job_id = (String) partnerParams.get("job_id");

      Utilities utilities = new Utilities(partner_id, api_key, sid_server);
      response = utilities.get_job_status(user_id, job_id, options);
    } catch (Exception e) {
      throw e;
    }

    return response;
  }

  private String callIDApi(JSONObject partnerParams, JSONObject idInfo) throws Exception {
    String response = null;
    try {
      IDApi connection = new IDApi(partner_id, api_key, sid_server);
      response = connection.submit_job(partnerParams.toString(), idInfo.toString());
    } catch (Exception e) {
      throw e;
    }

    return response;
  }

  private void validateImages(JSONArray images) throws Exception {
    try {
      if(images.size() < 1) {
        throw new IllegalArgumentException("You need to send through at least one selfie image");
      }

      Integer counter = 0;
      for (Object o : images) {
        Long image_type_id = (Long) ((JSONObject)o).get("image_type_id");
        if (image_type_id == 0 || image_type_id == 2)  {
          counter = counter + 1;
        }
      }

      if(counter < 1) {
        throw new IllegalArgumentException("You need to send through at least one selfie image");
      }
    } catch (Exception e) {
      throw e;
    }
  }

  private void validateEnrollWithId(JSONArray images, JSONObject idInfo) throws Exception {
    String entered = (String) ((JSONObject)idInfo).get("entered");

    Integer counter = 0;
    for (Object o : images) {
      Long image_type_id = (Long) ((JSONObject)o).get("image_type_id");

      if ((image_type_id == 1) || (image_type_id == 3) || (entered.equals("true")))  {
        counter = counter + 1;
      }
    }

    if(counter < 1) {
      throw new IllegalArgumentException("You are attempting to complete a job type 1 without providing an id card image or id info");
    }
  }

  private void validateReturnData() throws Exception {
    if(this.callbackUrl.trim().isEmpty() && this.returnJobStatus == false) {
      throw new IllegalArgumentException("Please choose to either get your response via the callback or job status query");
    }
  }

  private void extractOptions(JSONObject options) throws Exception {

    String optionalCallback = (String) ((JSONObject)options).get("optional_callback");
    Boolean returnJobStatus = (Boolean) ((JSONObject)options).get("return_job_status");
    Boolean returnHistory = (Boolean) ((JSONObject)options).get("return_history");
    Boolean returnImages = (Boolean) ((JSONObject)options).get("return_images");

    if(optionalCallback != null && !optionalCallback.trim().isEmpty()) {
      this.callbackUrl = optionalCallback;
    }

    this.returnJobStatus = returnJobStatus;
    this.returnHistory = returnHistory;
    this.returnImages = returnImages;
  }

  private void fillInOptions() throws Exception {
    try {
      Boolean returnJobStatus = false;
      Boolean returnHistory = false;
      Boolean returnImages = false;

      this.returnJobStatus = returnJobStatus;
      this.returnHistory = returnHistory;
      this.returnImages = returnImages;
    } catch(Exception e) {
      throw e;
    }
  }

  private JSONObject fillInIdInfo() throws Exception {
    JSONObject obj = new JSONObject();
    try {
      obj.put("entered", "false");
    } catch(Exception e) {
      throw e;
    }

    return obj;
  }

  private String determineSecKey() throws Exception {
    Signature connection = new Signature(partner_id, api_key);
    String secKey = "";
    JSONParser parser = new JSONParser();

    try {
      String signatureJsonStr = connection.generate_sec_key(timestamp);

      JSONObject signature = (JSONObject) parser.parse(signatureJsonStr);
      secKey = (String) signature.get("sec_key");
    } catch(Exception e) {
      throw e;
    }

    return secKey;
  }

  private String setupRequests() throws Exception {
    String res = null;
    try {
      String prepUploadUrl = url + "/upload";

      HttpClient client = new DefaultHttpClient();
      HttpPost post = new HttpPost(prepUploadUrl.trim());
      JSONObject uploadBody = configurePrepUploadJson();
      StringEntity entityForPost = new StringEntity(uploadBody.toString());
      post.setHeader("content-type", "application/json");
      post.setEntity(entityForPost);

      HttpResponse response = client.execute(post);
      final int statusCode = response.getStatusLine().getStatusCode();
      String strResult = readHttpResponse(response);

      if (statusCode != 200) {
        final String msg = String.format("Failed to post entity to %s, response=%d:%s - %s",
          prepUploadUrl, statusCode, response.getStatusLine().getReasonPhrase(), strResult);
        throw new RuntimeException(msg);
      } else {
        JSONParser parser = new JSONParser();
        JSONObject responseJson = (JSONObject) parser.parse(strResult);
        String uploadUrl = responseJson.get("upload_url").toString();
        String smileJobId = responseJson.get("smile_job_id").toString();

        JSONObject infoJson = configureInfoJson(uploadUrl);
        ByteArrayOutputStream baos = zipUpFile(infoJson);
        uploadFile(uploadUrl, baos);

        if(returnJobStatus == true) {
          Utilities utilitiesConnection = new Utilities(partner_id, api_key, sid_server);
          this.utilitiesConnection = utilitiesConnection;

          Integer counter = 0;
          JSONObject jsonJobStatusResponse = pollJobStatus(counter);
          jsonJobStatusResponse.put("success", true);
          jsonJobStatusResponse.put("smile_job_id", smileJobId);

          String jobStatusResponse = jsonJobStatusResponse.toString();

          res = jobStatusResponse;
        } else {
          JSONObject successResponse = new JSONObject();
          successResponse.put("success", true);
          successResponse.put("smile_job_id", smileJobId);
          res = successResponse.toString();
        }
      }
    } catch(Exception e) {
      throw e;
    }
    return res;
  }

  private JSONObject configurePrepUploadJson() throws Exception{
    JSONObject body = new JSONObject();
    try {
      body.put("file_name", "selfie.zip");
      body.put("timestamp", timestamp);
      body.put("sec_key", sec_key);
      body.put("smile_client_id", partner_id);
      body.put("partner_params", partnerParams);
      body.put("model_parameters", new JSONObject());
      body.put("callback_url", callbackUrl);
    } catch(Exception e) {
      throw e;
    }

    return body;
  }

  private String readHttpResponse(HttpResponse response) throws Exception {
    try {
      BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
      StringBuffer result = new StringBuffer();
      String line = "";
      while ((line = rd.readLine()) != null) {
        result.append(line);
      }
      return result.toString();
    } catch (Exception e) {
      throw e;
    }
  }

  private JSONObject configureInfoJson(String uploadUrl) {
    JSONObject json = new JSONObject();
    try {
      JSONObject api_version = new JSONObject();
      api_version.put("buildNumber", new Integer(0));
      api_version.put("majorVersion", new Integer(2));
      api_version.put("minorVersion", new Integer(0));

      JSONObject package_information = new JSONObject();
      package_information.put("apiVersion", api_version);
      package_information.put("language", "java");

      JSONObject userData = new JSONObject();
      userData.put("isVerifiedProcess", new Boolean(false));
      userData.put("name", "");
      userData.put("fbUserID", "");
      userData.put("firstName", "Bill");
      userData.put("lastName", "");
      userData.put("gender", "");
      userData.put("email", "");
      userData.put("phone", "");
      userData.put("countryCode", "+");
      userData.put("countryName", "");

      JSONObject misc_information = new JSONObject();
      misc_information.put("sec_key", sec_key);
      misc_information.put("retry", "false");
      misc_information.put("partner_params", partnerParams);
      misc_information.put("timestamp", timestamp);
      misc_information.put("file_name", "selfie.zip");
      misc_information.put("smile_client_id", partner_id);
      misc_information.put("callback_url", callbackUrl);
      misc_information.put("userData", userData);

      json.put("package_information", package_information);
      json.put("misc_information", misc_information);
      json.put("id_info", idInfo);
      json.put("images", configureImagePayload(images));
      json.put("server_information", uploadUrl);
    } catch(Exception e) {
      throw e;
    }
    return json;
  }

  private JSONArray configureImagePayload(JSONArray images) {
    JSONArray imagePayload = new JSONArray();

    for(Object o: images) {
      JSONObject imageObject = new JSONObject();
      if ( o instanceof JSONObject ) {
        Long image_type_id = (Long) ((JSONObject)o).get("image_type_id");
        imageObject.put("image_type_id", ((JSONObject)o).get("image_type_id"));

        if( image_type_id == 0 || image_type_id == 1 ) {
          imageObject.put("image", "");
          String filePath = ((JSONObject)o).get("image").toString();
          String fileName = new File(filePath).getName();
          imageObject.put("file_name", fileName);
        } else {
          imageObject.put("image", ((JSONObject)o).get("image") );
          imageObject.put("file_name", "");
        }
      }
      imagePayload.add(imageObject);
    }
    return imagePayload;
  }

  private ByteArrayOutputStream zipUpFile(JSONObject infoJson) throws Exception {
    // http://www.avajava.com/tutorials/lessons/how-can-i-create-a-zip-file-from-a-set-of-files.html
    // https://stackoverflow.com/questions/23612864/create-a-zip-file-in-memory
    ByteArrayOutputStream baos = null;
    try {
      baos = new ByteArrayOutputStream();
      ZipOutputStream zos = new ZipOutputStream(baos);

      ZipEntry entry = new ZipEntry("info.json");
      zos.putNextEntry(entry);
      zos.write(infoJson.toString().getBytes());
      zos.closeEntry();

      for(Object o: images) {
        if (o instanceof JSONObject) {

          Long image_type_id = (Long) ((JSONObject)o).get("image_type_id");
          if(image_type_id == 0 || image_type_id == 1 ) {
            String fileName = ((JSONObject)o).get("image").toString();
            File file = new File(fileName);
            FileInputStream fis = new FileInputStream(file);

            ZipEntry imageEntry = new ZipEntry(file.getName());
            zos.putNextEntry(imageEntry);

            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
              zos.write(bytes, 0, length);
            }

            zos.closeEntry();
            baos.close();
            fis.close();
          }
        }
      }
      zos.flush();
      zos.close();
    } catch (Exception e) {
      throw e;
    }
    return baos;
  }

  private void uploadFile(String awsUrl, ByteArrayOutputStream baos) throws Exception {
    try {
      CloseableHttpClient httpclient = HttpClients.createDefault();
      HttpPut putRequest = new HttpPut(awsUrl.trim().toString());
      putRequest.setHeader("content-type", "application/zip");
      putRequest.setEntity(new ByteArrayEntity(baos.toByteArray()));

      HttpResponse response = httpclient.execute(putRequest);
      final int statusCode = response.getStatusLine().getStatusCode();
      String strResult = readHttpResponse(response);

      if (statusCode != 200) {
        final String msg = String.format("Failed to post entity to %s, response=%d:%s - %s",
          awsUrl, statusCode, response.getStatusLine().getReasonPhrase(), strResult);
        throw new RuntimeException(msg);
      }
    } catch( Exception e) {
      throw e;
    }
  }

  private JSONObject pollJobStatus(Integer counter) throws Exception {
    Boolean job_complete = false;
    JSONObject responseJson = null;
    String responseStr = null;

    try {
      counter = counter + 1;
      if(counter < 4) {
        Thread.sleep(2000);
      } else {
        Thread.sleep(4000);
      }

      String user_id = (String) partnerParams.get("user_id");
      String job_id = (String) partnerParams.get("job_id");

      String jobStatusOptions = new Options(returnHistory, returnImages).get();
      responseStr = utilitiesConnection.get_job_status(user_id, job_id, jobStatusOptions);

      JSONParser parser = new JSONParser();
      responseJson = (JSONObject) parser.parse(responseStr);

      job_complete = (Boolean) responseJson.get("job_complete");
      if (job_complete == false && counter < 20) {
        responseJson = pollJobStatus(counter);
      }
    } catch (Exception e) {
      throw e;
    }
    return responseJson;
  }
}
