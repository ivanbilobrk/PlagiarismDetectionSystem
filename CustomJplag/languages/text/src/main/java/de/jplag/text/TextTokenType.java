package de.jplag.text;

import de.jplag.TokenType;

public record TextTokenType(String description) implements TokenType {
    public TextTokenType(String description) {
        this.description = description.toLowerCase();
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    public static TokenType getTokenType(String token) {
        return new TextTokenType(token);
    }
}
