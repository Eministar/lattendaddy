package dev.eministar.modules.giveaway;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class RequirementsChecker {

    public static CheckResult check(Member member, GiveawayData.Requirements req) {
        if (member == null) {
            return new CheckResult(false, "Mitglied nicht gefunden");
        }

        // Check account age
        if (req.minAccountAgeHours > 0) {
            Duration accountAge = Duration.between(
                    member.getUser().getTimeCreated().toInstant(),
                    Instant.now()
            );
            if (accountAge.toHours() < req.minAccountAgeHours) {
                return new CheckResult(false,
                        "Dein Account muss mindestens " + req.minAccountAgeHours + " Stunden alt sein.");
            }
        }

        // Check guild join age
        if (req.minGuildJoinHours > 0) {
            Duration joinAge = Duration.between(
                    member.getTimeJoined().toInstant(),
                    Instant.now()
            );
            if (joinAge.toHours() < req.minGuildJoinHours) {
                return new CheckResult(false,
                        "Du musst mindestens " + req.minGuildJoinHours + " Stunden auf diesem Server sein.");
            }
        }

        // Check deny roles
        if (req.denyRoleIds != null && !req.denyRoleIds.isEmpty()) {
            for (Role role : member.getRoles()) {
                if (req.denyRoleIds.contains(role.getId())) {
                    return new CheckResult(false,
                            "Du hast eine gesperrte Rolle: " + role.getName());
                }
            }
        }

        // Check require roles (OR logic - needs at least one)
        if (req.requireRoleIds != null && !req.requireRoleIds.isEmpty()) {
            boolean hasRequired = false;
            for (Role role : member.getRoles()) {
                if (req.requireRoleIds.contains(role.getId())) {
                    hasRequired = true;
                    break;
                }
            }
            if (!hasRequired) {
                return new CheckResult(false,
                        "Du benötigst eine der erforderlichen Rollen.");
            }
        }

        return new CheckResult(true, "OK");
    }

    public static int calculateEntries(Member member, GiveawayData.EntriesConfig config) {
        int total = config.base;

        if (config.bonusByRole != null) {
            List<Role> roles = member.getRoles();
            for (Role role : roles) {
                Integer bonus = config.bonusByRole.get(role.getId());
                if (bonus != null) {
                    total += bonus;
                }
            }
        }

        return Math.max(1, total);
    }

    public static class CheckResult {
        public final boolean passed;
        public final String message;

        public CheckResult(boolean passed, String message) {
            this.passed = passed;
            this.message = message;
        }
    }
}

