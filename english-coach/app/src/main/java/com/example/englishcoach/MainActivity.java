package com.example.englishcoach;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_AUDIO = 100;
    private TextView statusText, subtitleText;
    private Button btnDownload, btnCall;
    private ProgressBar progressBar;
    private ModelDownloadManager downloadManager;
    private boolean modelReady = false;
    private boolean isCallActive = false;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        subtitleText = findViewById(R.id.subtitleText);
        btnDownload = findViewById(R.id.btnDownload);
        btnCall = findViewById(R.id.btnCall);
        progressBar = findViewById(R.id.progressBar);

        // 检查模型是否存在
        File modelDir = new File(getFilesDir(), "models");
        File modelFile = new File(modelDir, "smollm-135m.gguf");
        if (modelFile.exists()) {
            modelReady = true;
            statusText.setText(R.string.model_ready);
            btnCall.setVisibility(Button.VISIBLE);
        } else {
            statusText.setText("需要下载模型资源 (约500MB)");
            btnDownload.setVisibility(Button.VISIBLE);
        }

        downloadManager = new ModelDownloadManager(this);

        btnDownload.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
                startDownload();
            } else {
                // 实际上INTERNET权限无需动态申请，但可申请存储权限（如果需要外部存储）
                startDownload();
            }
        });

        btnCall.setOnClickListener(v -> {
            if (!modelReady) {
                Toast.makeText(this, "请先下载模型", Toast.LENGTH_SHORT).show();
                return;
            }
            // 检查录音权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            } else {
                toggleCall();
            }
        });
    }

    private void startDownload() {
        btnDownload.setEnabled(false);
        progressBar.setVisibility(ProgressBar.VISIBLE);
        statusText.setText(R.string.downloading);

        downloadManager.downloadModel(new ModelDownloadManager.DownloadCallback() {
            @Override
            public void onProgress(int progress) {
                progressBar.setProgress(progress);
            }

            @Override
            public void onSuccess() {
                modelReady = true;
                progressBar.setVisibility(ProgressBar.GONE);
                statusText.setText(R.string.model_ready);
                btnDownload.setVisibility(Button.GONE);
                btnCall.setVisibility(Button.VISIBLE);
                Toast.makeText(MainActivity.this, "模型下载完成", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(ProgressBar.GONE);
                btnDownload.setEnabled(true);
                statusText.setText("下载失败: " + error);
                Toast.makeText(MainActivity.this, "下载出错，请重试", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void toggleCall() {
        if (isCallActive) {
            // 结束通话
            isCallActive = false;
            btnCall.setText(R.string.start_call);
            subtitleText.setText("");
            stopMockAudio();
        } else {
            // 开始通话
            isCallActive = true;
            btnCall.setText(R.string.end_call);
            // 模拟实时字幕和纠错演示
            startMockConversation();
        }
    }

    // 模拟一段对话，展示字幕和纠错效果（实际接入Vosk后会替换）
    private void startMockConversation() {
        String[] userPhrases = {"He go to school everyday.", "She don't like coffee."};
        String[] corrections = {"He goes to school every day.", "She doesn't like coffee."};
        final int[] idx = {0};

        Runnable phraseTask = new Runnable() {
            @Override
            public void run() {
                if (!isCallActive || idx[0] >= userPhrases.length) {
                    if (isCallActive) {
                        subtitleText.setText("通话结束，查看纠错历史");
                        isCallActive = false;
                        btnCall.setText(R.string.start_call);
                    }
                    return;
                }
                String userText = userPhrases[idx[0]];
                String correct = corrections[idx[0]];
                subtitleText.setText("你说：" + userText + "\n建议：" + correct);
                // 模拟停顿后下一句
                handler.postDelayed(this, 3000);
                idx[0]++;
            }
        };
        handler.postDelayed(phraseTask, 1000);
    }

    private void stopMockAudio() {
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleCall();
            } else {
                Toast.makeText(this, "需要录音权限才能通话", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
