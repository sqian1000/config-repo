package com.xiaohongshu.client.field;

/**
 * Enum for search note types
 */
public enum SearchNoteType {
    ALL("all"),
    VIDEO("video"),
    IMAGE("image");
    
    private final String value;
    
    SearchNoteType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}