package de.jplag;

/**
 * Shared token types that occur for any language.
 */
public enum SharedTokenType implements TokenType {
    /**
     * Marks the end of the file. Every parsed file must have this token type as its last element.
     */
    FILE_END("EOF");

    private final String description;

    SharedTokenType(String description) {
        this.description = description;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Boolean isExcludedFromMatching() {
        return true;
    }

    public static TokenType getTokenType(String token) {
        return FILE_END;
    }
}
