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
        String promptText = buildPromptText(true, customInput, request, response);

        if (promptText == null) {
            aiResponseArea.setText("Empty custom prompt or HTTP request.");
            return;
        }

        aiResponseArea.setText("Analyzing request/response...");        // Set AI response text to 12px
        aiResponseArea.setFont(new Font(aiResponseArea.getFont().getFamily(), Font.PLAIN, 12));

        // Execute the AI prompt in a separate thread
        executorService.execute(() -> {
            try {
                // Log that data masking has been applied
                logging.logToOutput("Sensitive data masking applied before sending to Gemini AI");
                
                PromptResponse aiResponse = promptHandler.sendWithSystemMessage(promptText);

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
                String htmlContent = renderer.render(parser.parse(sanitizedContent));

                logging.logToOutput("AI response received successfully");
                SwingUtilities.invokeLater(() ->
                        aiResponseArea.setText(htmlContent)
                );
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
        }

        // Build the prompt conditionally
        StringBuilder promptBuilder = new StringBuilder();
        
        // Security notice at the beginning
        promptBuilder.append("NOTE TO ANALYSTS: Sensitive data in this request (API keys, credentials, tokens, etc.) has been automatically masked for security.\n\n");

        if (analyzeRequest) {
            promptBuilder.append("Analyze this HTTP request");

            if (response != null) {
                promptBuilder.append(" and response");
            }
            
            // Mask sensitive data in request
            String maskedRequest = DataMasker.mask(request.toString());
            promptBuilder
                    .append(" for security issues:\n")
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
        }
        
        // Always append the custom prompt, regardless of checkbox
        // No need to mask the custom input as it's provided by the user
        promptBuilder.append(customInput);
        
        // Get final prompt text
        String finalPrompt = promptBuilder.toString();
        
        return finalPrompt;
    }
}
