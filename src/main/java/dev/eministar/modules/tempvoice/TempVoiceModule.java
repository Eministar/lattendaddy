package dev.eministar.modules.tempvoice;

import dev.eministar.config.Config;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

public class TempVoiceModule extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(TempVoiceModule.class);

    private static final String BANNER_URL = "https://yukicraft.net/bot/assets/tempvoice.gif";
    private static final int MAX_CHANNEL_NAME_LENGTH = 100;

    private final Map<String, String> ownerToChannel = new ConcurrentHashMap<>();
    private final Map<String, TempVoiceSettings> channelSettings = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> deleteTasks = new ConcurrentHashMap<>();
    private final Map<String, String> channelToGuild = new ConcurrentHashMap<>(); // channelId -> guildId
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private net.dv8tion.jda.api.JDA jda;

    private boolean enabled;
    private String sourceChannelId;
    private String fallbackCategoryId;
    private int deleteGraceSeconds;
    private int defaultMaxMembers;
    private int defaultBitrateKbps;

    public TempVoiceModule() {
        loadConfig();
    }

    public void setJDA(net.dv8tion.jda.api.JDA jda) {
        this.jda = jda;
    }

    private void loadConfig() {
        enabled = Config.getTempVoiceEnabled();
        sourceChannelId = Config.getTempVoiceSourceChannelId();
        fallbackCategoryId = Config.getTempVoiceFallbackCategoryId();
        deleteGraceSeconds = Config.getTempVoiceDeleteGraceSeconds();
        defaultMaxMembers = Config.getTempVoiceDefaultMaxMembers();
        defaultBitrateKbps = Config.getTempVoiceDefaultBitrateKbps();

        logger.info("TempVoice Module - Enabled: {}, Source: {}", enabled, sourceChannelId);
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        if (!enabled) return;

        AudioChannel joined = event.getChannelJoined();
        AudioChannel left = event.getChannelLeft();
        Member member = event.getMember();

        if (joined != null) {
            handleVoiceJoin(member, joined);
        }

        if (left != null) {
            handleVoiceLeave(left);
        }
    }

    private void handleVoiceJoin(Member member, AudioChannel channel) {
        if (member.getUser().isBot()) return;

        if (channel.getId().equals(sourceChannelId)) {
            String ownerId = member.getId();

            if (ownerToChannel.containsKey(ownerId)) {
                String existingChannelId = ownerToChannel.get(ownerId);
                VoiceChannel existingChannel = channel.getGuild().getVoiceChannelById(existingChannelId);

                if (existingChannel != null) {
                    channel.getGuild().moveVoiceMember(member, existingChannel).queue(
                        success -> logger.debug("Moved {} back to existing temp channel", member.getEffectiveName()),
                        error -> logger.error("Failed to move member to existing channel", error)
                    );
                    return;
                } else {
                    ownerToChannel.remove(ownerId);
                    channelSettings.remove(existingChannelId);
                }
            }

            createTempChannel(member, channel.getGuild());
        } else {
            cancelDeleteTask(channel.getId());
        }
    }

    private void handleVoiceLeave(AudioChannel channel) {
        if (isOwnedTempChannel(channel.getId())) {
            if (channel.getMembers().isEmpty()) {
                scheduleChannelDeletion(channel.getId());
            }
        }
    }

    private void createTempChannel(Member owner, Guild guild) {
        String channelName = sanitizeChannelName("🗣┃ " + owner.getEffectiveName());

        Category category = null;
        VoiceChannel sourceChannel = guild.getVoiceChannelById(sourceChannelId);
        if (sourceChannel != null && sourceChannel.getParentCategory() != null) {
            category = sourceChannel.getParentCategory();
        } else if (fallbackCategoryId != null && !fallbackCategoryId.isEmpty()) {
            category = guild.getCategoryById(fallbackCategoryId);
        }

        var action = guild.createVoiceChannel(channelName);
        if (category != null) {
            action = action.setParent(category);
        }

        action.queue(tempChannel -> {
            // Configure channel settings
            tempChannel.getManager()
                .setUserLimit(defaultMaxMembers)
                .setBitrate(defaultBitrateKbps * 1000)
                .queue();

            ownerToChannel.put(owner.getId(), tempChannel.getId());
            channelToGuild.put(tempChannel.getId(), guild.getId());
            TempVoiceSettings settings = new TempVoiceSettings(defaultMaxMembers, defaultBitrateKbps);
            channelSettings.put(tempChannel.getId(), settings);

            // Store JDA reference if not yet set
            if (this.jda == null) {
                this.jda = guild.getJDA();
            }

            guild.moveVoiceMember(owner, tempChannel).queue(
                success -> {
                    logger.info("Created temp channel '{}' for {}", channelName, owner.getEffectiveName());
                    sendControlPanel(tempChannel, owner, settings);
                },
                error -> logger.error("Failed to move member to new temp channel", error)
            );
        }, error -> logger.error("Failed to create temp voice channel", error));
    }

    private void sendControlPanel(VoiceChannel channel, Member owner, TempVoiceSettings settings) {
        channel.getPermissionContainer().getManager()
            .putPermissionOverride(owner,
                Permission.VIEW_CHANNEL.getRawValue() | Permission.VOICE_CONNECT.getRawValue(), 0)
            .queue();

        EmbedBuilder embed = createPanelEmbed(owner, settings);

        channel.sendMessageEmbeds(embed.build())
            .setComponents(createPanelButtons(settings))
            .queue(message -> {
                settings.setPanelMessageId(message.getId());
                logger.debug("Sent control panel to temp channel {}", channel.getId());
            }, error -> logger.error("Failed to send control panel", error));
    }

    private EmbedBuilder createPanelEmbed(Member owner, TempVoiceSettings settings) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmojiUtil.wrap("🎤") + " Temp-Voice Control Panel");
        embed.setImage(BANNER_URL);
        embed.setColor(new Color(0x5865F2));

        StringBuilder desc = new StringBuilder();
        desc.append("**Willkommen in deinem persönlichen Voice-Channel!**\n\n");
        desc.append(EmojiUtil.wrap("👑")).append(" **Besitzer:** ").append(owner.getAsMention()).append("\n\n");
        desc.append(EmojiUtil.wrap("⚙️")).append(" **Aktuelle Einstellungen:**\n");
        desc.append("• **Modus:** ").append(settings.isPrivateMode() ? "🔒 Privat" : "🌍 Öffentlich").append("\n");
        desc.append("• **Max. Mitglieder:** ").append(settings.getMaxMembers()).append("\n");
        desc.append("• **Bitrate:** ").append(settings.getBitrateKbps()).append(" kbps\n");

        if (!settings.getDescription().isEmpty()) {
            desc.append("• **Beschreibung:** ").append(settings.getDescription()).append("\n");
        }

        desc.append("\n").append(EmojiUtil.wrap("💡")).append(" *Nutze die Buttons unten, um deinen Channel anzupassen!*");

        embed.setDescription(desc.toString());
        embed.setFooter("Dieser Channel wird automatisch gelöscht, wenn niemand mehr drin ist", owner.getUser().getAvatarUrl());
        embed.setTimestamp(Instant.now());

        return embed;
    }

    private ActionRow[] createPanelButtons(TempVoiceSettings settings) {
        Button lockButton = settings.isPrivateMode()
            ? Button.success("tv_unlock", "🔓 Öffentlich machen")
            : Button.danger("tv_lock", "🔒 Privat machen");

        return new ActionRow[]{
            ActionRow.of(
                lockButton,
                Button.secondary("tv_desc", "📝 Beschreibung")
            ),
            ActionRow.of(
                Button.primary("tv_members_down", "👥 Max −"),
                Button.secondary("tv_members_info", "Max: " + settings.getMaxMembers()).asDisabled(),
                Button.primary("tv_members_up", "👥 Max +")
            ),
            ActionRow.of(
                Button.primary("tv_bitrate_down", "📶 Bitrate −"),
                Button.secondary("tv_bitrate_info", settings.getBitrateKbps() + " kbps").asDisabled(),
                Button.primary("tv_bitrate_up", "📶 Bitrate +")
            )
        };
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!enabled) return;
        if (!event.getComponentId().startsWith("tv_")) return;

        String channelId = event.getChannel().getId();
        if (!isOwnedTempChannel(channelId)) return;

        String ownerId = getChannelOwner(channelId);
        if (ownerId == null || !event.getUser().getId().equals(ownerId)) {
            event.reply(EmojiUtil.wrap("❌") + " Nur der Besitzer dieses Channels kann diese Einstellungen ändern!")
                .setEphemeral(true).queue();
            return;
        }

        TempVoiceSettings settings = channelSettings.get(channelId);
        if (settings == null) return;

        Guild guild = event.getGuild();
        if (guild == null) return;

        VoiceChannel voiceChannel = guild.getVoiceChannelById(channelId);
        if (voiceChannel == null) return;

        String action = event.getComponentId();

        switch (action) {
            case "tv_lock" -> handleLockChannel(event, voiceChannel, settings, true);
            case "tv_unlock" -> handleLockChannel(event, voiceChannel, settings, false);
            case "tv_members_up" -> handleMaxMembersChange(event, voiceChannel, settings, 1);
            case "tv_members_down" -> handleMaxMembersChange(event, voiceChannel, settings, -1);
            case "tv_bitrate_up" -> handleBitrateChange(event, voiceChannel, settings, 16);
            case "tv_bitrate_down" -> handleBitrateChange(event, voiceChannel, settings, -16);
            case "tv_desc" -> handleDescriptionModal(event);
        }
    }

    private void handleLockChannel(ButtonInteractionEvent event, VoiceChannel channel, TempVoiceSettings settings, boolean lock) {
        settings.setPrivateMode(lock);

        String ownerId = getChannelOwner(channel.getId());
        Guild guild = event.getGuild();
        if (guild == null) return;

        Member owner = guild.getMemberById(ownerId);

        var permManager = channel.getPermissionContainer().getManager();

        if (lock) {
            permManager = permManager.putPermissionOverride(
                guild.getPublicRole(),
                0,
                Permission.VIEW_CHANNEL.getRawValue() | Permission.VOICE_CONNECT.getRawValue()
            );
            if (owner != null) {
                permManager = permManager.putPermissionOverride(
                    owner,
                    Permission.VIEW_CHANNEL.getRawValue() | Permission.VOICE_CONNECT.getRawValue(),
                    0
                );
            }
        } else {
            permManager = permManager.removePermissionOverride(guild.getPublicRole());
        }

        permManager.queue(
            success -> {
                event.reply(EmojiUtil.wrap("✅") + " Channel ist jetzt " + (lock ? "🔒 **privat**" : "🌍 **öffentlich**") + "!")
                    .setEphemeral(true).queue();
                updatePanelMessage(event.getChannel().asTextChannel(), owner, settings);
            },
            error -> event.reply(EmojiUtil.wrap("❌") + " Fehler beim Ändern der Berechtigungen!")
                .setEphemeral(true).queue()
        );
    }

    private void handleMaxMembersChange(ButtonInteractionEvent event, VoiceChannel channel, TempVoiceSettings settings, int delta) {
        int newMax = settings.getMaxMembers() + delta;
        settings.setMaxMembers(newMax);

        channel.getManager().setUserLimit(settings.getMaxMembers()).queue(
            success -> {
                event.reply(EmojiUtil.wrap("✅") + " Max. Mitglieder auf **" + settings.getMaxMembers() + "** gesetzt!")
                    .setEphemeral(true).queue();
                Guild guild = event.getGuild();
                if (guild != null) {
                    updatePanelMessage(event.getChannel().asTextChannel(),
                        guild.getMemberById(getChannelOwner(channel.getId())), settings);
                }
            },
            error -> event.reply(EmojiUtil.wrap("❌") + " Fehler beim Ändern der Mitgliederanzahl!")
                .setEphemeral(true).queue()
        );
    }

    private void handleBitrateChange(ButtonInteractionEvent event, VoiceChannel channel, TempVoiceSettings settings, int delta) {
        Guild guild = event.getGuild();
        if (guild == null) return;

        int newBitrate = settings.getBitrateKbps() + delta;
        int maxBitrate = getMaxBitrate(guild);

        if (newBitrate > maxBitrate) {
            event.reply(EmojiUtil.wrap("❌") + " Maximale Bitrate für diesen Server: **" + maxBitrate + " kbps**")
                .setEphemeral(true).queue();
            return;
        }

        settings.setBitrateKbps(newBitrate);

        channel.getManager().setBitrate(settings.getBitrateKbps() * 1000).queue(
            success -> {
                event.reply(EmojiUtil.wrap("✅") + " Bitrate auf **" + settings.getBitrateKbps() + " kbps** gesetzt!")
                    .setEphemeral(true).queue();
                updatePanelMessage(event.getChannel().asTextChannel(),
                    guild.getMemberById(getChannelOwner(channel.getId())), settings);
            },
            error -> event.reply(EmojiUtil.wrap("❌") + " Fehler beim Ändern der Bitrate!")
                .setEphemeral(true).queue()
        );
    }

    private void handleDescriptionModal(ButtonInteractionEvent event) {
        TextInput descInput = TextInput.create("tv_desc_input", "Channel-Beschreibung", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Gib eine Beschreibung für deinen Channel ein...")
            .setRequired(false)
            .setMaxLength(200)
            .build();

        Modal modal = Modal.create("tv_desc_modal", "📝 Beschreibung ändern")
            .addActionRow(descInput)
            .build();

        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (!enabled) return;
        if (!event.getModalId().equals("tv_desc_modal")) return;

        String channelId = event.getChannel().getId();
        if (!isOwnedTempChannel(channelId)) return;

        String ownerId = getChannelOwner(channelId);
        if (ownerId == null || !event.getUser().getId().equals(ownerId)) {
            event.reply(EmojiUtil.wrap("❌") + " Nur der Besitzer kann die Beschreibung ändern!")
                .setEphemeral(true).queue();
            return;
        }

        TempVoiceSettings settings = channelSettings.get(channelId);
        if (settings == null) return;

        var descValue = event.getValue("tv_desc_input");
        String description = descValue != null ? descValue.getAsString() : "";
        settings.setDescription(description);

        Guild guild = event.getGuild();
        if (guild != null) {
            VoiceChannel voiceChannel = guild.getVoiceChannelById(channelId);
            if (voiceChannel != null) {
                event.reply(EmojiUtil.wrap("✅") + " Beschreibung aktualisiert!")
                    .setEphemeral(true).queue();
                updatePanelMessage(event.getChannel().asTextChannel(),
                    guild.getMemberById(ownerId), settings);
            }
        }
    }

    private void updatePanelMessage(TextChannel textChannel, Member owner, TempVoiceSettings settings) {
        if (settings.getPanelMessageId() == null || owner == null) return;

        textChannel.retrieveMessageById(settings.getPanelMessageId()).queue(
            message -> message.editMessageEmbeds(createPanelEmbed(owner, settings).build())
                .setComponents(createPanelButtons(settings))
                .queue(),
            error -> logger.warn("Failed to update panel message")
        );
    }

    private void scheduleChannelDeletion(String channelId) {
        cancelDeleteTask(channelId);

        ScheduledFuture<?> task = scheduler.schedule(() -> deleteTempChannel(channelId), deleteGraceSeconds, TimeUnit.SECONDS);

        deleteTasks.put(channelId, task);
        logger.debug("Scheduled deletion for channel {} in {} seconds", channelId, deleteGraceSeconds);
    }

    private void cancelDeleteTask(String channelId) {
        ScheduledFuture<?> task = deleteTasks.remove(channelId);
        if (task != null && !task.isDone()) {
            task.cancel(false);
            logger.debug("Cancelled deletion task for channel {}", channelId);
        }
    }

    private void deleteTempChannel(String channelId) {
        // First remove from tracking
        String ownerId = getChannelOwner(channelId);
        String guildId = channelToGuild.remove(channelId);

        if (ownerId != null) {
            ownerToChannel.remove(ownerId);
        }

        channelSettings.remove(channelId);
        deleteTasks.remove(channelId);

        // Actually delete the Discord voice channel
        if (jda != null && guildId != null) {
            Guild guild = jda.getGuildById(guildId);
            if (guild != null) {
                VoiceChannel channel = guild.getVoiceChannelById(channelId);
                if (channel != null) {
                    channel.delete().queue(
                        success -> logger.info("Deleted temp channel {}", channelId),
                        error -> logger.warn("Failed to delete temp channel {}: {}", channelId, error.getMessage())
                    );
                    return;
                }
            }
        }

        logger.info("Cleaned up tracking for temp channel {} (channel may already be deleted)", channelId);
    }

    private boolean isOwnedTempChannel(String channelId) {
        return channelSettings.containsKey(channelId);
    }

    private String getChannelOwner(String channelId) {
        return ownerToChannel.entrySet().stream()
            .filter(e -> e.getValue().equals(channelId))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    private String sanitizeChannelName(String name) {
        String sanitized = name.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}🗣]", "");
        if (sanitized.length() > MAX_CHANNEL_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_CHANNEL_NAME_LENGTH);
        }
        return sanitized;
    }

    private int getMaxBitrate(Guild guild) {
        return switch (guild.getBoostTier()) {
            case TIER_1 -> 128;
            case TIER_2 -> 256;
            case TIER_3 -> 384;
            default -> 96;
        };
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}

