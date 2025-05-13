package ai.settings;

import ai.gemini.GeminiAiClient;
import burp.api.montoya.ai.chat.Message;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.persistence.PersistedObject;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;

import static burp.api.montoya.ai.chat.Message.userMessage;

public class SettingsPanel extends JPanel {
    private final ExtensionSettings settings;
    private final PersistedObject persistedObject;
    private final Logging logging;
    
    public SettingsPanel(ExtensionSettings settings, PersistedObject persistedObject, Logging logging) {
        this.settings = settings;
        this.persistedObject = persistedObject;
        this.logging = logging;
        
        initUI();
    }
    
    private void initUI() {
        setLayout(new BorderLayout());
        
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Create a panel for the centered title
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titlePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titlePanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 40)); // Reduced height from 50 to 40
        
        // Title with enhanced styling - now centered
        JLabel titleLabel = new JLabel("BurpGPT Analyzer Settings");
        titleLabel.setFont(new Font(titleLabel.getFont().getName(), Font.BOLD, 18));
        titleLabel.setForeground(new Color(0, 102, 204)); // Professional blue color
        
        // Add title to the centered panel
        titlePanel.add(titleLabel);
        mainPanel.add(titlePanel); // Add the panel instead of the label directly
        mainPanel.add(Box.createRigidArea(new Dimension(0, 1))); // Increased spacing back to 10
        
        // Replace the JLabel with a JEditorPane for better HTML rendering
        JEditorPane descPane = new JEditorPane();
        descPane.setContentType("text/html");
        descPane.setText(
                "<html><body style='font-family: Arial; font-size: 12pt; margin: 5px;'>" +
                "<h3>Welcome to BurpGPT Analyzer!</h3>" +
                "<p>This extension uses Google's Gemini AI to analyze HTTP traffic and identify security vulnerabilities.</p>" +
                "<p>To get started:</p>" +
                "<ol>" +
                "<li>Enter your <b>Gemini API key</b> below</li>" +
                "<li>Choose your preferred Gemini model</li>" +
                "<li>Save your settings and restart the extension</li>" +
                "</ol>" +
                "<p>Need help? See the instructions at the bottom of this page.</p>" +
                "</body></html>"
        );
        
        // Make the pane non-editable and transparent
        descPane.setEditable(false);
        descPane.setOpaque(false);
        descPane.setMaximumSize(new Dimension(500, 250));
        descPane.setPreferredSize(new Dimension(500, 180));
        descPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        mainPanel.add(descPane);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        // API Key panel
        JPanel apiKeyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        apiKeyPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel apiKeyLabel = new JLabel("Gemini API Key:");
        JPasswordField apiKeyField = new JPasswordField(30);
        apiKeyField.setText(settings.getGeminiApiKey());
        apiKeyPanel.add(apiKeyLabel);
        apiKeyPanel.add(apiKeyField);
        
        // API Model selection
        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modelPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel modelLabel = new JLabel("Gemini Model:");
        JComboBox<String> modelComboBox = new JComboBox<>(new String[]{
            "gemini-1.5-pro",
            "gemini-1.5-flash"
        });
        
        // Default or saved model
        String baseUrl = settings.getGeminiBaseUrl();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            if (baseUrl.contains("gemini-1.5-pro")) {
                modelComboBox.setSelectedItem("gemini-1.5-pro");
            } else if (baseUrl.contains("gemini-1.5-flash")) {
                modelComboBox.setSelectedItem("gemini-1.5-flash");
            } else {
                // Default to gemini-1.5-pro if the saved model was gemini-pro or something else
                modelComboBox.setSelectedItem("gemini-1.5-pro");
            }
        } else {
            // Default to gemini-1.5-pro for new installations
            modelComboBox.setSelectedItem("gemini-1.5-pro");
        }
        
        modelPanel.add(modelLabel);
        modelPanel.add(modelComboBox);
        
        // Save button
        JButton saveButton = new JButton("Save Settings");
        saveButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        saveButton.addActionListener(e -> {
            // Always set useGemini to true since we're only using Gemini now
            settings.setUseGemini(true);
            settings.setGeminiApiKey(new String(apiKeyField.getPassword()));
            
            // Construct the API URL based on selected model
            String selectedModel = (String) modelComboBox.getSelectedItem();
            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + selectedModel + ":generateContent";
            settings.setGeminiBaseUrl(apiUrl);
            
            JOptionPane.showMessageDialog(this, 
                    "Settings saved. Please restart the extension for changes to take effect.",
                    "Settings Saved", 
                    JOptionPane.INFORMATION_MESSAGE);
            
            logging.logToOutput("BurpGPT Analyzer settings updated. Please restart the extension for changes to take effect.");
        });
        
        // Test button
        JButton testButton = new JButton("Test Gemini API Connection");
        testButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        testButton.addActionListener(e -> {
            String apiKey = new String(apiKeyField.getPassword());
            if (apiKey.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Please enter a Gemini API key.",
                        "Missing API Key",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // Show "Testing..." dialog
            JDialog testingDialog = new JDialog();
            testingDialog.setTitle("Testing API Connection");
            testingDialog.setSize(300, 100);
            testingDialog.setLocationRelativeTo(this);
            testingDialog.setLayout(new BorderLayout());
            testingDialog.add(new JLabel("Testing Gemini API connection...", JLabel.CENTER));
            
            // Show dialog in a separate thread so it doesn't block UI
            SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
                private String errorMessage = "";
                
                @Override
                protected Boolean doInBackground() {
                    try {
                        // Show dialog
                        SwingUtilities.invokeLater(() -> testingDialog.setVisible(true));
                        
                        String selectedModel = (String) modelComboBox.getSelectedItem();
                        String testUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + selectedModel + ":generateContent";
                        
                        // Create a test client just for this request
                        GeminiAiClient testClient = new GeminiAiClient(apiKey, testUrl, logging);
                        
                        // Create a simple test message
                        Message testMessage = userMessage("Hello! This is a test message from AI HTTP Analyzer extension. Please respond with a short confirmation.");
                        
                        // Send test request
                        testClient.sendPrompt(new Message[]{testMessage});
                        return true;
                    } catch (IOException ex) {
                        errorMessage = ex.getMessage();
                        logging.logToError("Gemini API test failed: " + errorMessage);
                        return false;
                    } finally {
                        // Close dialog
                        SwingUtilities.invokeLater(() -> testingDialog.setVisible(false));
                    }
                }
                
                @Override
                protected void done() {
                    try {
                        boolean success = get();
                        if (success) {
                            JOptionPane.showMessageDialog(SettingsPanel.this,
                                    "Connection successful! Gemini API is working correctly.",
                                    "Test Successful",
                                    JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(SettingsPanel.this,
                                    "Connection failed. Error: " + errorMessage,
                                    "Test Failed",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (Exception ex) {
                        logging.logToError("Error in test worker: " + ex.getMessage());
                    }
                }
            };
            
            worker.execute();
        });
        
        // Help/Info text
        JTextArea infoText = new JTextArea(
                "To get a Gemini API key:\n" +
                "1. Visit https://ai.google.dev/tutorials/setup\n" +
                "2. Create or select a project\n" +
                "3. Enable the Gemini API\n" +
                "4. Generate an API key\n\n" +
                "Copy-paste this curl command to test your API key:\n" +
                "curl -H \"Content-Type: application/json\" \\\n" +
                "     -d '{\"contents\":[{\"parts\":[{\"text\":\"Hello\"}]}]}' \\\n" +
                "     \"https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=YOUR_API_KEY\""
        );
        infoText.setEditable(false);
        infoText.setBackground(null);
        infoText.setBorder(BorderFactory.createTitledBorder("Help"));
        infoText.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Copy curl command button
        JButton copyButton = new JButton("Copy curl command");
        copyButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        copyButton.addActionListener(e -> {
            String curlCommand = "curl -H \"Content-Type: application/json\" \\\n" +
                    "     -d '{\"contents\":[{\"parts\":[{\"text\":\"Hello\"}]}]}' \\\n" +
                    "     \"https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=YOUR_API_KEY\"";
            
            StringSelection selection = new StringSelection(curlCommand);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
            
            JOptionPane.showMessageDialog(this,
                    "curl command copied to clipboard!",
                    "Copied",
                    JOptionPane.INFORMATION_MESSAGE);
        });
        
        // Add components with padding
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(descPane);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        mainPanel.add(apiKeyPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(modelPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonPanel.add(saveButton);
        buttonPanel.add(testButton);
        mainPanel.add(buttonPanel);
        
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        mainPanel.add(infoText);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(copyButton);
        
        // Add scroll support
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        
        add(scrollPane, BorderLayout.CENTER);
    }
}
