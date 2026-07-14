package com.example.englishcoach;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ModelDownloadManager {

    private static final String MODEL_URL = "https://huggingface.co/HuggingFaceTB/SmolLM-135M-Instruct-GGUF/resolve/main/smollm-135m-instruct-q8_0.gguf";

    private static final String TAG = "DownloadManager";
    private static final String MODEL_DIR = "models";
    private static final String MODEL_FILENAME = "smollm-135m.gguf";

    private Context context;
    private OkHttpClient client;

    public interface DownloadCallback {
        void onProgress(int progress);
        void onSuccess();
        void onError(String error);
    }

    public ModelDownloadManager(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build();
    }

    public static File getModelFile(Context context) {
        File modelDir = new File(context.getFilesDir(), MODEL_DIR);
        return new File(modelDir, MODEL_FILENAME);
    }

    public static boolean isModelReady(Context context) {
        File modelFile = getModelFile(context);
        return modelFile.exists() && modelFile.length() > 0;
    }

    public void downloadModel(DownloadCallback callback) {
        new Thread(() -> {
            File modelDir = new File(context.getFilesDir(), MODEL_DIR);
            if (!modelDir.exists()) modelDir.mkdirs();
            File modelFile = new File(modelDir, MODEL_FILENAME);

            if (modelFile.exists() && modelFile.length() > 0) {
                callback.onSuccess();
                return;
            }

            InputStream is = null;
            FileOutputStream fos = null;
            try {
                Request request = new Request.Builder().url(MODEL_URL).build();
                Response response = client.newCall(request).execute();

                if (!response.isSuccessful()) {
                    callback.onError("server error " + response.code());
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    callback.onError("empty response body");
                    return;
                }

                long contentLength = body.contentLength();
                is = body.byteStream();
                fos = new FileOutputStream(modelFile);
                byte[] buffer = new byte[8192];
                long downloaded = 0;
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                    downloaded += len;
                    int progress = contentLength > 0 ? (int) (downloaded * 100 / contentLength) : 0;
                    callback.onProgress(progress);
                }
                fos.flush();
                callback.onSuccess();
            } catch (IOException e) {
                Log.e(TAG, "download failed", e);
                if (modelFile.exists()) modelFile.delete();
                callback.onError(e.getMessage());
            } finally {
                try { if (is != null) is.close(); } catch (IOException ignored) {}
                try { if (fos != null) fos.close(); } catch (IOException ignored) {}
            }
        }).start();
    }
}
