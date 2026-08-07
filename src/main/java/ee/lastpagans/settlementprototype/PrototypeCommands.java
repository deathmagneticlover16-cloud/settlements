package ee.lastpagans.settlementprototype;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsClaim;
import ee.lastpagans.settlementprototype.item.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Locale;
import java.util.UUID;

public final class PrototypeCommands {
    private PrototypeCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("settlements")
                .then(Commands.literal("open")
                        .executes(context -> openSelected(context.getSource()))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> open(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> create(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "name")
                                        )))))
                .then(Commands.literal("delete").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> delete(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("rename")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> rename(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "name")
                                        )))))
                .then(Commands.literal("settownsquare")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> setTownSquare(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("setmusterground")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> setMusterGround(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("setbarracks")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> setBarracks(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("setbarrackschest")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> setBarracksChest(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("barrackssettings")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(2, 64))
                                        .then(Commands.argument("capacity", IntegerArgumentType.integer(1, 512))
                                                .executes(context -> setBarracksSettings(context.getSource(), StringArgumentType.getString(context, "id"),
                                                        IntegerArgumentType.getInteger(context, "radius"), IntegerArgumentType.getInteger(context, "capacity")))))))
                .then(Commands.literal("setfaction")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> setFaction(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("clearfaction")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> clearFaction(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("linkclaim")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> linkClaim(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("unlinkclaim")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> unlinkClaim(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("clearclaims")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> clearClaims(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("rank")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .executes(context -> setRank(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "rank")
                                        )))))
                .then(Commands.literal("housing")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("capacity", IntegerArgumentType.integer(0))
                                        .executes(context -> setHousing(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                IntegerArgumentType.getInteger(context, "capacity")
                                        )))))
                .then(Commands.literal("stability").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                        .executes(context -> setStability(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                IntegerArgumentType.getInteger(context, "value")
                                        )))))
                .then(Commands.literal("wealth").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(context -> addWealth(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                IntegerArgumentType.getInteger(context, "value")
                                        )))))
                .then(Commands.literal("notice")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(context -> setNotice(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "text")
                                        )))))
                .then(Commands.literal("member")
                        .then(Commands.literal("add")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("role", StringArgumentType.word())
                                                        .executes(context -> setMember(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "id"),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "role")
                                                        ))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> removeMember(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        EntityArgument.getPlayer(context, "player")
                                                ))))))
                .then(Commands.literal("transfer")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> transfer(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                EntityArgument.getPlayer(context, "player")
                                        )))))
                .then(Commands.literal("registervillager")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> registerNearestVillager(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "id")
                                ))))
                .then(Commands.literal("check").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> check(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("status")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> status(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("banner")
                        .then(Commands.literal("sethand")
                                .then(Commands.argument("settlement", StringArgumentType.word())
                                        .executes(context -> setBannerFromHand(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                        .then(Commands.literal("faction")
                                .then(Commands.argument("settlement", StringArgumentType.word())
                                        .executes(context -> setBannerMode(context.getSource(), StringArgumentType.getString(context, "settlement"), CampaignData.BannerMode.FACTION))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("settlement", StringArgumentType.word())
                                        .executes(context -> setBannerMode(context.getSource(), StringArgumentType.getString(context, "settlement"), CampaignData.BannerMode.NONE)))))
                .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("stats")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("settlement", StringArgumentType.word())
                                                .then(Commands.argument("stat", StringArgumentType.word())
                                                        .then(Commands.argument("value", IntegerArgumentType.integer(-1000000, 1000000))
                                                                .executes(context -> adminStat(context.getSource(), StringArgumentType.getString(context, "settlement"),
                                                                        StringArgumentType.getString(context, "stat"), IntegerArgumentType.getInteger(context, "value"), false))))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("settlement", StringArgumentType.word())
                                                .then(Commands.argument("stat", StringArgumentType.word())
                                                        .then(Commands.argument("value", IntegerArgumentType.integer(-1000000, 1000000))
                                                                .executes(context -> adminStat(context.getSource(), StringArgumentType.getString(context, "settlement"),
                                                                        StringArgumentType.getString(context, "stat"), IntegerArgumentType.getInteger(context, "value"), true)))))))
                        .then(Commands.literal("faction")
                                .then(Commands.literal("create")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(context -> adminCreateFaction(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "name")))))))
                        .then(Commands.literal("roster")
                                .then(Commands.literal("create")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(context -> createRoster(context.getSource(), StringArgumentType.getString(context, "id"), StringArgumentType.getString(context, "name"))))))
                                .then(Commands.literal("sethand")
                                        .then(Commands.argument("roster", StringArgumentType.word())
                                                .then(Commands.argument("unit_type", StringArgumentType.word())
                                                        .executes(context -> setRosterHand(context.getSource(), StringArgumentType.getString(context, "roster"), StringArgumentType.getString(context, "unit_type"))))))
                                .then(Commands.literal("copy")
                                        .then(Commands.argument("source", StringArgumentType.word())
                                                .then(Commands.argument("new_id", StringArgumentType.word())
                                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                .executes(context -> copyRoster(context.getSource(), StringArgumentType.getString(context, "source"), StringArgumentType.getString(context, "new_id"), StringArgumentType.getString(context, "name")))))))
                                .then(Commands.literal("default")
                                        .then(Commands.argument("roster", StringArgumentType.word())
                                                .executes(context -> setDefaultRoster(context.getSource(), StringArgumentType.getString(context, "roster")))))
                                .then(Commands.literal("assignfaction")
                                        .then(Commands.argument("roster", StringArgumentType.word())
                                                .executes(context -> assignFactionRoster(context.getSource(), StringArgumentType.getString(context, "roster")))
                                                .then(Commands.argument("faction", StringArgumentType.word())
                                                        .executes(context -> assignFactionRoster(context.getSource(), StringArgumentType.getString(context, "roster"), StringArgumentType.getString(context, "faction"))))))
                                .then(Commands.literal("list")
                                        .executes(context -> listRosters(context.getSource()))))
                        .then(Commands.literal("sethand")
                                .then(Commands.argument("unit_type", StringArgumentType.word())
                                        .then(Commands.argument("settlement", StringArgumentType.word())
                                                .executes(context -> setSettlementUnitHand(context.getSource(), StringArgumentType.getString(context, "settlement"), StringArgumentType.getString(context, "unit_type"))))))
                        .then(Commands.literal("mercenary")
                                .then(Commands.literal("sethand")
                                        .then(Commands.argument("wage", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("interval_days", IntegerArgumentType.integer(1, 365))
                                                        .executes(context -> setMercenaryHand(context.getSource(), IntegerArgumentType.getInteger(context, "wage"), IntegerArgumentType.getInteger(context, "interval_days"))))))
                                .then(Commands.literal("clearhand")
                                        .executes(context -> clearMercenaryHand(context.getSource()))))
        ));

        dispatcher.register(Commands.literal("character")
                .then(Commands.literal("open").executes(context -> openCharacter(context.getSource())))
                .then(Commands.literal("create").executes(context -> createCharacter(context.getSource())))
                .then(Commands.literal("seat")
                        .then(Commands.argument("settlement", StringArgumentType.word())
                                .executes(context -> setSeat(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "settlement")
                                ))))
                .then(Commands.literal("marry")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> proposeMarriage(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("reset").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> resetCharacter(context.getSource(), EntityArgument.getPlayer(context, "player")))))
        );


        dispatcher.register(Commands.literal("campaign")
                .executes(context -> openCampaign(context.getSource()))
                .then(Commands.literal("faction")
                        .executes(context -> openFaction(context.getSource()))
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> createFaction(context.getSource(), StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("military").executes(context -> openMilitary(context.getSource())))
                .then(Commands.literal("diplomacy").executes(context -> openDiplomacy(context.getSource())))
                .then(Commands.literal("family").executes(context -> openFamily(context.getSource())))
                .then(Commands.literal("news").executes(context -> openWorldNews(context.getSource()))));

        dispatcher.register(Commands.literal("settlementtestsilver").requires(source -> source.hasPermission(2))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                        .executes(context -> giveSilver(context.getSource(), IntegerArgumentType.getInteger(context, "amount")))));
    }

    private static ServerPlayer player(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return source.getPlayerOrException();
    }

    private static int openSelected(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PrototypeManager.open(player(source));
        return 1;
    }

    private static int open(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!PrototypeManager.open(player(source), id)) source.sendFailure(Component.literal("Unknown settlement: " + id));
        return 1;
    }

    private static int list(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData data = PrototypeData.get(player.serverLevel());
        String line = data.getSettlements().stream()
                .filter(settlement -> PrototypeManager.canView(player, settlement))
                .map(settlement -> settlement.getId() + " (" + settlement.getName() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("None");
        source.sendSuccess(() -> Component.literal("Settlements: " + line), false);
        return 1;
    }

    private static int create(CommandSourceStack source, String id, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData data = PrototypeData.get(player.serverLevel());
        PrototypeData.SettlementRecord settlement = data.createSettlement(id, name, player.getUUID());
        if (settlement == null) {
            source.sendFailure(Component.literal("That settlement ID is invalid or already used."));
            return 0;
        }
        settlement.setTownSquare(player.serverLevel().dimension().location().toString(), player.blockPosition());
        Team team = player.getTeam();
        if (team != null) settlement.setFactionTeam(team.getName());
        linkClaimAtPlayer(player, settlement, false);
        data.selectSettlement(player.getUUID(), settlement.getId());
        source.sendSuccess(() -> Component.literal("Founded " + settlement.getName() + " as a Village."), true);
        return 1;
    }

    private static int delete(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PrototypeData data = PrototypeData.get(player(source).serverLevel());
        if (!data.deleteSettlement(id)) {
            source.sendFailure(Component.literal("Unknown settlement."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Settlement deleted."), true);
        return 1;
    }

    private static int rename(CommandSourceStack source, String id, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.rename(name);
        source.sendSuccess(() -> Component.literal("Renamed settlement to " + settlement.getName() + "."), true);
        return 1;
    }

    private static int setTownSquare(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.setTownSquare(player.serverLevel().dimension().location().toString(), player.blockPosition());
        source.sendSuccess(() -> Component.literal("Town square set to your current position."), true);
        return 1;
    }

    private static int setMusterGround(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.setMusterGround(player.serverLevel().dimension().location().toString(), player.blockPosition());
        source.sendSuccess(() -> Component.literal("Muster Ground set to your current position. New troops will form in safe rows around it."), true);
        return 1;
    }

    private static int setBarracks(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.setBarracks(player.serverLevel().dimension().location().toString(), player.blockPosition());
        source.sendSuccess(() -> Component.literal("Barracks centre set to your current position."), true);
        return 1;
    }

    private static int setBarracksChest(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null || !settlement.hasBarracks()) {
            source.sendFailure(Component.literal("Set the Barracks before linking its upkeep chest."));
            return 0;
        }
        HitResult hit = player.pick(6.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            source.sendFailure(Component.literal("Look directly at the intended upkeep chest or supply block."));
            return 0;
        }
        settlement.setBarracksChest(blockHit.getBlockPos());
        source.sendSuccess(() -> Component.literal("Barracks upkeep position linked to " + blockHit.getBlockPos().toShortString() + "."), true);
        return 1;
    }

    private static int setBarracksSettings(CommandSourceStack source, String id, int radius, int capacity) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.setBarracksSettings(radius, capacity);
        source.sendSuccess(() -> Component.literal("Barracks radius set to " + radius + " and capacity to " + capacity + "."), true);
        return 1;
    }

    private static int setFaction(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        Team team = player.getTeam();
        if (team == null) {
            source.sendFailure(Component.literal("You are not currently in a Recruits/scoreboard faction."));
            return 0;
        }
        settlement.setFactionTeam(team.getName());
        source.sendSuccess(() -> Component.literal("Settlement linked to faction " + team.getName() + "."), true);
        return 1;
    }

    private static int clearFaction(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.setFactionTeam("");
        if (settlement.getRank() == PrototypeData.SettlementRank.CAPITAL) settlement.setRank(PrototypeData.SettlementRank.TOWN);
        source.sendSuccess(() -> Component.literal("Settlement is now independent."), true);
        return 1;
    }

    private static int linkClaim(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        return linkClaimAtPlayer(player, settlement, true) ? 1 : 0;
    }

    private static boolean linkClaimAtPlayer(ServerPlayer player, PrototypeData.SettlementRecord settlement, boolean notify) {
        if (ClaimEvents.recruitsClaimManager == null) {
            if (notify) player.sendSystemMessage(Component.literal("Recruits claim manager is not ready."));
            return false;
        }
        RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(new ChunkPos(player.blockPosition()));
        if (claim == null) {
            if (notify) player.sendSystemMessage(Component.literal("Stand inside the Recruits claim to link."));
            return false;
        }
        String dimension = player.serverLevel().dimension().location().toString();
        PrototypeData data = PrototypeData.get(player.serverLevel());
        PrototypeData.SettlementRecord existing = data.findSettlementByClaim(dimension, claim.getUUID());
        if (existing != null && existing != settlement) {
            if (notify) player.sendSystemMessage(Component.literal("That claim is already linked to " + existing.getName() + "."));
            return false;
        }
        boolean added = settlement.linkClaim(dimension, claim.getUUID(), claim.getName());
        if (claim.getOwnerFactionStringID() != null && settlement.getFactionTeam().isBlank()) {
            settlement.setFactionTeam(claim.getOwnerFactionStringID());
        }
        if (notify) player.sendSystemMessage(Component.literal((added ? "Linked claim " : "Claim was already linked: ")
                + claim.getName() + ". Total claims: " + settlement.getClaimCount() + "."));
        return true;
    }

    private static int unlinkClaim(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        if (ClaimEvents.recruitsClaimManager == null) {
            source.sendFailure(Component.literal("Recruits claim manager is not ready."));
            return 0;
        }
        RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(new ChunkPos(player.blockPosition()));
        if (claim == null) {
            source.sendFailure(Component.literal("Stand inside the linked claim you want to remove."));
            return 0;
        }
        boolean removed = settlement.unlinkClaim(player.serverLevel().dimension().location().toString(), claim.getUUID());
        if (!removed) {
            source.sendFailure(Component.literal("That claim is not linked to this settlement."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Unlinked claim " + claim.getName() + ". Remaining claims: " + settlement.getClaimCount() + "."), true);
        return 1;
    }

    private static int clearClaims(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        int count = settlement.getClaimCount();
        settlement.clearClaimLink();
        source.sendSuccess(() -> Component.literal("Removed all " + count + " linked claim(s)."), true);
        return 1;
    }

    private static int setRank(CommandSourceStack source, String id, String rawRank) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        PrototypeData.SettlementRank rank;
        try {
            rank = PrototypeData.SettlementRank.valueOf(rawRank.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Use village, town, or capital."));
            return 0;
        }
        if (rank == PrototypeData.SettlementRank.CAPITAL && settlement.getFactionTeam().isBlank()) {
            source.sendFailure(Component.literal("An independent settlement cannot be a faction capital."));
            return 0;
        }
        if (rank == PrototypeData.SettlementRank.CAPITAL) {
            PrototypeData data = PrototypeData.get(player.serverLevel());
            data.getSettlements().stream()
                    .filter(other -> other != settlement)
                    .filter(other -> other.getRank() == PrototypeData.SettlementRank.CAPITAL)
                    .filter(other -> other.getFactionTeam().equals(settlement.getFactionTeam()))
                    .forEach(other -> other.setRank(PrototypeData.SettlementRank.TOWN));
        }
        settlement.setRank(rank);
        source.sendSuccess(() -> Component.literal(settlement.getName() + " is now a " + rank.displayName() + "."), true);
        return 1;
    }

    private static int setHousing(CommandSourceStack source, String id, int capacity) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.setHousingCapacity(capacity);
        source.sendSuccess(() -> Component.literal("Housing capacity set to " + capacity + "."), true);
        return 1;
    }

    private static int setStability(CommandSourceStack source, String id, int value) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PrototypeData.SettlementRecord settlement = PrototypeData.get(player(source).serverLevel()).getSettlement(id);
        if (settlement == null) return 0;
        settlement.setStability(value);
        source.sendSuccess(() -> Component.literal("Stability set to " + value + "."), true);
        return 1;
    }

    private static int addWealth(CommandSourceStack source, String id, int value) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PrototypeData.SettlementRecord settlement = PrototypeData.get(player(source).serverLevel()).getSettlement(id);
        if (settlement == null) return 0;
        settlement.addWealth(value, "Administrator");
        source.sendSuccess(() -> Component.literal("Added " + value + " wealth."), true);
        return 1;
    }

    private static int setNotice(CommandSourceStack source, String id, String text) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.setNotice(text);
        source.sendSuccess(() -> Component.literal("Settlement notice updated."), true);
        return 1;
    }

    private static int setMember(CommandSourceStack source, String id, ServerPlayer target, String rawRole) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireOwnerOrAdmin(player, id);
        if (settlement == null) return 0;
        PrototypeData.SettlementRole role;
        try {
            role = PrototypeData.SettlementRole.valueOf(rawRole.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Use co_owner or citizen."));
            return 0;
        }
        if (role != PrototypeData.SettlementRole.CO_OWNER && role != PrototypeData.SettlementRole.CITIZEN) {
            source.sendFailure(Component.literal("Use co_owner or citizen."));
            return 0;
        }
        settlement.setRole(target.getUUID(), role);
        source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " is now " + role.name().toLowerCase(Locale.ROOT) + "."), true);
        return 1;
    }

    private static int removeMember(CommandSourceStack source, String id, ServerPlayer target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireOwnerOrAdmin(player, id);
        if (settlement == null) return 0;
        settlement.setRole(target.getUUID(), PrototypeData.SettlementRole.OUTSIDER);
        source.sendSuccess(() -> Component.literal("Member removed."), true);
        return 1;
    }

    private static int transfer(CommandSourceStack source, String id, ServerPlayer target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireOwnerOrAdmin(player, id);
        if (settlement == null) return 0;
        settlement.setOwner(target.getUUID());
        source.sendSuccess(() -> Component.literal("Ownership transferred to " + target.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static int registerNearestVillager(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(8.0D));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(8.0D)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player,
                eye,
                end,
                searchBox,
                entity -> entity instanceof Villager && !entity.isSpectator(),
                64.0D
        );
        Villager villager = hit != null && hit.getEntity() instanceof Villager found ? found : null;
        if (villager == null) {
            source.sendFailure(Component.literal("Look directly at a villager within 8 blocks."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(PrototypeManager.registerVillager(player, settlement, villager)), true);
        return 1;
    }

    private static int check(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PrototypeData.SettlementRecord settlement = PrototypeData.get(player(source).serverLevel()).getSettlement(id);
        if (settlement == null) return 0;
        int recovered = settlement.runManpowerChecker();
        source.sendSuccess(() -> Component.literal("Checker complete. Recovered: " + recovered + "."), true);
        return 1;
    }

    private static int status(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.SettlementRecord settlement = PrototypeData.get(player.serverLevel()).getSettlement(id);
        if (settlement == null || !PrototypeManager.canView(player, settlement)) return 0;
        source.sendSuccess(() -> Component.literal(
                settlement.getName() + " | " + settlement.getRank().displayName()
                        + " | villagers=" + settlement.getVillagerCount()
                        + " population=" + settlement.getDisplayedPopulation()
                        + " stability=" + settlement.getStability()
                        + " food=" + settlement.getFood()
                        + " wealth=" + settlement.getWealth()
                        + " MP=" + settlement.getAvailableManpower() + "/" + settlement.getMaximumManpower()
        ), false);
        return 1;
    }

    private static int openCharacter(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PrototypeManager.openCharacterSheet(player(source));
        return 1;
    }

    private static int createCharacter(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData.CharacterRecord existing = PrototypeData.get(player.serverLevel()).getCharacter(player.getUUID());
        if (existing != null && existing.isCreated()) {
            source.sendFailure(Component.literal("Your character is already confirmed. Ask an administrator to reset it if a correction is required."));
            PrototypeManager.openCharacterSheet(player);
            return 0;
        }
        PrototypeManager.promptCharacterCreation(player);
        return 1;
    }

    private static int setSeat(CommandSourceStack source, String settlementId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        PrototypeData data = PrototypeData.get(player.serverLevel());
        PrototypeData.SettlementRecord settlement = data.getSettlement(settlementId);
        PrototypeData.CharacterRecord character = data.getCharacter(player.getUUID());
        if (character == null || !character.isCreated()) {
            source.sendFailure(Component.literal("Create your character first."));
            return 0;
        }
        if (settlement == null || !player.getUUID().equals(settlement.getOwnerId())) {
            source.sendFailure(Component.literal("You may only choose a settlement you own."));
            return 0;
        }
        character.setPrimarySeat(settlement.getId());
        data.setDirty();
        source.sendSuccess(() -> Component.literal(settlement.getName() + " is now your primary seat."), false);
        return 1;
    }

    private static int proposeMarriage(CommandSourceStack source, ServerPlayer target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer proposer = player(source);
        PrototypeData characters = PrototypeData.get(proposer.serverLevel());
        PrototypeData.CharacterRecord proposerCharacter = characters.getCharacter(proposer.getUUID());
        PrototypeData.CharacterRecord targetCharacter = characters.getCharacter(target.getUUID());
        if (proposerCharacter == null || !proposerCharacter.isCreated()) {
            source.sendFailure(Component.literal("Create your character before proposing marriage."));
            return 0;
        }
        if (targetCharacter == null || !targetCharacter.isCreated()) {
            source.sendFailure(Component.literal("That player has not created a character."));
            return 0;
        }
        boolean created = CampaignData.get(proposer.serverLevel()).proposePlayerMarriage(proposer.getUUID(), target.getUUID());
        if (!created) { source.sendFailure(Component.literal("Marriage proposal could not be created.")); return 0; }
        target.sendSystemMessage(Component.literal(proposerCharacter.getDisplayName() + " has proposed marriage. Open the Family screen to answer."));
        source.sendSuccess(() -> Component.literal("Marriage proposal sent."), false); return 1;
    }

    private static int resetCharacter(CommandSourceStack source, ServerPlayer target) {
        PrototypeData data = PrototypeData.get(target.serverLevel());
        data.resetCharacter(target.getUUID());
        CampaignData.get(target.serverLevel()).clearFamilyData(target.getUUID());
        PrototypeManager.broadcastProfiles(target.getServer());
        PrototypeManager.promptCharacterCreation(target);
        source.sendSuccess(() -> Component.literal("Character reset for " + target.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static int openCampaign(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CampaignManager.openMain(player(source)); return 1;
    }

    private static int openFaction(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CampaignManager.openFaction(player(source), ""); return 1;
    }

    private static int openMilitary(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CampaignManager.openMilitary(player(source), ""); return 1;
    }

    private static int openDiplomacy(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CampaignManager.openDiplomacy(player(source), ""); return 1;
    }

    private static int openFamily(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CampaignManager.openFamily(player(source), ""); return 1;
    }

    private static int openWorldNews(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CampaignManager.openWorldNews(player(source)); return 1;
    }

    private static int setBannerFromHand(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source); PrototypeData.SettlementRecord settlement = requireManage(player, id); if (settlement == null) return 0;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof net.minecraft.world.item.BannerItem)) { source.sendFailure(Component.literal("Hold a Minecraft banner in your main hand.")); return 0; }
        CampaignData campaign = CampaignData.get(player.serverLevel()); campaign.visualFor(settlement.getId()).setCustomBanner(held); campaign.setDirty();
        source.sendSuccess(() -> Component.literal("Custom banner saved for " + settlement.getName() + "."), true); return 1;
    }

    private static int setBannerMode(CommandSourceStack source, String id, CampaignData.BannerMode mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source); PrototypeData.SettlementRecord settlement = requireManage(player, id); if (settlement == null) return 0;
        CampaignData campaign = CampaignData.get(player.serverLevel());
        if (mode == CampaignData.BannerMode.NONE) campaign.visualFor(settlement.getId()).clearCustomBanner(); else campaign.visualFor(settlement.getId()).setBannerMode(mode);
        campaign.setDirty(); source.sendSuccess(() -> Component.literal("Banner mode set to " + mode.name().toLowerCase(Locale.ROOT) + "."), true); return 1;
    }

    private static int createRoster(CommandSourceStack source, String id, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CampaignData data = CampaignData.get(player(source).serverLevel());
        if (data.createRoster(id, name) == null) { source.sendFailure(Component.literal("Roster ID invalid or already used.")); return 0; }
        source.sendSuccess(() -> Component.literal("Created roster " + id + "."), true); return 1;
    }

    private static int copyRoster(CommandSourceStack source, String sourceId, String newId, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CampaignData data = CampaignData.get(player(source).serverLevel());
        CampaignData.RosterRecord copy = data.copyRoster(sourceId, newId, name);
        if (copy == null) { source.sendFailure(Component.literal("Unknown source roster, invalid new ID, or ID already used.")); return 0; }
        source.sendSuccess(() -> Component.literal("Copied roster " + sourceId + " to " + copy.getId() + "."), true);
        return 1;
    }

    private static int setRosterHand(CommandSourceStack source, String rosterId, String rawType) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source); CampaignData data = CampaignData.get(player.serverLevel()); CampaignData.RosterRecord roster = data.getRoster(rosterId); CampaignData.UnitType type = CampaignData.UnitType.parse(rawType);
        if (roster == null || type == null) { source.sendFailure(Component.literal("Unknown roster or unit type.")); return 0; }
        ItemStack held = player.getMainHandItem(); if (!isRecruitSpawnEgg(player, held)) { source.sendFailure(Component.literal("Hold a configured Villager Recruits spawn egg.")); return 0; }
        roster.setTemplate(type, held); data.setDirty(); source.sendSuccess(() -> Component.literal("Saved " + type.displayName() + " template to " + roster.getName() + "."), true); return 1;
    }

    private static int setDefaultRoster(CommandSourceStack source, String rosterId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CampaignData data = CampaignData.get(player(source).serverLevel()); if (data.getRoster(rosterId) == null) { source.sendFailure(Component.literal("Unknown roster.")); return 0; }
        data.setDefaultRoster(rosterId); source.sendSuccess(() -> Component.literal("Default roster set to " + rosterId + "."), true); return 1;
    }

    private static int assignFactionRoster(CommandSourceStack source, String rosterId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        if (player.getTeam() == null) { source.sendFailure(Component.literal("Join a faction first or provide a faction ID.")); return 0; }
        return assignFactionRoster(source, rosterId, player.getTeam().getName());
    }

    private static int assignFactionRoster(CommandSourceStack source, String rosterId, String factionId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        CampaignData data = CampaignData.get(player.serverLevel());
        if (data.getRoster(rosterId) == null) { source.sendFailure(Component.literal("Unknown roster.")); return 0; }
        if (com.talhanation.recruits.FactionEvents.recruitsFactionManager == null
                || com.talhanation.recruits.FactionEvents.recruitsFactionManager.getFactionByStringID(factionId) == null) {
            source.sendFailure(Component.literal("Unknown Recruits faction: " + factionId)); return 0;
        }
        data.assignFactionRoster(factionId, rosterId);
        source.sendSuccess(() -> Component.literal("Assigned roster " + rosterId + " to " + factionId + "."), true);
        return 1;
    }

    private static int listRosters(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CampaignData data = CampaignData.get(player(source).serverLevel());
        String line = data.getRosters().stream()
                .map(roster -> roster.getId() + " (" + roster.getName() + ": "
                        + (roster.configuredTypes().isEmpty() ? "no templates" : roster.configuredTypes().stream().map(type -> type.name().toLowerCase(Locale.ROOT)).reduce((a, b) -> a + "," + b).orElse("")) + ")")
                .reduce((a, b) -> a + ", " + b).orElse("none");
        source.sendSuccess(() -> Component.literal("Rosters: " + line + " | default: " + data.getDefaultRosterId()), false);
        return 1;
    }

    private static int setSettlementUnitHand(CommandSourceStack source, String settlementId, String rawType) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source); PrototypeData.SettlementRecord settlement = PrototypeData.get(player.serverLevel()).getSettlement(settlementId); CampaignData.UnitType type = CampaignData.UnitType.parse(rawType);
        if (settlement == null || type == null) { source.sendFailure(Component.literal("Unknown settlement or unit type.")); return 0; }
        ItemStack held = player.getMainHandItem(); if (!isRecruitSpawnEgg(player, held)) { source.sendFailure(Component.literal("Hold a configured Villager Recruits spawn egg.")); return 0; }
        CampaignData data = CampaignData.get(player.serverLevel()); data.visualFor(settlement.getId()).setUnitTemplate(type, held); data.setDirty();
        source.sendSuccess(() -> Component.literal("Saved local " + type.displayName() + " override for " + settlement.getName() + "."), true); return 1;
    }


    private static int setMercenaryHand(CommandSourceStack source, int wage, int intervalDays) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String result = PrototypeManager.markHeldTemplateMercenary(player(source), wage, intervalDays);
        source.sendSuccess(() -> Component.literal(result), true);
        return result.startsWith("Held unit") ? 1 : 0;
    }

    private static int clearMercenaryHand(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String result = PrototypeManager.clearHeldTemplateMercenary(player(source));
        source.sendSuccess(() -> Component.literal(result), true);
        return result.startsWith("Held unit") ? 1 : 0;
    }

    private static boolean isRecruitSpawnEgg(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof SpawnEggItem egg)) return false;
        net.minecraft.world.entity.EntityType<?> type = egg.getType(stack.getTag());
        net.minecraft.world.entity.Entity test = type.create(player.serverLevel());
        return test instanceof AbstractRecruitEntity;
    }

    private static int adminStat(CommandSourceStack source, String id, String rawStat, int value, boolean add) {
        PrototypeData.SettlementRecord settlement = PrototypeData.get(source.getLevel()).getSettlement(id);
        if (settlement == null) {
            source.sendFailure(Component.literal("Unknown settlement: " + id));
            return 0;
        }
        String stat = rawStat == null ? "" : rawStat.toLowerCase(Locale.ROOT);
        switch (stat) {
            case "stability" -> { if (add) settlement.changeStability(value); else settlement.setStability(value); }
            case "food" -> { if (add) settlement.changeFood(value); else settlement.setFood(value); }
            case "wealth" -> { if (add) settlement.changeWealth(value); else settlement.setWealth(value); }
            case "housing", "housing_capacity" -> { if (add) settlement.changeHousingCapacity(value); else settlement.setHousingCapacity(value); }
            case "maximum_manpower", "max_manpower", "manpower_max" -> { if (add) settlement.changeMaximumManpower(value); else settlement.setMaximumManpower(value); }
            case "available_manpower", "manpower", "manpower_available" -> { if (add) settlement.changeAvailableManpower(value); else settlement.setAvailableManpower(value); }
            case "contracts", "pending_contracts" -> { if (add) settlement.changePendingContracts(value); else settlement.setPendingContracts(value); }
            default -> {
                source.sendFailure(Component.literal("Unknown stat. Use stability, food, wealth, housing, maximum_manpower, available_manpower, or contracts."));
                return 0;
            }
        }
        source.sendSuccess(() -> Component.literal((add ? "Adjusted " : "Set ") + stat + " for " + settlement.getName()
                + ". Current values: stability=" + settlement.getStability() + ", food=" + settlement.getFood()
                + ", wealth=" + settlement.getWealth() + ", housing=" + settlement.getHousingCapacity()
                + ", manpower=" + settlement.getAvailableManpower() + "/" + settlement.getMaximumManpower()
                + ", contracts=" + settlement.getPendingContracts() + "."), true);
        return 1;
    }

    private static int createFaction(CommandSourceStack source, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        String result = CampaignManager.createFaction(player, name);
        if (player.getTeam() == null) source.sendFailure(Component.literal(result));
        else source.sendSuccess(() -> Component.literal(result), true);
        return player.getTeam() == null ? 0 : 1;
    }

    private static int adminCreateFaction(CommandSourceStack source, ServerPlayer target, String name) {
        String result = CampaignManager.createFaction(target, name);
        if (target.getTeam() == null) source.sendFailure(Component.literal(result));
        else source.sendSuccess(() -> Component.literal(result), true);
        return target.getTeam() == null ? 0 : 1;
    }

    private static int giveSilver(CommandSourceStack source, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        ItemStack stack = new ItemStack(ModItems.SILVER_COIN.get(), amount);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        return 1;
    }

    private static PrototypeData.SettlementRecord requireManage(ServerPlayer player, String id) {
        PrototypeData.SettlementRecord settlement = PrototypeData.get(player.serverLevel()).getSettlement(id);
        if (settlement == null) {
            player.sendSystemMessage(Component.literal("Unknown settlement."));
            return null;
        }
        if (!PrototypeManager.canManage(player, settlement)) {
            player.sendSystemMessage(Component.literal("You cannot manage that settlement."));
            return null;
        }
        return settlement;
    }

    private static PrototypeData.SettlementRecord requireOwnerOrAdmin(ServerPlayer player, String id) {
        PrototypeData.SettlementRecord settlement = PrototypeData.get(player.serverLevel()).getSettlement(id);
        if (settlement == null) return null;
        if (!player.createCommandSourceStack().hasPermission(2) && settlement.getRole(player.getUUID()) != PrototypeData.SettlementRole.OWNER) {
            player.sendSystemMessage(Component.literal("Only the owner or an administrator may do that."));
            return null;
        }
        return settlement;
    }
}
