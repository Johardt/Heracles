package me.johardt.theseus.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Detects duplicate object keys before Gson can silently replace the first value. */
public final class JsonDuplicateKeyDetector {
    private JsonDuplicateKeyDetector() {}

    public static List<String> findDuplicates(String json) {
        Parser parser = new Parser(json);
        parser.value("$");
        parser.space();
        if (!parser.end()) throw new IllegalArgumentException("Unexpected content at character " + parser.index);
        return List.copyOf(parser.duplicates);
    }

    private static final class Parser {
        private final String input;
        private final List<String> duplicates = new ArrayList<>();
        private int index;
        private Parser(String input) { this.input = input; }
        private boolean end() { return index == input.length(); }
        private void space() { while (!end() && Character.isWhitespace(input.charAt(index))) index++; }
        private void value(String path) {
            space();
            if (end()) throw malformed();
            switch (input.charAt(index)) {
                case '{' -> object(path);
                case '[' -> array(path);
                case '"' -> string();
                default -> primitive();
            }
        }
        private void object(String path) {
            index++; space(); Set<String> keys = new LinkedHashSet<>();
            if (consume('}')) return;
            while (true) {
                space(); if (end() || input.charAt(index) != '"') throw malformed();
                String key = string();
                if (!keys.add(key)) duplicates.add(path + "." + key);
                space(); expect(':'); value(path + "." + key); space();
                if (consume('}')) return;
                expect(',');
            }
        }
        private void array(String path) {
            index++; space(); int element = 0;
            if (consume(']')) return;
            while (true) {
                value(path + "[" + element++ + "]"); space();
                if (consume(']')) return;
                expect(',');
            }
        }
        private String string() {
            expect('"'); StringBuilder value = new StringBuilder();
            while (!end()) {
                char character = input.charAt(index++);
                if (character == '"') return value.toString();
                if (character == '\\') {
                    if (end()) throw malformed();
                    char escaped = input.charAt(index++);
                    if (escaped == 'u') { for (int i = 0; i < 4; i++) { if (end() || Character.digit(input.charAt(index++), 16) < 0) throw malformed(); } value.append('?'); }
                    else value.append(escaped);
                } else value.append(character);
            }
            throw malformed();
        }
        private void primitive() {
            int start = index;
            while (!end() && " \t\r\n,]}".indexOf(input.charAt(index)) < 0) index++;
            if (start == index) throw malformed();
        }
        private boolean consume(char expected) { if (!end() && input.charAt(index) == expected) { index++; return true; } return false; }
        private void expect(char expected) { space(); if (!consume(expected)) throw malformed(); }
        private IllegalArgumentException malformed() { return new IllegalArgumentException("Malformed JSON near character " + index); }
    }
}
