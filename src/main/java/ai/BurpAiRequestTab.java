package ai;

import ai.utils.DataMasker;
import burp.api.montoya.ai.chat.PromptResponse;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class BurpAiRequestTab extends JPanel
{
    private final Logging logging;
    private final ExecutorService executorService;
    private final PromptHandler promptHandler;

    public BurpAiRequestTab(Logging logging, UserInterface userInterface, ExecutorService executorService, PromptHandler promptHandler, HttpRequestResponse requestResponse) {
        this.logging = logging;
        this.executorService = executorService;
        this.promptHandler = promptHandler;

        this.setLayout(new BorderLayout());

        // Create split panes for layout with specific orientation
        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true);
        JSplitPane horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);

        // Initialize editors using class field
        HttpRequestEditor requestEditor = userInterface.createHttpRequestEditor();
        HttpResponseEditor responseEditor = userInterface.createHttpResponseEditor();

        if (requestResponse != null) {
            requestEditor.setRequest(requestResponse.request());
            responseEditor.setResponse(requestResponse.response());
        }

        // Create AI response area with minimum size
        JEditorPane aiResponseArea = new JEditorPane();
        aiResponseArea.setContentType("text/html");
        aiResponseArea.setEditable(false);
        JScrollPane aiScrollPane = new JScrollPane(aiResponseArea);
        aiScrollPane.setPreferredSize(new Dimension(800, 200));

        // Add components to splits and set preferred sizes
        horizontalSplit.setLeftComponent(requestEditor.uiComponent());
        horizontalSplit.setRightComponent(responseEditor.uiComponent());
        horizontalSplit.setResizeWeight(0.5);

        verticalSplit.setTopComponent(horizontalSplit);
        verticalSplit.setBottomComponent(aiScrollPane);
        verticalSplit.setResizeWeight(0.7);

        // Set divider locations
        horizontalSplit.setDividerLocation(0.5);
        verticalSplit.setDividerLocation(0.7);        this.add(verticalSplit, BorderLayout.CENTER);
        
        // Create a bottom panel with a FlowLayout and uniform spacing
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        // Create a custom input field for additional user prompt
        JTextField customInputField = new JTextField(30);
        customInputField.setPreferredSize(new Dimension(300, 35));
        
        // Add placeholder text and tooltip to make the purpose clearer
        customInputField.setToolTipText("Enter custom analysis instructions, e.g., 'Check for SQL injection vulnerabilities' or 'Find CSRF issues'");
        
        // Implement placeholder text functionality
        customInputField.setText("Enter instructions for AI analysis...");
        customInputField.setForeground(Color.GRAY);
        
        // Clear placeholder on focus
        customInputField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent evt) {
                if (customInputField.getText().equals("Enter instructions for AI analysis...")) {
                    customInputField.setText("");
                    customInputField.setForeground(Color.BLACK);
                }
            }
            
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                if (customInputField.getText().isEmpty()) {
                    customInputField.setText("Enter instructions for AI analysis...");
                    customInputField.setForeground(Color.GRAY);
                }
            }
        });

        // Add analyze button with custom styling
        JButton analyzeButton = new JButton("Analyze with BurpGPT");
        analyzeButton.setBackground(Color.decode("#ff6633"));
        analyzeButton.setForeground(Color.WHITE);
        //analyzeButton.setFont(new Font("Segoe UI Emoji", 13));
        analyzeButton.setFocusPainted(false);
        analyzeButton.setBorderPainted(false);
        analyzeButton.setOpaque(true);        // Make button thicker
        analyzeButton.setPreferredSize(new Dimension(analyzeButton.getPreferredSize().width, 35));
        analyzeButton.setMargin(new Insets(5, 10, 5, 10));
        
        // Add input field and button to bottom panel
        bottomPanel.add(customInputField);
        bottomPanel.add(analyzeButton);
        
        this.add(bottomPanel, BorderLayout.SOUTH);

        Consumer<AWTEvent> runPrompt = e -> {
            analyzeRequest(
                    requestEditor.getRequest(),
                    responseEditor.getResponse(),
                    aiResponseArea,
                    true, // Always include request/response now
                    customInputField.getText());
            // Clear the input field
            customInputField.setText("");
        };

        customInputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    runPrompt.accept(e);
                }
            }
        });

        analyzeButton.addActionListener(runPrompt::accept);
    }

    private void analyzeRequest(HttpRequest request,
                                HttpResponse response,
                                JEditorPane aiResponseArea,
                                boolean includeRequestResponse,
                                String customInput) {
        // Always include request/response now, so no need for the includeRequestResponse parameter
        // but keeping it for backward compatibility
        String promptText = buildPromptText(true, customInput, request, response);        if (promptText == null) {
            aiResponseArea.setText("Empty custom prompt or HTTP request.");
            return;
        }

        // Set content type first to ensure HTML renders properly
        aiResponseArea.setContentType("text/html");
        
        // Notify user that sensitive data masking is being applied
        aiResponseArea.setText("<html><body style='font-family: Segoe UI, Arial, sans-serif; padding: 10px;'>" +
                              "<h3 style='color: #0077cc;'>Analyzing request/response...</h3>" +
                              "<p><i>🔒 Sensitive data masking is enabled - API keys, tokens, credentials and PII will be masked before sending to Gemini AI</i></p>" +
                              "<p>Please wait while BurpGPT performs security analysis...</p>" +
                              "</body></html>");
                              
        // Set AI response text to 12px
        aiResponseArea.setFont(new Font(aiResponseArea.getFont().getFamily(), Font.PLAIN, 12));

        // Execute the AI prompt in a separate thread
        executorService.execute(() -> {
            try {                // Apply data masking
                String maskedPrompt = DataMasker.mask(promptText);
                int maskedValueCount = DataMasker.getMaskedValueCount();
                  // Log that data masking has been applied
                logging.logToOutput("Sensitive data masking applied before sending to Gemini AI - " + maskedValueCount + " values masked");
                  PromptResponse aiResponse = promptHandler.sendWithSystemMessage(maskedPrompt);

                String content = aiResponse.content();

                // Remove any backticks before converting to HTML
                if (content.contains("`")) {
                    content = content.replaceAll("`+", "");
                }
                
                // Sanitize the HTML content to remove potentially dangerous elements
                Document.OutputSettings outputSettings = new Document.OutputSettings();
                outputSettings.prettyPrint(false);
                String sanitizedContent = Jsoup.clean(content, "", Safelist.basic(), outputSettings);

                // Convert Markdown to HTML
                Parser parser = Parser.builder().build();
                HtmlRenderer renderer = HtmlRenderer.builder().build();
                String htmlContent = renderer.render(parser.parse(sanitizedContent));                // Add custom CSS styling for better readability and vulnerability highlighting
                String sensitiveDataAlert = "";
                  // Add sensitive data notification banner if data was masked
                if (maskedValueCount > 0) {
                    Map<String, Integer> detectedTypes = DataMasker.getDetectedSensitiveDataTypes();
                    
                    StringBuilder typesList = new StringBuilder();
                    typesList.append("<ul style='margin-top: 5px; margin-bottom: 5px;'>");
                    
                    // Sort by count (highest first)
                    detectedTypes.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .forEach(entry -> {
                            typesList.append("<li><strong>")
                                   .append(entry.getValue())
                                   .append("</strong> ")
                                   .append(entry.getKey())
                                   .append(entry.getValue() > 1 ? "s" : "")
                                   .append("</li>");
                        });
                    
                    typesList.append("</ul>");
                    
                    sensitiveDataAlert = "<div style='background-color: #fff0f0; border: 1px solid #ffcccb; " +
                        "border-left: 4px solid #cc0000; padding: 10px; margin-bottom: 15px; border-radius: 3px;'>" +
                        "<h3 style='color: #cc0000; margin-top: 0;'>⚠️ Sensitive Data Detected</h3>" +
                        "<p><strong>" + maskedValueCount + " instance" + (maskedValueCount > 1 ? "s" : "") + " of sensitive data " + 
                        (maskedValueCount > 1 ? "were" : "was") + " detected</strong> and masked before analysis:</p>" +
                        typesList.toString() +
                        "<p style='margin-top: 8px;'><strong>Security Risk:</strong> <span class='critical'>Critical</span> - " +
                        "Exposed sensitive data could lead to unauthorized access, account takeover, or data breaches.</p>" +
                        "<p>All sensitive data has been automatically masked to protect security. " +
                        "Review the findings below for detailed analysis.</p>" +
                        "</div>";
                }                String styledHtmlContent = 
                    "<html><head><style>" +
                    "body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.5; padding: 10px; color: #333; }" +
                    "h1 { color: #ff6633; margin-top: 20px; margin-bottom: 10px; font-size: 18px; border-bottom: 1px solid #eee; padding-bottom: 5px; }" +
                    "h2 { color: #0077cc; margin-top: 15px; margin-bottom: 10px; font-size: 16px; }" +
                    "h3 { font-weight: bold; margin-top: 15px; margin-bottom: 5px; font-size: 14px; }" +
                    "ul, ol { margin-top: 5px; margin-bottom: 15px; }" +
                    "li { margin-bottom: 5px; }" +
                    "pre { background-color: #f5f5f5; border: 1px solid #ddd; border-radius: 3px; padding: 10px; overflow: auto; margin: 10px 0; }" +
                    "code { font-family: Consolas, monospace; background-color: #f5f5f5; padding: 2px 4px; border-radius: 3px; }" +
                    "p { margin-bottom: 10px; }" +
                    ".critical { color: #cc0000; font-weight: bold; background-color: #fff0f0; padding: 2px 5px; border-radius: 3px; }" +
                    ".high { color: #ff4500; font-weight: bold; background-color: #fff8f0; padding: 2px 5px; border-radius: 3px; }" +
                    ".medium { color: #ff8c00; font-weight: bold; background-color: #fffcf0; padding: 2px 5px; border-radius: 3px; }" +
                    ".low { color: #2e8b57; font-weight: bold; background-color: #f0fff8; padding: 2px 5px; border-radius: 3px; }" +
                    ".vuln-section { border-left: 4px solid #ff6633; padding: 8px 15px; margin: 15px 0; background-color: #fcfcfc; }" +
                    ".sensitive-data-section { border-left: 4px solid #cc0000; padding: 8px 15px; margin: 15px 0; background-color: #fff0f0; }" +
                    "table { border-collapse: collapse; width: 100%; margin: 15px 0; }" +
                    "th, td { text-align: left; padding: 8px; border: 1px solid #ddd; }" +
                    "th { background-color: #f2f2f2; }" +
                    "tr:nth-child(even) { background-color: #f9f9f9; }" +
                    "</style></head><body>" + sensitiveDataAlert + htmlContent + "</body></html>";logging.logToOutput("AI response received successfully");
                
                // Set the content type first before setting text to ensure proper HTML rendering
                SwingUtilities.invokeLater(() -> {
                    aiResponseArea.setContentType("text/html");
                    aiResponseArea.setText(styledHtmlContent);
                });
                
                // Clear the DataMasker data after we're done
                DataMasker.clearData();
            } catch (RuntimeException error) {
                // More detailed error message with troubleshooting steps
                String errorMessage = error.getMessage();
                final String errorDetails = "<html><body style='width: 400px; font-family: sans-serif;'>" +
                        "<h3 style='color: #cc0000;'>AI Analysis Error</h3>" +
                        "<p><b>Error message:</b> " + errorMessage + "</p>" +
                        "<h4>Troubleshooting:</h4>" +
                        "<ul>" +
                        "<li>Check the Burp Suite extension logs for more details</li>" +
                        ((errorMessage != null && errorMessage.toLowerCase().contains("gemini")) ?
                            "<li>Verify your Gemini API key in the AI HTTP Settings tab</li>" +
                            "<li>Check your internet connection and firewall settings</li>" +
                            "<li>Make sure your Gemini API key has access to the selected model</li>" +
                            "<li>Try a different Gemini model (gemini-pro is most reliable)</li>" :
                            "<li>Make sure Burp AI is enabled in Burp Suite settings</li>" +
                            "<li>Check your internet connection</li>" +
                            "<li>Try switching to Gemini API in the AI HTTP Settings tab</li>") +
                        "</ul></body></html>";
                
                logging.logToError("AI error: " + error.getMessage());
                SwingUtilities.invokeLater(() ->
                        aiResponseArea.setContentType("text/html")
                );
                SwingUtilities.invokeLater(() ->
                        aiResponseArea.setText(errorDetails)
                );
            } catch (Exception error) {
                // Generic error handling for unexpected errors
                final String errorDetails = "<html><body style='width: 400px; font-family: sans-serif;'>" +
                        "<h3 style='color: #cc0000;'>Unexpected Error</h3>" +
                        "<p><b>Error message:</b> " + error.getMessage() + "</p>" +
                        "<p>This is likely a bug in the extension. Please report this error.</p>" +
                        "<h4>Troubleshooting:</h4>" +
                        "<ul>" +
                        "<li>Check the Burp Suite extension logs for more details</li>" +
                        "<li>Try restarting Burp Suite</li>" +
                        "<li>Update to the latest version of this extension</li>" +
                        "</ul></body></html>";
                
                logging.logToError("Unexpected AI error: " + error);
                error.printStackTrace();
                
                SwingUtilities.invokeLater(() ->
                        aiResponseArea.setContentType("text/html")
                );
                SwingUtilities.invokeLater(() ->
                        aiResponseArea.setText(errorDetails)
                );            }
        });
    }
    
    private static String buildPromptText(boolean includeRequestResponse, String customInput, HttpRequest request, HttpResponse response) {
        boolean analyzeRequest = includeRequestResponse && request != null;

        if (!analyzeRequest && customInput.isEmpty()) {
            return null;
        }        // Build the prompt conditionally
        StringBuilder promptBuilder = new StringBuilder();
        
        // Security notice at the beginning
        promptBuilder.append("NOTE TO ANALYSTS: Sensitive data in this request (API keys, credentials, tokens, etc.) has been automatically masked for security.\n\n");

        if (analyzeRequest) {
            promptBuilder.append("Analyze this HTTP request");

            if (response != null) {
                promptBuilder.append(" and response");
            }
            
            promptBuilder.append(" for security vulnerabilities including but not limited to:\n");
            promptBuilder.append("- SQL/NoSQL Injection\n");
            promptBuilder.append("- Cross-Site Scripting (XSS)\n");
            promptBuilder.append("- Cross-Site Request Forgery (CSRF)\n");
            promptBuilder.append("- Authentication flaws\n");
            promptBuilder.append("- Authorization bypass\n");
            promptBuilder.append("- Security header misconfiguration\n");
            promptBuilder.append("- Sensitive data exposure\n");
            promptBuilder.append("- Server-Side Request Forgery (SSRF)\n");
            promptBuilder.append("- Insecure Direct Object References (IDOR)\n\n");
            
            // Mask sensitive data in request
            String maskedRequest = DataMasker.mask(request.toString());
            promptBuilder
                    .append("REQUEST:\n")
                    .append(maskedRequest);

            if (response != null) {
                // Mask sensitive data in response
                String maskedResponse = DataMasker.mask(response.toString());
                promptBuilder
                        .append("\n\nRESPONSE:\n")
                        .append(maskedResponse);
            }

            promptBuilder.append("\n\n");
            promptBuilder.append("Format your response with clear headers for each vulnerability, assign severity (Critical/High/Medium/Low), and provide specific exploitation steps and remediation advice.\n\n");
        }
        
        // Always append the custom prompt, regardless of checkbox
        // No need to mask the custom input as it's provided by the user
        promptBuilder.append(customInput);
        
        // Get final prompt text
        String finalPrompt = promptBuilder.toString();
        
        return finalPrompt;
    }
}
