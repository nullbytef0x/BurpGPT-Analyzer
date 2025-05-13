/*
 * Copyright (c) 2025. PortSwigger Ltd. All rights reserved.
 *
 * This code may be used to extend the functionality of Burp Suite Community Edition
 * and Burp Suite Professional, provided that this usage does not violate the
 * license terms for those products.
 */

package ai;

import ai.gemini.GeminiAiClient;
import burp.api.montoya.ai.chat.Message;
import burp.api.montoya.ai.chat.PromptResponse;
import burp.api.montoya.logging.Logging;

import java.io.IOException;

import static burp.api.montoya.ai.chat.Message.systemMessage;
import static burp.api.montoya.ai.chat.Message.userMessage;

public class PromptHandler
{
    private final Logging logging;
    private final Message systemMessage;
    private final GeminiAiClient geminiClient;

    public PromptHandler(Logging logging, String systemPrompt, GeminiAiClient geminiClient)
    {
        this.logging = logging;
        this.systemMessage = systemMessage(systemPrompt);
        this.geminiClient = geminiClient;
        
        logging.logToOutput("BurpGPT Analyzer initialized with Gemini AI");
    }

    public Message[] build(String userPrompt)
    {
        return new Message[]{systemMessage, userMessage(userPrompt)};
    }

    public PromptResponse sendWithSystemMessage(String userPrompt) throws RuntimeException
    {
        if (geminiClient == null || !geminiClient.isConfigured()) {
            throw new RuntimeException("Please configure Gemini API key in the AI HTTP Settings tab.");
        }
        
        try {
            logging.logToOutput("Using Gemini AI for prompt");
            return geminiClient.sendPrompt(build(userPrompt));
        } catch (IOException e) {
            logging.logToError("Error using Gemini AI: " + e.getMessage());
            throw new RuntimeException("Error using Gemini AI: " + e.getMessage());
        }
    }
}
