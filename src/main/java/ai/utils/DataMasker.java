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
    // Debug flag - set to false in production
    private static final boolean DEBUG = false;
    
    // Common patterns for sensitive data
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(api[-_]?key|apikey|x-api[-_]?key|api[-_]?token|app[-_]?key|app[-_]?secret|client[-_]?secret|access[-_]?key|secret[-_]?key)([\"\\s:=]+)([\"']?)(\\w{16,})([\"']?)");
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile("(?i)(Authorization[\"\\s:=]+)([\"']?)(Basic|Bearer|OAuth|Token|Digest|SAML|Negotiate)\\s+([\\w\\.-]+[=]*[/+]*[=]*)([\"']?)");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|passwd|pwd|secret|credentials|pass)([\"\\s:=]+)([\"']?)([^&\"'\\s}{\\]\\[\\n\\r]{3,})([\"']?)");
    private static final Pattern SESSION_COOKIE_PATTERN = Pattern.compile("(?i)((session|sid|auth|token|jwt|access_token|id_token|JSESSIONID|PHPSESSID|ASP.NET_SessionId)=)([^&;\\s]{3,})");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("(?:\\d{4}[-\\s]?){3}\\d{4}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}[-\\s]?\\d{2}[-\\s]?\\d{4}\\b");
    private static final Pattern JWT_TOKEN_PATTERN = Pattern.compile("\\b(eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,})\\b");
    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("\\b(AKIA[0-9A-Z]{16})\\b");
    private static final Pattern GOOGLE_API_KEY_PATTERN = Pattern.compile("\\b(AIza[0-9A-Za-z\\-_]{35})\\b");
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile("-----BEGIN (RSA |DSA |EC )?PRIVATE KEY-----");
    private static final Pattern OAUTH_CLIENT_ID_PATTERN = Pattern.compile("(?i)(client_id|client[-_]?secret)([\"\\s:=]+)([\"']?)([\\w-]{10,})([\"']?)");
    private static final Pattern STRIPE_KEY_PATTERN = Pattern.compile("\\b(sk_live_[0-9a-zA-Z]{24}|pk_live_[0-9a-zA-Z]{24})\\b");
    private static final Pattern FIREBASE_KEY_PATTERN = Pattern.compile("\\bAIza[0-9A-Za-z-_]{35}\\b");
    // Additional patterns for more comprehensive masking
    private static final Pattern GENERIC_SECRET_PATTERN = Pattern.compile("(?i)\\b(secret|private|confidential|sensitive)[-_]?([a-zA-Z0-9]{10,})\\b");
    private static final Pattern URL_AUTH_PATTERN = Pattern.compile("(https?://)([^:]+):([^@\\s]+)@");
    private static final Pattern JSON_TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]{8,})\"");
    private static final Pattern IP_ADDRESS_WITH_CREDS = Pattern.compile("([a-zA-Z0-9_-]+):([^@\\s]+)@(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})");
    private static final Pattern AZURE_CONNECTION_STRING = Pattern.compile("(DefaultEndpointsProtocol=https?;AccountName=[^;]+;AccountKey=)([^;]+)(;)");
    private static final Pattern AUTH_TOKEN_HEADER = Pattern.compile("(?i)(x-auth-token|x-access-token|token):\\s*([\\w\\.-]+)");

    // Track which types of sensitive data were detected
    private static final Map<String, Integer> detectedSensitiveDataTypes = new HashMap<>();

    // For storing original values if needed for future reference
    private static final Map<String, String> originalValues = new HashMap<>();    /**
     * Masks sensitive data in the given text
     * @param input The input text containing potentially sensitive data
     * @return The masked text with sensitive data redacted
     */
    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }        // Make sure to clear existing values each time
        originalValues.clear();
        detectedSensitiveDataTypes.clear();
        String result = input;
        
        // Only log in debug mode
        if (DEBUG) {
            System.out.println("[DataMasker] Processing input of length: " + input.length());
        }
        
        // Mask API keys
        result = maskPattern(result, API_KEY_PATTERN, 4, "API Key");
        
        // Mask auth tokens
        result = maskPattern(result, AUTHORIZATION_PATTERN, 4, "Authorization Token");
        
        // Mask passwords
        result = maskPattern(result, PASSWORD_PATTERN, 4, "Password");
        
        // Mask session cookies
        result = maskPattern(result, SESSION_COOKIE_PATTERN, 3, "Session Cookie");
        
        // Mask JWT tokens
        result = maskPattern(result, JWT_TOKEN_PATTERN, 1, "JWT Token");
        
        // Mask AWS keys
        result = maskPattern(result, AWS_KEY_PATTERN, 1, "AWS Key");
        
        // Mask Google API keys
        result = maskPattern(result, GOOGLE_API_KEY_PATTERN, 1, "Google API Key");
        
        // Mask OAuth client IDs and secrets
        result = maskPattern(result, OAUTH_CLIENT_ID_PATTERN, 4, "OAuth Client ID/Secret");
        
        // Mask Stripe keys
        result = maskPattern(result, STRIPE_KEY_PATTERN, 0, "Stripe API Key");
        
        // Mask Firebase keys
        result = maskPattern(result, FIREBASE_KEY_PATTERN, 0, "Firebase Key");
        
        // Mask private keys
        result = maskPattern(result, PRIVATE_KEY_PATTERN, 0, "Private Key");
        
        // Additional masking for improved security
        result = maskPattern(result, GENERIC_SECRET_PATTERN, 2, "Generic Secret");
        result = maskPattern(result, URL_AUTH_PATTERN, 3, "URL Authentication");
        result = maskPattern(result, JSON_TOKEN_PATTERN, 1, "JSON Token");
        result = maskPattern(result, IP_ADDRESS_WITH_CREDS, 2, "IP Credentials");
        result = maskPattern(result, AZURE_CONNECTION_STRING, 2, "Azure Connection String");
        result = maskPattern(result, AUTH_TOKEN_HEADER, 2, "Auth Header Token");
        
        // Mask credit cards
        result = maskPattern(result, CREDIT_CARD_PATTERN, 0, "Credit Card");
        
        // Mask emails (enabled for better security)
        result = maskPattern(result, EMAIL_PATTERN, 0, "Email Address");
        
        // Mask SSNs
        result = maskPattern(result, SSN_PATTERN, 0, "SSN");
        
        return result;
    }    /**
     * Applies masking to a specific pattern match
     */    private static String maskPattern(String input, Pattern pattern, int captureGroup, String dataType) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        int matchCount = 0;
        
        while (matcher.find()) {
            try {
                matchCount++;
                String valueToMask = captureGroup < matcher.groupCount() + 1 ? matcher.group(captureGroup) : matcher.group();
                
                if (valueToMask != null && valueToMask.length() > 2) {
                    // Improved masking: for shorter values, show only 2 chars; for longer, show 4 chars max
                    int charsToShow = Math.min(4, Math.max(2, valueToMask.length() / 4));
                    String maskedValue;
                    
                    // For credit cards and SSNs, use a special masking format
                    if (dataType.equals("Credit Card")) {
                        // Format: XXXX-XXXX-XXXX-1234 (last 4 digits shown)
                        int len = valueToMask.length();
                        maskedValue = "X".repeat(len - 4) + valueToMask.substring(Math.max(0, len - 4));
                    } else {
                        // Standard masking: first few chars + asterisks
                        maskedValue = valueToMask.substring(0, Math.min(charsToShow, valueToMask.length())) + 
                                     "*".repeat(Math.max(0, valueToMask.length() - charsToShow));
                    }                    
                    // Store original for reference if needed (but sanitized)
                    String key = dataType + "_" + originalValues.size();
                    originalValues.put(key, valueToMask);
                    
                    // Track the type of sensitive data detected
                    detectedSensitiveDataTypes.put(dataType, detectedSensitiveDataTypes.getOrDefault(dataType, 0) + 1);
                    
                    // Log in debug mode only
                    if (DEBUG) {
                        System.out.println("[DataMasker] Found " + dataType + ": " + 
                                          valueToMask.substring(0, Math.min(4, valueToMask.length())) + 
                                          "*** (masked)");
                    }
                    
                    // Create a secure replacement
                    String replacement = matcher.group().replace(valueToMask, maskedValue);
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                } else {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                }
            } catch (IndexOutOfBoundsException e) {
                // Fallback for group index issues - just mask the whole match
                matcher.appendReplacement(sb, "[MASKED-DATA]");
                detectedSensitiveDataTypes.put(dataType, detectedSensitiveDataTypes.getOrDefault(dataType, 0) + 1);
            }
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * Returns the count of masked values
     * @return Number of masked values
     */
    public static int getMaskedValueCount() {
        return originalValues.size();
    }
    
    /**
     * Returns a map of the types of sensitive data detected and their counts
     * @return Map of sensitive data types and counts
     */
    public static Map<String, Integer> getDetectedSensitiveDataTypes() {
        return new HashMap<>(detectedSensitiveDataTypes);
    }
    
    /**
     * Clears the stored original values and detection counts
     */
    public static void clearData() {
        originalValues.clear();
        detectedSensitiveDataTypes.clear();
    }
    
    /**
     * Determines if any sensitive data was detected
     * @return true if sensitive data was detected, false otherwise
     */
    public static boolean hasSensitiveData() {
        return !originalValues.isEmpty();
    }
}
