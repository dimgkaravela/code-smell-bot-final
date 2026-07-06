package dev.dimitra.bot.github;

public record PullRequestContext(RepoRef head, RepoRef base, String fullDiff) {
}
