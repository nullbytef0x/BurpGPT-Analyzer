package ai.gemini;

import burp.api.montoya.ai.chat.PromptResponse;

public class GeminiPromptResponse implements PromptResponse {
    private final String content;

    public GeminiPromptResponse(String content) {
        this.content = content;
    }

    @Override
    public String content() {
        return content;
    }
}
