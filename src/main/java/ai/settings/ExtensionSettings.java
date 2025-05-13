package ai.settings;

import burp.api.montoya.persistence.PersistedObject;

public class ExtensionSettings {
    private final PersistedObject persistedObject;
    
    private static final String USE_GEMINI_KEY = "useGemini";
    private static final String GEMINI_API_KEY = "geminiApiKey";
    private static final String GEMINI_BASE_URL = "geminiBaseUrl";
    private static final String DEFAULT_GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent";
    
    public ExtensionSettings(PersistedObject persistedObject) {
        this.persistedObject = persistedObject;
    }
    
    public boolean useGemini() {
        // getBoolean method does not accept default value parameter
        String value = persistedObject.getString(USE_GEMINI_KEY);
        return "true".equalsIgnoreCase(value);
    }
    
    public void setUseGemini(boolean useGemini) {
        persistedObject.setString(USE_GEMINI_KEY, String.valueOf(useGemini));
    }
    
    public String getGeminiApiKey() {
        return persistedObject.getString(GEMINI_API_KEY);
    }
    
    public void setGeminiApiKey(String apiKey) {
        persistedObject.setString(GEMINI_API_KEY, apiKey);
    }
    
    public String getGeminiBaseUrl() {
        String url = persistedObject.getString(GEMINI_BASE_URL);
        if (url == null || url.isEmpty()) {
            return DEFAULT_GEMINI_URL;
        }
        return url;
    }
    
    public void setGeminiBaseUrl(String baseUrl) {
        persistedObject.setString(GEMINI_BASE_URL, baseUrl);
    }
}
