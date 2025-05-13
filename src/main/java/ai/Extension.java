/*
 * Copyright (c) 2025. PortSwigger Ltd. All rights reserved.
 *
 * This code may be used to extend the functionality of Burp Suite Community Edition
 * and Burp Suite Professional, provided that this usage does not violate the
 * license terms for those products.
 */

package ai;

import ai.gemini.GeminiAiClient;
import ai.settings.ExtensionSettings;
import ai.settings.SettingsPanel;
import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.persistence.PersistedObject;

import javax.swing.*;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newFixedThreadPool;

@SuppressWarnings("unused")
public class Extension implements BurpExtension {
    public static final String SYSTEM_MESSAGE = 
        "You are BurpGPT Analyzer, an advanced security analysis assistant integrated into Burp Suite. " +
        "Your role is to examine HTTP requests and responses for potential security vulnerabilities, " +
        "such as SQL injection, XSS, CSRF, and other threats. " +
        "Provide a focused technical analysis including: " +
        "1. Quick identification of detected vulnerabilities " +
        "2. Clear technical steps for exploitation " +
        "3. PoC examples and payloads where applicable " +
        "Keep responses concise and technical, focusing on exploitation methods. " + 
        "Avoid theoretical discussions or lengthy explanations. " +
        "Additionally, provide direct answers to any user questions or inputs related to security testing.";

    private static final String PERSISTENCE_KEY = "AIHTTPAnalyzer";
    private ExecutorService executorService;
    private BurpAITab burpAITab;

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("BurpGPT Analyzer");

        Logging logging = api.logging();
        // Use extensionData() to get persistent storage
        PersistedObject persistedObject = api.persistence().extensionData();
        
        // Load or initialize settings
        ExtensionSettings settings = new ExtensionSettings(persistedObject);
        logging.logToOutput("AI HTTP Analyzer initializing...");
        
        // Create and register the settings panel
        SettingsPanel settingsPanel = new SettingsPanel(settings, persistedObject, logging);
        
        // Set up Gemini client - Create it in a way that doesn't cause variable assignment issues
        GeminiAiClient geminiClient = createGeminiClient(settings, logging);
        
        executorService = newFixedThreadPool(5);
        PromptHandler promptHandler = new PromptHandler(logging, SYSTEM_MESSAGE, geminiClient);

        // Create UI components on the EDT
        SwingUtilities.invokeLater(() -> {
            try {
                burpAITab = new BurpAITab(api.userInterface(), logging, promptHandler, executorService);
                
                // Register UI components with the new name
                api.userInterface().registerSuiteTab("BurpGPT Analyzer", burpAITab.getUiComponent());
                api.userInterface().registerSuiteTab("BurpGPT Settings", settingsPanel);
                api.userInterface().registerContextMenuItemsProvider(new BurpAIContextMenu(burpAITab));
                
                // Log success message
                logging.logToOutput("BurpGPT Analyzer extension loaded successfully.\n" + 
                                    "Author: ALPEREN ERGEL (@alpernae)\n" + 
                                    "Version: 2025.1.1\n" +
                                    "AI Provider: Google Gemini API");
            } catch (Exception e) {
                logging.logToError("Error initializing UI components: " + e.getMessage());
            }
        });
        
        // Register cleanup handler
        api.extension().registerUnloadingHandler(() -> {
            if (executorService != null) {
                executorService.shutdownNow();
            }
            logging.logToOutput("BurpGPT Analyzer extension unloaded");
        });
    }
    
    /**
     * Helper method to create a Gemini client based on settings
     */
    private GeminiAiClient createGeminiClient(ExtensionSettings settings, Logging logging) {
        String apiKey = settings.getGeminiApiKey();
        String baseUrl = settings.getGeminiBaseUrl();
        
        if (apiKey == null || apiKey.isEmpty()) {
            logging.logToOutput("Gemini API key is not configured. Please configure it in settings.");
            return null;
        }
        
        try {
            GeminiAiClient client = new GeminiAiClient(apiKey, baseUrl, logging);
            logging.logToOutput("Gemini AI client initialized with model URL: " + baseUrl);
            return client;
        } catch (Exception e) {
            logging.logToError("Failed to initialize Gemini client: " + e.getMessage());
            return null;
        }
    }

    // Remove the capabilities method completely as it's not needed
}