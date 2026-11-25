package com.lastpagans.campaignmap.data;

import com.lastpagans.campaignmap.api.ProvinceOwnerState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CampaignOwnershipData extends SavedData {
    private static final String DATA_NAME = "campaignmap_ownership";
    private static final int MAX_PERSISTED_PROVINCES = 100_000;
    private final Map<ResourceLocation, ProvinceOwnerState> states = new LinkedHashMap<>();

    public static CampaignOwnershipData get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(CampaignOwnershipData::load,
                CampaignOwnershipData::new, DATA_NAME);
    }

    public static CampaignOwnershipData load(CompoundTag root) {
        CampaignOwnershipData data = new CampaignOwnershipData();
        ListTag list = root.getList("provinces", Tag.TAG_COMPOUND);
        int loaded = 0;
        for (Tag raw : list) {
            if (loaded++ >= MAX_PERSISTED_PROVINCES) break;
            CompoundTag tag = (CompoundTag) raw;
            ResourceLocation provinceId = ResourceLocation.tryParse(tag.getString("province"));
            if (provinceId == null) continue;
            ProvinceOwnerState.PoliticalIdentity owner = readIdentity(tag, "owner");
            ProvinceOwnerState.PoliticalIdentity controller = readIdentity(tag, "controller");
            if (owner != null || controller != null) {
                data.states.put(provinceId, new ProvinceOwnerState(provinceId, owner, controller));
            }
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        for (ProvinceOwnerState state : states.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("province", state.provinceId().toString());
            writeIdentity(tag, "owner", state.owner());
            writeIdentity(tag, "controller", state.controller());
            list.add(tag);
        }
        root.put("provinces", list);
        return root;
    }

    public synchronized ProvinceOwnerState state(ResourceLocation provinceId) {
        return states.getOrDefault(provinceId, new ProvinceOwnerState(provinceId, null, null));
    }

    public synchronized ProvinceOwnerState setOwner(CommandSourceStack source, ResourceLocation provinceId,
                                                    @Nullable ProvinceOwnerState.PoliticalIdentity owner) {
        ProvinceOwnerState previous = state(provinceId);
        if (source == null || !source.hasPermission(2)) return previous;
        return store(new ProvinceOwnerState(provinceId, owner, previous.controller()));
    }

    public synchronized ProvinceOwnerState setController(CommandSourceStack source, ResourceLocation provinceId,
                                                         @Nullable ProvinceOwnerState.PoliticalIdentity controller) {
        ProvinceOwnerState previous = state(provinceId);
        if (source == null || !source.hasPermission(2)) return previous;
        return store(new ProvinceOwnerState(provinceId, previous.owner(), controller));
    }

    public synchronized Collection<ProvinceOwnerState> states() {
        return List.copyOf(states.values());
    }

    private ProvinceOwnerState store(ProvinceOwnerState state) {
        if (state.owner() == null && state.controller() == null) states.remove(state.provinceId());
        else states.put(state.provinceId(), state);
        setDirty();
        return state;
    }

    @Nullable
    private static ProvinceOwnerState.PoliticalIdentity readIdentity(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = parent.getCompound(key);
        return new ProvinceOwnerState.PoliticalIdentity(tag.getString("id"), tag.getInt("color") & 0xFFFFFF);
    }

    private static void writeIdentity(CompoundTag parent, String key,
                                      @Nullable ProvinceOwnerState.PoliticalIdentity identity) {
        if (identity == null) return;
        CompoundTag tag = new CompoundTag();
        tag.putString("id", identity.id());
        tag.putInt("color", identity.color() & 0xFFFFFF);
        parent.put(key, tag);
    }
}
