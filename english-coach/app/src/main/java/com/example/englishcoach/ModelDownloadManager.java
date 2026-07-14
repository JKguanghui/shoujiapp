package com.example.englishcoach;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ModelDownloadManager {

    // 请替换为你真实的模型文件直链（例如 GitHub Releases 的下载地址）
    private static final String MODEL_URL = "https://github.com/yourusername/english-coach/releases/download/v1.0/smollm-135m.gguf";
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

    public void downloadModel(DownloadCallback callback) {
        new Thread(() -> {
            try {
                File modelDir = new File(context.getFilesDir(), "models");
                if (!modelDir.exists()) modelDir.mkdirs();
                File modelFile = new File(modelDir, "smollm-135m.gguf");

                Request request = new Request.Builder().url(MODEL_URL).build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    callback.onError("服务器响应错误 " + response.code());
                    return;
                }

                long contentLength = response.body().contentLength();
                InputStream is = response.body().byteStream();
                FileOutputStream fos = new FileOutputStream(modelFile);
                byte[] buffer = new byte[4096];
                long downloaded = 0;
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                    downloaded += len;
                    int progress = contentLength > 0 ? (int) (downloaded * 100 / contentLength) : 0;
                    callback.onProgress(progress);
                }
                fos.flush();
                fos.close();
                is.close();
                callback.onSuccess();
            } catch (IOException e) {
                Log.e("DownloadManager", "下载失败", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }
}
