package ee.lastpagans.settlements;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-persistent technology progress for independent settlements and political factions. */
public final class ResearchData extends SavedData {
    private static final String DATA_NAME = "settlements_research";

    /** One active project per political research polity. Progress is stored in 1/1000 ticks. */
    private final Map<String, ActiveResearch> active = new HashMap<>();
    private final Map<String, Set<String>> completed = new HashMap<>();

    public static ResearchData get(ServerLevel level) {
        ServerLevel storage = level.getServer().overworld();
        return storage.getDataStorage().computeIfAbsent(ResearchData::load, ResearchData::new, DATA_NAME);
    }

    public static ResearchData load(CompoundTag tag) {
        ResearchData data = new ResearchData();
        for (Tag raw : tag.getList("Active", Tag.TAG_COMPOUND)) {
            ActiveResearch value = ActiveResearch.fromNBT((CompoundTag) raw);
            if (!value.polityKey().isBlank() && !value.stageId().isBlank()) data.active.put(value.polityKey(), value);
        }
        for (Tag raw : tag.getList("Completed", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            String polity = entry.getString("Polity");
            if (polity.isBlank()) continue;
            Set<String> stages = new HashSet<>();
            for (Tag stage : entry.getList("Stages", Tag.TAG_STRING)) {
                String id = stage.getAsString();
                if (!id.isBlank()) stages.add(id);
            }
            if (!stages.isEmpty()) data.completed.put(polity, stages);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag activeTag = new ListTag();
        active.values().forEach(value -> activeTag.add(value.toNBT()));
        tag.put("Active", activeTag);

        ListTag completeTag = new ListTag();
        completed.forEach((polity, stages) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Polity", polity);
            ListTag values = new ListTag();
            stages.stream().sorted().forEach(stage -> values.add(StringTag.valueOf(stage)));
            entry.put("Stages", values);
            completeTag.add(entry);
        });
        tag.put("Completed", completeTag);
        return tag;
    }

    public ActiveResearch active(String polity) {
        return active.get(polity == null ? "" : polity);
    }

    public boolean start(String polity, HistoryStagesCompat.Technology technology) {
        if (polity == null || polity.isBlank() || technology == null || technology.id().isBlank()) return false;
        if (active.containsKey(polity) || isCompleted(polity, technology.id())) return false;
        active.put(polity, new ActiveResearch(polity, technology.id(), technology.name(), Math.max(1L, technology.researchTicks()), 0L));
        setDirty();
        return true;
    }

    public boolean cancel(String polity) {
        boolean removed = active.remove(polity == null ? "" : polity) != null;
        if (removed) setDirty();
        return removed;
    }

    /** Adds scaled progress. Returns true when the project completes. */
    public boolean progress(String polity, long scaledTicks) {
        ActiveResearch value = active(polity);
        if (value == null || scaledTicks <= 0) return false;
        long next = Math.min(Long.MAX_VALUE / 2L, value.progressScaledTicks() + scaledTicks);
        long required = Math.max(1L, value.baseTicks()) * 1000L;
        if (next >= required) {
            active.remove(polity);
            completed.computeIfAbsent(polity, ignored -> new HashSet<>()).add(value.stageId());
            setDirty();
            return true;
        }
        active.put(polity, value.withProgress(next));
        setDirty();
        return false;
    }

    public void forceComplete(String polity, String stageId) {
        if (polity == null || polity.isBlank() || stageId == null || stageId.isBlank()) return;
        ActiveResearch current = active(polity);
        if (current != null && stageId.equals(current.stageId())) active.remove(polity);
        completed.computeIfAbsent(polity, ignored -> new HashSet<>()).add(stageId);
        setDirty();
    }

    public boolean isCompleted(String polity, String stageId) {
        return completed.getOrDefault(polity == null ? "" : polity, Set.of()).contains(stageId);
    }

    public Set<String> completed(String polity) {
        return Set.copyOf(completed.getOrDefault(polity == null ? "" : polity, Set.of()));
    }

    /** Clears all active and completed research owned by one polity. */
    public boolean clearPolity(String polity) {
        if (polity == null || polity.isBlank()) return false;
        boolean changed = active.remove(polity) != null;
        changed |= completed.remove(polity) != null;
        if (changed) setDirty();
        return changed;
    }

    /** Merge an independent polity into a political faction without duplicating concurrent research. */
    public void mergePolity(String fromPolity, String toPolity) {
        if (fromPolity == null || toPolity == null || fromPolity.isBlank() || toPolity.isBlank() || fromPolity.equals(toPolity)) return;
        Set<String> sourceCompleted = completed.remove(fromPolity);
        if (sourceCompleted != null && !sourceCompleted.isEmpty()) completed.computeIfAbsent(toPolity, ignored -> new HashSet<>()).addAll(sourceCompleted);
        ActiveResearch sourceActive = active.remove(fromPolity);
        if (sourceActive != null && !active.containsKey(toPolity) && !isCompleted(toPolity, sourceActive.stageId())) {
            active.put(toPolity, new ActiveResearch(toPolity, sourceActive.stageId(), sourceActive.stageName(), sourceActive.baseTicks(), sourceActive.progressScaledTicks()));
        }
        if (sourceCompleted != null || sourceActive != null) setDirty();
    }

    public Set<String> politiesWithActiveResearch() { return Set.copyOf(active.keySet()); }

    public record ActiveResearch(String polityKey, String stageId, String stageName, long baseTicks, long progressScaledTicks) {
        public ActiveResearch withProgress(long value) { return new ActiveResearch(polityKey, stageId, stageName, baseTicks, value); }
        public double progress() {
            return Math.max(0.0D, Math.min(1.0D, progressScaledTicks / (double)(Math.max(1L, baseTicks) * 1000L)));
        }
        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Polity", polityKey);
            tag.putString("Stage", stageId);
            tag.putString("Name", stageName);
            tag.putLong("BaseTicks", baseTicks);
            tag.putLong("ProgressScaledTicks", progressScaledTicks);
            return tag;
        }
        private static ActiveResearch fromNBT(CompoundTag tag) {
            return new ActiveResearch(tag.getString("Polity"), tag.getString("Stage"), tag.getString("Name"),
                    Math.max(1L, tag.getLong("BaseTicks")), Math.max(0L, tag.getLong("ProgressScaledTicks")));
        }
    }
}
