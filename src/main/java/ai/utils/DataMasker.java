package ai.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to mask sensitive data in HTTP requests and responses
 * before sending them to AI services for analysis.
 */
public class DataMasker {

    // Common patterns for sensitive data
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(api[-_]?key|apikey|x-api[-_]?key|api[-_]?token|app[-_]?key|app[-_]?secret|client[-_]?secret)([\"\\s:=]+)([\"']?)(\\w+)([\"']?)");
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile("(?i)(Authorization[\"\\s:=]+)([\"']?)(Basic|Bearer|OAuth|Token|Digest|SAML|Negotiate)\\s+([\\w\\.-]+[=]*[/+]*[=]*)([\"']?)");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|passwd|pwd|secret|credentials|pass)([\"\\s:=]+)([\"']?)([^&\"'\\s}{\\]\\[\\n\\r]{3,})([\"']?)");
    private static final Pattern SESSION_COOKIE_PATTERN = Pattern.compile("(?i)((session|sid|auth|token|jwt|access_token|id_token|JSESSIONID|PHPSESSID|ASP.NET_SessionId)=)([^&;\\s]{3,})");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("(?:\\d{4}[-\\s]?){3}\\d{4}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}[-\\s]?\\d{2}[-\\s]?\\d{4}\\b");
    private static final Pattern JWT_TOKEN_PATTERN = Pattern.compile("\\b(eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,})\\b");

    // For storing original values if needed for future reference
    private static final Map<String, String> originalValues = new HashMap<>();    /**
     * Masks sensitive data in the given text
     * @param input The input text containing potentially sensitive data
     * @return The masked text with sensitive data redacted
     */
    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        originalValues.clear();
        String result = input;
        
        // Mask API keys
        result = maskPattern(result, API_KEY_PATTERN, 4);
        
        // Mask auth tokens
        result = maskPattern(result, AUTHORIZATION_PATTERN, 4);
        
        // Mask passwords
        result = maskPattern(result, PASSWORD_PATTERN, 4);
        
        // Mask session cookies
        result = maskPattern(result, SESSION_COOKIE_PATTERN, 3);
        
        // Mask JWT tokens
        result = maskPattern(result, JWT_TOKEN_PATTERN, 1);
        
        // Mask credit cards
        result = maskPattern(result, CREDIT_CARD_PATTERN, 0);
        
        // Mask emails (enabled for better security)
        result = maskPattern(result, EMAIL_PATTERN, 0);
        
        // Mask SSNs
        result = maskPattern(result, SSN_PATTERN, 0);
        
        return result;
    }
    
    /**
     * Applies masking to a specific pattern match
     */
    private static String maskPattern(String input, Pattern pattern, int captureGroup) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String valueToMask = matcher.group(captureGroup);
            if (valueToMask != null && valueToMask.length() > 4) {
                // Keep first 4 chars, mask the rest
                String maskedValue = valueToMask.substring(0, Math.min(4, valueToMask.length())) + 
                                    "*".repeat(Math.max(0, valueToMask.length() - 4));
                
                // Store original for reference if needed
                String key = "masked_" + originalValues.size();
                originalValues.put(key, valueToMask);
                
                String replacement = matcher.group().replace(valueToMask, maskedValue);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
}
