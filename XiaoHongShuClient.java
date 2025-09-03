package com.xiaohongshu.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.xiaohongshu.client.exception.DataFetchError;
import com.xiaohongshu.client.exception.IPBlockError;
import com.xiaohongshu.client.field.SearchNoteType;
import com.xiaohongshu.client.field.SearchSortType;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XiaoHongShu API Client for Java
 * Converted from Python implementation
 */
public class XiaoHongShuClient {
    
    private final String proxy;
    private final int timeout;
    private final Map<String, String> headers;
    private final String host = "https://edith.xiaohongshu.com";
    private final String domain = "https://www.xiaohongshu.com";
    private final String IP_ERROR_STR = "网络连接异常，请检查网络设置或重启试试";
    private final int IP_ERROR_CODE = 300012;
    private final String NOTE_ABNORMAL_STR = "笔记状态异常，请稍后查看";
    private final int NOTE_ABNORMAL_CODE = -510001;
    
    private final Page playwrightPage;
    private Map<String, String> cookieDict;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public XiaoHongShuClient(int timeout, String proxy, Map<String, String> headers, 
                           Page playwrightPage, Map<String, String> cookieDict) {
        this.timeout = timeout;
        this.proxy = proxy;
        this.headers = new HashMap<>(headers);
        this.playwrightPage = playwrightPage;
        this.cookieDict = new HashMap<>(cookieDict);
        this.objectMapper = new ObjectMapper();
        
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout));
        
        if (proxy != null && !proxy.isEmpty()) {
            // Note: Java HTTP client proxy configuration would need additional setup
            // This is a simplified version
        }
        
        this.httpClient = clientBuilder.build();
    }
    
    /**
     * Pre-process headers with signature
     */
    private CompletableFuture<Map<String, String>> preHeaders(String url, Object data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Call JavaScript function in Playwright page
                Object[] params = {url, data};
                Object encryptParams = playwrightPage.evaluate("([url, data]) => window._webmsxyw(url,data)", params);
                Object localStorage = playwrightPage.evaluate("() => window.localStorage");
                
                // Convert to maps for easier access
                @SuppressWarnings("unchecked")
                Map<String, Object> encryptParamsMap = (Map<String, Object>) encryptParams;
                @SuppressWarnings("unchecked")
                Map<String, Object> localStorageMap = (Map<String, Object>) localStorage;
                
                // Generate signatures (this would need to be implemented based on the sign function)
                Map<String, String> signs = generateSigns(
                    cookieDict.getOrDefault("a1", ""),
                    (String) localStorageMap.getOrDefault("b1", ""),
                    (String) encryptParamsMap.getOrDefault("X-s", ""),
                    String.valueOf(encryptParamsMap.getOrDefault("X-t", ""))
                );
                
                Map<String, String> newHeaders = new HashMap<>(headers);
                newHeaders.put("X-S", signs.get("x-s"));
                newHeaders.put("X-T", signs.get("x-t"));
                newHeaders.put("x-S-Common", signs.get("x-s-common"));
                newHeaders.put("X-B3-Traceid", signs.get("x-b3-traceid"));
                
                return newHeaders;
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate headers", e);
            }
        });
    }
    
    /**
     * Placeholder for sign function - needs to be implemented based on the Python sign function
     */
    private Map<String, String> generateSigns(String a1, String b1, String xS, String xT) {
        // This needs to be implemented based on the actual signing logic
        // from the Python help.sign function
        Map<String, String> signs = new HashMap<>();
        signs.put("x-s", xS);
        signs.put("x-t", xT);
        signs.put("x-s-common", ""); // Placeholder
        signs.put("x-b3-traceid", ""); // Placeholder
        return signs;
    }
    
    /**
     * Generic HTTP request method with retry logic
     */
    public CompletableFuture<Object> request(String method, String url, Map<String, String> requestHeaders, 
                                           String body, boolean returnResponse) {
        return CompletableFuture.supplyAsync(() -> {
            int maxRetries = 3;
            int retryCount = 0;
            
            while (retryCount < maxRetries) {
                try {
                    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(timeout));
                    
                    // Add headers
                    if (requestHeaders != null) {
                        for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
                            requestBuilder.header(header.getKey(), header.getValue());
                        }
                    }
                    
                    // Set method and body
                    switch (method.toUpperCase()) {
                        case "GET":
                            requestBuilder.GET();
                            break;
                        case "POST":
                            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                                body != null ? body : "", StandardCharsets.UTF_8));
                            requestBuilder.header("Content-Type", "application/json");
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
                    }
                    
                    HttpRequest request = requestBuilder.build();
                    HttpResponse<String> response = httpClient.send(request, 
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    
                    // Handle specific status codes
                    if (response.statusCode() == 471 || response.statusCode() == 461) {
                        String verifyType = response.headers().firstValue("Verifytype").orElse("");
                        String verifyUuid = response.headers().firstValue("Verifyuuid").orElse("");
                        String msg = String.format("出现验证码，请求失败，Verifytype: %s，Verifyuuid: %s, Response: %s", 
                                verifyType, verifyUuid, response.body());
                        throw new RuntimeException(msg);
                    }
                    
                    if (returnResponse) {
                        return response.body();
                    }
                    
                    // Parse JSON response
                    Map<String, Object> data = objectMapper.readValue(response.body(), 
                            new TypeReference<Map<String, Object>>() {});
                    
                    Boolean success = (Boolean) data.get("success");
                    if (success != null && success) {
                        return data.getOrDefault("data", data.getOrDefault("success", new HashMap<>()));
                    } else {
                        Integer code = (Integer) data.get("code");
                        if (code != null && code == IP_ERROR_CODE) {
                            throw new RuntimeException(new IPBlockError(IP_ERROR_STR));
                        } else {
                            String msg = (String) data.get("msg");
                            throw new RuntimeException(new DataFetchError(msg));
                        }
                    }
                    
                } catch (Exception e) {
                    retryCount++;
                    if (retryCount >= maxRetries) {
                        throw new RuntimeException("Request failed after " + maxRetries + " attempts", e);
                    }
                    
                    try {
                        TimeUnit.SECONDS.sleep(1); // Wait 1 second before retry
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry wait", ie);
                    }
                }
            }
            
            throw new RuntimeException("Request failed after all retry attempts");
        });
    }
    
    /**
     * GET request with signature headers
     */
    public CompletableFuture<Map<String, Object>> get(String uri, Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String finalUri = uri;
                if (params != null && !params.isEmpty()) {
                    finalUri = uri + "?" + encodeParams(params);
                }
                
                Map<String, String> requestHeaders = preHeaders(finalUri, null).join();
                Object result = request("GET", host + finalUri, requestHeaders, null, false).join();
                
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) result;
                return resultMap;
            } catch (Exception e) {
                throw new RuntimeException("GET request failed", e);
            }
        });
    }
    
    /**
     * POST request with signature headers
     */
    public CompletableFuture<Map<String, Object>> post(String uri, Map<String, Object> data, boolean returnResponse) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> requestHeaders = preHeaders(uri, data).join();
                String jsonStr = objectMapper.writeValueAsString(data);
                Object result = request("POST", host + uri, requestHeaders, jsonStr, returnResponse).join();
                
                if (returnResponse) {
                    // Return raw string response
                    Map<String, Object> responseMap = new HashMap<>();
                    responseMap.put("response", result);
                    return responseMap;
                }
                
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) result;
                return resultMap;
            } catch (Exception e) {
                throw new RuntimeException("POST request failed", e);
            }
        });
    }
    
    public CompletableFuture<Map<String, Object>> post(String uri, Map<String, Object> data) {
        return post(uri, data, false);
    }
    
    /**
     * Get note media content
     */
    public CompletableFuture<byte[]> getNoteMedia(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(timeout))
                        .GET()
                        .build();
                
                HttpResponse<byte[]> response = httpClient.send(request, 
                        HttpResponse.BodyHandlers.ofByteArray());
                
                if (response.statusCode() == 200) {
                    return response.body();
                } else {
                    System.err.println("[XiaoHongShuClient.getNoteMedia] request " + url + 
                            " err, status: " + response.statusCode());
                    return null;
                }
            } catch (Exception e) {
                System.err.println("[XiaoHongShuClient.getNoteMedia] Exception for " + url + " - " + e.getMessage());
                return null;
            }
        });
    }
    
    /**
     * Ping method to check if login state is valid
     */
    public CompletableFuture<Boolean> pong() {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("[XiaoHongShuClient.pong] Begin to pong xhs...");
            try {
                Map<String, Object> noteCard = getNoteByKeyword("小红书", getSearchId(), 1, 20, 
                        SearchSortType.GENERAL, SearchNoteType.ALL).join();
                
                @SuppressWarnings("unchecked")
                List<Object> items = (List<Object>) noteCard.get("items");
                return items != null && !items.isEmpty();
            } catch (Exception e) {
                System.err.println("[XiaoHongShuClient.pong] Ping xhs failed: " + e.getMessage() + 
                        ", and try to login again...");
                return false;
            }
        });
    }
    
    /**
     * Update cookies from browser context
     */
    public void updateCookies(Map<String, String> newCookieDict) {
        this.cookieDict = new HashMap<>(newCookieDict);
        // Convert cookie dict to cookie string
        StringBuilder cookieStr = new StringBuilder();
        for (Map.Entry<String, String> entry : cookieDict.entrySet()) {
            if (cookieStr.length() > 0) {
                cookieStr.append("; ");
            }
            cookieStr.append(entry.getKey()).append("=").append(entry.getValue());
        }
        headers.put("Cookie", cookieStr.toString());
    }
    
    /**
     * Search notes by keyword
     */
    public CompletableFuture<Map<String, Object>> getNoteByKeyword(String keyword, String searchId, 
            int page, int pageSize, SearchSortType sort, SearchNoteType noteType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String uri = "/api/sns/web/v1/search/notes";
                Map<String, Object> data = new HashMap<>();
                data.put("keyword", keyword);
                data.put("page", page);
                data.put("page_size", pageSize);
                data.put("search_id", searchId);
                data.put("sort", sort.getValue());
                data.put("note_type", noteType.getValue());
                
                return post(uri, data).join();
            } catch (Exception e) {
                throw new RuntimeException("Failed to get note by keyword", e);
            }
        });
    }
    
    /**
     * Get note by ID
     */
    public CompletableFuture<Map<String, Object>> getNoteById(String noteId, String xsecSource, String xsecToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (xsecSource == null || xsecSource.isEmpty()) {
                    xsecSource = "pc_search";
                }
                
                Map<String, Object> data = new HashMap<>();
                data.put("source_note_id", noteId);
                data.put("image_formats", Arrays.asList("jpg", "webp", "avif"));
                
                Map<String, Object> extra = new HashMap<>();
                extra.put("need_body_topic", 1);
                data.put("extra", extra);
                data.put("xsec_source", xsecSource);
                data.put("xsec_token", xsecToken);
                
                String uri = "/api/sns/web/v1/feed";
                Map<String, Object> res = post(uri, data).join();
                
                if (res != null && res.containsKey("items")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> items = (List<Map<String, Object>>) res.get("items");
                    if (!items.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> noteCard = (Map<String, Object>) items.get(0).get("note_card");
                        return noteCard;
                    }
                }
                
                System.err.println("[XiaoHongShuClient.getNoteById] get note id:" + noteId + 
                        " empty and res:" + res);
                return new HashMap<>();
            } catch (Exception e) {
                throw new RuntimeException("Failed to get note by ID", e);
            }
        });
    }
    
    /**
     * Get note comments
     */
    public CompletableFuture<Map<String, Object>> getNoteComments(String noteId, String xsecToken, String cursor) {
        String uri = "/api/sns/web/v2/comment/page";
        Map<String, Object> params = new HashMap<>();
        params.put("note_id", noteId);
        params.put("cursor", cursor != null ? cursor : "");
        params.put("top_comment_id", "");
        params.put("image_formats", "jpg,webp,avif");
        params.put("xsec_token", xsecToken);
        
        return get(uri, params);
    }
    
    /**
     * Get sub comments for a specific parent comment
     */
    public CompletableFuture<Map<String, Object>> getNoteSubComments(String noteId, String rootCommentId, 
            String xsecToken, int num, String cursor) {
        String uri = "/api/sns/web/v2/comment/sub/page";
        Map<String, Object> params = new HashMap<>();
        params.put("note_id", noteId);
        params.put("root_comment_id", rootCommentId);
        params.put("num", num);
        params.put("cursor", cursor != null ? cursor : "");
        params.put("image_formats", "jpg,webp,avif");
        params.put("top_comment_id", "");
        params.put("xsec_token", xsecToken);
        
        return get(uri, params);
    }
    
    /**
     * Get all comments for a note
     */
    public CompletableFuture<List<Map<String, Object>>> getNoteAllComments(String noteId, String xsecToken, 
            double crawlInterval, Consumer<List<Map<String, Object>>> callback, int maxCount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Map<String, Object>> result = new ArrayList<>();
                boolean commentsHasMore = true;
                String commentsCursor = "";
                
                while (commentsHasMore && result.size() < maxCount) {
                    Map<String, Object> commentsRes = getNoteComments(noteId, xsecToken, commentsCursor).join();
                    commentsHasMore = (Boolean) commentsRes.getOrDefault("has_more", false);
                    commentsCursor = (String) commentsRes.getOrDefault("cursor", "");
                    
                    if (!commentsRes.containsKey("comments")) {
                        System.out.println("[XiaoHongShuClient.getNoteAllComments] No 'comments' key found in response: " + commentsRes);
                        break;
                    }
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> comments = (List<Map<String, Object>>) commentsRes.get("comments");
                    
                    if (result.size() + comments.size() > maxCount) {
                        comments = comments.subList(0, maxCount - result.size());
                    }
                    
                    if (callback != null) {
                        callback.accept(comments);
                    }
                    
                    Thread.sleep((long) (crawlInterval * 1000));
                    result.addAll(comments);
                    
                    // Get sub comments
                    List<Map<String, Object>> subComments = getCommentsAllSubComments(
                            comments, xsecToken, crawlInterval, callback).join();
                    result.addAll(subComments);
                }
                
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Failed to get all comments", e);
            }
        });
    }
    
    /**
     * Get all sub comments for a list of parent comments
     */
    public CompletableFuture<List<Map<String, Object>>> getCommentsAllSubComments(
            List<Map<String, Object>> comments, String xsecToken, double crawlInterval, 
            Consumer<List<Map<String, Object>>> callback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if sub comments crawling is enabled (would need config implementation)
                boolean enableGetSubComments = true; // Placeholder for config.ENABLE_GET_SUB_COMMENTS
                if (!enableGetSubComments) {
                    System.out.println("[XiaoHongShuClient.getCommentsAllSubComments] Crawling sub_comment mode is not enabled");
                    return new ArrayList<>();
                }
                
                List<Map<String, Object>> result = new ArrayList<>();
                
                for (Map<String, Object> comment : comments) {
                    String noteId = (String) comment.get("note_id");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> subComments = (List<Map<String, Object>>) comment.get("sub_comments");
                    
                    if (subComments != null && callback != null) {
                        callback.accept(subComments);
                    }
                    
                    Boolean subCommentHasMore = (Boolean) comment.get("sub_comment_has_more");
                    if (subCommentHasMore == null || !subCommentHasMore) {
                        continue;
                    }
                    
                    String rootCommentId = (String) comment.get("id");
                    String subCommentCursor = (String) comment.get("sub_comment_cursor");
                    
                    while (subCommentHasMore) {
                        Map<String, Object> commentsRes = getNoteSubComments(
                                noteId, rootCommentId, xsecToken, 10, subCommentCursor).join();
                        
                        if (commentsRes == null) {
                            System.out.println("[XiaoHongShuClient.getCommentsAllSubComments] No response found for note_id: " + noteId);
                            continue;
                        }
                        
                        subCommentHasMore = (Boolean) commentsRes.getOrDefault("has_more", false);
                        subCommentCursor = (String) commentsRes.getOrDefault("cursor", "");
                        
                        if (!commentsRes.containsKey("comments")) {
                            System.out.println("[XiaoHongShuClient.getCommentsAllSubComments] No 'comments' key found in response: " + commentsRes);
                            break;
                        }
                        
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> subCommentsList = (List<Map<String, Object>>) commentsRes.get("comments");
                        
                        if (callback != null) {
                            callback.accept(subCommentsList);
                        }
                        
                        Thread.sleep((long) (crawlInterval * 1000));
                        result.addAll(subCommentsList);
                    }
                }
                
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Failed to get all sub comments", e);
            }
        });
    }
    
    /**
     * Get creator information by parsing HTML
     */
    public CompletableFuture<Map<String, Object>> getCreatorInfo(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String uri = "/user/profile/" + userId;
                Object htmlContent = request("GET", domain + uri, headers, null, true).join();
                
                Pattern pattern = Pattern.compile("<script>window.__INITIAL_STATE__=(.+)</script>", Pattern.MULTILINE);
                Matcher matcher = pattern.matcher((String) htmlContent);
                
                if (!matcher.find()) {
                    return new HashMap<>();
                }
                
                String stateJson = matcher.group(1).replace(":undefined", ":null");
                Map<String, Object> info = objectMapper.readValue(stateJson, 
                        new TypeReference<Map<String, Object>>() {});
                
                if (info == null) {
                    return new HashMap<>();
                }
                
                @SuppressWarnings("unchecked")
                Map<String, Object> user = (Map<String, Object>) info.get("user");
                @SuppressWarnings("unchecked")
                Map<String, Object> userPageData = (Map<String, Object>) user.get("userPageData");
                
                return userPageData != null ? userPageData : new HashMap<>();
            } catch (Exception e) {
                throw new RuntimeException("Failed to get creator info", e);
            }
        });
    }
    
    /**
     * Get notes by creator
     */
    public CompletableFuture<Map<String, Object>> getNotesByCreator(String creator, String cursor, int pageSize) {
        String uri = "/api/sns/web/v1/user_posted";
        Map<String, Object> data = new HashMap<>();
        data.put("user_id", creator);
        data.put("cursor", cursor);
        data.put("num", pageSize);
        data.put("image_formats", "jpg,webp,avif");
        
        return get(uri, data);
    }
    
    /**
     * Get all notes by creator
     */
    public CompletableFuture<List<Map<String, Object>>> getAllNotesByCreator(String userId, 
            double crawlInterval, Consumer<List<Map<String, Object>>> callback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Map<String, Object>> result = new ArrayList<>();
                boolean notesHasMore = true;
                String notesCursor = "";
                int maxNotesCount = 1000; // Placeholder for config.CRAWLER_MAX_NOTES_COUNT
                
                while (notesHasMore && result.size() < maxNotesCount) {
                    Map<String, Object> notesRes = getNotesByCreator(userId, notesCursor, 30).join();
                    
                    if (notesRes == null || notesRes.isEmpty()) {
                        System.err.println("[XiaoHongShuClient.getNotesByCreator] The current creator may have been banned by xhs, so they cannot access the data.");
                        break;
                    }
                    
                    notesHasMore = (Boolean) notesRes.getOrDefault("has_more", false);
                    notesCursor = (String) notesRes.getOrDefault("cursor", "");
                    
                    if (!notesRes.containsKey("notes")) {
                        System.out.println("[XiaoHongShuClient.getAllNotesByCreator] No 'notes' key found in response: " + notesRes);
                        break;
                    }
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> notes = (List<Map<String, Object>>) notesRes.get("notes");
                    System.out.println("[XiaoHongShuClient.getAllNotesByCreator] got user_id:" + userId + 
                            " notes len : " + notes.size());
                    
                    int remaining = maxNotesCount - result.size();
                    if (remaining <= 0) {
                        break;
                    }
                    
                    List<Map<String, Object>> notesToAdd = notes.subList(0, Math.min(notes.size(), remaining));
                    if (callback != null) {
                        callback.accept(notesToAdd);
                    }
                    
                    result.addAll(notesToAdd);
                    Thread.sleep((long) (crawlInterval * 1000));
                }
                
                System.out.println("[XiaoHongShuClient.getAllNotesByCreator] Finished getting notes for user " + 
                        userId + ", total: " + result.size());
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Failed to get all notes by creator", e);
            }
        });
    }
    
    /**
     * Get note short URL
     */
    public CompletableFuture<String> getNoteShortUrl(String noteId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String uri = "/api/sns/web/short_url";
                Map<String, Object> data = new HashMap<>();
                data.put("original_url", domain + "/discovery/item/" + noteId);
                
                Map<String, Object> response = post(uri, data, true).join();
                return (String) response.get("response");
            } catch (Exception e) {
                throw new RuntimeException("Failed to get note short URL", e);
            }
        });
    }
    
    /**
     * Get note by ID from HTML parsing with retry logic
     */
    public CompletableFuture<Map<String, Object>> getNoteByIdFromHtml(String noteId, String xsecSource, 
            String xsecToken, boolean enableCookie) {
        return CompletableFuture.supplyAsync(() -> {
            int maxRetries = 3;
            int retryCount = 0;
            
            while (retryCount < maxRetries) {
                try {
                    String url = "https://www.xiaohongshu.com/explore/" + noteId + 
                            "?xsec_token=" + URLEncoder.encode(xsecToken, StandardCharsets.UTF_8) + 
                            "&xsec_source=" + URLEncoder.encode(xsecSource, StandardCharsets.UTF_8);
                    
                    Map<String, String> copyHeaders = new HashMap<>(headers);
                    if (!enableCookie) {
                        copyHeaders.remove("Cookie");
                    }
                    
                    Object htmlContent = request("GET", url, copyHeaders, null, true).join();
                    return parseNoteFromHtml((String) htmlContent, noteId);
                    
                } catch (Exception e) {
                    retryCount++;
                    if (retryCount >= maxRetries) {
                        System.err.println("Failed to get note from HTML after " + maxRetries + " attempts: " + e.getMessage());
                        return null;
                    }
                    
                    try {
                        Thread.sleep(1000); // Wait 1 second before retry
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry wait", ie);
                    }
                }
            }
            
            return null;
        });
    }
    
    /**
     * Parse note information from HTML content
     */
    private Map<String, Object> parseNoteFromHtml(String html, String noteId) {
        try {
            Pattern pattern = Pattern.compile("window.__INITIAL_STATE__=({.*})</script>", Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(html);
            
            if (!matcher.find()) {
                return new HashMap<>();
            }
            
            String state = matcher.group(1).replace("undefined", "\"\"");
            
            if ("{}".equals(state)) {
                return new HashMap<>();
            }
            
            Map<String, Object> noteDict = transformJsonKeys(state);
            @SuppressWarnings("unchecked")
            Map<String, Object> note = (Map<String, Object>) noteDict.get("note");
            @SuppressWarnings("unchecked")
            Map<String, Object> noteDetailMap = (Map<String, Object>) note.get("note_detail_map");
            @SuppressWarnings("unchecked")
            Map<String, Object> noteDetail = (Map<String, Object>) noteDetailMap.get(noteId);
            @SuppressWarnings("unchecked")
            Map<String, Object> noteData = (Map<String, Object>) noteDetail.get("note");
            
            return noteData != null ? noteData : new HashMap<>();
        } catch (Exception e) {
            System.err.println("Failed to parse note from HTML: " + e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * Transform JSON keys from camelCase to snake_case
     */
    private Map<String, Object> transformJsonKeys(String jsonData) {
        try {
            Map<String, Object> dataDict = objectMapper.readValue(jsonData, 
                    new TypeReference<Map<String, Object>>() {});
            return transformKeysRecursive(dataDict);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> transformKeysRecursive(Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String newKey = camelToUnderscore(entry.getKey());
            Object value = entry.getValue();
            
            if (value == null) {
                result.put(newKey, value);
            } else if (value instanceof Map) {
                result.put(newKey, transformKeysRecursive((Map<String, Object>) value));
            } else if (value instanceof List) {
                List<Object> list = (List<Object>) value;
                List<Object> newList = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map) {
                        newList.add(transformKeysRecursive((Map<String, Object>) item));
                    } else {
                        newList.add(item);
                    }
                }
                result.put(newKey, newList);
            } else {
                result.put(newKey, value);
            }
        }
        
        return result;
    }
    
    /**
     * Convert camelCase to snake_case
     */
    private String camelToUnderscore(String key) {
        return key.replaceAll("(?<!^)(?=[A-Z])", "_").toLowerCase();
    }
    
    /**
     * Encode parameters for URL
     */
    private String encodeParams(Map<String, Object> params) {
        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (encoded.length() > 0) {
                encoded.append("&");
            }
            encoded.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return encoded.toString();
    }
    
    /**
     * Generate search ID (placeholder implementation)
     */
    private String getSearchId() {
        // This should be implemented based on the get_search_id function from help module
        return UUID.randomUUID().toString();
    }
}