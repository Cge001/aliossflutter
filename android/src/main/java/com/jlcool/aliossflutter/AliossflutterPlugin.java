package com.jlcool.aliossflutter;

import android.util.Log;

import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.ClientException;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.ServiceException;
import com.alibaba.sdk.android.oss.callback.OSSCompletedCallback;
import com.alibaba.sdk.android.oss.callback.OSSProgressCallback;
import com.alibaba.sdk.android.oss.common.OSSConstants;
import com.alibaba.sdk.android.oss.common.OSSLog;
import com.alibaba.sdk.android.oss.common.auth.OSSAuthCredentialsProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationToken;
import com.alibaba.sdk.android.oss.common.utils.IOUtils;
import com.alibaba.sdk.android.oss.internal.OSSAsyncTask;
import com.alibaba.sdk.android.oss.model.DeleteObjectRequest;
import com.alibaba.sdk.android.oss.model.DeleteObjectResult;
import com.alibaba.sdk.android.oss.model.GeneratePresignedUrlRequest;
import com.alibaba.sdk.android.oss.model.GetObjectRequest;
import com.alibaba.sdk.android.oss.model.GetObjectResult;
import com.alibaba.sdk.android.oss.model.PutObjectRequest;
import com.alibaba.sdk.android.oss.model.PutObjectResult;


import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Handler;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * AliossflutterPlugin
 */
public class AliossflutterPlugin implements MethodCallHandler {
    String endpoint;
    static Registrar registrar;

    static MethodChannel channel;
    Result _result;
    MethodCall _call;
    private static OSS oss;

    private AliossflutterPlugin(Registrar registrar) {
        this.registrar = registrar;
    }

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        channel = new MethodChannel(registrar.messenger(), "aliossflutter");
        channel.setMethodCallHandler(new AliossflutterPlugin(registrar));
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        _result = result;
        _call = call;

        switch (call.method) {
            case "upload":
                upload(call);
                break;
            case "download":
                download(call);
                break;
            case "init":
                init();
                break;
            case "signurl":
                signUrl(call);
                break;
            case "des":
                des(call);
                break;
            case "delete":
                delete(call);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private void init() {
        endpoint = _call.argument("endpoint");
        final String stsServer = _call.argument("stsserver");
        final String crypt_key = _call.argument("cryptkey");
        final String _id = _call.argument("id");
        final OSSCredentialProvider credentialProvider = new OSSFederationCredentialProvider() {
            @Override
            public OSSFederationToken getFederationToken() {
                try {
                    URL stsUrl = new URL(stsServer);
                    HttpURLConnection conn = (HttpURLConnection) stsUrl.openConnection();
                    InputStream input = conn.getInputStream();
                    String jsonText = IOUtils.readStreamAsString(input, OSSConstants.DEFAULT_CHARSET_NAME);

                    if (!"".equals(crypt_key)) {
                        JSONObject jsonObj = new JSONObject(jsonText);
                        String dec = jsonObj.getString("Data");
                        SecretUtils.PASSWORD_CRYPT_KEY = crypt_key;
                        jsonText = new String(SecretUtils.decryptMode(dec));
                    }
                    JSONObject jsonObjs = new JSONObject(jsonText);
//                    {
//                        "StatusCode": 200,
//                            "AccessKeyId":"STS.iA645eTOXEqP3cg3VeHf",
//                            "AccessKeySecret":"rV3VQrpFQ4BsyHSAvi5NVLpPIVffDJv4LojUBZCf",
//                            "Expiration":"2015-11-03T09:52:59Z",
//                            "SecurityToken":"CAES7QIIARKAAZPlqaN9ILiQZPS+JDkS/GSZN45RLx4YS/p3OgaUC+oJl3XSlbJ7StKpQ...."
//                      }
                    String ak = jsonObjs.getString("AccessKeyId");
                    String sk = jsonObjs.getString("AccessKeySecret");
                    String token = jsonObjs.getString("SecurityToken");
                    String expiration = jsonObjs.getString("Expiration");
                    return new OSSFederationToken(ak, sk, token, expiration);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
        final ClientConfiguration conf = new ClientConfiguration();
        conf.setConnectionTimeout(15 * 1000); // 连接超时时间，默认15秒
        conf.setSocketTimeout(15 * 1000); // Socket超时时间，默认15秒
        conf.setMaxConcurrentRequest(5); // 最大并发请求数，默认5个
        conf.setMaxErrorRetry(2); // 失败后最大重试次数，默认2次
        new Thread(new Runnable() {
            @Override
            public void run() {
                oss = new OSSClient(registrar.context(), endpoint, credentialProvider, conf);
                Map<String, String> m1 = new HashMap();
                m1.put("result", "success");
                m1.put("id", _id);
                channel.invokeMethod("onInit", m1);
            }
        }).start();


    }

    private void upload(final MethodCall call) {
        final String bucket = call.argument("bucket");
        final String file = call.argument("file");
        final String key = call.argument("key");
        final String _id = call.argument("id");
        PutObjectRequest put = new PutObjectRequest(bucket, key, file);
        put.setProgressCallback(new OSSProgressCallback<PutObjectRequest>() {
            @Override
            public void onProgress(PutObjectRequest request, long currentSize, long totalSize) {
                Log.d("onProgress", "currentSize: " + currentSize + " totalSize: " + totalSize);
                Map<String, String> m1 = new HashMap<String, String>();
                m1.put("currentSize", String.valueOf(currentSize));
                m1.put("totalSize", String.valueOf(totalSize));
                m1.put("id", _id);
                channel.invokeMethod("onProgress", m1);
            }
        });
        if (oss == null) {
            Map<String, String> m1 = new HashMap();
            m1.put("result", "fail");
            m1.put("id", _id);
			m1.put("key", key);
            m1.put("message", "请先初始化");
            channel.invokeMethod("onUpload", m1);
        } else {
            OSSAsyncTask task = oss.asyncPutObject(put, new OSSCompletedCallback<PutObjectRequest, PutObjectResult>() {
                        @Override
                        public void onSuccess(PutObjectRequest request, PutObjectResult result) {
                            Log.d("onSuccess", "onSuccess");
                            Log.d("ETag", result.getETag());
                            Log.d("RequestId", result.getRequestId());
                            Map<String, String> m1 = new HashMap();
                            m1.put("result", "success");
                            m1.put("tag", result.getETag());
                            m1.put("id", _id);
                            m1.put("key", key);
                            m1.put("requestid", result.getRequestId());
                            channel.invokeMethod("onUpload", m1);
                        }

                        @Override
                        public void onFailure(PutObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                            // 请求异常
                            Map<String, String> m1 = new HashMap();
                            if (clientExcepion != null) {
                                // 本地异常如网络异常等
                                clientExcepion.printStackTrace();
                                m1.put("result", "fail");
                                m1.put("id", _id);
                                m1.put("key", key);
                                m1.put("message", clientExcepion.getMessage());
                            }
                            if (serviceException != null) {
                                // 服务异常
                                Log.e("ErrorCode", serviceException.getErrorCode());
                                Log.e("RequestId", serviceException.getRequestId());
                                Log.e("HostId", serviceException.getHostId());
                                Log.e("RawMessage", serviceException.getRawMessage());


                                m1.put("result", "fail");
                                m1.put("id", _id);
                                m1.put("key", key);
                                m1.put("message", serviceException.getRawMessage());
                            }
                            channel.invokeMethod("onUpload", m1);
                        }
                    }
            );
        }
    }

    private void download(final MethodCall call) {
		final String _key = call.argument("key");
		final String _id = call.argument("id");
        if (oss == null) {
            Map<String, String> m1 = new HashMap();
            m1.put("result", "fail");
			m1.put("id", _id);
            m1.put("message", "请先初始化");
            channel.invokeMethod("onDownload", m1);
        } else {
//      try {
            final String _bucket = call.argument("bucket");
            
            String process = call.argument("process");
            
            final String _path = call.argument("path");
            GetObjectRequest get = new GetObjectRequest(_bucket, _key);
            if (!"".equals(process)) {
                get.setxOssProcess(process);
            }
//设置下载进度回调
            get.setProgressListener(new OSSProgressCallback<GetObjectRequest>() {
                @Override
                public void onProgress(GetObjectRequest request, long currentSize, long totalSize) {
                    Map<String, Long> m1 = new HashMap<String, Long>();
                    m1.put("currentSize", currentSize);
                    m1.put("totalSize", totalSize);
                    channel.invokeMethod("onProgress", m1);
                }
            });
            OSSAsyncTask task = oss.asyncGetObject(get, new OSSCompletedCallback<GetObjectRequest, GetObjectResult>() {
                @Override
                public void onSuccess(GetObjectRequest request, GetObjectResult result) {
                    // 请求成功
                    InputStream inputStream = result.getObjectContent();
                    byte[] buffer = new byte[2048];
                    int len;
                    OutputStream os = null;
                    try {
                        os = new FileOutputStream(_path);
                        while ((len = inputStream.read(buffer)) != -1) {
                            os.write(buffer, 0, len);
                        }
                        Map<String, String> m1 = new HashMap();
                        m1.put("result", "success");
                        m1.put("id", _id);
                        m1.put("path", _path);
                        channel.invokeMethod("onDownload", m1);
                        _result.success(m1);
                        os.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Map<String, String> m1 = new HashMap();
                        m1.put("result", "fail");
                        m1.put("id", _id);
                        m1.put("path", _path);
                        m1.put("message", e.getMessage());
                        channel.invokeMethod("onDownload", m1);
                        _result.success(m1);
                    } finally {
                        try {
                            os.close();
                            inputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void onFailure(GetObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                    // 请求异常
                    if (clientExcepion != null) {
                        // 本地异常如网络异常等
                        clientExcepion.printStackTrace();
                        Map<String, String> m1 = new HashMap();
                        m1.put("result", "fail");
                        m1.put("id", _id);
                        m1.put("path", _path);
                        m1.put("message", clientExcepion.getMessage());
                        channel.invokeMethod("onDownload", m1);
                        _result.success(m1);
                    }
                    if (serviceException != null) {
                        // 服务异常
                        Log.e("ErrorCode", serviceException.getErrorCode());
                        Log.e("RequestId", serviceException.getRequestId());
                        Log.e("HostId", serviceException.getHostId());
                        Log.e("RawMessage", serviceException.getRawMessage());
                        Map<String, String> m1 = new HashMap();
                        m1.put("result", "fail");
                        m1.put("id", _id);
                        m1.put("path", _path);
                        m1.put("message", String.valueOf(serviceException.getStatusCode()));
                        channel.invokeMethod("onDownload", m1);
                        _result.success(m1);
                    }
                }
            });
        }
    }
    //删除
    private void delete(final MethodCall call) {
        final String _key = call.argument("key");
        final String _id = call.argument("id");
        if (oss == null) {
            Map<String, String> m1 = new HashMap();
            m1.put("result", "fail");
            m1.put("id", _id);
            m1.put("message", "请先初始化");
            channel.invokeMethod("onDelete", m1);
        } else {
            final String _bucket = call.argument("bucket");
// 创建删除请求
            DeleteObjectRequest delete = new DeleteObjectRequest(_bucket, _key);
// 异步删除
            OSSAsyncTask deleteTask = oss.asyncDeleteObject(delete, new OSSCompletedCallback<DeleteObjectRequest, DeleteObjectResult>() {
                @Override
                public void onSuccess(DeleteObjectRequest request, DeleteObjectResult result) {
                    Map<String, String> m1 = new HashMap();
                    m1.put("result", "success");
                    m1.put("id", _id);
                    m1.put("key", _key);
                    channel.invokeMethod("onDelete", m1);
                    _result.success(m1);
                }

                @Override
                public void onFailure(DeleteObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                    // 请求异常
                    if (clientExcepion != null) {
                        Map<String, String> m1 = new HashMap();
                        m1.put("result", "fail");
                        m1.put("id", _id);
                        m1.put("key", _key);
                        m1.put("message", clientExcepion.getMessage());
                        channel.invokeMethod("onDelete", m1);
                        _result.success(m1);
                    }
                    if (serviceException != null) {
                        // 服务异常
                        Log.e("ErrorCode", serviceException.getErrorCode());
                        Log.e("RequestId", serviceException.getRequestId());
                        Log.e("HostId", serviceException.getHostId());
                        Log.e("RawMessage", serviceException.getRawMessage());
                        Map<String, String> m1 = new HashMap();
                        m1.put("result", "fail");
                        m1.put("id", _id);
                        m1.put("key", _key);
                        m1.put("message", String.valueOf(serviceException.getStatusCode()));
                        channel.invokeMethod("onDelete", m1);
                        _result.success(m1);
                    }
                }

            });
        }
        }
    //签名url
    private void signUrl(final MethodCall call) {
		final String _id = call.argument("id");
		final String _key = call.argument("key");
        if (oss == null) {
            Map<String, String> m1 = new HashMap();
            m1.put("result", "fail");
			m1.put("id", _id);
			m1.put("_key", _key);
            m1.put("message", "请先初始化");
            channel.invokeMethod("onSign", m1);
        } else {
//      try {
            final String _bucket = call.argument("bucket");
            
            final String _type = call.argument("type");
            
            final Long _interval = Long.parseLong(call.argument("interval").toString());
            Map<String, String> m1 = new HashMap();

            if ("0".equals(_type)) {
                m1.put("result", "success");
                m1.put("id", _id);
                m1.put("url", oss.presignPublicObjectURL(_bucket, _key));
                channel.invokeMethod("onSign", m1);
                _result.success(m1);
            } else if ("1".equals(_type)) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Map<String, String> m1 = new HashMap();
                            m1.put("result", "success");
                            String url = oss.presignConstrainedObjectURL(_bucket, _key, _interval);
                            m1.put("url", url);
                            m1.put("id", _id);
                            channel.invokeMethod("onSign", m1);
                            _result.success(m1);
                        } catch (ClientException e) {
                            Map<String, String> m1 = new HashMap();
                            m1.put("result", "fail");
                            m1.put("message", e.toString());
                            m1.put("id", _id);
                            channel.invokeMethod("onSign", m1);
                            _result.success(m1);
                        }
                    }
                }).start();
            }else{
                m1.put("result", "fail");
                m1.put("message", "签名类型错误");
                m1.put("id", _id);
                channel.invokeMethod("onSign", m1);
                _result.success(m1);
            }

        }
    }
    //3des 加密
    private void des(final MethodCall call) {
        final String _key = call.argument("key");
        //encrypt or decrypt
        final String _type = call.argument("type");
        final  String _data=call.argument("data");
        SecretUtils.PASSWORD_CRYPT_KEY = _key;
        String _res = "";
        if(_type.equals("encrypt")){
            _res=new String(SecretUtils.encryptMode(_data));
        }else if(_type.equals("decrypt")){
            _res=new String(SecretUtils.decryptMode(_data));
        }
        _result.success(_res.replaceAll("\r|\n", ""));
    }
}
