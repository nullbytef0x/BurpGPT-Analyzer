package ai;

import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static java.util.Collections.emptyList;

public class BurpAIContextMenu implements ContextMenuItemsProvider {
    private final BurpAITab burpAITab;

    public BurpAIContextMenu(BurpAITab burpAITab) {
        this.burpAITab = burpAITab;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        if (!event.isFromTool(ToolType.PROXY) && !event.isFromTool(ToolType.REPEATER) && !event.isFromTool(ToolType.TARGET)) {
            return emptyList();
        }

        JMenuItem sendToBurpAI = new JMenuItem("Send to BurpGPT Analyzer");
        sendToBurpAI.addActionListener(e -> {
            HttpRequestResponse requestResponse = event.messageEditorRequestResponse().isPresent()
                    ? event.messageEditorRequestResponse().get().requestResponse()
                    : event.selectedRequestResponses().get(0);

            burpAITab.sendNewRequestToTab(requestResponse);
        });

        return List.of(sendToBurpAI);
    }
}
