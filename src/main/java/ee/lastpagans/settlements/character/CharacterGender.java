package ee.lastpagans.settlements.character;

import java.util.Locale;

public enum CharacterGender {
    MALE("Male"),
    FEMALE("Female"),
    OTHER("Other");

    private final String displayName;

    CharacterGender(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() { return displayName; }

    public static CharacterGender parse(String value) {
        if (value == null) return null;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return null; }
    }
}
