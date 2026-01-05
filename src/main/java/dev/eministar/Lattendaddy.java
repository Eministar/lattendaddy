package dev.eministar;

import dev.eministar.command.Command;
import dev.eministar.command.CommandManager;
import dev.eministar.config.Config;
import dev.eministar.modules.ModuleLoader;
import dev.eministar.modules.goodbye.GoodbyeListener;
import dev.eministar.modules.welcome.WelcomeListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class Lattendaddy {
    private static final Logger logger = LoggerFactory.getLogger(Lattendaddy.class);

    public static void main(String[] args) {
        logger.info("Starte Lattendaddy...");

        if (Config.needsSetup()) {
            logger.info("Setup erforderlich. Starte Wizard...");
            Config.runSetupWizard();
            return; // Exit after setup
        }

        String token = Config.getToken();
        if (token == null || token.isEmpty()) {
            logger.error("Kein gültiger Token gefunden. Bitte führe den Setup erneut aus.");
            return;
        }

        try {
            // FlagQuiz Persistenz laden
            dev.eministar.modules.flags.FlagQuizService.load();
            // DPQ Zustand laden (laufende Nummer etc.)

            JDABuilder builder = JDABuilder.createDefault(token);
            builder.enableIntents(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT,
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_VOICE_STATES,
                    GatewayIntent.GUILD_PRESENCES
            );
            // WICHTIG: Präsenzdaten und vollständiges Member-Chaunking aktivieren, sonst bleibt Online-Count 0
            builder.enableCache(CacheFlag.ONLINE_STATUS);
            builder.setMemberCachePolicy(MemberCachePolicy.ALL);
            builder.setChunkingFilter(ChunkingFilter.ALL);

            CommandManager manager = new CommandManager(Config.getPrefix());

            // Store commands that are also listeners to register them later
            java.util.List<Object> commandListeners = new java.util.ArrayList<>();

            // Load modules automatically
            Set<Class<? extends Command>> commandClasses = ModuleLoader.loadCommands();
            for (Class<? extends Command> clazz : commandClasses) {
                try {
                    Command cmd = clazz.getDeclaredConstructor().newInstance();
                    manager.register(cmd);
                    logger.info("Command geladen: {}", cmd.name());

                    // If the command is also a ListenerAdapter, store it for later registration
                    if (cmd instanceof net.dv8tion.jda.api.hooks.ListenerAdapter) {
                        commandListeners.add(cmd);
                        logger.info("Command '{}' ist auch ein Event Listener", cmd.name());
                    }
                } catch (Exception e) {
                    logger.error("Fehler beim Laden des Commands: {}", clazz.getSimpleName(), e);
                }
            }

            // Register core listeners (welcome/goodbye)
            WelcomeListener welcome = new WelcomeListener();
            GoodbyeListener goodbye = new GoodbyeListener();
            dev.eministar.modules.birthday.BirthdayListener birthday = new dev.eministar.modules.birthday.BirthdayListener();
            dev.eministar.modules.ticket.TicketListener ticket = new dev.eministar.modules.ticket.TicketListener();
            dev.eministar.modules.suggestion.SuggestionListener suggestion = new dev.eministar.modules.suggestion.SuggestionListener();
            dev.eministar.modules.tempvoice.TempVoiceModule tempVoice = new dev.eministar.modules.tempvoice.TempVoiceModule();

            dev.eministar.modules.counting.CountingListener counting = new dev.eministar.modules.counting.CountingListener();
            dev.eministar.modules.misc.PingReactionListener pingReaction = new dev.eministar.modules.misc.PingReactionListener();
            dev.eministar.modules.channelcounts.ChannelCountListener channelCounts = new dev.eministar.modules.channelcounts.ChannelCountListener();
            dev.eministar.modules.flags.FlagQuizListener flagQuiz = new dev.eministar.modules.flags.FlagQuizListener();

            // Giveaway Button Listener and Scheduler
            dev.eministar.modules.giveaway.GiveawayStore giveawayStore = new dev.eministar.modules.giveaway.GiveawayStore("./data/giveaways.json");
            dev.eministar.modules.giveaway.GiveawayButtonListener giveawayListener = new dev.eministar.modules.giveaway.GiveawayButtonListener(giveawayStore);
            dev.eministar.modules.giveaway.GiveawayScheduler giveawayScheduler = new dev.eministar.modules.giveaway.GiveawayScheduler(giveawayStore);

            // Poll Scheduler - use the PollStore from the already-created PollModule
            dev.eministar.modules.poll.PollStore pollStore = new dev.eministar.modules.poll.PollStore("./data/polls.json");
            dev.eministar.modules.poll.PollScheduler pollScheduler = new dev.eministar.modules.poll.PollScheduler(pollStore);

            JDA jda = builder.build().awaitReady();
            manager.registerToJda(jda);

            // WICHTIG: CommandManager als Event Listener registrieren!
            jda.addEventListener(manager);

            // Set JDA reference for TempVoice
            tempVoice.setJDA(jda);

            // Register all event listeners
            jda.addEventListener(welcome);
            jda.addEventListener(goodbye);
            jda.addEventListener(birthday);
            jda.addEventListener(ticket);
            jda.addEventListener(suggestion);
            jda.addEventListener(tempVoice);
            jda.addEventListener(counting);
            jda.addEventListener(pingReaction);
            jda.addEventListener(channelCounts);
            jda.addEventListener(flagQuiz);
            jda.addEventListener(giveawayListener);

            // Register all commands that are also listeners (like PollModule)
            for (Object listener : commandListeners) {
                jda.addEventListener(listener);
                logger.info("Command-Listener registriert: {}", listener.getClass().getSimpleName());
            }

            // Start schedulers
            giveawayScheduler.start(jda);
            pollScheduler.start(jda);

            // FlagQuiz Dashboard sicherstellen
            dev.eministar.modules.flags.FlagQuizService.bootstrapDashboards(jda);

            // Auto-start Hymn module if enabled
            if (Config.getHymnEnabled()) {
                String guildId = Config.getGuildId();
                if (!guildId.isEmpty()) {
                    try {
                        logger.info("Auto-started Hymn module");
                    } catch (Exception e) {
                        logger.error("Failed to auto-start Hymn module", e);
                    }
                }
            }

            logger.info("Lattendaddy erfolgreich gestartet mit {} Commands.", commandClasses.size());
        } catch (Exception e) {
            logger.error("Fehler beim Starten des Bots", e);
        }
    }
}