package ee.lastpagans.settlements;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ResearchSnapshot(
        boolean historyStagesInstalled,
        String polityKey,
        String polityName,
        boolean canManage,
        String activeStageId,
        String activeStageName,
        int progressPermille,
        int speedPercent,
        int researchTier,
        long remainingSeconds,
        List<TechnologyEntry> technologies,
        String message
) {
    public static ResearchSnapshot from(ServerPlayer player, String message) {
        ResearchManager.Polity polity = ResearchManager.polityFor(player);
        boolean installed = HistoryStagesCompat.installed();
        if (polity == null) {
            return new ResearchSnapshot(installed, "", "No research polity", false, "", "", 0, 100, 1, 0L,
                    List.of(), message == null ? "" : message);
        }
        ResearchData data = ResearchData.get(player.serverLevel());
        ResearchData.ActiveResearch active = data.active(polity.key());
        int speed = ResearchManager.speedPercent(player.getServer(), polity);
        int researchTier = ResearchManager.researchTier(player.getServer(), polity);
        long remainingSeconds = active == null ? 0L : ResearchManager.remainingSeconds(active, speed);
        Set<String> complete = data.completed(polity.key());
        Map<String, HistoryStagesCompat.Technology> definitions = HistoryStagesCompat.technologyMap();
        List<TechnologyEntry> entries = new ArrayList<>();
        for (HistoryStagesCompat.Technology technology : definitions.values()) {
            boolean completed = complete.contains(technology.id());
            boolean available = !completed && HistoryStagesCompat.dependenciesMet(technology, complete) && ResearchManager.tierAllows(technology, researchTier);
            List<String> dependencyIds = dependencyIds(technology, definitions);
            entries.add(new TechnologyEntry(technology.id(), technology.name(), technology.icon(),
                    Math.max(1L, technology.researchTicks() / 20L), completed, available,
                    dependencyText(technology, definitions), dependencyIds,
                    technology.minimumTier(), technology.tierMode(), depth(technology, definitions, new java.util.HashSet<>())));
        }
        entries.sort(java.util.Comparator.comparingInt(TechnologyEntry::depth).thenComparing(TechnologyEntry::name, String.CASE_INSENSITIVE_ORDER));
        return new ResearchSnapshot(installed, polity.key(), polity.name(), ResearchManager.canManage(player, polity),
                active == null ? "" : active.stageId(), active == null ? "" : active.stageName(),
                active == null ? 0 : (int)Math.round(active.progress() * 1000.0D), speed, researchTier, remainingSeconds,
                List.copyOf(entries), message == null ? "" : message);
    }

    private static int depth(HistoryStagesCompat.Technology technology, Map<String, HistoryStagesCompat.Technology> definitions, Set<String> visiting) {
        if (technology == null || technology.dependencies().isEmpty() || !visiting.add(technology.id())) return 0;
        int value = 0;
        for (HistoryStagesCompat.DependencyRule group : technology.dependencies()) {
            for (String dependency : group.stageIds()) value = Math.max(value, 1 + depth(definitions.get(dependency), definitions, visiting));
        }
        visiting.remove(technology.id());
        return Math.min(8, value);
    }

    private static List<String> dependencyIds(HistoryStagesCompat.Technology technology, Map<String, HistoryStagesCompat.Technology> definitions) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (HistoryStagesCompat.DependencyRule group : technology.dependencies()) {
            for (String id : group.stageIds()) if (definitions.containsKey(id)) ids.add(id);
        }
        return List.copyOf(ids);
    }

    private static String dependencyText(HistoryStagesCompat.Technology technology, Map<String, HistoryStagesCompat.Technology> definitions) {
        if (technology.dependencies().isEmpty()) return "None";
        List<String> groups = new ArrayList<>();
        for (HistoryStagesCompat.DependencyRule group : technology.dependencies()) {
            List<String> names = new ArrayList<>();
            for (String id : group.stageIds()) {
                HistoryStagesCompat.Technology dependency = definitions.get(id);
                names.add(dependency == null ? id : dependency.name());
            }
            if (!names.isEmpty()) groups.add(String.join(group.anyOf() ? " OR " : " + ", names));
        }
        return groups.isEmpty() ? "External History Stages requirement" : String.join("; ", groups);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(historyStagesInstalled);
        buffer.writeUtf(polityKey, 128);
        buffer.writeUtf(polityName, 96);
        buffer.writeBoolean(canManage);
        buffer.writeUtf(activeStageId, 128);
        buffer.writeUtf(activeStageName, 128);
        buffer.writeVarInt(progressPermille);
        buffer.writeVarInt(speedPercent);
        buffer.writeVarInt(researchTier);
        buffer.writeVarLong(remainingSeconds);
        buffer.writeCollection(technologies, (buf, value) -> value.encode(buf));
        buffer.writeUtf(message, 256);
    }

    public static ResearchSnapshot decode(FriendlyByteBuf buffer) {
        return new ResearchSnapshot(buffer.readBoolean(), buffer.readUtf(128), buffer.readUtf(96), buffer.readBoolean(),
                buffer.readUtf(128), buffer.readUtf(128), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong(),
                buffer.readList(TechnologyEntry::decode), buffer.readUtf(256));
    }

    public record TechnologyEntry(String id, String name, String icon, long durationSeconds,
                                  boolean completed, boolean available, String dependencies, List<String> dependencyIds,
                                  int requiredTier, String tierMode, int depth) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(id, 128);
            buffer.writeUtf(name, 128);
            buffer.writeUtf(icon == null ? "" : icon, 128);
            buffer.writeVarLong(durationSeconds);
            buffer.writeBoolean(completed);
            buffer.writeBoolean(available);
            buffer.writeUtf(dependencies == null ? "" : dependencies, 384);
            buffer.writeCollection(dependencyIds == null ? List.<String>of() : dependencyIds, (buf, value) -> buf.writeUtf(value, 128));
            buffer.writeVarInt(requiredTier);
            buffer.writeUtf(tierMode == null ? "MIN" : tierMode, 16);
            buffer.writeVarInt(depth);
        }
        private static TechnologyEntry decode(FriendlyByteBuf buffer) {
            return new TechnologyEntry(buffer.readUtf(128), buffer.readUtf(128), buffer.readUtf(128), buffer.readVarLong(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(384), buffer.readList(buf -> buf.readUtf(128)),
                    buffer.readVarInt(), buffer.readUtf(16), buffer.readVarInt());
        }
    }
}
