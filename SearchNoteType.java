/**
 * Enum representing the types of notes that can be searched for
 */
public enum SearchNoteType {
    ALL("0"),
    VIDEO("1"),
    IMAGE("2");
    
    private final String value;
    
    SearchNoteType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}