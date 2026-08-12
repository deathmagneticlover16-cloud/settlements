package ee.lastpagans.settlements;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
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
        long remainingSeconds,
        List<TechnologyEntry> technologies,
        String message
) {
    public static ResearchSnapshot from(ServerPlayer player, String message) {
        ResearchManager.Polity polity = ResearchManager.polityFor(player);
        boolean installed = HistoryStagesCompat.installed();
        if (polity == null) {
            return new ResearchSnapshot(installed, "", "No research polity", false, "", "", 0, 100, 0L,
                    List.of(), message == null ? "" : message);
        }
        ResearchData data = ResearchData.get(player.serverLevel());
        ResearchData.ActiveResearch active = data.active(polity.key());
        int speed = ResearchManager.speedPercent(player.getServer(), polity);
        long remainingSeconds = active == null ? 0L : ResearchManager.remainingSeconds(active, speed);
        Set<String> complete = data.completed(polity.key());
        Map<String, HistoryStagesCompat.Technology> definitions = HistoryStagesCompat.technologyMap();
        List<TechnologyEntry> entries = new ArrayList<>();
        for (HistoryStagesCompat.Technology technology : definitions.values()) {
            boolean completed = complete.contains(technology.id());
            boolean available = !completed && HistoryStagesCompat.dependenciesMet(technology, complete);
            entries.add(new TechnologyEntry(technology.id(), technology.name(), technology.icon(),
                    Math.max(1L, technology.researchTicks() / 20L), completed, available,
                    dependencyText(technology)));
        }
        return new ResearchSnapshot(installed, polity.key(), polity.name(), ResearchManager.canManage(player, polity),
                active == null ? "" : active.stageId(), active == null ? "" : active.stageName(),
                active == null ? 0 : (int)Math.round(active.progress() * 1000.0D), speed, remainingSeconds,
                List.copyOf(entries), message == null ? "" : message);
    }

    private static String dependencyText(HistoryStagesCompat.Technology technology) {
        if (technology.dependencies().isEmpty()) return "None";
        List<String> groups = new ArrayList<>();
        for (HistoryStagesCompat.DependencyRule group : technology.dependencies()) {
            groups.add(String.join(group.anyOf() ? " OR " : " + ", group.stageIds()));
        }
        return String.join("; ", groups);
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
        buffer.writeVarLong(remainingSeconds);
        buffer.writeCollection(technologies, (buf, value) -> value.encode(buf));
        buffer.writeUtf(message, 256);
    }

    public static ResearchSnapshot decode(FriendlyByteBuf buffer) {
        return new ResearchSnapshot(buffer.readBoolean(), buffer.readUtf(128), buffer.readUtf(96), buffer.readBoolean(),
                buffer.readUtf(128), buffer.readUtf(128), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong(),
                buffer.readList(TechnologyEntry::decode), buffer.readUtf(256));
    }

    public record TechnologyEntry(String id, String name, String icon, long durationSeconds,
                                  boolean completed, boolean available, String dependencies) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(id, 128);
            buffer.writeUtf(name, 128);
            buffer.writeUtf(icon == null ? "" : icon, 128);
            buffer.writeVarLong(durationSeconds);
            buffer.writeBoolean(completed);
            buffer.writeBoolean(available);
            buffer.writeUtf(dependencies == null ? "" : dependencies, 384);
        }
        private static TechnologyEntry decode(FriendlyByteBuf buffer) {
            return new TechnologyEntry(buffer.readUtf(128), buffer.readUtf(128), buffer.readUtf(128), buffer.readVarLong(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(384));
        }
    }
}
