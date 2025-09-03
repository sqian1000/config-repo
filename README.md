# XiaoHongShu Client (Java)

A Java client for interacting with the Xiaohongshu (Little Red Book) API. This is a Java port of the Python XiaoHongShu client, providing the same functionality with Java-specific implementations.

## Features

- **API Authentication**: Handles request signing and authentication
- **Note Search**: Search for notes by keywords with various filters
- **Note Details**: Retrieve detailed information about specific notes
- **Comments**: Get comments and sub-comments for notes
- **User Information**: Fetch creator profiles and their notes
- **Media Download**: Download note media content
- **Browser Integration**: Uses Selenium WebDriver for JavaScript execution
- **Async Operations**: Built with CompletableFuture for non-blocking operations
- **Error Handling**: Comprehensive error handling with custom exceptions

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher
- Chrome/Chromium browser (for Selenium WebDriver)

## Installation

1. Clone the repository:
```bash
git clone <repository-url>
cd xiaohongshu-client
```

2. Install dependencies:
```bash
mvn clean install
```

## Dependencies

The project uses the following main dependencies:

- **Jackson**: JSON processing and serialization
- **OkHttp**: HTTP client for API requests
- **Selenium**: WebDriver for browser automation and JavaScript execution
- **JUnit**: Unit testing framework

## Usage

### Basic Setup

```java
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

// Initialize WebDriver
WebDriver driver = new ChromeDriver();

// Setup headers and cookies
Map<String, String> headers = new HashMap<>();
headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

Map<String, String> cookies = new HashMap<>();
cookies.put("a1", "your_a1_cookie_value");

// Create client instance
XiaoHongShuClient client = new XiaoHongShuClient(
    60,           // timeout
    null,         // proxy (optional)
    headers,      // request headers
    driver,       // WebDriver instance
    cookies       // cookie dictionary
);
```

### Search Notes

```java
// Search for notes by keyword
CompletableFuture<Map<String, Object>> searchResult = client.getNoteByKeyword(
    "小红书",                    // keyword
    "search_id_here",          // search ID
    1,                         // page number
    20,                        // page size
    SearchSortType.GENERAL,    // sort type
    SearchNoteType.ALL         // note type
);

// Handle the result
searchResult.thenAccept(result -> {
    System.out.println("Search completed: " + result);
}).exceptionally(throwable -> {
    System.err.println("Search failed: " + throwable.getMessage());
    return null;
});
```

### Get Note Details

```java
// Get note details by ID
CompletableFuture<Map<String, Object>> noteDetails = client.getNoteById(
    "note_id_here",      // note ID
    "pc_search",         // xsec source
    "xsec_token_here"    // xsec token
);

noteDetails.thenAccept(note -> {
    System.out.println("Note title: " + note.get("title"));
    System.out.println("Note content: " + note.get("content"));
});
```

### Get Comments

```java
// Get all comments for a note
CompletableFuture<List<Map<String, Object>>> allComments = client.getNoteAllComments(
    "note_id_here",           // note ID
    "xsec_token_here",        // xsec token
    1.0f,                     // crawl interval (seconds)
    new CommentCallback() {    // callback for processing comments
        @Override
        public void onComment(String noteId, List<Map<String, Object>> comments) {
            System.out.println("Received " + comments.size() + " comments for note " + noteId);
        }
    },
    100                       // max comment count
);
```

### Get User Notes

```java
// Get all notes by a creator
CompletableFuture<List<Map<String, Object>>> userNotes = client.getAllNotesByCreator(
    "user_id_here",           // user ID
    1.0f,                     // crawl interval (seconds)
    new NotesCallback() {      // callback for processing notes
        @Override
        public void onNotes(List<Map<String, Object>> notes) {
            System.out.println("Received " + notes.size() + " notes from user");
        }
    }
);
```

### Download Media

```java
// Download note media
CompletableFuture<byte[]> mediaContent = client.getNoteMedia("media_url_here");

mediaContent.thenAccept(content -> {
    if (content != null) {
        // Save content to file
        try {
            Files.write(Paths.get("media.jpg"), content);
            System.out.println("Media downloaded successfully");
        } catch (IOException e) {
            System.err.println("Failed to save media: " + e.getMessage());
        }
    }
});
```

## Configuration

The client can be configured through the `Config` class:

```java
// Enable/disable sub-comment crawling
Config.ENABLE_GET_SUB_COMMENTS = true;

// Set maximum notes count for creators
Config.CRAWLER_MAX_NOTES_COUNT = 1000;
```

## Error Handling

The client throws specific exceptions for different error scenarios:

- `DataFetchError`: General API errors
- `IPBlockError`: IP address blocked by the service

```java
try {
    Map<String, Object> result = client.getNoteById("note_id", "source", "token").get();
} catch (IPBlockError e) {
    System.err.println("IP blocked: " + e.getMessage());
    // Handle IP blocking (e.g., change proxy, wait, etc.)
} catch (DataFetchError e) {
    System.err.println("Data fetch error: " + e.getMessage());
    // Handle general API errors
} catch (Exception e) {
    System.err.println("Unexpected error: " + e.getMessage());
}
```

## Async Operations

All API methods return `CompletableFuture` objects, allowing for non-blocking operations:

```java
// Chain multiple operations
client.getNoteByKeyword("keyword", "search_id", 1, 20, SearchSortType.GENERAL, SearchNoteType.ALL)
    .thenCompose(searchResult -> {
        // Process search results
        List<Map<String, Object>> items = (List<Map<String, Object>>) searchResult.get("items");
        if (!items.isEmpty()) {
            String noteId = (String) items.get(0).get("id");
            return client.getNoteById(noteId, "pc_search", "token");
        }
        return CompletableFuture.completedFuture(null);
    })
    .thenAccept(note -> {
        if (note != null) {
            System.out.println("Note details: " + note);
        }
    })
    .exceptionally(throwable -> {
        System.err.println("Operation failed: " + throwable.getMessage());
        return null;
    });
```

## Browser Integration

The client uses Selenium WebDriver to execute JavaScript for request signing. Make sure you have the appropriate WebDriver installed:

```bash
# For Chrome
# Download ChromeDriver from https://chromedriver.chromium.org/
# Add to PATH or specify in code

System.setProperty("webdriver.chrome.driver", "/path/to/chromedriver");
WebDriver driver = new ChromeDriver();
```

## Testing

Run the test suite:

```bash
mvn test
```

## Building

Build the project:

```bash
mvn clean package
```

This will create a JAR file in the `target` directory.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Notes

- The client requires valid cookies and authentication tokens to work properly
- Some operations may be rate-limited by the service
- The signing algorithm implementation may need to be updated based on service changes
- Always respect the service's terms of service and rate limits