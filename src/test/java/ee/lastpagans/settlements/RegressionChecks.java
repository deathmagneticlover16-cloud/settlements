package ee.lastpagans.settlements;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ee.lastpagans.settlements.character.BreastAppearance;
import ee.lastpagans.settlements.character.CharacterBodyCodec;
import ee.lastpagans.settlements.character.CharacterBodyData;
import ee.lastpagans.settlements.character.CharacterGender;

import java.util.UUID;

/** Dependency-free regression checks against the actual mapped Minecraft/Forge classes. */
public final class RegressionChecks {
    private static int assertions;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        capturePersistenceAndGeometry();
        bannerValidation();
        treasuryAccounting();
        dyeNames();
        researchTiers();
        capturePacket();
        recruitTemplateSanitization();
        characterBodyData();
        assertions += CharacterDraftChecks.run();
        assertions += WildfireBridgeChecks.run();
        System.out.println("Settlements regression checks passed: " + assertions + " assertions");
    }

    private static void capturePersistenceAndGeometry() {
        CaptureZoneData data = new CaptureZoneData();
        CaptureZoneData.CaptureZoneRecord zone = new CaptureZoneData.CaptureZoneRecord(UUID.randomUUID(), "test",
                CaptureZoneData.ZoneType.MAIN, "minecraft:overworld", new BlockPos(10, 64, 10), new BlockPos(0, 68, 0));
        check(zone.width() == 11 && zone.depth() == 11, "reversed corners normalize inclusively");
        check(zone.contains(new BlockPos(0, -20, 10)), "zone uses its horizontal footprint");
        CaptureZoneData.CaptureZoneRecord nested = new CaptureZoneData.CaptureZoneRecord(UUID.randomUUID(), "other",
                CaptureZoneData.ZoneType.SECONDARY, "minecraft:overworld", new BlockPos(2, 10, 2), new BlockPos(4, 12, 4));
        check(zone.intersects(nested) && nested.intersects(zone), "nested cross-settlement zones overlap regardless of height");
        CaptureZoneData.CaptureZoneRecord otherDimension = new CaptureZoneData.CaptureZoneRecord(UUID.randomUUID(), "test",
                CaptureZoneData.ZoneType.SECONDARY, "minecraft:the_nether", BlockPos.ZERO, new BlockPos(10, 10, 10));
        check(!zone.intersects(otherDimension), "dimensions do not overlap");
        zone.setProgress(Double.NaN);
        check(zone.progress() == 0D, "non-finite progress cannot corrupt capture state");
        zone.setProgress(1D);
        zone.setCaptured(true);
        zone.setState(CaptureZoneData.ZoneState.CONTESTED);
        data.put(zone);
        data.setOccupyingTeam("test", "invaders");
        CaptureZoneData restored = CaptureZoneData.load(data.save(new CompoundTag()));
        check(restored.get(zone.id()).captured(), "contesting a captured zone preserves control across save/load");
        check(restored.get(zone.id()).state() == CaptureZoneData.ZoneState.CONTESTED, "contested state persists separately");
        check(restored.occupyingTeam("test").equals("invaders"), "occupation persists");
        restored.removeSettlement("test");
        check(restored.allZones().isEmpty() && restored.occupyingTeam("test").isEmpty(), "deletion clears objectives and occupation");
    }

    private static void bannerValidation() {
        check(FactionBannerUtil.sanitize(new ItemStack(Items.WHITE_BANNER)).isEmpty(), "blank faction banner rejected");
        ItemStack valid = banner("cr", DyeColor.RED.getId(), 1);
        valid.getOrCreateTag().putString("untrusted", "discard");
        ItemStack clean = FactionBannerUtil.sanitize(valid);
        check(!clean.isEmpty() && clean.getCount() == 1, "valid designed banner accepted");
        check(!clean.getTag().contains("untrusted"), "extra item NBT removed");
        check(clean.getTagElement("BlockEntityTag").getList("Patterns", Tag.TAG_COMPOUND).size() == 1, "pattern survives sanitization");
        check(FactionBannerUtil.sanitize(banner("unknown_pattern", 0, 1)).isEmpty(), "unknown pattern rejected");
        check(FactionBannerUtil.sanitize(banner("b", 0, 1)).isEmpty(), "base layer cannot impersonate a pattern");
        check(FactionBannerUtil.sanitize(banner("cr", -1, 1)).isEmpty(), "invalid dye rejected");
        check(FactionBannerUtil.sanitize(banner("cr", 0, 7)).isEmpty(), "seventh layer rejected");
        check(!FactionBannerUtil.sanitize(banner("cr", 0, 6)).isEmpty(), "six layers accepted");
    }

    private static ItemStack banner(String pattern, int color, int count) {
        ItemStack result = new ItemStack(Items.WHITE_BANNER);
        ListTag layers = new ListTag();
        for (int i = 0; i < count; i++) {
            CompoundTag layer = new CompoundTag();
            layer.putString("Pattern", pattern);
            layer.putInt("Color", color);
            layers.add(layer);
        }
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.put("Patterns", layers);
        result.addTagElement("BlockEntityTag", blockEntity);
        return result;
    }

    private static void treasuryAccounting() {
        CampaignSystemsData systems = new CampaignSystemsData();
        systems.setFactionWealth("polity", 100);
        check(!systems.spendFactionWealth("polity", 101) && systems.factionWealth("polity") == 100, "failed spend is lossless");
        check(systems.spendFactionWealth("polity", 100) && systems.factionWealth("polity") == 0, "exact balance can be spent");
        systems.setFactionWealth("polity", Integer.MAX_VALUE);
        systems.changeFactionWealth("polity", 1);
        check(systems.factionWealth("polity") == Integer.MAX_VALUE, "positive overflow cannot wipe treasury");
        systems.changeFactionWealth("polity", Integer.MIN_VALUE);
        check(systems.factionWealth("polity") == 0, "negative balance clamps to zero");
    }

    private static void dyeNames() {
        for (DyeColor dye : DyeColor.values()) {
            check(FactionColorUtil.isKnown(dye.getName()) && FactionColorUtil.dye(dye.getName()) == dye, "registered dye name round trip: " + dye.getName());
            int recruits = Byte.toUnsignedInt(FactionColorUtil.closestRecruitsUnitColor(dye));
            check(recruits <= 24, "Recruits unit palette index is bounded");
        }
        check(!FactionColorUtil.isKnown("missing_dye") && FactionColorUtil.dye("missing_dye") == DyeColor.WHITE, "unavailable persisted dyes have a display fallback");
        CampaignData campaign = new CampaignData();
        campaign.setFactionColors("test", "teal", "navy");
        CampaignData loaded = CampaignData.load(campaign.save(new CompoundTag()));
        check(loaded.factionProfile("test").teamDye().equals("teal") && loaded.factionProfile("test").unitDye().equals("navy"),
                "optional dye names persist even when the color mod is absent");
    }

    private static void researchTiers() {
        SettlementData data = new SettlementData();
        SettlementData.SettlementRecord settlement = data.createSettlement("tier_test", "Tier test", UUID.randomUUID());
        settlement.setCapital(true);
        for (SettlementData.SettlementRank rank : SettlementData.SettlementRank.values()) {
            settlement.setRank(rank);
            check(ResearchManager.settlementTier(settlement) == rank.ordinal() + 1, "capital does not elevate research tier: " + rank);
        }
    }

    private static void capturePacket() {
        java.util.List<ee.lastpagans.settlements.api.CaptureZoneView> zones = new java.util.ArrayList<>();
        for (int i = 0; i < 257; i++) zones.add(new ee.lastpagans.settlements.api.CaptureZoneView(UUID.randomUUID(), "test",
                CaptureZoneData.ZoneType.SECONDARY, "minecraft:overworld", BlockPos.ZERO, new BlockPos(12, 4, 12),
                1D, CaptureZoneData.ZoneState.CONTESTED, "invaders", false, true));
        net.minecraft.network.FriendlyByteBuf buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            ee.lastpagans.settlements.network.CaptureZoneSnapshotPacket.encode(new ee.lastpagans.settlements.network.CaptureZoneSnapshotPacket(zones), buffer);
            var decoded = ee.lastpagans.settlements.network.CaptureZoneSnapshotPacket.decode(buffer);
            check(decoded.zones().equals(zones), "capture packet retains more than 256 zones and contested control");
            check(buffer.readableBytes() == 0, "capture packet consumes its complete payload");
        } finally { buffer.release(); }
    }

    private static void recruitTemplateSanitization() {
        CompoundTag root = new CompoundTag();
        CompoundTag entity = new CompoundTag();
        entity.putBoolean("NoAI", true);
        entity.putBoolean("NoGravity", true);
        entity.putBoolean("Invulnerable", true);
        entity.putBoolean("Fleeing", true);
        entity.putBoolean("ShouldHoldPos", true);
        entity.putBoolean("ShouldMovePos", true);
        entity.putUUID("OwnerUUID", UUID.randomUUID());
        entity.putInt("FollowState", 4);
        entity.putInt("SettlementsMercenaryLifecycle", 1);
        entity.putBoolean("SettlementsMercenaryPendingSpawn", true);
        entity.putInt("Variant", 3);
        entity.put("ArmorItems", new ListTag());
        root.put("EntityTag", entity);
        SettlementManager.sanitizeTemplate(root);
        CompoundTag clean = root.getCompound("EntityTag");
        for (String unsafe : java.util.List.of("NoAI", "NoGravity", "Invulnerable", "Fleeing",
                "ShouldHoldPos", "ShouldMovePos", "OwnerUUID", "FollowState",
                "SettlementsMercenaryLifecycle", "SettlementsMercenaryPendingSpawn"))
            check(!clean.contains(unsafe), "runtime recruit template state removed: " + unsafe);
        check(clean.getInt("Variant") == 3, "template appearance is preserved");
        check(clean.contains("ArmorItems", Tag.TAG_LIST), "template equipment is preserved");
    }

    private static void characterBodyData() {
        BreastAppearance appearance = new BreastAppearance(true, 0.68F, 0.05F, -0.04F, -0.08F, 0.03F, false);
        CharacterBodyData body = new CharacterBodyData(1, CharacterGender.FEMALE, appearance,
                true, true, true, false, 0.34F, 0.75F);
        check(body.sanitized().equals(body), "valid character body remains unchanged");
        check(Math.abs(body.effectiveBustSize() - 0.68F) < 0.0001F, "enabled female body keeps configured bust");
        CharacterBodyData male = body.withGender(CharacterGender.MALE);
        check(!male.breasts().enabled() && male.effectiveBustSize() == 0.0F, "male body enforces breasts disabled");
        CharacterBodyData disabled = body.withBreasts(appearance.withEnabled(false));
        check(disabled.gender() == CharacterGender.FEMALE && disabled.effectiveBustSize() == 0.0F,
                "breasts disabled does not change gender");
        CharacterBodyData malformed = new CharacterBodyData(1, CharacterGender.OTHER,
                new BreastAppearance(true, Float.NaN, 5.0F, -5.0F, 2.0F, 4.0F, true),
                true, true, true, false, Float.POSITIVE_INFINITY, -4.0F);
        check(!malformed.hasFiniteValues(), "non-finite network body is rejected before sanitization");
        CharacterBodyData safe = malformed.sanitized();
        check(safe.hasFiniteValues() && safe.breasts().xOffset() == 1.0F && safe.breasts().yOffset() == -1.0F
                && safe.breasts().zOffset() == 0.0F && safe.breasts().cleavage() == 0.10F,
                "body values use Wildfire-compatible defaults and bounds");
        check(CharacterBodyData.fromNBT(body.toNBT()).equals(body), "body NBT round trip is exact");

        net.minecraft.network.FriendlyByteBuf buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            CharacterBodyCodec.write(buffer, body);
            check(CharacterBodyCodec.read(buffer).equals(body) && buffer.readableBytes() == 0,
                    "body network codec consumes and preserves complete payload");
        } finally { buffer.release(); }

        UUID playerId = UUID.randomUUID();
        SettlementData data = new SettlementData();
        SettlementData.CharacterRecord record = data.getOrCreateCharacter(playerId);
        record.create("Test", "", "Character", "Other", "Neutral", "Lord");
        CompoundTag legacyTag = data.save(new CompoundTag());
        check(!SettlementData.load(legacyTag).getCharacter(playerId).hasBodyData(),
                "legacy character without Body remains unmanaged");
        record.setBody(body);
        SettlementData.CharacterRecord restored = SettlementData.load(data.save(new CompoundTag())).getCharacter(playerId);
        check(restored.hasBodyData() && restored.getBody().equals(body), "authoritative body persists nested in CharacterRecord");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
