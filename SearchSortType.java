/**
 * Enum representing the sorting options for search results
 */
public enum SearchSortType {
    GENERAL("general"),
    POPULAR("popular"),
    LATEST("latest");
    
    private final String value;
    
    SearchSortType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}