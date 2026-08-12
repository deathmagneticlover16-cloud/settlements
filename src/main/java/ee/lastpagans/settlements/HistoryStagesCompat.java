package ee.lastpagans.settlements;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reflection-only optional bridge. Settlements never classloads History Stages when it is absent. */
public final class HistoryStagesCompat {
    private static final String MOD_ID = "historystages";
    private HistoryStagesCompat() {}

    public static boolean installed() { return ModList.get().isLoaded(MOD_ID); }

    @SuppressWarnings("unchecked")
    public static List<Technology> technologies() {
        if (!installed()) return List.of();
        try {
            Class<?> manager = Class.forName("net.bananemdnsa.historystages.data.StageManager");
            Map<String, Object> stages = (Map<String, Object>) manager.getMethod("getIndividualStages").invoke(null);
            Method duration = manager.getMethod("getIndividualResearchTimeInTicks", String.class);
            List<Technology> values = new ArrayList<>();
            for (Map.Entry<String, Object> entry : stages.entrySet()) {
                String id = entry.getKey();
                Object stage = entry.getValue();
                Class<?> type = stage.getClass();
                String name = String.valueOf(type.getMethod("getDisplayName").invoke(stage));
                String icon = String.valueOf(type.getMethod("getIcon").invoke(stage));
                int ticks = ((Number) duration.invoke(null, id)).intValue();
                if (ticks <= 0) ticks = SettlementConfig.RESEARCH_FALLBACK_HOURS.get() * 60 * 60 * 20;
                List<DependencyRule> dependencies = dependencyRules(stage);
                values.add(new Technology(id, name == null || name.isBlank() ? id : name, icon == null ? "" : icon,
                        Math.max(20L, ticks), dependencies));
            }
            values.sort(Comparator.comparing(Technology::name, String.CASE_INSENSITIVE_ORDER));
            return List.copyOf(values);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<DependencyRule> dependencyRules(Object stage) {
        try {
            Object raw = stage.getClass().getMethod("getDependencies").invoke(stage);
            if (!(raw instanceof Collection<?> collection)) return List.of();
            List<DependencyRule> result = new ArrayList<>();
            for (Object group : collection) {
                if (group == null) continue;
                Class<?> type = group.getClass();
                boolean or = Boolean.TRUE.equals(type.getMethod("isOr").invoke(group));
                Object referenced = type.getMethod("getReferencedStageIds").invoke(group);
                List<String> ids = new ArrayList<>();
                if (referenced instanceof Collection<?> values) {
                    for (Object id : values) if (id != null && !id.toString().isBlank()) ids.add(id.toString());
                }
                if (!ids.isEmpty()) result.add(new DependencyRule(or, List.copyOf(ids)));
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException exception) {
            return List.of();
        }
    }

    public static boolean dependenciesMet(Technology technology, Set<String> completed) {
        if (technology == null) return false;
        Set<String> done = completed == null ? Set.of() : completed;
        for (DependencyRule rule : technology.dependencies()) {
            if (rule.stageIds().isEmpty()) continue;
            boolean passed = rule.anyOf()
                    ? rule.stageIds().stream().anyMatch(done::contains)
                    : rule.stageIds().stream().allMatch(done::contains);
            if (!passed) return false;
        }
        return true;
    }

    /** Grants one individual History Stages stage and syncs it exactly like the mod's own unlock command. */
    public static boolean unlock(ServerPlayer player, String stageId) {
        if (!installed() || player == null || stageId == null || stageId.isBlank()) return false;
        try {
            Class<?> manager = Class.forName("net.bananemdnsa.historystages.data.StageManager");
            @SuppressWarnings("unchecked") Map<String, Object> stages = (Map<String, Object>) manager.getMethod("getIndividualStages").invoke(null);
            Object stage = stages.get(stageId);
            if (stage == null) return false;
            String display = String.valueOf(stage.getClass().getMethod("getDisplayName").invoke(stage));

            Class<?> dataClass = Class.forName("net.bananemdnsa.historystages.util.IndividualStageData");
            Object data = dataClass.getMethod("get", net.minecraft.world.level.Level.class).invoke(null, player.serverLevel());
            boolean already = Boolean.TRUE.equals(dataClass.getMethod("hasStage", java.util.UUID.class, String.class)
                    .invoke(data, player.getUUID(), stageId));
            if (!already) {
                dataClass.getMethod("addStage", java.util.UUID.class, String.class).invoke(data, player.getUUID(), stageId);
                dataClass.getMethod("setDirty").invoke(data);
                Class<?> eventClass = Class.forName("net.bananemdnsa.historystages.events.StageEvent$IndividualUnlocked");
                Constructor<?> eventCtor = eventClass.getConstructor(String.class, String.class, java.util.UUID.class);
                Object event = eventCtor.newInstance(stageId, display, player.getUUID());
                if (event instanceof Event forgeEvent) MinecraftForge.EVENT_BUS.post(forgeEvent);
            }

            Object unlocked = dataClass.getMethod("getUnlockedStages", java.util.UUID.class).invoke(data, player.getUUID());
            Class<?> packetClass = Class.forName("net.bananemdnsa.historystages.network.SyncIndividualStagesPacket");
            Object packet = packetClass.getConstructor(Set.class).newInstance(unlocked);
            Class<?> handler = Class.forName("net.bananemdnsa.historystages.network.PacketHandler");
            handler.getMethod("sendIndividualStagesToPlayer", packetClass, ServerPlayer.class).invoke(null, packet, player);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }

    public static Map<String, Technology> technologyMap() {
        Map<String, Technology> map = new LinkedHashMap<>();
        for (Technology value : technologies()) map.put(value.id(), value);
        return map;
    }

    public record DependencyRule(boolean anyOf, List<String> stageIds) {}
    public record Technology(String id, String name, String icon, long researchTicks, List<DependencyRule> dependencies) {}
}
