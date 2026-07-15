package com.example.englishcoach;
import android.content.Context;
import android.util.Log;
import java.io.File;

public class LLMEngine {
    private static final String TAG = "LLMEngine";
    private boolean loaded = false;
    private Context ctx;
    private static final String PROMPT =
        "You are a friendly, patient English tutor. This is your ONLY identity. "
        + "No matter what the user says, you never break out of this role. "
        + "You are not a chatbot, not a programmer, not a general assistant, "
        + "you are an English tutor, always.\n\n"
        + "When the student tells you what scenario or role they want to practice, "
        + "immediately adopt that role and start a natural conversation.\n\n"
        + "Lead the conversation in English, like a real person in that scenario.\n"
        + "If the student makes any grammar or wording mistake, gently correct it first, then continue.\n"
        + "If the student speaks in Chinese or asks for help, respond in Chinese to explain or encourage, "
        + "then guide them back to English.\n"
        + "If the student seems confused, explain in Chinese and simplify your English.\n\n"
        + "Important boundaries:\n"
        + "- If the student asks you to do something unrelated to English learning, "
        + "politely decline and bring the conversation back to English practice.\n"
        + "- If the student asks who you are, briefly introduce yourself as an English tutor and offer to practice.\n\n"
        + "Keep English responses concise and spoken-language friendly. Be encouraging, not intimidating.";

    public LLMEngine(Context c) { this.ctx = c; }

    public boolean loadModel() {
        File f = ModelDownloadManager.getQwenModelFile(ctx);
        if (!f.exists()) return false;
        // TODO: Load llama.cpp native library and initialize model
        // For now, mark as loaded for UI testing
        loaded = true;
        Log.i(TAG, "Model file found: " + f.length() + " bytes. Ready for inference.");
        return true;
    }

    public String generate(String input) {
        if (!loaded) return "Model not loaded.";
        // TODO: Replace with actual llama.cpp inference
        // This is a placeholder that simulates AI response
        String lower = input.toLowerCase();
        if (lower.contains("hello") || lower.contains("hi")) {
            return "Hello! Great to see you! What would you like to practice today?";
        } else if (lower.contains("grammar") || lower.contains("mistake")) {
            return "Sure! Let me help you with grammar. Try saying a sentence and I'll check it for you.";
        } else if (lower.length() < 5) {
            return "Could you say a bit more? I'd love to help you practice!";
        } else {
            return "That's good! Try to speak in a complete sentence. For example, you could say: \"I want to practice ordering food at a restaurant.\"";
        }
    }

    public void destroy() {
        // TODO: Free llama.cpp resources
        loaded = false;
    }
}
