package dev.dimitra.bot.model;

public record ContextFile(
        String path,
        String relation,
        String content
) {}
