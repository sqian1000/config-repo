import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Example usage of the XiaoHongShu client
 */
public class Example {
    
    public static void main(String[] args) {
        // Set up Chrome driver (make sure ChromeDriver is in your PATH)
        System.setProperty("webdriver.chrome.driver", "/path/to/chromedriver");
        
        try (WebDriver driver = new ChromeDriver()) {
            // Setup headers
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.put("Accept", "application/json, text/plain, */*");
            headers.put("Accept-Language", "en-US,en;q=0.9");
            headers.put("Accept-Encoding", "gzip, deflate, br");
            headers.put("Connection", "keep-alive");
            
            // Setup cookies (you need to get these from a logged-in browser session)
            Map<String, String> cookies = new HashMap<>();
            cookies.put("a1", "your_a1_cookie_value_here");
            cookies.put("webId", "your_web_id_here");
            cookies.put("web_session", "your_web_session_here");
            
            // Create client instance
            XiaoHongShuClient client = new XiaoHongShuClient(
                60,           // timeout in seconds
                null,         // proxy (optional)
                headers,      // request headers
                driver,       // WebDriver instance
                cookies       // cookie dictionary
            );
            
            // Example 1: Search for notes
            searchNotesExample(client);
            
            // Example 2: Get note details
            getNoteDetailsExample(client);
            
            // Example 3: Get comments
            getCommentsExample(client);
            
            // Example 4: Get user notes
            getUserNotesExample(client);
            
        } catch (Exception e) {
            System.err.println("Example failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Example: Search for notes by keyword
     */
    private static void searchNotesExample(XiaoHongShuClient client) {
        System.out.println("=== Searching for notes ===");
        
        CompletableFuture<Map<String, Object>> searchResult = client.getNoteByKeyword(
            "美食",                    // keyword: food
            "search_id_example",      // search ID
            1,                        // page number
            10,                       // page size
            SearchSortType.POPULAR,   // sort by popularity
            SearchNoteType.ALL        // all note types
        );
        
        searchResult.thenAccept(result -> {
            System.out.println("Search completed successfully");
            if (result.containsKey("items")) {
                System.out.println("Found items in search results");
            }
        }).exceptionally(throwable -> {
            System.err.println("Search failed: " + throwable.getMessage());
            return null;
        });
    }
    
    /**
     * Example: Get note details by ID
     */
    private static void getNoteDetailsExample(XiaoHongShuClient client) {
        System.out.println("=== Getting note details ===");
        
        // Note: You need actual note ID, xsec source, and token from search results
        String noteId = "example_note_id";
        String xsecSource = "pc_search";
        String xsecToken = "example_xsec_token";
        
        CompletableFuture<Map<String, Object>> noteDetails = client.getNoteById(
            noteId, xsecSource, xsecToken
        );
        
        noteDetails.thenAccept(note -> {
            if (note != null && !note.isEmpty()) {
                System.out.println("Note details retrieved successfully");
                System.out.println("Note title: " + note.get("title"));
                System.out.println("Note content: " + note.get("content"));
            } else {
                System.out.println("No note details found");
            }
        }).exceptionally(throwable -> {
            System.err.println("Failed to get note details: " + throwable.getMessage());
            return null;
        });
    }
    
    /**
     * Example: Get comments for a note
     */
    private static void getCommentsExample(XiaoHongShuClient client) {
        System.out.println("=== Getting comments ===");
        
        String noteId = "example_note_id";
        String xsecToken = "example_xsec_token";
        
        CompletableFuture<List<Map<String, Object>>> allComments = client.getNoteAllComments(
            noteId,                    // note ID
            xsecToken,                 // xsec token
            1.0f,                      // crawl interval (1 second)
            new XiaoHongShuClient.CommentCallback() {
                @Override
                public void onComment(String noteId, List<Map<String, Object>> comments) {
                    System.out.println("Received " + comments.size() + " comments for note " + noteId);
                    // Process comments here
                    for (Map<String, Object> comment : comments) {
                        String content = (String) comment.get("content");
                        String userId = (String) comment.get("user_id");
                        System.out.println("Comment from user " + userId + ": " + content);
                    }
                }
            },
            50                         // max comment count
        );
        
        allComments.thenAccept(comments -> {
            System.out.println("Total comments retrieved: " + comments.size());
        }).exceptionally(throwable -> {
            System.err.println("Failed to get comments: " + throwable.getMessage());
            return null;
        });
    }
    
    /**
     * Example: Get all notes by a user
     */
    private static void getUserNotesExample(XiaoHongShuClient client) {
        System.out.println("=== Getting user notes ===");
        
        String userId = "example_user_id";
        
        CompletableFuture<List<Map<String, Object>>> userNotes = client.getAllNotesByCreator(
            userId,                    // user ID
            1.0f,                      // crawl interval (1 second)
            new XiaoHongShuClient.NotesCallback() {
                @Override
                public void onNotes(List<Map<String, Object>> notes) {
                    System.out.println("Received " + notes.size() + " notes from user " + userId);
                    // Process notes here
                    for (Map<String, Object> note : notes) {
                        String title = (String) note.get("title");
                        String noteId = (String) note.get("id");
                        System.out.println("Note ID: " + noteId + ", Title: " + title);
                    }
                }
            }
        );
        
        userNotes.thenAccept(notes -> {
            System.out.println("Total notes retrieved for user: " + notes.size());
        }).exceptionally(throwable -> {
            System.err.println("Failed to get user notes: " + throwable.getMessage());
            return null;
        });
    }
    
    /**
     * Example: Download media from a note
     */
    private static void downloadMediaExample(XiaoHongShuClient client) {
        System.out.println("=== Downloading media ===");
        
        String mediaUrl = "https://example.com/media.jpg";
        
        CompletableFuture<byte[]> mediaContent = client.getNoteMedia(mediaUrl);
        
        mediaContent.thenAccept(content -> {
            if (content != null) {
                System.out.println("Media downloaded successfully, size: " + content.length + " bytes");
                // Here you would save the content to a file
                // Files.write(Paths.get("downloaded_media.jpg"), content);
            } else {
                System.out.println("Failed to download media");
            }
        }).exceptionally(throwable -> {
            System.err.println("Media download failed: " + throwable.getMessage());
            return null;
        });
    }
    
    /**
     * Example: Chain multiple operations
     */
    private static void chainedOperationsExample(XiaoHongShuClient client) {
        System.out.println("=== Chained operations ===");
        
        // Search for notes, then get details for the first result
        client.getNoteByKeyword("旅行", "search_id", 1, 5, SearchSortType.GENERAL, SearchNoteType.ALL)
            .thenCompose(searchResult -> {
                System.out.println("Search completed, processing results...");
                
                if (searchResult.containsKey("items")) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) searchResult.get("items");
                    if (!items.isEmpty()) {
                        Map<String, Object> firstItem = items.get(0);
                        String noteId = (String) firstItem.get("id");
                        System.out.println("Getting details for note: " + noteId);
                        
                        // Get note details
                        return client.getNoteById(noteId, "pc_search", "token");
                    }
                }
                
                return CompletableFuture.completedFuture(null);
            })
            .thenAccept(note -> {
                if (note != null) {
                    System.out.println("Note details retrieved: " + note.get("title"));
                } else {
                    System.out.println("No note details available");
                }
            })
            .exceptionally(throwable -> {
                System.err.println("Chained operation failed: " + throwable.getMessage());
                return null;
            });
    }
}