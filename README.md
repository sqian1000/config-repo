# XiaoHongShu Java Client

A Java implementation of the XiaoHongShu (小红书) API client, converted from the original Python version.

## Features

- Asynchronous HTTP requests using Java 11+ HttpClient
- Browser automation support via Playwright
- Comprehensive note and comment crawling
- Creator information retrieval
- Retry logic for robust API calls
- JSON processing with Jackson

## Dependencies

- Java 11+
- Jackson (JSON processing)
- Playwright (browser automation)
- SLF4J (logging)

## Usage

```java
import com.xiaohongshu.client.XiaoHongShuClient;
import com.xiaohongshu.client.field.SearchNoteType;
import com.xiaohongshu.client.field.SearchSortType;
import com.microsoft.playwright.Page;

// Initialize client
Map<String, String> headers = new HashMap<>();
Map<String, String> cookies = new HashMap<>();
XiaoHongShuClient client = new XiaoHongShuClient(
    60, // timeout
    null, // proxy
    headers,
    playwrightPage,
    cookies
);

// Search notes by keyword
CompletableFuture<Map<String, Object>> notes = client.getNoteByKeyword(
    "小红书", 
    "search_id", 
    1, 
    20, 
    SearchSortType.GENERAL, 
    SearchNoteType.ALL
);

// Get note details
CompletableFuture<Map<String, Object>> noteDetail = client.getNoteById(
    "note_id", 
    "pc_search", 
    "xsec_token"
);

// Get all comments for a note
CompletableFuture<List<Map<String, Object>>> comments = client.getNoteAllComments(
    "note_id", 
    "xsec_token", 
    1.0, 
    null, 
    100
);
```

## Build

```bash
mvn clean compile
mvn test
mvn package
```

## Key Differences from Python Version

1. **Asynchronous Programming**: Uses `CompletableFuture` instead of `async/await`
2. **HTTP Client**: Uses Java 11+ HttpClient instead of httpx
3. **JSON Processing**: Uses Jackson instead of built-in json module
4. **Error Handling**: Uses checked exceptions and RuntimeException wrapping
5. **Type Safety**: Strong typing with generics and type casting
6. **Threading**: Uses `Thread.sleep()` for delays instead of `asyncio.sleep()`

## Notes

- The `sign` function from the Python helper module needs to be implemented separately
- Configuration values (like `CRAWLER_MAX_NOTES_COUNT`) need to be defined
- Proxy configuration for HttpClient may need additional setup
- Some utility functions may need to be implemented based on the original Python utils module