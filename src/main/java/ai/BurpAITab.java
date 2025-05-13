package ai;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.UserInterface;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class BurpAITab {
    private final JPanel mainPanel;
    private final JTabbedPane tabbedPane;
    private final Map<Component, HttpRequestResponse> tabRequests;
    private int tabCounter = 1;
    private final Logging logging;
    private final PromptHandler promptHandler;
    private final ExecutorService executorService;
    private final UserInterface userInterface;

    public BurpAITab(UserInterface userInterface, Logging logging, PromptHandler promptHandler, ExecutorService executorService) {
        this.userInterface = userInterface;
        this.logging = logging;
        this.promptHandler = promptHandler;
        this.executorService = executorService;

        tabRequests = new HashMap<>();

        mainPanel = new JPanel(new BorderLayout());
        tabbedPane = new JTabbedPane();
        
        // Add initial empty tab
        createNewTab("Default", null);
        
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
    }

    public void sendNewRequestToTab(HttpRequestResponse requestResponse) {
        String tabTitle = "Request " + tabCounter++;
        createNewTab(tabTitle, requestResponse);
    }

    private void createNewTab(String title, HttpRequestResponse requestResponse) {
        Component tabContent = createTabContent(requestResponse);
        tabbedPane.addTab(title, tabContent);
        tabbedPane.setTabComponentAt(tabbedPane.getTabCount() - 1, createTabComponent(title));

        if (requestResponse != null) {
            logging.logToOutput("Creating new tab with request: " + requestResponse.request().toString());
            tabRequests.put(tabContent, requestResponse);
        }

        tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);
    }

    private Component createTabContent(HttpRequestResponse requestResponse) {
        return new BurpAiRequestTab(logging, userInterface, executorService, promptHandler, requestResponse);
    }

    private Component createTabComponent(String title) {
        JPanel tabComponent = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        tabComponent.setOpaque(false);

        // Create label instead of text field
        JLabel titleLabel = new JLabel(title);
        titleLabel.setPreferredSize(new Dimension(70, 30));

        // Create text field for editing (initially invisible)
        JTextField titleField = new JTextField(title);
        titleField.setPreferredSize(new Dimension(100, 20));
        titleField.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        titleField.setVisible(false);

        // Add mouse listener to the entire tab component for selection
        tabComponent.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Select this tab when clicked
                int index = tabbedPane.indexOfTabComponent(tabComponent);
                if (index != -1) {
                    tabbedPane.setSelectedIndex(index);
                }
            }
        });

        // Handle double click on label
        titleLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    titleLabel.setVisible(false);
                    titleField.setText(titleLabel.getText());
                    titleField.setVisible(true);
                    titleField.requestFocus();
                } else if (e.getClickCount() == 1) {
                    // Select this tab on single click too
                    int index = tabbedPane.indexOfTabComponent(tabComponent);
                    if (index != -1) {
                        tabbedPane.setSelectedIndex(index);
                    }
                }
            }
        });

        // Handle editing complete
        titleField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                finishEditing(titleLabel, titleField);
            }
        });

        titleField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    finishEditing(titleLabel, titleField);
                }
            }
        });

        // Improved close button styling
        JButton closeButton = new JButton("×");
        closeButton.setFont(new Font(closeButton.getFont().getName(), Font.PLAIN, 12));
        closeButton.setPreferredSize(new Dimension(12, 12));
        closeButton.setMargin(new Insets(0, 0, 0, 0));
        closeButton.setFocusable(false);
        closeButton.addActionListener(e -> {
            int index = tabbedPane.indexOfTabComponent(tabComponent);
            if (index != -1 && tabbedPane.getTabCount() > 1) { // Prevent closing last tab
                Component content = tabbedPane.getComponentAt(index);
                tabRequests.remove(content);
                tabbedPane.remove(index);
            }
        });

        tabComponent.add(titleLabel);
        tabComponent.add(titleField);
        tabComponent.add(closeButton);
        return tabComponent;
    }

    private void finishEditing(JLabel label, JTextField textField) {
        label.setText(textField.getText());
        label.setVisible(true);
        textField.setVisible(false);
    }

    public Component getUiComponent() {
        return mainPanel;
    }

//    This is unused:
//    private void updateTabContent(Component tabContent, HttpRequestResponse requestResponse) {
//        if (requestResponse == null) {
//            logging.logToError("Request/Response is null");
//            return;
//        }
//
//        logging.logToOutput("Updating tab content with request: " + requestResponse.request().toString());
//
//        if (tabContent instanceof JPanel) {
//            JSplitPane verticalSplit = (JSplitPane) ((JPanel) tabContent).getComponent(0);
//            JSplitPane horizontalSplit = (JSplitPane) verticalSplit.getTopComponent();
//
//            Component leftComponent = horizontalSplit.getLeftComponent();
//            Component rightComponent = horizontalSplit.getRightComponent();
//
//            if (leftComponent instanceof Component) {
//                HttpRequestEditor reqEditor = (HttpRequestEditor) SwingUtilities.getAncestorOfClass(HttpRequestEditor.class, leftComponent);
//                if (reqEditor != null) {
//                    reqEditor.setRequest(requestResponse.request());
//                }
//            }
//
//            if (rightComponent instanceof Component) {
//                HttpResponseEditor respEditor = (HttpResponseEditor) SwingUtilities.getAncestorOfClass(HttpResponseEditor.class, rightComponent);
//                if (respEditor != null) {
//                    respEditor.setResponse(requestResponse.response());
//                }
//            }
//        }
//    }
}
