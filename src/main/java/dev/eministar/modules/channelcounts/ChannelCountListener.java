package dev.eministar.modules.channelcounts;

import dev.eministar.config.Config;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateOnlineStatusEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Aktualisiert zwei Kanäle mit den gewünschten Namen:
 * "🌐・[Zahl] ᴏɴʟɪɴᴇ ᴍᴇᴍʙᴇʀ" und
 * "🔥・[Zahl] ᴍᴇᴍʙᴇʀ"
 */
public class ChannelCountListener extends ListenerAdapter {
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
    private final Set<Long> loadedGuilds = ConcurrentHashMap.newKeySet();

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        if (!Config.getChannelCountsEnabled()) return;
        event.getJDA().getGuilds().forEach(this::ensureMembersLoaded);
        scheduler.scheduleWithFixedDelay(() -> event.getJDA().getGuilds().forEach(this::updateForGuildSafe), 0, 60, TimeUnit.SECONDS);
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        if (!Config.getChannelCountsEnabled()) return;
        ensureMembersLoaded(event.getGuild());
        updateForGuildSafe(event.getGuild());
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        if (!Config.getChannelCountsEnabled()) return;
        updateForGuildSafe(event.getGuild());
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        if (!Config.getChannelCountsEnabled()) return;
        updateForGuildSafe(event.getGuild());
    }

    @Override
    public void onUserUpdateOnlineStatus(@NotNull UserUpdateOnlineStatusEvent event) {
        if (!Config.getChannelCountsEnabled()) return;
        event.getJDA().getGuilds().forEach(this::updateForGuildSafe);
    }

    private void ensureMembersLoaded(Guild guild) {
        long id = guild.getIdLong();
        if (loadedGuilds.contains(id)) return;
        guild.loadMembers()
                .onSuccess(members -> {
                    loadedGuilds.add(id);
                    updateForGuildSafe(guild);
                })
                .onError(throwable -> {});
    }

    private void updateForGuildSafe(Guild guild) {
        try { updateForGuild(guild); } catch (Exception ignored) {}
    }

    private void updateForGuild(Guild guild) {
        String onlineId = Config.getChannelCountsOnlineChannelId();
        String memberId = Config.getChannelCountsMemberChannelId();
        if ((onlineId == null || onlineId.isEmpty()) && (memberId == null || memberId.isEmpty())) return;

        boolean includeBots = Config.getChannelCountsIncludeBots();

        long totalMembers = includeBots ? guild.getMemberCount() : guild.getMembers().stream().filter(m -> !m.getUser().isBot()).count();
        long onlineMembers = guild.getMembers().stream()
                .filter(m -> includeBots || !m.getUser().isBot())
                .filter(m -> isConsideredOnline(m.getOnlineStatus()))
                .count();

        String memberName = "\uD83D\uDD25 〢" + totalMembers + " ᴍᴇᴍʙᴇʀ";

        if (memberId != null && !memberId.isEmpty()) renameById(guild, memberId, memberName);
    }

    private boolean isConsideredOnline(OnlineStatus status) {
        return status == OnlineStatus.ONLINE || status == OnlineStatus.IDLE || status == OnlineStatus.DO_NOT_DISTURB;
    }

    private void renameById(Guild guild, String channelId, String newName) {
        GuildChannel ch = guild.getGuildChannelById(channelId);
        if (ch == null) return;
        if (!guild.getSelfMember().hasPermission(ch, Permission.MANAGE_CHANNEL)) return;
        if (newName.length() > 100) newName = newName.substring(0, 100);
        if (newName.equals(ch.getName())) return;
        ch.getManager().setName(newName).queue();
    }
}
