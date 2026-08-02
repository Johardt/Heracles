package me.johardt.heracles.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class QuestCommands {
    private QuestCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("heracles")
            .executes(context -> status(context.getSource()))
            .then(Commands.literal("open").executes(context -> open(context.getSource())))
            .then(Commands.literal("dummy")
                .then(Commands.argument("value", StringArgumentType.string())
                    .executes(context -> dummy(context.getSource(), StringArgumentType.getString(context, "value")))))
            .then(Commands.literal("demo")
                .executes(context -> dummy(context.getSource(), "demo_welcome")))
            .then(Commands.literal("claim")
                .then(Commands.argument("quest", StringArgumentType.string())
                    .executes(context -> claim(context.getSource(), StringArgumentType.getString(context, "quest")))))
            .then(Commands.literal("submit")
                .then(Commands.argument("quest", StringArgumentType.string())
                    .then(Commands.argument("task", StringArgumentType.string())
                        .executes(context -> submit(context.getSource(), StringArgumentType.getString(context, "quest"), StringArgumentType.getString(context, "task"))))))
            .then(Commands.literal("reset")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> reset(context.getSource())))
            .then(Commands.literal("reload")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> reload(context.getSource())))
            .then(Commands.literal("validate")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> validate(context.getSource())))
        );
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Heracles core quests are active. Use /heracles open or press H."), false);
        return 1;
    }

    private static int open(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        QuestRuntime.get().sync(source.getPlayerOrException(), true);
        return 1;
    }

    private static int dummy(CommandSourceStack source, String value) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        QuestRuntime runtime = QuestRuntime.get();
        boolean changed = runtime.triggerDummy(player, value);
        String locked = changed ? null : runtime.lockedDummyReason(player, value);
        source.sendSuccess(() -> Component.literal(changed ? "Dummy quest task completed."
            : locked != null ? locked : "No dummy task matched '" + value + "'."), false);
        return changed ? 1 : 0;
    }

    private static int claim(CommandSourceStack source, String quest) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean claimed = QuestRuntime.get().claim(source.getPlayerOrException(), quest);
        source.sendSuccess(() -> Component.literal(claimed ? "Quest rewards claimed." : "Quest is missing, incomplete, or already claimed."), false);
        return claimed ? 1 : 0;
    }

    private static int submit(CommandSourceStack source, String quest, String task) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean submitted = QuestRuntime.get().submit(source.getPlayerOrException(), quest, task);
        source.sendSuccess(() -> Component.literal(submitted ? "Task submission accepted." : "Task could not be submitted."), false);
        return submitted ? 1 : 0;
    }

    private static int reset(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        QuestRuntime.get().reset(source.getPlayerOrException());
        source.sendSuccess(() -> Component.literal("Your Heracles quest progress was reset."), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        int count = QuestRuntime.get().reload();
        int issues = QuestRuntime.get().validationIssues().size();
        source.sendSuccess(() -> Component.literal("Reloaded " + count + " Heracles quests with " + issues + " validation issue(s)."), true);
        return count;
    }

    private static int validate(CommandSourceStack source) {
        var issues = QuestRuntime.get().validationIssues();
        if (issues.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No Heracles quest validation issues found."), false);
            return 1;
        }
        issues.forEach(issue -> source.sendFailure(Component.literal(
            issue.severity() + " " + issue.path() + ": " + issue.message())));
        return 0;
    }
}
