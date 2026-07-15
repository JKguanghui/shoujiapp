package com.example.englishcoach;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ModelDownloadManager {

    private static final String TAG = "ModelDownload";

    // Model URLs - Qwen2.5-0.5B Q4_K_M (recommended for 4GB phones)
    // Switch to 1.5B URL for 6GB+ phones
    private static final String QWEN_MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf";
    private static final String QWEN_MODEL_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf";
    private static final long QWEN_MODEL_SIZE = 380_000_000L; // ~380MB

    // Vosk model URL
    private static final String VOSK_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";
    private static final String VOSK_MODEL_NAME = "vosk-model-small-en-us-0.15";

    private Context context;
    private OkHttpClient client;
    private AtomicBoolean cancelled = new AtomicBoolean(false);
    private SharedPreferences prefs;

    public interface DownloadCallback {
        void onProgress(int progress, long downloaded, long total);
        void onSuccess(File file);
        void onError(String error);
        void onStatusUpdate(String status);
    }

    public ModelDownloadManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE);
        this.client = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build();
    }

    public static File getModelDir(Context context) {
        File dir = new File(context.getFilesDir(), "models");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getQwenModelFile(Context context) {
        return new File(getModelDir(context), QWEN_MODEL_NAME);
    }

    public static File getVoskModelDir(Context context) {
        return new File(getModelDir(context), VOSK_MODEL_NAME);
    }

    public static boolean isQwenReady(Context context) {
        File f = getQwenModelFile(context);
        return f.exists() && f.length() > 10_000_000; // at least 10MB
    }

    public static boolean isVoskReady(Context context) {
        File d = getVoskModelDir(context);
        return d.exists() && d.isDirectory() && new File(d, "conf/mfcc.conf").exists();
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    public void cancelDownload() {
        cancelled.set(true);
    }

    /**
     * Download Qwen model with resume support
     */
    public void downloadQwenModel(DownloadCallback callback) {
        cancelled.set(false);
        new Thread(() -> {
            File modelFile = getQwenModelFile(context);

            // Check if already downloaded
            if (modelFile.exists() && modelFile.length() > 10_000_000) {
                callback.onStatusUpdate("Model already downloaded");
                callback.onSuccess(modelFile);
                return;
            }

            // Check network
            if (!isNetworkAvailable()) {
                callback.onError("No network connection");
                return;
            }

            downloadWithResume(QWEN_MODEL_URL, modelFile, QWEN_MODEL_SIZE, callback);
        }).start();
    }

    /**
     * Download Vosk model (zip, extract after download)
     */
    public void downloadVoskModel(DownloadCallback callback) {
        cancelled.set(false);
        new Thread(() -> {
            File voskDir = getVoskModelDir(context);

            if (isVoskReady(context)) {
                callback.onStatusUpdate("Vosk model ready");
                callback.onSuccess(voskDir);
                return;
            }

            if (!isNetworkAvailable()) {
                callback.onError("No network connection");
                return;
            }

            File zipFile = new File(getModelDir(context), "vosk-model.zip");

            callback.onStatusUpdate("Downloading Vosk speech model...");
            downloadWithResume(VOSK_MODEL_URL, zipFile, 0, new DownloadCallback() {
                @Override
                public void onProgress(int progress, long downloaded, long total) {
                    callback.onProgress(progress, downloaded, total);
                }

                @Override
                public void onSuccess(File file) {
                    callback.onStatusUpdate("Extracting...");
                    try {
                        ZipUtils.unzip(file, getModelDir(context));
                        file.delete(); // delete zip after extraction
                        callback.onSuccess(voskDir);
                    } catch (IOException e) {
                        callback.onError("Extraction failed: " + e.getMessage());
                    }
                }

                @Override
                public void onError(String error) {
                    callback.onError(error);
                }

                @Override
                public void onStatusUpdate(String status) {
                    callback.onStatusUpdate(status);
                }
            });
        }).start();
    }

    private void downloadWithResume(String url, File targetFile, long expectedSize, DownloadCallback callback) {
        long existingSize = targetFile.exists() ? targetFile.length() : 0;

        try {
            Request.Builder reqBuilder = new Request.Builder().url(url);
            if (existingSize > 0) {
                reqBuilder.addHeader("Range", "bytes=" + existingSize + "-");
                callback.onStatusUpdate("Resuming download...");
            }

            Response response = client.newCall(reqBuilder.build()).execute();

            if (!response.isSuccessful() && response.code() != 206) {
                callback.onError("Server error: " + response.code());
                return;
            }

            ResponseBody body = response.body();
            if (body == null) {
                callback.onError("Empty response");
                return;
            }

            long contentLength = body.contentLength();
            long totalSize = (response.code() == 206) ? existingSize + contentLength : contentLength;
            if (expectedSize > 0) totalSize = expectedSize;

            // If server doesn't support resume, start fresh
            FileOutputStream fos;
            if (response.code() == 206) {
                fos = new FileOutputStream(targetFile, true); // append
            } else {
                fos = new FileOutputStream(targetFile); // overwrite
                existingSize = 0;
            }

            InputStream is = body.byteStream();
            byte[] buffer = new byte[16384];
            long downloaded = existingSize;
            int len;

            while ((len = is.read(buffer)) != -1) {
                if (cancelled.get()) {
                    fos.close();
                    is.close();
                    callback.onError("Download cancelled");
                    return;
                }
                fos.write(buffer, 0, len);
                downloaded += len;
                int progress = totalSize > 0 ? (int) (downloaded * 100 / totalSize) : 0;
                callback.onProgress(progress, downloaded, totalSize);
            }

            fos.flush();
            fos.close();
            is.close();

            callback.onSuccess(targetFile);

        } catch (IOException e) {
            Log.e(TAG, "Download failed", e);
            callback.onError(e.getMessage());
        }
    }
}
