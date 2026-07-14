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

        downloadManager = new ModelDownloadManager(this);
        checkModelState();

        btnDownload.setOnClickListener(v -> startDownload());

        btnCall.setOnClickListener(v -> {
            if (!modelReady) {
                Toast.makeText(this, "please download model first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            } else {
                toggleCall();
            }
        });
    }

    private void checkModelState() {
        File modelFile = ModelDownloadManager.getModelFile(this);
        if (modelFile.exists() && modelFile.length() > 0) {
            modelReady = true;
            long sizeMB = modelFile.length() / (1024 * 1024);
            statusText.setText("Model ready (" + sizeMB + "MB)");
            btnDownload.setVisibility(Button.GONE);
            btnCall.setVisibility(Button.VISIBLE);
        } else {
            modelReady = false;
            statusText.setText("Need to download model (~500MB)");
            btnDownload.setVisibility(Button.VISIBLE);
            btnCall.setVisibility(Button.GONE);
        }
    }

    private void startDownload() {
        btnDownload.setEnabled(false);
        progressBar.setVisibility(ProgressBar.VISIBLE);
        progressBar.setProgress(0);
        statusText.setText(R.string.downloading);

        downloadManager.downloadModel(new ModelDownloadManager.DownloadCallback() {
            @Override
            public void onProgress(int progress) {
                runOnUiThread(() -> progressBar.setProgress(progress));
            }

            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    modelReady = true;
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnDownload.setVisibility(Button.GONE);
                    btnCall.setVisibility(Button.VISIBLE);
                    File modelFile = ModelDownloadManager.getModelFile(MainActivity.this);
                    long sizeMB = modelFile.length() / (1024 * 1024);
                    statusText.setText("Model ready (" + sizeMB + "MB)");
                    Toast.makeText(MainActivity.this, "Model downloaded", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnDownload.setEnabled(true);
                    statusText.setText("Download failed: " + error);
                    Toast.makeText(MainActivity.this, "Download error, retry", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void toggleCall() {
        if (isCallActive) {
            isCallActive = false;
            btnCall.setText(R.string.start_call);
            subtitleText.setText("");
            stopMockAudio();
        } else {
            isCallActive = true;
            btnCall.setText(R.string.end_call);
            startMockConversation();
        }
    }

    private void startMockConversation() {
        String[] userPhrases = {"He go to school everyday.", "She don't like coffee."};
        String[] corrections = {"He goes to school every day.", "She doesn't like coffee."};
        final int[] idx = {0};

        Runnable phraseTask = new Runnable() {
            @Override
            public void run() {
                if (!isCallActive || idx[0] >= userPhrases.length) {
                    if (isCallActive) {
                        subtitleText.setText("Call ended");
                        isCallActive = false;
                        btnCall.setText(R.string.start_call);
                    }
                    return;
                }
                String userText = userPhrases[idx[0]];
                String correct = corrections[idx[0]];
                subtitleText.setText("You: " + userText + "\nSuggestion: " + correct);
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
                Toast.makeText(this, "Need audio permission", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
