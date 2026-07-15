package com.example.englishcoach;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.Locale;

public class TTSManager {
    private static final String TAG = "TTSManager";
    private TextToSpeech tts;
    private boolean ready = false;
    private TTSListener listener;

    public interface TTSListener {
        void onSpeakStart();
        void onSpeakEnd();
    }

    public TTSManager(Context ctx, TTSListener l) {
        this.listener = l;
        tts = new TextToSpeech(ctx, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.US);
                ready = (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED);
                tts.setSpeechRate(0.9f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    public void onStart(String id) { if (listener != null) listener.onSpeakStart(); }
                    public void onDone(String id) { if (listener != null) listener.onSpeakEnd(); }
                    public void onError(String id) {}
                });
            }
        });
    }

    public void speak(String text) {
        if (!ready || tts == null) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance");
    }

    public void stop() {
        if (tts != null && tts.isSpeaking()) tts.stop();
    }

    public boolean isReady() { return ready; }

    public void destroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}
