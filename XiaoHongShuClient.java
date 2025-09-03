import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import okhttp3.*;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Java equivalent of the Python XiaoHongShuClient
 * Handles API requests to Xiaohongshu (Little Red Book) with proper authentication and signing
 */
public class XiaoHongShuClient {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    
    private final String proxy;
    private final int timeout;
    private final Map<String, String> headers;
    private final String host = "https://edith.xiaohongshu.com";
    private final String domain = "https://www.xiaohongshu.com";
    private final String IP_ERROR_STR = "网络连接异常，请检查网络设置或重启试试";
    private final int IP_ERROR_CODE = 300012;
    private final String NOTE_ABNORMAL_STR = "笔记状态异常，请稍后查看";
    private final int NOTE_ABNORMAL_CODE = -510001;
    private final WebDriver playwrightPage;
    private final Map<String, String> cookieDict;
    
    public XiaoHongShuClient(
            int timeout,
            String proxy,
            Map<String, String> headers,
            WebDriver playwrightPage,
            Map<String, String> cookieDict
    ) {
        this.proxy = proxy;
        this.timeout = timeout;
        this.headers = new HashMap<>(headers);
        this.playwrightPage = playwrightPage;
        this.cookieDict = new HashMap<>(cookieDict);
    }
    
    /**
     * Request header parameter signing
     */
    private CompletableFuture<Map<String, String>> preHeaders(String url, String data) {
        return CompletableFuture.suppressAsync(() -> {
            try {
                // Execute JavaScript to get encrypted parameters
                JavascriptExecutor js = (JavascriptExecutor) playwrightPage;
                String script = String.format("window._webmsxyw('%s', '%s')", url, data != null ? data : "");
                Object result = js.executeScript(script);
                
                // Get local storage
                String localStorageScript = "return window.localStorage";
                Object localStorage = js.executeScript(localStorageScript);
                
                // Parse the result and generate signs
                Map<String, Object> encryptParams = parseJsonToMap(result.toString());
                Map<String, Object> localStorageMap = parseJsonToMap(localStorage.toString());
                
                String a1 = cookieDict.getOrDefault("a1", "");
                String b1 = (String) localStorageMap.getOrDefault("b1", "");
                String xS = (String) encryptParams.getOrDefault("X-s", "");
                String xT = String.valueOf(encryptParams.getOrDefault("X-t", ""));
                
                Map<String, String> signs = sign(a1, b1, xS, xT);
                
                Map<String, String> newHeaders = new HashMap<>(this.headers);
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
     * Wrapper for HTTP requests with retry logic
     */
    public CompletableFuture<Object> request(String method, String url, Map<String, Object> kwargs) {
        return CompletableFuture.suppressAsync(() -> {
            try {
                boolean returnResponse = (Boolean) kwargs.getOrDefault("return_response", false);
                
                Request.Builder requestBuilder = new Request.Builder().url(url);
                
                // Add headers
                Map<String, String> requestHeaders = (Map<String, String>) kwargs.get("headers");
                if (requestHeaders != null) {
                    for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                        requestBuilder.addHeader(entry.getKey(), entry.getValue());
                    }
                }
                
                // Set method and body
                RequestBody requestBody = null;
                if ("POST".equalsIgnoreCase(method)) {
                    String jsonData = (String) kwargs.get("data");
                    if (jsonData != null) {
                        requestBody = RequestBody.create(jsonData, MediaType.parse("application/json"));
                    }
                }
                
                Request request = requestBuilder.method(method, requestBody).build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.code() == 471 || response.code() == 461) {
                        String verifyType = response.header("Verifytype");
                        String verifyUuid = response.header("Verifyuuid");
                        String msg = String.format("出现验证码，请求失败，Verifytype: %s，Verifyuuid: %s, Response: %s", 
                                verifyType, verifyUuid, response);
                        System.err.println(msg);
                        throw new RuntimeException(msg);
                    }
                    
                    if (returnResponse) {
                        return response.body().string();
                    }
                    
                    String responseBody = response.body().string();
                    Map<String, Object> data = parseJsonToMap(responseBody);
                    
                    if ((Boolean) data.get("success")) {
                        return data.getOrDefault("data", data.get("success"));
                    } else if (Integer.valueOf(String.valueOf(data.get("code"))) == IP_ERROR_CODE) {
                        throw new IPBlockError(IP_ERROR_STR);
                    } else {
                        throw new DataFetchError((String) data.get("msg"));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Request failed", e);
            }
        });
    }
    
    /**
     * GET request with header signing
     */
    public CompletableFuture<Map<String, Object>> get(String uri, Map<String, Object> params) {
        return CompletableFuture.suppressAsync(() -> {
            try {
                String finalUri = uri;
                if (params != null && !params.isEmpty()) {
                    String queryString = params.entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8))
                            .collect(Collectors.joining("&"));
                    finalUri = uri + "?" + queryString;
                }
                
                Map<String, String> headers = preHeaders(finalUri, null).get();
                Map<String, Object> kwargs = new HashMap<>();
                kwargs.put("headers", headers);
                
                Object result = request("GET", host + finalUri, kwargs).get();
                return (Map<String, Object>) result;
            } catch (Exception e) {
                throw new RuntimeException("GET request failed", e);
            }
        });
    }
    
    /**
     * POST request with header signing
     */
    public CompletableFuture<Map<String, Object>> post(String uri, Map<String, Object> data, Map<String, Object> kwargs) {
        return CompletableFuture.suppressAsync(() -> {
            try {
                Map<String, String> headers = preHeaders(uri, objectMapper.writeValueAsString(data)).get();
                
                String jsonStr = objectMapper.writeValueAsString(data);
                
                Map<String, Object> requestKwargs = new HashMap<>(kwargs);
                requestKwargs.put("headers", headers);
                requestKwargs.put("data", jsonStr);
                
                Object result = request("POST", host + uri, requestKwargs).get();
                return (Map<String, Object>) result;
            } catch (Exception e) {
                throw new RuntimeException("POST request failed", e);
            }
        });
    }
    
    /**
     * Get note media content
     */
    public CompletableFuture<byte[]> getNoteMedia(String url) {
        return CompletableFuture.suppressAsync(() -> {
            try {
                Request request = new Request.Builder().url(url).build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        System.err.println("[XiaoHongShuClient.getNoteMedia] request " + url + " err, res:" + response.body().string());
                        return null;
                    }
                    return response.body().bytes();
                }
            } catch (Exception e) {
                System.err.println("[XiaoHongShuClient.getNoteMedia] " + e.getClass().getSimpleName() + " for " + url + " - " + e);
                return null;
            }
        });
    }
    
    /**
     * Check if login state is still valid
     */
    public CompletableFuture<Boolean> pong() {
        return CompletableFuture.suppressAsync(() -> {
            try {
                System.out.println("[XiaoHongShuClient.pong] Begin to pong xhs...");
                Map<String, Object> noteCard = getNoteByKeyword("小红书", null, 1, 20, SearchSortType.GENERAL, SearchNoteType.ALL).get();
                return noteCard.containsKey("items");
            } catch (Exception e) {
                System.err.println("[XiaoHongShuClient.pong] Ping xhs failed: " + e + ", and try to login again...");
                return false;
            }
        });
    }
    
    /**
     * Update cookies from browser context
     */
    public void updateCookies(Set<Cookie> cookies) {
        Map<String, String> cookieMap = new HashMap<>();
        StringBuilder cookieStr = new StringBuilder();
        
        for (Cookie cookie : cookies) {
            cookieMap.put(cookie.getName(), cookie.getValue());
            if (cookieStr.length() > 0) {
                cookieStr.append("; ");
            }
            cookieStr.append(cookie.getName()).append("=").append(cookie.getValue());
        }
        
        headers.put("Cookie", cookieStr.toString());
        cookieDict.clear();
        cookieDict.putAll(cookieMap);
    }
    
    /**
     * Search notes by keyword
     */
    public CompletableFuture<Map<String, Object>> getNoteByKeyword(
            String keyword,
            String searchId,
            int page,
            int pageSize,
            SearchSortType sort,
            SearchNoteType noteType
    ) {
        return CompletableFuture.suppressAsync(() -> {
            try {
                String uri = "/api/sns/web/v1/search/notes";
                Map<String, Object> data = new HashMap<>();
                data.put("keyword", keyword);
                data.put("page", page);
                data.put("page_size", pageSize);
                data.put("search_id", searchId);
                data.put("sort", sort.getValue());
                data.put("note_type", noteType.getValue());
                
                return post(uri, data, new HashMap<>()).get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to get note by keyword", e);
            }
        });
    }
    
    /**
     * Get note details by ID
     */
    public CompletableFuture<Map<String, Object>> getNoteById(
            String noteId,
            String xsecSource,
            String xsecToken
    ) {
        return CompletableFuture.suppressAsync(() -> {
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
                Map<String, Object> res = (Map<String, Object>) post(uri, data, new HashMap<>()).get();
                
                if (res != null && res.containsKey("items")) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) res.get("items");
                    if (!items.isEmpty()) {
                        Map<String, Object> firstItem = items.get(0);
                        return (Map<String, Object>) firstItem.get("note_card");
                    }
                }
                
                System.err.println("[XiaoHongShuClient.getNoteById] get note id:" + noteId + " empty and res:" + res);
                return new HashMap<>();
            } catch (Exception e) {
                throw new RuntimeException("Failed to get note by ID", e);
            }
        });
    }
    
    /**
     * Get note comments
     */
    public CompletableFuture<Map<String, Object>> getNoteComments(
            String noteId,
            String xsecToken,
            String cursor
    ) {
        return CompletableFuture.suppressAsync(() -> {
            try {
                String uri = "/api/sns/web/v2/comment/page";
                Map<String, Object> params = new HashMap<>();
                params.put("note_id", noteId);
                params.put("cursor", cursor != null ? cursor : "");
                params.put("top_comment_id", "");
                params.put("image_formats", "jpg,webp,avif");
                params.put("xsec_token", xsecToken);
                
                return get(uri, params).get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to get note comments", e);
            }
        });
    }
    
    /**
     * Get sub-comments for a specific parent comment
     */
    public CompletableFuture<Map<String, Object>> getNoteSubComments(
            String noteId,
            String rootCommentId,
            String xsecToken,
            int num,
            String cursor
    ) {
        return CompletableFuture.suppressAsync(() -> {
            try {
                String uri = "/api/sns/web/v2/comment/sub/page";
                Map<String, Object> params = new HashMap<>();
                params.put("note_id", noteId);
                params.put("root_comment_id", rootCommentId);
                params.put("num", num);
                params.put("cursor", cursor != null ? cursor : "");
                params.put("image_formats", "jpg,webp,avif");
                params.put("top_comment_id", "");
                params.put("xsec_token", xsecToken);
                
                return get(uri, params).get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to get note sub-comments", e);
            }
        });
    }
    
    /**
     * Get all comments for a note
     */
    public CompletableFuture<List<Map<String, Object>>> getNoteAllComments(
            String noteId,
            String xsecToken,
            float crawlInterval,
            CommentCallback callback,
            int maxCount
    ) {
        return CompletableFuture.suppressAsync(() -> {
            try {
                List<Map<String, Object>> result = new ArrayList<>();
                boolean commentsHasMore = true;
                String commentsCursor = "";
                
                while (commentsHasMore && result.size() < maxCount) {
                    Map<String, Object> commentsRes = getNoteComments(noteId, xsecToken, commentsCursor).get();
                    commentsHasMore = (Boolean) commentsRes.getOrDefault("has_more", false);
                    commentsCursor = (String) commentsRes.getOrDefault("cursor", "");
                    
                    if (!commentsRes.containsKey("comments")) {
                        System.out.println("[XiaoHongShuClient.getNoteAllComments] No 'comments' key found in response: " + commentsRes);
                        break;
                    }
                    
                    List<Map<String, Object>> comments = (List<Map<String, Object>>) commentsRes.get("comments");
                    if (result.size() + comments.size() > maxCount) {
                        comments = comments.subList(0, maxCount - result.size());
                    }
                    
                    if (callback != null) {
                        callback.onComment(noteId, comments);
                    }
                    
                    Thread.sleep((long) (crawlInterval * 1000));
                    result.addAll(comments);
                    
                    List<Map<String, Object>> subComments = getCommentsAllSubComments(
                            comments, xsecToken, crawlInterval, callback
                    ).get();
                    result.addAll(subComments);
                }
                
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Failed to get all comments", e);
            }
        });
    }
    
    /**
     * Get all sub-comments for a list of comments
     */
    public CompletableFuture<List<Map<String, Object>>> getCommentsAllSubComments(
            List<Map<String, Object>> comments,
            String xsecToken,
            float crawlInterval,
            CommentCallback callback
    ) {
        return CompletableFuture.suppressAsync(() -> {
            try {
                if (!Config.ENABLE_GET_SUB_COMMENTS) {
                    System.out.println("[XiaoHongShuClient.getCommentsAllSubComments] Crawling sub_comment mode is not enabled");
                    return new ArrayList<>();
                }
                
                List<Map<String, Object>> result = new ArrayList<>();
                
                for (Map<String, Object> comment : comments) {
                    String noteId = (String) comment.get("note_id");
                    List<Map<String, Object>> subComments = (List<Map<String, Object>>) comment.get("sub_comments");
                    
                    if (subComments != null && callback != null) {
                        callback.onComment(noteId, subComments);
                    }
                    
                    Boolean subCommentHasMore = (Boolean) comment.get("sub_comment_has_more");
                    if (subCommentHasMore == null || !subCommentHasMore) {
                        continue;
                    }
                    
                    String rootCommentId = (String) comment.get("id");
                    String subCommentCursor = (String) comment.get("sub_comment_cursor");
                    
                    while (subCommentHasMore) {
                        Map<String, Object> commentsRes = getNoteSubComments(
                                noteId, rootCommentId, xsecToken, 10, subCommentCursor
                        ).get();
                        
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
                        
                        List<Map<String, Object>> newComments = (List<Map<String, Object>>) commentsRes.get("comments");
                        if (callback != null) {
                            callback.onComment(noteId, newComments);
                        }
                        
                        Thread.sleep((long) (crawlInterval * 1000));
                        result.addAll(newComments);
                    }
                }
                
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Failed to get all sub-comments", e);
            }
        });
    }
    
    /**
     * Get creator info by parsing HTML
     */
    public CompletableFuture<Map<String, Object>> getCreatorInfo(String userId) {
        return CompletableFuture.suppressAsync(() -> {
            try {
                String uri = "/user/profile/" + userId;
                String htmlContent = (String) request("GET", domain + uri, Map.of("return_response", true, "headers", headers)).get();
                
                Pattern pattern = Pattern.compile("<script>window\\.__INITIAL_STATE__=(.+)</script>");
                Matcher matcher = pattern.matcher(htmlContent);
                
                if (!matcher.find()) {
                    return new HashMap<>();
                }
                
                String stateJson = matcher.group(1).replace(":undefined", ":null");
                Map<String, Object> info = parseJsonToMap(stateJson);
                
                if (info == null) {
                    return new HashMap<>();
                }
                
                Map<String, Object> user = (Map<String, Object>) info.get("user");
                return (Map<String, Object>) user.get("userPageData");
            } catch (Exception e) {
                throw new RuntimeException("Failed to get creator info", e);
            }
        });
    }
    
    /**
     * Get notes by creator
     */
    public CompletableFuture<Map<String, Object>> getNotesByCreator(
            String creator,
            String cursor,
            int pageSize
    ) {
        return CompletableFuture.suppressAsync(() -> {
            try {
                String uri = "/api/sns/web/v1/user_posted";
                Map<String, Object> data = new HashMap<>();
                data.put("user_id", creator);
                data.put("cursor", cursor);
                data.put("num", pageSize);
                data.put("image_formats", "jpg,webp,avif");
                
                return get(uri, data).get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to get notes by creator", e);
            }
        });
    }
    
    /**
     * Get all notes by creator
     */
    public CompletableFuture<List<Map<String, Object>>> getAllNotesByCreator(
            String userId,
            float crawlInterval,
            NotesCallback callback
    ) {
        return CompletableFuture.suppressAsync(() -> {
            try {
                List<Map<String, Object>> result = new ArrayList<>();
                boolean notesHasMore = true;
                String notesCursor = "";
                
                while (notesHasMore && result.size() < Config.CRAWLER_MAX_NOTES_COUNT) {
                    Map<String, Object> notesRes = getNotesByCreator(userId, notesCursor).get();
                    
                    if (notesRes == null) {
                        System.err.println("[XiaoHongShuClient.getAllNotesByCreator] The current creator may have been banned by xhs, so they cannot access the data.");
                        break;
                    }
                    
                    notesHasMore = (Boolean) notesRes.getOrDefault("has_more", false);
                    notesCursor = (String) notesRes.getOrDefault("cursor", "");
                    
                    if (!notesRes.containsKey("notes")) {
                        System.out.println("[XiaoHongShuClient.getAllNotesByCreator] No 'notes' key found in response: " + notesRes);
                        break;
                    }
                    
                    List<Map<String, Object>> notes = (List<Map<String, Object>>) notesRes.get("notes");
                    System.out.println("[XiaoHongShuClient.getAllNotesByCreator] got user_id:" + userId + " notes len : " + notes.size());
                    
                    int remaining = Config.CRAWLER_MAX_NOTES_COUNT - result.size();
                    if (remaining <= 0) {
                        break;
                    }
                    
                    List<Map<String, Object>> notesToAdd = notes.subList(0, Math.min(remaining, notes.size()));
                    if (callback != null) {
                        callback.onNotes(notesToAdd);
                    }
                    
                    result.addAll(notesToAdd);
                    Thread.sleep((long) (crawlInterval * 1000));
                }
                
                System.out.println("[XiaoHongShuClient.getAllNotesByCreator] Finished getting notes for user " + userId + ", total: " + result.size());
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Failed to get all notes by creator", e);
            }
        });
    }
    
    /**
     * Get note short URL
     */
    public CompletableFuture<Map<String, Object>> getNoteShortUrl(String noteId) {
        return CompletableFuture.suppressAsync(() -> {
            try {
                String uri = "/api/sns/web/short_url";
                Map<String, Object> data = new HashMap<>();
                data.put("original_url", domain + "/discovery/item/" + noteId);
                
                return post(uri, data, Map.of("return_response", true)).get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to get note short URL", e);
            }
        });
    }
    
    /**
     * Get note by ID from HTML with retry logic
     */
    public CompletableFuture<Map<String, Object>> getNoteByIdFromHtml(
            String noteId,
            String xsecSource,
            String xsecToken,
            boolean enableCookie
    ) {
        return CompletableFuture.suppressAsync(() -> {
            try {
                String url = "https://www.xiaohongshu.com/explore/" + noteId + 
                        "?xsec_token=" + xsecToken + "&xsec_source=" + xsecSource;
                
                Map<String, String> copyHeaders = new HashMap<>(headers);
                if (!enableCookie) {
                    copyHeaders.remove("Cookie");
                }
                
                String html = (String) request("GET", url, Map.of("return_response", true, "headers", copyHeaders)).get();
                
                try {
                    return getNoteDict(html, noteId);
                } catch (Exception e) {
                    return null;
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to get note by ID from HTML", e);
            }
        });
    }
    
    // Helper methods
    
    private Map<String, Object> parseJsonToMap(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }
    
    private Map<String, String> sign(String a1, String b1, String xS, String xT) {
        // This would need to be implemented based on the actual signing algorithm
        // For now, returning placeholder values
        Map<String, String> signs = new HashMap<>();
        signs.put("x-s", xS);
        signs.put("x-t", xT);
        signs.put("x-s-common", "placeholder");
        signs.put("x-b3-traceid", "placeholder");
        return signs;
    }
    
    private Map<String, Object> getNoteDict(String html, String noteId) {
        Pattern pattern = Pattern.compile("window\\.__INITIAL_STATE__=({.*})</script>");
        Matcher matcher = pattern.matcher(html);
        
        if (matcher.find()) {
            String state = matcher.group(1).replace("undefined", "\"\"");
            
            if (!state.equals("{}")) {
                Map<String, Object> noteDict = transformJsonKeys(state);
                Map<String, Object> note = (Map<String, Object>) noteDict.get("note");
                Map<String, Object> noteDetailMap = (Map<String, Object>) note.get("note_detail_map");
                return (Map<String, Object>) noteDetailMap.get(noteId);
            }
        }
        return new HashMap<>();
    }
    
    private Map<String, Object> transformJsonKeys(String jsonData) {
        try {
            Map<String, Object> dataDict = parseJsonToMap(jsonData);
            Map<String, Object> dictNew = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : dataDict.entrySet()) {
                String newKey = camelToUnderscore(entry.getKey());
                Object value = entry.getValue();
                
                if (value == null) {
                    dictNew.put(newKey, value);
                } else if (value instanceof Map) {
                    dictNew.put(newKey, transformJsonKeys(objectMapper.writeValueAsString(value)));
                } else if (value instanceof List) {
                    List<Object> newList = new ArrayList<>();
                    for (Object item : (List<?>) value) {
                        if (item != null && item instanceof Map) {
                            newList.add(transformJsonKeys(objectMapper.writeValueAsString(item)));
                        } else {
                            newList.add(item);
                        }
                    }
                    dictNew.put(newKey, newList);
                } else {
                    dictNew.put(newKey, value);
                }
            }
            
            return dictNew;
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform JSON keys", e);
        }
    }
    
    private String camelToUnderscore(String key) {
        return key.replaceAll("(?<!^)(?=[A-Z])", "_").toLowerCase();
    }
    
    // Callback interfaces
    public interface CommentCallback {
        void onComment(String noteId, List<Map<String, Object>> comments);
    }
    
    public interface NotesCallback {
        void onNotes(List<Map<String, Object>> notes);
    }
}