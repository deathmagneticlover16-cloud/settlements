package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.Settlements;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SettlementNetwork {
    private static final String PROTOCOL = "43";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Settlements.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
    );

    private SettlementNetwork() {}

    public static void register() {
        // Every handler performs its own enqueueWork. Register on the network thread so Forge does
        // not add a second main-thread queue hop via consumerMainThread.
        int id = 0;
        CHANNEL.messageBuilder(SnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(SnapshotPacket::encode).decoder(SnapshotPacket::decode).consumerNetworkThread(SnapshotPacket::handle).add();
        CHANNEL.messageBuilder(SettlementActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(SettlementActionPacket::encode).decoder(SettlementActionPacket::decode).consumerNetworkThread(SettlementActionPacket::handle).add();
        CHANNEL.messageBuilder(AreaSelectionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(AreaSelectionPacket::encode).decoder(AreaSelectionPacket::decode).consumerNetworkThread(AreaSelectionPacket::handle).add();
        CHANNEL.messageBuilder(HousingAreaActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(HousingAreaActionPacket::encode).decoder(HousingAreaActionPacket::decode).consumerNetworkThread(HousingAreaActionPacket::handle).add();
        CHANNEL.messageBuilder(HousingAreasPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(HousingAreasPacket::encode).decoder(HousingAreasPacket::decode).consumerNetworkThread(HousingAreasPacket::handle).add();
        CHANNEL.messageBuilder(OpenCharacterCreationPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(OpenCharacterCreationPacket::encode).decoder(OpenCharacterCreationPacket::decode).consumerNetworkThread(OpenCharacterCreationPacket::handle).add();
        CHANNEL.messageBuilder(SubmitCharacterPacket.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(SubmitCharacterPacket::encode).decoder(SubmitCharacterPacket::decode).consumerNetworkThread(SubmitCharacterPacket::handle).add();
        CHANNEL.messageBuilder(ApplyCharacterBodyPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(ApplyCharacterBodyPacket::encode).decoder(ApplyCharacterBodyPacket::decode).consumerNetworkThread(ApplyCharacterBodyPacket::handle).add();
        CHANNEL.messageBuilder(ClearCharacterBodyPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(ClearCharacterBodyPacket::encode).decoder(ClearCharacterBodyPacket::decode).consumerNetworkThread(ClearCharacterBodyPacket::handle).add();
        CHANNEL.messageBuilder(CharacterSheetPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(CharacterSheetPacket::encode).decoder(CharacterSheetPacket::decode).consumerNetworkThread(CharacterSheetPacket::handle).add();
        CHANNEL.messageBuilder(UpdateCharacterIdentityPacket.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(UpdateCharacterIdentityPacket::encode).decoder(UpdateCharacterIdentityPacket::decode).consumerNetworkThread(UpdateCharacterIdentityPacket::handle).add();
        CHANNEL.messageBuilder(ProfileSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(ProfileSyncPacket::encode).decoder(ProfileSyncPacket::decode).consumerNetworkThread(ProfileSyncPacket::handle).add();
        CHANNEL.messageBuilder(OpenCampaignRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(OpenCampaignRequestPacket::encode).decoder(OpenCampaignRequestPacket::decode).consumerNetworkThread(OpenCampaignRequestPacket::handle).add();
        CHANNEL.messageBuilder(CampaignSnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(CampaignSnapshotPacket::encode).decoder(CampaignSnapshotPacket::decode).consumerNetworkThread(CampaignSnapshotPacket::handle).add();
        CHANNEL.messageBuilder(FactionSnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(FactionSnapshotPacket::encode).decoder(FactionSnapshotPacket::decode).consumerNetworkThread(FactionSnapshotPacket::handle).add();
        CHANNEL.messageBuilder(FactionActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(FactionActionPacket::encode).decoder(FactionActionPacket::decode).consumerNetworkThread(FactionActionPacket::handle).add();
        CHANNEL.messageBuilder(MilitarySnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(MilitarySnapshotPacket::encode).decoder(MilitarySnapshotPacket::decode).consumerNetworkThread(MilitarySnapshotPacket::handle).add();
        CHANNEL.messageBuilder(DiplomacySnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(DiplomacySnapshotPacket::encode).decoder(DiplomacySnapshotPacket::decode).consumerNetworkThread(DiplomacySnapshotPacket::handle).add();
        CHANNEL.messageBuilder(DiplomacyActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(DiplomacyActionPacket::encode).decoder(DiplomacyActionPacket::decode).consumerNetworkThread(DiplomacyActionPacket::handle).add();
        CHANNEL.messageBuilder(FamilySnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(FamilySnapshotPacket::encode).decoder(FamilySnapshotPacket::decode).consumerNetworkThread(FamilySnapshotPacket::handle).add();
        CHANNEL.messageBuilder(FamilyActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(FamilyActionPacket::encode).decoder(FamilyActionPacket::decode).consumerNetworkThread(FamilyActionPacket::handle).add();
        CHANNEL.messageBuilder(WorldNewsSnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(WorldNewsSnapshotPacket::encode).decoder(WorldNewsSnapshotPacket::decode).consumerNetworkThread(WorldNewsSnapshotPacket::handle).add();
        CHANNEL.messageBuilder(MapMarkersPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(MapMarkersPacket::encode).decoder(MapMarkersPacket::decode).consumerNetworkThread(MapMarkersPacket::handle).add();
        CHANNEL.messageBuilder(CampaignToastPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(CampaignToastPacket::encode).decoder(CampaignToastPacket::decode).consumerNetworkThread(CampaignToastPacket::handle).add();
        CHANNEL.messageBuilder(SetMainSeatPacket.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(SetMainSeatPacket::encode).decoder(SetMainSeatPacket::decode).consumerNetworkThread(SetMainSeatPacket::handle).add();
        CHANNEL.messageBuilder(ResearchSnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(ResearchSnapshotPacket::encode).decoder(ResearchSnapshotPacket::decode).consumerNetworkThread(ResearchSnapshotPacket::handle).add();
        CHANNEL.messageBuilder(ResearchActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(ResearchActionPacket::encode).decoder(ResearchActionPacket::decode).consumerNetworkThread(ResearchActionPacket::handle).add();
        CHANNEL.messageBuilder(MercenaryRosterSnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(MercenaryRosterSnapshotPacket::encode).decoder(MercenaryRosterSnapshotPacket::decode).consumerNetworkThread(MercenaryRosterSnapshotPacket::handle).add();
        CHANNEL.messageBuilder(MercenaryRosterActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(MercenaryRosterActionPacket::encode).decoder(MercenaryRosterActionPacket::decode).consumerNetworkThread(MercenaryRosterActionPacket::handle).add();
        CHANNEL.messageBuilder(CaptureZoneSnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(CaptureZoneSnapshotPacket::encode).decoder(CaptureZoneSnapshotPacket::decode).consumerNetworkThread(CaptureZoneSnapshotPacket::handle).add();
        CHANNEL.messageBuilder(FactionCreatePacket.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(FactionCreatePacket::encode).decoder(FactionCreatePacket::decode).consumerNetworkThread(FactionCreatePacket::handle).add();
        CHANNEL.messageBuilder(CharacterSubmissionRejectedPacket.class, id, NetworkDirection.PLAY_TO_CLIENT).encoder(CharacterSubmissionRejectedPacket::encode).decoder(CharacterSubmissionRejectedPacket::decode).consumerNetworkThread(CharacterSubmissionRejectedPacket::handle).add();
    }
}
