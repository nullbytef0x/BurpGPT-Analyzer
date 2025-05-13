package ai.gemini;

import burp.api.montoya.logging.Logging;
import burp.api.montoya.ai.chat.Message;
import burp.api.montoya.ai.chat.PromptResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class GeminiAiClient {
    private final String apiKey;
    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final Logging logging;
    private final Gson gson;

    public GeminiAiClient(String apiKey, String baseUrl, Logging logging) {
        this.apiKey = apiKey;
        // Default to gemini-pro model if not specified
        this.baseUrl = baseUrl != null && !baseUrl.isEmpty() 
            ? baseUrl 
            : "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent";
        this.logging = logging;
        this.gson = new Gson();
        
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public PromptResponse sendPrompt(Message[] messages) throws IOException {
        JsonObject requestBody = new JsonObject();
        JsonArray contents = new JsonArray();
        
        // Process messages based on their string representation
        for (Message message : messages) {
            String messageStr = message.toString().toLowerCase();
            String content = "";
            
            // Extract content regardless of type
            if (messageStr.startsWith("system: ")) {
                content = messageStr.substring("system: ".length());
            } else if (messageStr.startsWith("user: ")) {
                content = messageStr.substring("user: ".length());
            } else {
                // If we can't determine type, just use the whole message
                content = messageStr;
            }

            // If this is the test message, update it to use the new name
            if (content.contains("this is a test message from ai http analyzer")) {
                content = content.replace("ai http analyzer", "burpgpt analyzer");
            }
            
            // Create a single user message for Gemini
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            
            JsonArray parts = new JsonArray();
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", content);
            parts.add(textPart);
            
            userMessage.add("parts", parts);
            contents.add(userMessage);
            
            // Log the constructed message
            logging.logToOutput("Added message to Gemini request: " + content);
        }
        
        // Make sure we have at least one content entry
        if (contents.size() == 0) {
            // Add a default message to prevent API error
            JsonObject defaultMessage = new JsonObject();
            defaultMessage.addProperty("role", "user");
            
            JsonArray parts = new JsonArray();
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", "Please analyze any security issues in this HTTP communication.");
            parts.add(textPart);
            
            defaultMessage.add("parts", parts);
            contents.add(defaultMessage);
            logging.logToOutput("No valid messages found, added default message");
        }
        
        requestBody.add("contents", contents);
        
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.2);
        generationConfig.addProperty("topP", 0.95);
        generationConfig.addProperty("maxOutputTokens", 2048);
        requestBody.add("generationConfig", generationConfig);
        
        String requestUrl = baseUrl + "?key=" + apiKey;
        
        logging.logToOutput("Sending request to Gemini API: " + baseUrl);
        logging.logToOutput("Request body: " + requestBody.toString());
        
        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
                .url(requestUrl)
                .post(body)
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Empty response";
                logging.logToError("Gemini API error: " + response.code() + " - " + errorBody);
                throw new IOException("Unexpected response code: " + response.code() + ", Body: " + errorBody);
            }
            
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            logging.logToOutput("Received response from Gemini API");
            
            try {
                String content = jsonResponse
                        .getAsJsonArray("candidates")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0)
                        .getAsJsonObject()
                        .get("text")
                        .getAsString();
                        
                return new GeminiPromptResponse(content);
            } catch (Exception e) {
                logging.logToError("Error parsing Gemini response: " + responseBody);
                throw new IOException("Error parsing Gemini response: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            logging.logToError("Error calling Gemini API: " + e.getMessage());
            throw new IOException("Error calling Gemini API: " + e.getMessage(), e);
        }
    }
    
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
