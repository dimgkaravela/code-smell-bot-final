package dev.dimitra.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Matches GitHub /pulls/{number}/files JSON.
 * Using a record so analysis code can access PR diff metadata directly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangedFile(
        String filename,
        String status,
        Integer additions,
        Integer deletions,
        Integer changes,
        String patch,
        String sha,
        @JsonProperty("previous_filename") String previousFilename
) {}
