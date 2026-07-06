package dev.dimitra.bot;

import dev.dimitra.bot.github.GitHubPrClient;
import dev.dimitra.bot.llm.LlmClient;
import dev.dimitra.bot.llm.LlmRouter;

public class Main {
    public static void main(String[] args) throws Exception {
        try {
            run();
        } catch (IllegalArgumentException e) {
            fail(e.getMessage());
        }
    }

    private static void run() throws Exception {
        BotConfig config = BotConfig.fromEnv();
        GitHubPrClient github = new GitHubPrClient(config.token());
        LlmClient llm = LlmRouter.fromEnv();
        new CodeSmellBotFacade(github, llm).run(config);
    }

    private static void fail(String msg) {
        System.err.println("[ERROR] " + msg);
        System.exit(1);
    }
}
