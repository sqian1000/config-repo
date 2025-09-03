package com.xiaohongshu.client.field;

/**
 * Enum for search sort types
 */
public enum SearchSortType {
    GENERAL("general"),
    POPULARITY_DESC("popularity_desc"),
    TIME_DESC("time_desc");
    
    private final String value;
    
    SearchSortType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}