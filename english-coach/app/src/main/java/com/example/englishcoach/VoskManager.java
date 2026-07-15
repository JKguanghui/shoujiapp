package com.example.englishcoach;

import android.content.Context;
import android.util.Log;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.File;
import java.io.IOException;

/**
 * Manages Vosk speech recognition.
 * Provides streaming speech-to-text with silence detection.
 */
public class VoskManager {

    private static final String TAG = "VoskManager";
    private static final float SAMPLE_RATE = 16000.0f;

    private Context context;
    private Model model;
    private SpeechService speechService;
    private boolean isListening = false;
    private boolean isInitialized = false;

    public interface VoskCallback {
        void onPartialResult(String text);
        void onFinalResult(String text);
        void onError(String error);
        void onReady();
    }

    private VoskCallback callback;

    public VoskManager(Context context) {
        this.context = context;
    }

    /**
     * Initialize Vosk with the downloaded model directory
     */
    public void init(VoskCallback callback) {
        this.callback = callback;

        File modelDir = ModelDownloadManager.getVoskModelDir(context);
        if (!modelDir.exists() || !new File(modelDir, "conf/mfcc.conf").exists()) {
            callback.onError("Vosk model not found. Please download first.");
            return;
        }

        new Thread(() -> {
            try {
                model = new Model(modelDir.getAbsolutePath());
                isInitialized = true;
                callback.onReady();
            } catch (IOException e) {
                Log.e(TAG, "Failed to load Vosk model", e);
                callback.onError("Failed to load speech model: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Start listening for speech
     */
    public void startListening() {
        if (!isInitialized || model == null) {
            if (callback != null) callback.onError("Speech model not initialized");
            return;
        }

        try {
            Recognizer recognizer = new Recognizer(model, SAMPLE_RATE);
            recognizer.setWords(true);

            speechService = new SpeechService(recognizer, SAMPLE_RATE);
            speechService.startListening(new RecognitionListener() {
                @Override
                public void onPartialResult(String hypothesis) {
                    // Extract text from JSON result
                    String text = extractText(hypothesis);
                    if (!text.isEmpty() && callback != null) {
                        callback.onPartialResult(text);
                    }
                }

                @Override
                public void onResult(String hypothesis) {
                    String text = extractText(hypothesis);
                    if (!text.isEmpty() && callback != null) {
                        callback.onFinalResult(text);
                    }
                }

                @Override
                public void onFinalResult(String hypothesis) {
                    String text = extractText(hypothesis);
                    if (!text.isEmpty() && callback != null) {
                        callback.onFinalResult(text);
                    }
                }
            });
            isListening = true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to start listening", e);
            if (callback != null) callback.onError("Failed to start microphone: " + e.getMessage());
        }
    }

    /**
     * Stop listening
     */
    public void stopListening() {
        if (speechService != null) {
            speechService.stop();
            isListening = false;
        }
    }

    /**
     * Pause listening (when AI is speaking)
     */
    public void pause() {
        if (speechService != null) {
            speechService.setPause(true);
        }
    }

    /**
     * Resume listening after AI finishes
     */
    public void resume() {
        if (speechService != null) {
            speechService.setPause(false);
        }
    }

    public boolean isListening() {
        return isListening;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Extract plain text from Vosk JSON result
     */
    private String extractText(String json) {
        if (json == null || json.isEmpty()) return "";
        try {
            // Simple extraction: {"text": "hello world"}
            int start = json.indexOf("\"text\"");
            if (start < 0) return "";
            start = json.indexOf("\"", start + 6);
            if (start < 0) return "";
            start++;
            int end = json.indexOf("\"", start);
            if (end < 0) return "";
            return json.substring(start, end).trim();
        } catch (Exception e) {
            return "";
        }
    }

    public void destroy() {
        stopListening();
        if (speechService != null) {
            speechService.shutdown();
        }
        if (model != null) {
            model.close();
        }
    }
}
