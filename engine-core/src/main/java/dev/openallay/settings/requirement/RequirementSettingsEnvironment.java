package dev.openallay.settings.requirement;

import dev.openallay.capability.CapabilityKind;
import dev.openallay.requirement.RequirementAvailability;
import dev.openallay.requirement.RequirementEnvironment;
import dev.openallay.requirement.RequirementKind;
import dev.openallay.requirement.RequirementReport;
import dev.openallay.requirement.RequirementStatus;
import dev.openallay.settings.ClientSettingsSnapshot;
import dev.openallay.settings.capability.CapabilitySettingsView;
import dev.openallay.settings.extension.ExtensionSettingsView;
import dev.openallay.settings.skill.SkillSettingsView;
import dev.openallay.script.UnrestrictedJavascriptConfig;
import dev.openallay.script.command.CommandCapabilityConfig;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Local configuration facts only. Runtime request authority remains owned by runtime code. */
public final class RequirementSettingsEnvironment {
    public static final String UNRESTRICTED_JAVASCRIPT = "openallay:unrestricted_javascript";
    /** Published Extension manifests and Skill metadata can use this exact capability ID. */
    public static final String UNRESTRICTED_JAVASCRIPT_ALIAS = "unrestricted-javascript";
    public static final String EXPERIMENTAL_COMMANDS = "openallay:experimental_commands";

    private RequirementSettingsEnvironment() {}

    public static RequirementEnvironment from(ClientSettingsSnapshot snapshot) {
        return from(snapshot.capabilities(), snapshot.skills(), snapshot.extensions(),
                snapshot.experimentalCommands(), snapshot.unrestrictedJavascript());
    }

    public static RequirementEnvironment from(
            CapabilitySettingsView capabilities, SkillSettingsView skills,
            ExtensionSettingsView extensions, CommandCapabilityConfig commands,
            UnrestrictedJavascriptConfig unrestricted) {
        boolean commandsAvailable = commands.enabled() || unrestricted.enabled();
        Map<String, RequirementAvailability> capabilityFacts = new TreeMap<>();
        for (dev.openallay.capability.CapabilitySettingsEntry entry : capabilities.catalog().entries()) {
            if (entry.kind() == CapabilityKind.SKILL) continue;
            capabilityFacts.put(entry.id(), fact(entry.id(), !entry.available()
                    ? RequirementStatus.UNAVAILABLE
                    : !entry.enabled() || capabilities.policy().disabledTools().contains(entry.id())
                            ? RequirementStatus.DISABLED : RequirementStatus.SATISFIED));
        }
        RequirementStatus unrestrictedStatus = unrestricted.enabled()
                ? RequirementStatus.SATISFIED : RequirementStatus.DISABLED;
        capabilityFacts.put(UNRESTRICTED_JAVASCRIPT, fact(UNRESTRICTED_JAVASCRIPT, unrestrictedStatus));
        capabilityFacts.put(UNRESTRICTED_JAVASCRIPT_ALIAS,
                fact(UNRESTRICTED_JAVASCRIPT_ALIAS, unrestrictedStatus));
        capabilityFacts.put(EXPERIMENTAL_COMMANDS, fact(EXPERIMENTAL_COMMANDS,
                commandsAvailable ? RequirementStatus.SATISFIED : RequirementStatus.DISABLED));

        Map<String, RequirementAvailability> extensionFacts = new TreeMap<>();
        for (dev.openallay.settings.extension.ExtensionSettingsView.Extension extension : extensions.extensions()) {
            {
final java.util.Map<java.lang.String, dev.openallay.requirement.RequirementAvailability> $oaSwitch0_exit_result_prior1 = extensionFacts;
final java.lang.String $oaSwitch0_exit_result_prior2 = extension.id();
final java.lang.String $oaSwitch0_exit_result_prior0 = extension.name();
dev.openallay.requirement.RequirementStatus $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((extension.state())) {
case ACTIVE:
{
$oaSwitch0_exit_result = RequirementStatus.SATISFIED; break $oaSwitch0_exit;
}
case RESTART_REQUIRED:
{
$oaSwitch0_exit_result = RequirementStatus.RESTART_REQUIRED; break $oaSwitch0_exit;
}
case COMMUNITY:
{
$oaSwitch0_exit_result = RequirementStatus.MISSING; break $oaSwitch0_exit;
}
case INCOMPATIBLE:
case UNAVAILABLE:
{
$oaSwitch0_exit_result = RequirementStatus.UNAVAILABLE; break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
$oaSwitch0_exit_result_prior1.put($oaSwitch0_exit_result_prior2, fact($oaSwitch0_exit_result_prior0, $oaSwitch0_exit_result));
}
        }
        Map<String, RequirementAvailability> skillFacts = new TreeMap<>();
        for (dev.openallay.settings.skill.SkillSettingsView.Skill skill : skills.skills()) {
            String id = skill.metadata().name();
            boolean runtimeUnavailable = (id.equals("run-game-commands") && !commandsAvailable)
                    || (id.equals("unrestricted-javascript") && !unrestricted.enabled());
            skillFacts.put(id, fact(id, runtimeUnavailable ? RequirementStatus.UNAVAILABLE
                    : capabilities.policy().disabledSkills().contains(id)
                            ? RequirementStatus.DISABLED : RequirementStatus.SATISFIED));
        }
        return new RequirementEnvironment(capabilityFacts, extensionFacts, skillFacts);
    }

    /** Only disabled entries with a known persistent owner can be offered as exact changes. */
    public static List<RequirementChange> changes(
            RequirementReport report, CapabilitySettingsView capabilities) {
        return dev.openallay.util.Java8Collections.toList(report.entries().stream()
                .filter(entry -> entry.status() == RequirementStatus.DISABLED)
                .filter(entry -> entry.kind() == RequirementKind.SKILL
                        ? capabilities.policy().disabledSkills().contains(entry.id())
                        : entry.kind() == RequirementKind.CAPABILITY
                                && (isUnrestrictedJavascript(entry.id())
                                    || entry.id().equals(EXPERIMENTAL_COMMANDS)
                                    || capabilities.policy().disabledTools().contains(entry.id())))
                .map(entry -> new RequirementChange(entry.kind(), entry.id(),
                        isUnrestrictedJavascript(entry.id()))));
    }

    /** Two reviewed declarations, one persistent setting owner; no generic ID normalization. */
    public static boolean isUnrestrictedJavascript(String id) {
        return UNRESTRICTED_JAVASCRIPT.equals(id) || UNRESTRICTED_JAVASCRIPT_ALIAS.equals(id);
    }

    private static RequirementAvailability fact(String name, RequirementStatus status) {
        return new RequirementAvailability(name, status, "");
    }
}
