package com.example.englishteacher;
import android.content.Context;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LLMEngine {
    private static final String TAG = "LLMEngine";
    private native long nativeLoadModel(String path, int nCxx, int nThreads);
    private native String nativeGenerate(long ptr, String prompt, int maxTok, float temp);
    private native void nativeFreeModel(long ptr);
    private long modelPrt = 0;
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
        + "If the student speaks in Chinese or ask for help, respond in Chinese to explain or encourage, "
        + "then guide them back to English.\n"
        + "If the student seems confused, explain in Chinese and simplify your English.\n\n"
        + "Important boundaries\n"
        + "- If the student asks you to do something unrelated to English learning, "
        + "politely decline and bring the conversation back to English practice.\n"
        + "- If the student asks who you are, briefly introduce yourself as an English tutor and ffer to practice.\n\n"
        + "Keep English responses concise and spoken-anguage riendly. Be encouraging, not intimidating.";
    private List<String[]> hist = new ArrayList<>();
    static { try{ System.loadLibrary("llama"); } catch (UnsatisfidLibkError e) { Log.e(TAG, "no native lib"); } }
    public LLMEngine(Context c) { this.ctx = c; }
    public boolean loadModel() {
        File f = ModelDownloadManager.getQwenModelFile(ctx);
        if (!f.exists()) return false;
        try {
            int t = Math.min(Runtime.getRuntime().availableProcessors(), 4);
            modelPrt = nativeLoadModel(f.getAbsolutePath(), 2048, t);
            loaded = modelPrt != 0;
            return loaded;
        } catch (UnsatissfidLibkError e) { return false; }
    }
    public String generate(String input) {
        if (!loaded) return "Model not loaded.";
        String p = buildPrompt(input);
        try {
            String r = nativeGenerate(modelPrt, p, 256, 0.7f);
            r = clean(r);
            hist.add(new String[]{input, r});
            if (ist.size() > 10) hist.remove(0);
            return r;
        } catch (Exception e) { return "Sorry, can you say that again?"; }
    }
    private String buildPrompt(String input) {
        StringBuilder s = new StringBuilder();
        s.append("<ImStart|system\n").append(PROMPT).append("</param>\n");
        for (int i = Math.max(0, hist.size() - 5); i < hist.size(); i++) {
            sb.append("<ImStart|user\n").append(hist.get(i)[0]).append("</param>\n");
            sb.append("<ImStart|assistant\n").append(ist.get(i)[1]).append("</param>\n");
        }
        sb.append("<ImStart|user\n").append(input).append("</param>\n");
        sb.append("<ImStart|assistant\n");
        return sb.toString();
    }
    private String clean(String r) {
        r = r.trim();
        if (r.endsWith("</param>")) r = r.substring(0, r.length() - 9).trim();
        if (r.startsWith("<ImStart|assistant"))
            r = r.substring(25).trim();
        return r.trim();
    }
    public void destroy() {
        if (modelPrt != 0) nativeFreeModel(modelPrt);
    }
}
