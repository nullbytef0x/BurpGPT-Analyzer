package ai.utils;

/**
 * Simple test class to verify DataMasker functionality
 * This is not a unit test, just a helper for development
 */
public class DataMaskingTest {

    public static void testDataMasker() {
        System.out.println("=== Testing DataMasker ===");
        
        // API Key tests
        testMasking("API Key in header", "X-API-KEY: abc123def456ghi789");
        testMasking("API Key in query", "https://example.com/api?apikey=mySecretApiKey123");
        testMasking("API Key in JSON", "\"api_key\": \"1234567890abcdef\"");
        
        // Authorization header tests
        testMasking("Bearer token", "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
        testMasking("Basic auth", "Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=");
        testMasking("OAuth token", "Authorization: OAuth 2YotnFZFEjr1zCsicMWpAA");
        
        // Password tests
        testMasking("Password in form", "password=SuperSecretPassword123&username=john");
        testMasking("Password in JSON", "\"password\": \"SuperSecretPassword123\"");
        
        // Cookie tests
        testMasking("Session cookie", "Cookie: sessionid=1234567890abcdef; path=/");
        testMasking("Auth cookie", "Cookie: auth=2YotnFZFEjr1zCsicMWpAA; path=/");
        
        // Credit card test
        testMasking("Credit card", "cc_number=4111-1111-1111-1111");
        
        // SSN test
        testMasking("SSN", "ssn=123-45-6789");
        
        // Email test
        testMasking("Email", "email=john.doe@example.com");
        
        // More complex test
        testMasking("Complex HTTP request", 
            "POST /api/login HTTP/1.1\n" +
            "Host: example.com\n" +
            "Content-Type: application/json\n" +
            "X-API-KEY: abc123def456ghi789\n" +
            "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9\n" +
            "Cookie: sessionid=1234567890abcdef; auth=2YotnFZFEjr1zCsicMWpAA\n\n" +
            "{\n" +
            "  \"username\": \"john.doe@example.com\",\n" +
            "  \"password\": \"SuperSecretPassword123\",\n" +
            "  \"credit_card\": \"4111-1111-1111-1111\",\n" +
            "  \"ssn\": \"123-45-6789\"\n" +
            "}"
        );
    }
    
    private static void testMasking(String testName, String input) {
        System.out.println("\n--- " + testName + " ---");
        System.out.println("Original: " + input);
        System.out.println("Masked:   " + DataMasker.mask(input));
    }
    
    public static void main(String[] args) {
        testDataMasker();
    }
}
