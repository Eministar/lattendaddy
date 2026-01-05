package dev.eministar.command;

import dev.eministar.config.Config;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class CommandManager extends ListenerAdapter {
    private final Map<String, Command> commands = new HashMap<>();
    private final String prefix;
    private static final Logger logger = LoggerFactory.getLogger(CommandManager.class);

    public CommandManager(String prefix) {
        this.prefix = prefix;
    }

    public void register(Command cmd) {
        commands.put(cmd.name().toLowerCase(), cmd);
        logger.debug("Command registriert: {}", cmd.name());
    }

    public void registerToJda(JDA jda) {
        logger.info("Registriere {} Commands...", commands.size());

        var slashCommands = commands.values().stream()
                .map(cmd -> {
                    CommandData data = cmd.getSlashCommandData();
                    logger.debug("Command '{}' hat SlashCommandData: {}", cmd.name(), data != null);
                    return data != null ? data : Commands.slash(cmd.name(), cmd.description());
                })
                .toList();

        String guildId = Config.getGuildId();
        if (!guildId.isEmpty()) {
            // Register for specific guild
            Guild guild = jda.getGuildById(guildId);
            if (guild != null) {
                guild.updateCommands().addCommands(slashCommands).queue();
                logger.info("Slash-Commands für Guild {} registriert.", guildId);
            } else {
                logger.warn("Guild mit ID {} nicht gefunden. Registriere global.", guildId);
                jda.updateCommands().addCommands(slashCommands).queue();
            }
        } else {
            // Register globally
            jda.updateCommands().addCommands(slashCommands).queue();
            logger.info("Slash-Commands global registriert.");
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        String raw = event.getMessage().getContentRaw();
        if (!raw.startsWith(prefix)) return;
        String without = raw.substring(prefix.length()).trim();
        if (without.isEmpty()) return;
        String[] parts = without.split(" ");
        String cmdName = parts[0].toLowerCase();
        Command cmd = commands.get(cmdName);
        if (cmd == null) return;
        String[] args = new String[Math.max(0, parts.length-1)];
        System.arraycopy(parts, 1, args, 0, args.length);
        cmd.execute(event, args);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        logger.info("Slash Command empfangen: {} von User {}", event.getName(), event.getUser().getName());
        Command cmd = commands.get(event.getName().toLowerCase());
        if (cmd != null) {
            try {
                logger.debug("Führe Command aus: {}", event.getName());
                cmd.executeSlash(event.getInteraction());
            } catch (Exception e) {
                logger.error("Fehler beim Ausführen von Command '{}': {}", event.getName(), e.getMessage(), e);
                if (!event.isAcknowledged()) {
                    event.reply("❌ Ein Fehler ist aufgetreten: " + e.getMessage()).setEphemeral(true).queue();
                }
            }
        } else {
            logger.warn("Unbekannter Command: {} - Verfügbare Commands: {}", event.getName(), commands.keySet());
            event.reply("❌ Unbekannter Command!").setEphemeral(true).queue();
        }
    }
}
