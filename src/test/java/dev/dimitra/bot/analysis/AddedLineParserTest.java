package dev.dimitra.bot.analysis;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AddedLineParserTest {
    private final AddedLineParser parser = new AddedLineParser();

    @Test
    void parsesSimpleAddedLine() {
        Map<Integer, String> addedLines = parser.parse("""
                diff --git a/src/Foo.java b/src/Foo.java
                --- a/src/Foo.java
                +++ b/src/Foo.java
                @@ -1,0 +1,1 @@
                +class Foo {}
                """);

        assertEquals(Map.of(1, "class Foo {}"), addedLines);
    }

    @Test
    void parsesAddedLinesAcrossMultipleHunks() {
        Map<Integer, String> addedLines = parser.parse("""
                @@ -1,2 +10,3 @@
                 class Foo {
                +    void first() {}
                @@ -20,2 +30,3 @@
                     void existing() {}
                +    void second() {}
                """);

        assertEquals(Map.of(
                11, "    void first() {}",
                31, "    void second() {}"), addedLines);
    }

    @Test
    void ignoresDeletedLines() {
        Map<Integer, String> addedLines = parser.parse("""
                @@ -4,3 +4,3 @@
                 class Foo {
                -    void removed() {}
                +    void added() {}
                """);

        assertEquals(Map.of(5, "    void added() {}"), addedLines);
    }

    @Test
    void contextLinesIncreaseNewFileCounter() {
        Map<Integer, String> addedLines = parser.parse("""
                @@ -10,4 +20,4 @@
                 class Foo {
                     void existing() {}
                +    void addedAfterContext() {}
                """);

        assertEquals(Map.of(22, "    void addedAfterContext() {}"), addedLines);
    }

    @Test
    void ignoresNoNewlineMarker() {
        Map<Integer, String> addedLines = parser.parse("""
                @@ -1,0 +1,1 @@
                +class Foo {}
                \\ No newline at end of file
                """);

        assertEquals(Map.of(1, "class Foo {}"), addedLines);
    }
}
