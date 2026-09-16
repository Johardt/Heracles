package me.johardt.heracles.client;

/** Pure parsing rules for the integer position fields in the authoring dock. */
public final class QuestPositionInput {
    private QuestPositionInput() {}

    public static Result parse(String text, int previousValue) {
        if (text == null || text.isBlank()) return new Result(false, previousValue, text == null ? "" : text);
        try {
            return new Result(true, Integer.parseInt(text.trim()), text);
        } catch (NumberFormatException ignored) {
            return new Result(false, previousValue, text);
        }
    }

    public record Result(boolean valid, int value, String text) {}
}
