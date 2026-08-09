package ee.lastpagans.settlements;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsClaim;
import ee.lastpagans.settlements.item.ModItems;
import net.minecraft.ChatFormatting;
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

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SettlementCommands {
    private SettlementCommands() {
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
                .then(Commands.literal("rank").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .executes(context -> setRank(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "rank")
                                        )))))
                .then(Commands.literal("housing").requires(source -> source.hasPermission(2))
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
                .then(Commands.literal("housingarea")
                        .then(Commands.literal("pos1").then(Commands.argument("settlement", StringArgumentType.word())
                                .executes(context -> regionPos1(context.getSource(), StringArgumentType.getString(context, "settlement"), "housing"))))
                        .then(Commands.literal("pos2").then(Commands.argument("settlement", StringArgumentType.word())
                                .executes(context -> regionPos2(context.getSource(), StringArgumentType.getString(context, "settlement"), "housing"))))
                        .then(Commands.literal("clear").then(Commands.argument("settlement", StringArgumentType.word())
                                .executes(context -> clearHousingAreas(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                        .then(Commands.literal("recalculate").then(Commands.argument("settlement", StringArgumentType.word())
                                .executes(context -> recalculateHousing(context.getSource(), StringArgumentType.getString(context, "settlement"))))))
                .then(Commands.literal("musterarea")
                        .then(Commands.literal("pos1").then(Commands.argument("settlement", StringArgumentType.word())
                                .executes(context -> regionPos1(context.getSource(), StringArgumentType.getString(context, "settlement"), "muster"))))
                        .then(Commands.literal("pos2").then(Commands.argument("settlement", StringArgumentType.word())
                                .executes(context -> regionPos2(context.getSource(), StringArgumentType.getString(context, "settlement"), "muster")))))
                .then(Commands.literal("prayersite")
                        .then(Commands.literal("pos1").then(Commands.argument("settlement", StringArgumentType.word())
                                .executes(context -> regionPos1(context.getSource(), StringArgumentType.getString(context, "settlement"), "prayer"))))
                        .then(Commands.literal("pos2").then(Commands.argument("settlement", StringArgumentType.word())
                                .executes(context -> regionPos2(context.getSource(), StringArgumentType.getString(context, "settlement"), "prayer")))))
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
                                                .then(Commands.argument("team_color", StringArgumentType.word())
                                                        .then(Commands.argument("unit_color", IntegerArgumentType.integer(0, 24))
                                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                        .executes(context -> adminCreateFaction(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                                                StringArgumentType.getString(context, "team_color"), IntegerArgumentType.getInteger(context, "unit_color"),
                                                                                StringArgumentType.getString(context, "name"))))))))
                                .then(Commands.literal("wealth")
                                        .then(Commands.literal("set").then(Commands.argument("faction", StringArgumentType.word())
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100000000))
                                                        .executes(context -> adminFactionWealth(context.getSource(), StringArgumentType.getString(context, "faction"), IntegerArgumentType.getInteger(context, "value"), false)))))
                                        .then(Commands.literal("add").then(Commands.argument("faction", StringArgumentType.word())
                                                .then(Commands.argument("value", IntegerArgumentType.integer(-100000000, 100000000))
                                                        .executes(context -> adminFactionWealth(context.getSource(), StringArgumentType.getString(context, "faction"), IntegerArgumentType.getInteger(context, "value"), true)))))))
                        .then(Commands.literal("reset")
                                .then(Commands.literal("character").then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> warnResetCharacter(context.getSource(), EntityArgument.getPlayer(context, "player")))
                                        .then(Commands.literal("confirm").executes(context -> adminResetCharacter(context.getSource(), EntityArgument.getPlayer(context, "player"))))))
                                .then(Commands.literal("player").then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.literal("confirm").executes(context -> adminResetPlayer(context.getSource(), EntityArgument.getPlayer(context, "player"))))))
                                .then(Commands.literal("traits").then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminResetTraits(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("marriage").then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminResetMarriage(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("religion").then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminResetReligion(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("levy").then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminResetLevy(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("cooldowns").then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminResetCooldowns(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("faction_rank").then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminResetFactionRank(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("selection").then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminResetSelection(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("settlement").then(Commands.argument("settlement", StringArgumentType.word())
                                        .executes(context -> warnResetSettlement(context.getSource(), StringArgumentType.getString(context, "settlement")))
                                        .then(Commands.literal("confirm").executes(context -> adminResetSettlement(context.getSource(), StringArgumentType.getString(context, "settlement")))))))
                        .then(Commands.literal("setowner").then(Commands.argument("settlement", StringArgumentType.word())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminSetOwner(context.getSource(), StringArgumentType.getString(context, "settlement"), EntityArgument.getPlayer(context, "player"))))))
                        .then(Commands.literal("addcoowner").then(Commands.argument("settlement", StringArgumentType.word())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminSetCoOwner(context.getSource(), StringArgumentType.getString(context, "settlement"), EntityArgument.getPlayer(context, "player"), true)))))
                        .then(Commands.literal("removecoowner").then(Commands.argument("settlement", StringArgumentType.word())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminSetCoOwner(context.getSource(), StringArgumentType.getString(context, "settlement"), EntityArgument.getPlayer(context, "player"), false)))))
                        .then(Commands.literal("settier").then(Commands.argument("settlement", StringArgumentType.word())
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .executes(context -> adminSetTier(context.getSource(), StringArgumentType.getString(context, "settlement"), StringArgumentType.getString(context, "tier"))))))
                        .then(Commands.literal("clearclaims").then(Commands.argument("settlement", StringArgumentType.word())
                                .executes(context -> adminClearClaims(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                        .then(Commands.literal("clearhousing").then(Commands.argument("settlement", StringArgumentType.word())
                                .executes(context -> adminClearHousing(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                        .then(Commands.literal("cleararmydata").then(Commands.argument("settlement", StringArgumentType.word())
                                .executes(context -> adminClearArmyData(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                        .then(Commands.literal("clearmodifiers").then(Commands.argument("settlement", StringArgumentType.word())
                                .executes(context -> adminDebugModifierClear(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                        .then(Commands.literal("recalculate").then(Commands.argument("settlement", StringArgumentType.word())
                                .executes(context -> adminRecalculateSettlement(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                        .then(Commands.literal("inspect")
                                .then(Commands.literal("player").then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminInspectPlayer(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("settlement").then(Commands.argument("settlement", StringArgumentType.word())
                                        .executes(context -> adminInspectSettlement(context.getSource(), StringArgumentType.getString(context, "settlement"))))))
                        .then(Commands.literal("protect")
                                .then(Commands.literal("settlement").then(Commands.argument("settlement", StringArgumentType.word())
                                        .then(Commands.argument("protected", BoolArgumentType.bool())
                                                .executes(context -> adminProtectSettlement(context.getSource(), StringArgumentType.getString(context, "settlement"), BoolArgumentType.getBool(context, "protected"))))))
                                .then(Commands.literal("faction").then(Commands.argument("faction", StringArgumentType.word())
                                        .then(Commands.argument("protected", BoolArgumentType.bool())
                                                .executes(context -> adminProtectFaction(context.getSource(), StringArgumentType.getString(context, "faction"), BoolArgumentType.getBool(context, "protected")))))))
                        .then(Commands.literal("holysite")
                                .then(Commands.literal("pos1").executes(context -> adminHolySitePos1(context.getSource())))
                                .then(Commands.literal("add").then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("faith", StringArgumentType.greedyString())
                                                .executes(context -> adminHolySiteAdd(context.getSource(), StringArgumentType.getString(context, "id"), StringArgumentType.getString(context, "faith"))))))
                                .then(Commands.literal("remove").then(Commands.argument("id", StringArgumentType.word())
                                        .executes(context -> adminHolySiteRemove(context.getSource(), StringArgumentType.getString(context, "id"))))))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("advance").then(Commands.argument("settlement", StringArgumentType.word())
                                        .then(Commands.argument("days", IntegerArgumentType.integer(1, 3650))
                                                .executes(context -> adminDebugAdvance(context.getSource(), StringArgumentType.getString(context, "settlement"), IntegerArgumentType.getInteger(context, "days")))))
                                .then(Commands.literal("clear_time").then(Commands.argument("settlement", StringArgumentType.word())
                                        .executes(context -> adminDebugClearTime(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                                .then(Commands.literal("modifier")
                                        .then(Commands.literal("add").then(Commands.argument("settlement", StringArgumentType.word())
                                                .then(Commands.argument("modifier", StringArgumentType.word())
                                                        .then(Commands.argument("duration_minutes", IntegerArgumentType.integer(0, 1000000))
                                                                .executes(context -> adminDebugModifierAdd(context.getSource(), StringArgumentType.getString(context, "settlement"), StringArgumentType.getString(context, "modifier"), IntegerArgumentType.getInteger(context, "duration_minutes")))))))
                                        .then(Commands.literal("remove").then(Commands.argument("settlement", StringArgumentType.word())
                                                .then(Commands.argument("modifier", StringArgumentType.word())
                                                        .executes(context -> adminDebugModifierRemove(context.getSource(), StringArgumentType.getString(context, "settlement"), StringArgumentType.getString(context, "modifier"))))))
                                        .then(Commands.literal("clear").then(Commands.argument("settlement", StringArgumentType.word())
                                                .executes(context -> adminDebugModifierClear(context.getSource(), StringArgumentType.getString(context, "settlement"))))))
                                .then(Commands.literal("levy")
                                        .then(Commands.literal("ready").then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> adminResetCooldowns(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                                        .then(Commands.literal("age").then(Commands.argument("settlement", StringArgumentType.word())
                                                .then(Commands.argument("days", IntegerArgumentType.integer(1, 3650))
                                                        .executes(context -> adminDebugAdvance(context.getSource(), StringArgumentType.getString(context, "settlement"), IntegerArgumentType.getInteger(context, "days")))))))
                                .then(Commands.literal("devotion").then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                .executes(context -> adminDebugDevotion(context.getSource(), EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "value"))))))
                                .then(Commands.literal("prosperity").then(Commands.argument("settlement", StringArgumentType.word())
                                        .executes(context -> adminDebugProsperity(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                                .then(Commands.literal("manpower").then(Commands.argument("settlement", StringArgumentType.word())
                                        .executes(context -> adminDebugManpower(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                                .then(Commands.literal("war_score").then(Commands.argument("war", StringArgumentType.word())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                                                .executes(context -> adminDebugWarScore(context.getSource(), StringArgumentType.getString(context, "war"), IntegerArgumentType.getInteger(context, "value"))))))
                                .then(Commands.literal("capitulate").then(Commands.argument("war", StringArgumentType.word())
                                        .then(Commands.argument("side", StringArgumentType.word())
                                                .executes(context -> adminDebugCapitulate(context.getSource(), StringArgumentType.getString(context, "war"), StringArgumentType.getString(context, "side"))))))
                                .then(Commands.literal("event")
                                        .then(Commands.literal("trigger").then(Commands.argument("settlement", StringArgumentType.word())
                                                .then(Commands.argument("event", StringArgumentType.word())
                                                        .then(Commands.argument("duration_minutes", IntegerArgumentType.integer(1, 1000000))
                                                                .executes(context -> adminDebugEvent(context.getSource(), StringArgumentType.getString(context, "settlement"), StringArgumentType.getString(context, "event"), IntegerArgumentType.getInteger(context, "duration_minutes"), true))))))
                                        .then(Commands.literal("end").then(Commands.argument("settlement", StringArgumentType.word())
                                                .then(Commands.argument("event", StringArgumentType.word())
                                                        .executes(context -> adminDebugEvent(context.getSource(), StringArgumentType.getString(context, "settlement"), StringArgumentType.getString(context, "event"), 0, false))))))
                                .then(Commands.literal("spouse_refresh").then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminDebugSpouseRefresh(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("mercenary_payment").then(Commands.argument("faction", StringArgumentType.word())
                                        .executes(context -> adminDebugMercenaryPayment(context.getSource(), StringArgumentType.getString(context, "faction")))))
                                .then(Commands.literal("inspect").then(Commands.argument("settlement", StringArgumentType.word())
                                        .executes(context -> adminInspectSettlement(context.getSource(), StringArgumentType.getString(context, "settlement"))))))
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
        )));

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
                                .executes(context -> warnResetCharacter(context.getSource(), EntityArgument.getPlayer(context, "player")))
                                .then(Commands.literal("confirm").executes(context -> resetCharacter(context.getSource(), EntityArgument.getPlayer(context, "player"))))))
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

        dispatcher.register(Commands.literal("settlementtestcurrency").requires(source -> source.hasPermission(2))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                        .executes(context -> giveCurrency(context.getSource(), IntegerArgumentType.getInteger(context, "amount")))));
    }

    private static ServerPlayer player(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return source.getPlayerOrException();
    }

    private static int openSelected(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SettlementManager.open(player(source));
        return 1;
    }

    private static int open(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!SettlementManager.open(player(source), id)) source.sendFailure(Component.literal("Unknown settlement: " + id));
        return 1;
    }

    private static int list(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData data = SettlementData.get(player.serverLevel());
        String line = data.getSettlements().stream()
                .filter(settlement -> SettlementManager.canView(player, settlement))
                .map(settlement -> settlement.getId() + " (" + settlement.getName() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("None");
        source.sendSuccess(() -> Component.literal("Settlements: " + line), false);
        return 1;
    }

    private static int create(CommandSourceStack source, String id, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData data = SettlementData.get(player.serverLevel());
        if (player.getTeam() == null && data.getSettlements().stream().filter(value -> player.getUUID().equals(value.getOwnerId()) && value.getFactionTeam().isBlank()).count() >= 2) {
            source.sendFailure(Component.literal("Independent rulers may own at most two settlements. Create a faction before founding another."));
            return 0;
        }
        SettlementData.SettlementRecord settlement = data.createSettlement(id, name, player.getUUID());
        if (settlement == null) {
            source.sendFailure(Component.literal("That settlement ID is invalid or already used."));
            return 0;
        }
        settlement.setTownSquare(player.serverLevel().dimension().location().toString(), player.blockPosition());
        Team team = player.getTeam();
        if (team != null) settlement.setFactionTeam(team.getName());
        linkClaimAtPlayer(player, settlement, false);
        SettlementManager.scheduleFirstSettlers(player, settlement);
        data.selectSettlement(player.getUUID(), settlement.getId());
        source.sendSuccess(() -> Component.literal("Founded " + settlement.getName() + " as a Village."), true);
        return 1;
    }

    private static int delete(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SettlementData data = SettlementData.get(player(source).serverLevel());
        if (!data.deleteSettlement(id)) {
            source.sendFailure(Component.literal("Unknown settlement."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Settlement deleted."), true);
        return 1;
    }

    private static int rename(CommandSourceStack source, String id, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.rename(name);
        source.sendSuccess(() -> Component.literal("Renamed settlement to " + settlement.getName() + "."), true);
        return 1;
    }

    private static int setTownSquare(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.setTownSquare(player.serverLevel().dimension().location().toString(), player.blockPosition());
        source.sendSuccess(() -> Component.literal("Town square set to your current position."), true);
        return 1;
    }

    private static int setMusterGround(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.setMusterGround(player.serverLevel().dimension().location().toString(), player.blockPosition());
        source.sendSuccess(() -> Component.literal("Muster Ground set to your current position. New troops will form in safe rows around it."), true);
        return 1;
    }

    private static int setBarracks(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.setBarracks(player.serverLevel().dimension().location().toString(), player.blockPosition());
        source.sendSuccess(() -> Component.literal("Barracks centre set to your current position."), true);
        return 1;
    }

    private static int setBarracksChest(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = requireManage(player, id);
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
        SettlementData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.setBarracksSettings(radius, capacity);
        source.sendSuccess(() -> Component.literal("Barracks radius set to " + radius + " and capacity to " + capacity + "."), true);
        return 1;
    }

    private static int setFaction(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = requireManage(player, id);
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
        SettlementData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.setFactionTeam("");
        if (settlement.getRank() == SettlementData.SettlementRank.CAPITAL) settlement.setRank(SettlementData.SettlementRank.TOWN);
        source.sendSuccess(() -> Component.literal("Settlement is now independent."), true);
        return 1;
    }

    private static int linkClaim(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        return linkClaimAtPlayer(player, settlement, true) ? 1 : 0;
    }

    static boolean linkClaimAtPlayer(ServerPlayer player, SettlementData.SettlementRecord settlement, boolean notify) {
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
        SettlementData data = SettlementData.get(player.serverLevel());
        SettlementData.SettlementRecord existing = data.findSettlementByClaim(dimension, claim.getUUID());
        if (existing != null && existing != settlement) {
            if (notify) player.sendSystemMessage(Component.literal("That claim is already linked to " + existing.getName() + "."));
            return false;
        }
        boolean added = settlement.linkClaim(dimension, claim.getUUID(), claim.getName());
        if (claim.getOwnerFactionStringID() != null && settlement.getFactionTeam().isBlank()) {
            settlement.setFactionTeam(claim.getOwnerFactionStringID());
        }
        if (added && CampaignSystemsData.get(player.serverLevel()).settlementProtected(settlement.getId())) {
            // Claims linked after an admin settlement/faction was protected must inherit
            // Recruits' admin-claim flag too, otherwise the new district can still be sieged.
            claim.setAdminClaim(true);
            ClaimEvents.recruitsClaimManager.addOrUpdateClaim(player.serverLevel(), claim);
            ClaimEvents.recruitsClaimManager.broadcastClaimsToAll(player.serverLevel());
        }
        if (notify) player.sendSystemMessage(Component.literal((added ? "Linked claim " : "Claim was already linked: ")
                + claim.getName() + ". Total claims: " + settlement.getClaimCount() + "."));
        return true;
    }

    private static int unlinkClaim(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = requireManage(player, id);
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
        SettlementData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        int count = settlement.getClaimCount();
        settlement.clearClaimLink();
        source.sendSuccess(() -> Component.literal("Removed all " + count + " linked claim(s)."), true);
        return 1;
    }

    private static int setRank(CommandSourceStack source, String id, String rawRank) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        SettlementData.SettlementRank rank;
        try {
            rank = SettlementData.SettlementRank.valueOf(rawRank.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Use village, town, or capital."));
            return 0;
        }
        if (rank == SettlementData.SettlementRank.CAPITAL && settlement.getFactionTeam().isBlank()) {
            source.sendFailure(Component.literal("An independent settlement cannot be a faction capital."));
            return 0;
        }
        if (rank == SettlementData.SettlementRank.CAPITAL) {
            SettlementData data = SettlementData.get(player.serverLevel());
            data.getSettlements().stream()
                    .filter(other -> other != settlement)
                    .filter(other -> other.getRank() == SettlementData.SettlementRank.CAPITAL)
                    .filter(other -> other.getFactionTeam().equals(settlement.getFactionTeam()))
                    .forEach(other -> other.setRank(SettlementData.SettlementRank.TOWN));
        }
        settlement.setRank(rank);
        source.sendSuccess(() -> Component.literal(settlement.getName() + " is now a " + rank.displayName() + "."), true);
        return 1;
    }

    private static int setHousing(CommandSourceStack source, String id, int capacity) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.setHousingCapacity(capacity);
        source.sendSuccess(() -> Component.literal("Housing capacity set to " + capacity + "."), true);
        return 1;
    }

    private static int setStability(CommandSourceStack source, String id, int value) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SettlementData.SettlementRecord settlement = SettlementData.get(player(source).serverLevel()).getSettlement(id);
        if (settlement == null) return 0;
        settlement.setStability(value);
        source.sendSuccess(() -> Component.literal("Stability set to " + value + "."), true);
        return 1;
    }

    private static int addWealth(CommandSourceStack source, String id, int value) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SettlementData.SettlementRecord settlement = SettlementData.get(player(source).serverLevel()).getSettlement(id);
        if (settlement == null) return 0;
        settlement.addWealth(value, "Administrator");
        source.sendSuccess(() -> Component.literal("Added " + value + " wealth."), true);
        return 1;
    }

    private static int setNotice(CommandSourceStack source, String id, String text) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = requireManage(player, id);
        if (settlement == null) return 0;
        settlement.setNotice(text);
        source.sendSuccess(() -> Component.literal("Settlement notice updated."), true);
        return 1;
    }

    private static int setMember(CommandSourceStack source, String id, ServerPlayer target, String rawRole) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = requireOwnerOrAdmin(player, id);
        if (settlement == null) return 0;
        SettlementData.SettlementRole role;
        try {
            role = SettlementData.SettlementRole.valueOf(rawRole.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Use co_owner or citizen."));
            return 0;
        }
        if (role != SettlementData.SettlementRole.CO_OWNER && role != SettlementData.SettlementRole.CITIZEN) {
            source.sendFailure(Component.literal("Use co_owner or citizen."));
            return 0;
        }
        settlement.setRole(target.getUUID(), role);
        source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " is now " + role.name().toLowerCase(Locale.ROOT) + "."), true);
        return 1;
    }

    private static int removeMember(CommandSourceStack source, String id, ServerPlayer target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = requireOwnerOrAdmin(player, id);
        if (settlement == null) return 0;
        settlement.setRole(target.getUUID(), SettlementData.SettlementRole.OUTSIDER);
        source.sendSuccess(() -> Component.literal("Member removed."), true);
        return 1;
    }

    private static int transfer(CommandSourceStack source, String id, ServerPlayer target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = requireOwnerOrAdmin(player, id);
        if (settlement == null) return 0;
        SettlementData data = SettlementData.get(player.serverLevel());
        String settlementFaction = settlement.getFactionTeam() == null ? "" : settlement.getFactionTeam();
        String targetFaction = target.getTeam() == null ? "" : target.getTeam().getName();
        boolean adminOverride = source.hasPermission(2);
        if (!adminOverride && !settlementFaction.equals(targetFaction)) {
            source.sendFailure(Component.literal("Settlement ownership can only be transferred within its current polity. Use diplomacy for cross-polity territorial transfers."));
            return 0;
        }
        if (targetFaction.isBlank() && data.getSettlements().stream().filter(value -> target.getUUID().equals(value.getOwnerId()) && value.getFactionTeam().isBlank() && value != settlement).count() >= 2) {
            source.sendFailure(Component.literal(target.getGameProfile().getName() + " already owns the maximum two independent settlements."));
            return 0;
        }
        settlement.setOwner(target.getUUID());
        source.sendSuccess(() -> Component.literal("Ownership transferred to " + target.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static int registerNearestVillager(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = requireManage(player, id);
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
        source.sendSuccess(() -> Component.literal(SettlementManager.registerVillager(player, settlement, villager)), true);
        return 1;
    }

    private static int check(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SettlementData.SettlementRecord settlement = SettlementData.get(player(source).serverLevel()).getSettlement(id);
        if (settlement == null) return 0;
        int recovered = settlement.runManpowerChecker();
        source.sendSuccess(() -> Component.literal("Checker complete. Recovered: " + recovered + "."), true);
        return 1;
    }

    private static int status(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.SettlementRecord settlement = SettlementData.get(player.serverLevel()).getSettlement(id);
        if (settlement == null || !SettlementManager.canView(player, settlement)) return 0;
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
        SettlementManager.openCharacterSheet(player(source));
        return 1;
    }

    private static int createCharacter(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData.CharacterRecord existing = SettlementData.get(player.serverLevel()).getCharacter(player.getUUID());
        if (existing != null && existing.isCreated()) {
            source.sendFailure(Component.literal("Your character is already confirmed. Ask an administrator to reset it if a correction is required."));
            SettlementManager.openCharacterSheet(player);
            return 0;
        }
        SettlementManager.promptCharacterCreation(player);
        return 1;
    }

    private static int setSeat(CommandSourceStack source, String settlementId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SettlementData data = SettlementData.get(player.serverLevel());
        SettlementData.SettlementRecord settlement = data.getSettlement(settlementId);
        SettlementData.CharacterRecord character = data.getCharacter(player.getUUID());
        if (character == null || !character.isCreated()) {
            source.sendFailure(Component.literal("Create your character first."));
            return 0;
        }
        if (settlement == null || !player.getUUID().equals(settlement.getOwnerId())) {
            source.sendFailure(Component.literal("You may only choose a settlement you own."));
            return 0;
        }
        CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
        if (!source.hasPermission(2) && (extra.settlementProtected(settlement.getId()) || extra.factionProtected(settlement.getFactionTeam()))) {
            source.sendFailure(Component.literal("Protected admin settlements cannot be selected as a normal player's Main Seat."));
            return 0;
        }
        character.setPrimarySeat(settlement.getId());
        data.setDirty();
        source.sendSuccess(() -> Component.literal(settlement.getName() + " is now your primary seat."), false);
        return 1;
    }

    private static int proposeMarriage(CommandSourceStack source, ServerPlayer target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer proposer = player(source);
        SettlementData characters = SettlementData.get(proposer.serverLevel());
        SettlementData.CharacterRecord proposerCharacter = characters.getCharacter(proposer.getUUID());
        SettlementData.CharacterRecord targetCharacter = characters.getCharacter(target.getUUID());
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

    private static int warnResetCharacter(CommandSourceStack source, ServerPlayer target) {
        source.sendFailure(Component.literal("WARNING: This will reset " + target.getGameProfile().getName()
                + "'s character identity, traits, faith, marriage, devotion, divorce history, candidate history and character-specific levy cooldown. "
                + "Their settlements and faction membership will remain. Add 'confirm' to proceed."));
        return 0;
    }

    private static int resetCharacter(CommandSourceStack source, ServerPlayer target) {
        SettlementData data = SettlementData.get(target.serverLevel());
        data.resetCharacter(target.getUUID());
        CampaignData.get(target.serverLevel()).clearFamilyData(target.getUUID());
        CampaignSystemsData.get(target.serverLevel()).resetCharacterData(target.getUUID());
        SettlementManager.broadcastProfiles(target.getServer());
        SettlementManager.promptCharacterCreation(target);
        source.sendSuccess(() -> Component.literal("Character reset for " + target.getGameProfile().getName()
                + ". Settlements and faction membership were preserved."), true);
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
        ServerPlayer player = player(source); SettlementData.SettlementRecord settlement = requireManage(player, id); if (settlement == null) return 0;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof net.minecraft.world.item.BannerItem)) { source.sendFailure(Component.literal("Hold a Minecraft banner in your main hand.")); return 0; }
        CampaignData campaign = CampaignData.get(player.serverLevel()); campaign.visualFor(settlement.getId()).setCustomBanner(held); campaign.setDirty();
        source.sendSuccess(() -> Component.literal("Custom banner saved for " + settlement.getName() + "."), true); return 1;
    }

    private static int setBannerMode(CommandSourceStack source, String id, CampaignData.BannerMode mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source); SettlementData.SettlementRecord settlement = requireManage(player, id); if (settlement == null) return 0;
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
        ServerPlayer player = player(source); SettlementData.SettlementRecord settlement = SettlementData.get(player.serverLevel()).getSettlement(settlementId); CampaignData.UnitType type = CampaignData.UnitType.parse(rawType);
        if (settlement == null || type == null) { source.sendFailure(Component.literal("Unknown settlement or unit type.")); return 0; }
        ItemStack held = player.getMainHandItem(); if (!isRecruitSpawnEgg(player, held)) { source.sendFailure(Component.literal("Hold a configured Villager Recruits spawn egg.")); return 0; }
        CampaignData data = CampaignData.get(player.serverLevel()); data.visualFor(settlement.getId()).setUnitTemplate(type, held); data.setDirty();
        source.sendSuccess(() -> Component.literal("Saved local " + type.displayName() + " override for " + settlement.getName() + "."), true); return 1;
    }


    private static int setMercenaryHand(CommandSourceStack source, int wage, int intervalDays) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String result = SettlementManager.markHeldTemplateMercenary(player(source), wage, intervalDays);
        source.sendSuccess(() -> Component.literal(result), true);
        return result.startsWith("Held unit") ? 1 : 0;
    }

    private static int clearMercenaryHand(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String result = SettlementManager.clearHeldTemplateMercenary(player(source));
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
        SettlementData.SettlementRecord settlement = SettlementData.get(source.getLevel()).getSettlement(id);
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
            case "prosperity" -> { CampaignSystemsData extra = CampaignSystemsData.get(source.getLevel()); extra.setProsperity(settlement.getId(), add ? extra.prosperity(settlement.getId()) + value : value); }
            case "levy_tradition", "tradition" -> { CampaignSystemsData extra = CampaignSystemsData.get(source.getLevel()); extra.setLevyTradition(settlement.getId(), add ? extra.levyTradition(settlement.getId()) + value : value); }
            case "foreign_manpower", "foreign_mp" -> { CampaignSystemsData extra = CampaignSystemsData.get(source.getLevel()); extra.setForeignManpower(settlement.getId(), add ? extra.foreignManpower(settlement.getId()) + value : value); if (extra.foreignManpower(settlement.getId()) > 0) extra.addModifier(settlement.getId(), CampaignSystemsManager.standardModifier(source.getLevel(), settlement.getId(), "foreign_soldiers", 0)); else extra.removeModifier(settlement.getId(), "foreign_soldiers"); }
            default -> {
                source.sendFailure(Component.literal("Unknown stat. Use stability, food, wealth, prosperity, levy_tradition, housing, maximum_manpower, available_manpower, foreign_manpower, or contracts."));
                return 0;
            }
        }
        source.sendSuccess(() -> Component.literal((add ? "Adjusted " : "Set ") + stat + " for " + settlement.getName()
                + ". Current values: stability=" + settlement.getStability() + ", food=" + settlement.getFood()
                + ", wealth=" + settlement.getWealth() + ", housing=" + settlement.getHousingCapacity()
                + ", manpower=" + settlement.getAvailableManpower() + "/" + settlement.getMaximumManpower()
                + ", foreignMP=" + CampaignSystemsData.get(source.getLevel()).foreignManpower(settlement.getId())
                + ", levyTradition=" + CampaignSystemsData.get(source.getLevel()).levyTradition(settlement.getId())
                + ", contracts=" + settlement.getPendingContracts() + "."), true);
        return 1;
    }

    private static int adminFactionWealth(CommandSourceStack source, String faction, int value, boolean add) {
        CampaignSystemsData extra = CampaignSystemsData.get(source.getLevel());
        if (faction == null || faction.isBlank()) { source.sendFailure(Component.literal("Faction ID cannot be blank.")); return 0; }
        if (add) extra.changeFactionWealth(faction, value); else extra.setFactionWealth(faction, value);
        source.sendSuccess(() -> Component.literal("Faction treasury for " + faction + " is now " + extra.factionWealth(faction) + "."), true);
        return 1;
    }

    private static int createFaction(CommandSourceStack source, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        // The legacy command cannot express Recruits Team Color + Unit Color and previously
        // created white/default factions. Route players to the authoritative creator instead.
        CampaignManager.openFaction(player, "Faction creation uses the GUI so Team Color, Unit Color and the required patterned banner cannot be bypassed.");
        source.sendSuccess(() -> Component.literal("Open the Faction screen and create the faction there. The typed name was not submitted."), false);
        return 1;
    }

    private static int adminCreateFaction(CommandSourceStack source, ServerPlayer target, String teamColorRaw, int unitColor, String name) {
        ChatFormatting teamColor;
        try {
            teamColor = ChatFormatting.valueOf(teamColorRaw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Unknown Team Color. Use a Minecraft color name such as BLUE, DARK_BLUE, RED, GOLD, or WHITE."));
            return 0;
        }
        if (!teamColor.isColor()) {
            source.sendFailure(Component.literal("That formatting value is not a color."));
            return 0;
        }
        String result = CampaignManager.createFaction(target, name, teamColor, (byte)Math.max(0, Math.min(24, unitColor)));
        if (target.getTeam() == null) source.sendFailure(Component.literal(result));
        else source.sendSuccess(() -> Component.literal(result), true);
        return target.getTeam() == null ? 0 : 1;
    }

    private static int giveCurrency(CommandSourceStack source, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        ItemStack stack = new ItemStack(SettlementManager.getCurrencyItem(), amount);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        return 1;
    }


    private static int adminResetCharacter(CommandSourceStack source, ServerPlayer target) {
        return resetCharacter(source, target);
    }

    private static int adminResetPlayer(CommandSourceStack source, ServerPlayer target) {
        SettlementData settlements = SettlementData.get(target.serverLevel());
        int retiredLevies = CampaignSystemsManager.forceRetirePlayerLevies(target.serverLevel(), target.getUUID());
        int affectedSettlements = settlements.resetPlayerData(target.getUUID());
        CampaignData.get(target.serverLevel()).resetPlayerData(target.getUUID());
        CampaignSystemsData.get(target.serverLevel()).resetPlayerData(target.getUUID());
        SettlementManager.broadcastProfiles(target.getServer());
        SettlementManager.promptCharacterCreation(target);
        source.sendSuccess(() -> Component.literal("FULL Settlements data reset for " + target.getGameProfile().getName() + ". Affected settlement memberships/ownership references: " + affectedSettlements + ". Retired levy entities: " + retiredLevies + "."), true);
        return 1;
    }

    private static int adminResetTraits(CommandSourceStack source, ServerPlayer target) {
        SettlementData.get(target.serverLevel()).resetTraits(target.getUUID());
        source.sendSuccess(() -> Component.literal("Traits reset for " + target.getGameProfile().getName() + ". They will be prompted to choose traits again."), true); return 1;
    }
    private static int adminResetMarriage(CommandSourceStack source, ServerPlayer target) {
        CampaignData.get(target.serverLevel()).resetMarriage(target.getUUID()); CampaignSystemsData.get(target.serverLevel()).resetCandidateHistory(target.getUUID());
        source.sendSuccess(() -> Component.literal("Marriage and candidate history reset for " + target.getGameProfile().getName() + "."), true); return 1;
    }
    private static int adminResetReligion(CommandSourceStack source, ServerPlayer target) {
        CampaignSystemsData.get(target.serverLevel()).resetReligion(target.getUUID()); source.sendSuccess(() -> Component.literal("Devotion/religion progression reset for " + target.getGameProfile().getName() + ". Character faith itself was preserved."), true); return 1;
    }
    private static int adminResetLevy(CommandSourceStack source, ServerPlayer target) {
        int retired = CampaignSystemsManager.forceRetirePlayerLevies(target.serverLevel(), target.getUUID());
        source.sendSuccess(() -> Component.literal("Levy state reset for " + target.getGameProfile().getName() + ". Retired/removed levy entities: " + retired + "."), true); return 1;
    }
    private static int adminResetCooldowns(CommandSourceStack source, ServerPlayer target) {
        CampaignSystemsData.get(target.serverLevel()).clearLevyCooldown(target.getUUID()); source.sendSuccess(() -> Component.literal("Levy cooldown cleared for " + target.getGameProfile().getName() + "."), true); return 1;
    }
    private static int adminResetFactionRank(CommandSourceStack source, ServerPlayer target) {
        CampaignData.get(target.serverLevel()).resetFactionRank(target.getUUID());
        SettlementManager.broadcastProfiles(target.getServer());
        source.sendSuccess(() -> Component.literal("Settlements faction-rank override cleared for " + target.getGameProfile().getName() + "."), true);
        return 1;
    }
    private static int adminResetSelection(CommandSourceStack source, ServerPlayer target) {
        SettlementData.get(target.serverLevel()).clearSelection(target.getUUID());
        source.sendSuccess(() -> Component.literal("Selected settlement cleared for " + target.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static int warnResetSettlement(CommandSourceStack source, String id) {
        SettlementData.SettlementRecord settlement = SettlementData.get(source.getLevel()).getSettlement(id);
        if (settlement == null) { source.sendFailure(Component.literal("Unknown settlement.")); return 0; }
        source.sendFailure(Component.literal("WARNING: This permanently deletes Settlements data for " + settlement.getName()
                + ", including roles, resident registry, housing, modifiers, mobilisation tracking and settlement history. Recruits claims/entities are not deleted. Add 'confirm' to proceed."));
        return 0;
    }

    private static int adminResetSettlement(CommandSourceStack source, String id) {
        SettlementData data = SettlementData.get(source.getLevel());
        SettlementData.SettlementRecord settlement = data.getSettlement(id);
        if (settlement == null) { source.sendFailure(Component.literal("Unknown settlement.")); return 0; }
        String settlementId = settlement.getId();
        String name = settlement.getName();
        CampaignSystemsData extra = CampaignSystemsData.get(source.getLevel());
        boolean keepFactionProtection = !settlement.getFactionTeam().isBlank() && extra.factionProtected(settlement.getFactionTeam());
        if (ClaimEvents.recruitsClaimManager != null) {
            for (SettlementData.ClaimLink link : settlement.getClaimLinks()) {
                RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(link.id());
                if (claim != null) {
                    claim.setAdminClaim(keepFactionProtection);
                    ClaimEvents.recruitsClaimManager.addOrUpdateClaim(source.getLevel(), claim);
                }
            }
            ClaimEvents.recruitsClaimManager.broadcastClaimsToAll(source.getLevel());
        }
        extra.clearSettlementData(settlementId);
        data.deleteSettlement(settlementId);
        source.sendSuccess(() -> Component.literal("Settlement " + name + " was reset/deleted. Recruits claims and world entities were preserved."), true);
        return 1;
    }

    private static int adminSetOwner(CommandSourceStack source, String id, ServerPlayer target) {
        SettlementData.SettlementRecord settlement = SettlementData.get(source.getLevel()).getSettlement(id);
        if (settlement == null) { source.sendFailure(Component.literal("Unknown settlement.")); return 0; }
        settlement.setOwner(target.getUUID());
        source.sendSuccess(() -> Component.literal("Owner of " + settlement.getName() + " set to " + target.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static int adminSetCoOwner(CommandSourceStack source, String id, ServerPlayer target, boolean add) {
        SettlementData.SettlementRecord settlement = SettlementData.get(source.getLevel()).getSettlement(id);
        if (settlement == null) { source.sendFailure(Component.literal("Unknown settlement.")); return 0; }
        if (target.getUUID().equals(settlement.getOwnerId())) { source.sendFailure(Component.literal("The settlement owner cannot be changed through co-owner commands.")); return 0; }
        settlement.setRole(target.getUUID(), add ? SettlementData.SettlementRole.CO_OWNER : SettlementData.SettlementRole.OUTSIDER);
        source.sendSuccess(() -> Component.literal((add ? "Added " : "Removed ") + target.getGameProfile().getName() + (add ? " as co-owner of " : " as co-owner from ") + settlement.getName() + "."), true);
        return 1;
    }

    private static int adminSetTier(CommandSourceStack source, String id, String rawTier) {
        SettlementData.SettlementRecord settlement = SettlementData.get(source.getLevel()).getSettlement(id);
        if (settlement == null) { source.sendFailure(Component.literal("Unknown settlement.")); return 0; }
        String normalized = rawTier.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        SettlementData.SettlementRank rank;
        try { rank = SettlementData.SettlementRank.valueOf(normalized); }
        catch (IllegalArgumentException exception) { source.sendFailure(Component.literal("Use village, town, capital, or city_state.")); return 0; }
        if (rank == SettlementData.SettlementRank.CAPITAL && settlement.getFactionTeam().isBlank()) {
            source.sendFailure(Component.literal("Independent settlements cannot be faction capitals. Use city_state instead.")); return 0;
        }
        if (rank == SettlementData.SettlementRank.CAPITAL) {
            SettlementData.get(source.getLevel()).settlementsForFaction(settlement.getFactionTeam()).stream()
                    .filter(other -> other != settlement && other.getRank() == SettlementData.SettlementRank.CAPITAL)
                    .forEach(other -> other.setRank(SettlementData.SettlementRank.TOWN));
        }
        settlement.setRank(rank);
        source.sendSuccess(() -> Component.literal(settlement.getName() + " tier set to " + rank.displayName() + "."), true);
        return 1;
    }

    private static int adminClearClaims(CommandSourceStack source, String id) {
        SettlementData.SettlementRecord settlement = SettlementData.get(source.getLevel()).getSettlement(id);
        if (settlement == null) { source.sendFailure(Component.literal("Unknown settlement.")); return 0; }
        int count = settlement.getClaimCount();
        settlement.clearClaimLink();
        source.sendSuccess(() -> Component.literal("Cleared " + count + " linked claim reference(s) from " + settlement.getName() + ". Recruits claims themselves were preserved."), true);
        return 1;
    }

    private static int adminClearHousing(CommandSourceStack source, String id) {
        SettlementData.SettlementRecord settlement = SettlementData.get(source.getLevel()).getSettlement(id);
        if (settlement == null) { source.sendFailure(Component.literal("Unknown settlement.")); return 0; }
        CampaignSystemsData.get(source.getLevel()).clearHousingAreas(settlement.getId());
        settlement.setHousingCapacity(0);
        source.sendSuccess(() -> Component.literal("Cleared registered housing for " + settlement.getName() + "."), true);
        return 1;
    }

    private static int adminClearArmyData(CommandSourceStack source, String id) {
        SettlementData.SettlementRecord settlement = SettlementData.get(source.getLevel()).getSettlement(id);
        if (settlement == null) { source.sendFailure(Component.literal("Unknown settlement.")); return 0; }
        settlement.clearMilitaryTracking();
        CampaignSystemsData.get(source.getLevel()).clearSettlementArmyData(settlement.getId());
        settlement.runManpowerChecker();
        source.sendSuccess(() -> Component.literal("Cleared Settlements military/levy/mercenary tracking for " + settlement.getName() + ". Existing external Recruits entities were not deleted."), true);
        return 1;
    }

    private static int adminRecalculateSettlement(CommandSourceStack source, String id) {
        SettlementData data = SettlementData.get(source.getLevel());
        SettlementData.SettlementRecord settlement = data.getSettlement(id);
        if (settlement == null) { source.sendFailure(Component.literal("Unknown settlement.")); return 0; }
        int housing = CampaignSystemsManager.recalculateHousing(source.getLevel(), settlement);
        int recovered = settlement.runManpowerChecker();
        int prosperity = CampaignSystemsManager.recalculateProsperity(settlement, CampaignSystemsData.get(source.getLevel()), source.getLevel().getDayTime() / 24000L);
        CampaignSystemsData extra = CampaignSystemsData.get(source.getLevel());
        if (extra.foreignManpower(settlement.getId()) <= 0) extra.removeModifier(settlement.getId(), "foreign_soldiers");
        source.sendSuccess(() -> Component.literal("Recalculated " + settlement.getName() + ": housing=" + housing
                + ", prosperity=" + prosperity + ", recoveredMP=" + recovered + ", availableMP=" + settlement.getAvailableManpower()
                + "/" + settlement.getMaximumManpower() + ", foreignMP=" + extra.foreignManpower(settlement.getId()) + "."), true);
        return 1;
    }

    private static int adminInspectPlayer(CommandSourceStack source, ServerPlayer target) {
        SettlementData data = SettlementData.get(target.serverLevel()); SettlementData.CharacterRecord c = data.getCharacter(target.getUUID());
        CampaignSystemsData extra = CampaignSystemsData.get(target.serverLevel()); String faction = target.getTeam() == null ? "Independent" : target.getTeam().getName();
        long owned = data.getSettlements().stream().filter(s -> target.getUUID().equals(s.getOwnerId())).count();
        long managed = data.getSettlements().stream().filter(s -> SettlementManager.canManage(target, s)).count();
        source.sendSuccess(() -> Component.literal("[Settlements inspect] " + target.getGameProfile().getName() + " | character=" + (c == null || !c.isCreated() ? "not created" : c.getDisplayName())
                + " | faith=" + (c == null ? "-" : c.getFaith()) + " | traits=" + (c == null ? "[]" : c.getTraits()) + " | faction=" + faction
                + " | owned=" + owned + " | manageable=" + managed + " | devotion=" + extra.devotion(target.getUUID())
                + " | levyCooldownMs=" + Math.max(0L, extra.levyCooldownUntil(target.getUUID()) - System.currentTimeMillis())), false); return 1;
    }

    private static int adminInspectSettlement(CommandSourceStack source, String id) {
        SettlementData data = SettlementData.get(source.getLevel()); SettlementData.SettlementRecord s = data.getSettlement(id); if (s == null) { source.sendFailure(Component.literal("Unknown settlement.")); return 0; }
        CampaignSystemsData extra = CampaignSystemsData.get(source.getLevel());
        String modifiers = extra.modifiers(s.getId()).stream().map(m -> m.name + "(" + m.stabilityPerDay + " stab/day)").reduce((a,b) -> a + ", " + b).orElse("none");
        List<CampaignSystemsData.LevyDeployment> activeLevies = extra.levyDeployments().stream().filter(value -> s.getId().equals(value.settlementId)).toList();
        int activeLevyUnits = activeLevies.stream().mapToInt(value -> Math.max(0, value.initialUnits - value.returnedUnits)).sum();
        source.sendSuccess(() -> Component.literal("[Settlement inspect] " + s.getName() + " [" + s.getId() + "] | faction=" + (s.getFactionTeam().isBlank() ? "Independent" : s.getFactionTeam())
                + " | rank=" + s.getRank().displayName() + " | pop=" + s.getDisplayedPopulation() + " | villagers=" + s.getVillagerCount() + " | housing=" + s.getHousingCapacity()
                + " | stability=" + s.getStability() + " | prosperity=" + extra.prosperity(s.getId()) + " | food=" + s.getFood() + " | wealth=" + s.getWealth()
                + " | manpower=" + s.getAvailableManpower() + "/" + s.getMaximumManpower() + " used=" + s.getUsedManpower() + " levyReserved=" + s.getLevyReservedManpower()
                + " | levyTradition=" + extra.levyTradition(s.getId()) + "/50 | activeLevyBatches=" + activeLevies.size() + " units≈" + activeLevyUnits
                + " | claims=" + s.getClaimCount() + " | protected=" + extra.settlementProtected(s.getId()) + " | modifiers=" + modifiers), false); return 1;
    }

    private static int adminProtectSettlement(CommandSourceStack source, String id, boolean protect) {
        SettlementData.SettlementRecord s = SettlementData.get(source.getLevel()).getSettlement(id); if (s == null) { source.sendFailure(Component.literal("Unknown settlement.")); return 0; }
        CampaignSystemsData.get(source.getLevel()).setSettlementProtected(s.getId(), protect);
        if (ClaimEvents.recruitsClaimManager != null) for (SettlementData.ClaimLink link : s.getClaimLinks()) { RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(link.id()); if (claim != null) { claim.setAdminClaim(protect); ClaimEvents.recruitsClaimManager.addOrUpdateClaim(source.getLevel(), claim); } }
        if (ClaimEvents.recruitsClaimManager != null) ClaimEvents.recruitsClaimManager.broadcastClaimsToAll(source.getLevel());
        source.sendSuccess(() -> Component.literal(s.getName() + " protected=" + protect + ". Linked Recruits claims were updated as admin claims."), true); return 1;
    }
    private static int adminProtectFaction(CommandSourceStack source, String faction, boolean protect) {
        if (FactionEvents.recruitsFactionManager == null || FactionEvents.recruitsFactionManager.getFactionByStringID(faction) == null) { source.sendFailure(Component.literal("Unknown Recruits faction.")); return 0; }
        CampaignSystemsData.get(source.getLevel()).setFactionProtected(faction, protect);
        for (SettlementData.SettlementRecord settlement : SettlementData.get(source.getLevel()).settlementsForFaction(faction)) adminProtectSettlement(source, settlement.getId(), protect);
        source.sendSuccess(() -> Component.literal(faction + " protected=" + protect + "."), true); return 1;
    }
    private static int adminHolySitePos1(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException { ServerPlayer p=player(source); CampaignSystemsManager.setRegionPos1(p); source.sendSuccess(() -> Component.literal("Holy Site pos1 set. Stand at the opposite corner and run /settlements admin holysite add <id> <faith>."), false); return 1; }
    private static int adminHolySiteAdd(CommandSourceStack source, String id, String faith) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=player(source); if (!CampaignDefinitions.isValidReligion(faith)) { source.sendFailure(Component.literal("Unknown faith. Use one of the configured Settlements religions.")); return 0; }
        CampaignSystemsData.RegionRecord region=CampaignSystemsManager.completeRegion(p); if(region==null||!region.valid()){source.sendFailure(Component.literal("Set holy-site pos1 first in this dimension."));return 0;}
        CampaignSystemsData.get(p.serverLevel()).addHolySite(new CampaignSystemsData.HolySiteRecord(id, id.replace('_',' '), faith, region, 20)); source.sendSuccess(() -> Component.literal("Holy Site " + id + " registered for " + faith + ". Visiting it once per Minecraft day grants 20 Devotion."), true); return 1;
    }
    private static int adminHolySiteRemove(CommandSourceStack source, String id) { boolean removed=CampaignSystemsData.get(source.getLevel()).removeHolySite(id); if(!removed){source.sendFailure(Component.literal("Holy Site not found."));return 0;} source.sendSuccess(() -> Component.literal("Holy Site removed."), true); return 1; }

    private static int adminDebugAdvance(CommandSourceStack source, String id, int days) {
        SettlementData.SettlementRecord s = SettlementData.get(source.getLevel()).getSettlement(id); if (s == null) { source.sendFailure(Component.literal("Unknown settlement.")); return 0; }
        CampaignSystemsData extra = CampaignSystemsData.get(source.getLevel()); extra.advanceDebugTime(s.getId(), days * 24L * 60L * 60L * 1000L);
        CampaignSystemsManager.processDebugSettlement(source.getServer(), s, source.getLevel().getDayTime() / 24000L + days);
        source.sendSuccess(() -> Component.literal("Advanced settlement-local campaign timers for " + s.getName() + " by " + days + " day(s). Global diplomacy timers and Minecraft world time were not changed."), true); return 1;
    }
    private static int adminDebugClearTime(CommandSourceStack source, String id) {
        SettlementData.SettlementRecord settlement = SettlementData.get(source.getLevel()).getSettlement(id);
        if (settlement == null) { source.sendFailure(Component.literal("Unknown settlement.")); return 0; }
        CampaignSystemsData.get(source.getLevel()).clearDebugTime(settlement.getId());
        source.sendSuccess(() -> Component.literal("Cleared artificial campaign-time offset for " + settlement.getName() + ". Vanilla world time was not changed."), true);
        return 1;
    }
    private static int adminDebugModifierAdd(CommandSourceStack source, String id, String modifier, int durationMinutes) {
        SettlementData.SettlementRecord s = SettlementData.get(source.getLevel()).getSettlement(id); if (s == null) { source.sendFailure(Component.literal("Unknown settlement.")); return 0; }
        CampaignSystemsData extra = CampaignSystemsData.get(source.getLevel()); extra.addModifier(s.getId(), CampaignSystemsManager.standardModifier(source.getLevel(), s.getId(), modifier, durationMinutes <= 0 ? 0 : durationMinutes * 60_000L));
        source.sendSuccess(() -> Component.literal("Added modifier " + modifier + " to " + s.getName() + (durationMinutes <= 0 ? " permanently." : " for " + durationMinutes + " minute(s).")), true); return 1;
    }
    private static int adminDebugModifierRemove(CommandSourceStack source, String id, String modifier) {
        boolean removed = CampaignSystemsData.get(source.getLevel()).removeModifier(id, modifier); if (!removed) { source.sendFailure(Component.literal("Modifier not found.")); return 0; } source.sendSuccess(() -> Component.literal("Modifier removed."), true); return 1;
    }
    private static int adminDebugModifierClear(CommandSourceStack source, String id) { CampaignSystemsData.get(source.getLevel()).clearModifiers(id); source.sendSuccess(() -> Component.literal("All temporary/campaign modifiers cleared for " + id + "."), true); return 1; }
    private static int adminDebugDevotion(CommandSourceStack source, ServerPlayer target, int value) { CampaignSystemsData.get(target.serverLevel()).setDevotion(target.getUUID(), value); source.sendSuccess(() -> Component.literal("Devotion set to " + value + " for " + target.getGameProfile().getName() + "."), true); return 1; }
    private static int adminDebugProsperity(CommandSourceStack source, String id) { SettlementData.SettlementRecord s = SettlementData.get(source.getLevel()).getSettlement(id); if (s == null) return 0; int value = CampaignSystemsManager.recalculateProsperity(s, CampaignSystemsData.get(source.getLevel()), source.getLevel().getDayTime() / 24000L); source.sendSuccess(() -> Component.literal("Prosperity recalculated: " + value + "."), true); return 1; }
    private static int adminDebugManpower(CommandSourceStack source, String id) { SettlementData.SettlementRecord s = SettlementData.get(source.getLevel()).getSettlement(id); if (s == null) return 0; s.runManpowerChecker(); source.sendSuccess(() -> Component.literal("Manpower recovery/recalculation applied: " + s.getAvailableManpower() + "/" + s.getMaximumManpower() + "."), true); return 1; }
    private static int adminDebugWarScore(CommandSourceStack source, String rawId, int value) { try { UUID id = UUID.fromString(rawId); CampaignData.WarRecord war = CampaignData.get(source.getLevel()).getWar(id); if (war == null) { source.sendFailure(Component.literal("Unknown war UUID.")); return 0; } CampaignSystemsData.get(source.getLevel()).setWarScore(id, value); source.sendSuccess(() -> Component.literal("War score set to " + value + " for " + id + ". ±90 enables capitulation logic."), true); return 1; } catch (IllegalArgumentException e) { source.sendFailure(Component.literal("Invalid war UUID.")); return 0; } }
    private static int adminDebugCapitulate(CommandSourceStack source, String rawId, String rawSide) {
        try {
            UUID id = UUID.fromString(rawId);
            CampaignData.WarRecord war = CampaignData.get(source.getLevel()).getWar(id);
            if (war == null) { source.sendFailure(Component.literal("Unknown war UUID.")); return 0; }
            String side = rawSide.toLowerCase(Locale.ROOT);
            int score;
            if (side.equals("attacker")) score = -100;
            else if (side.equals("defender")) score = 100;
            else { source.sendFailure(Component.literal("Use attacker or defender. The named side is the side being capitulated.")); return 0; }
            CampaignSystemsData.get(source.getLevel()).setWarScore(id, score);
            source.sendSuccess(() -> Component.literal("Forced " + side + " capitulation for war " + id + " (war score " + score + ")."), true);
            return 1;
        } catch (IllegalArgumentException e) { source.sendFailure(Component.literal("Invalid war UUID.")); return 0; }
    }
    private static int adminDebugEvent(CommandSourceStack source, String id, String rawEvent, int durationMinutes, boolean trigger) {
        SettlementData.SettlementRecord settlement = SettlementData.get(source.getLevel()).getSettlement(id);
        if (settlement == null) { source.sendFailure(Component.literal("Unknown settlement.")); return 0; }
        String event = SettlementData.normalizeId(rawEvent);
        if (!event.equals("plague")) { source.sendFailure(Component.literal("Unknown test event. Currently supported: plague.")); return 0; }
        CampaignSystemsData extra = CampaignSystemsData.get(source.getLevel());
        if (trigger) {
            extra.addModifier(settlement.getId(), CampaignSystemsManager.standardModifier(source.getLevel(), settlement.getId(), event, durationMinutes * 60_000L));
            settlement.addChronicle("Campaign event began: Plague.");
            source.sendSuccess(() -> Component.literal("Triggered Plague for " + settlement.getName() + " for " + durationMinutes + " minute(s)."), true);
        } else {
            if (!extra.removeModifier(settlement.getId(), event)) { source.sendFailure(Component.literal("That event is not active.")); return 0; }
            settlement.addChronicle("Campaign event ended: Plague.");
            source.sendSuccess(() -> Component.literal("Ended Plague for " + settlement.getName() + "."), true);
        }
        return 1;
    }
    private static int adminDebugSpouseRefresh(CommandSourceStack source, ServerPlayer target) { CampaignData data = CampaignData.get(target.serverLevel()); long day = target.serverLevel().getDayTime()/24000L; data.forceRefreshCandidates(target.getUUID(), day); source.sendSuccess(() -> Component.literal("Spouse candidates force-refreshed for " + target.getGameProfile().getName() + "."), true); return 1; }
    private static int adminDebugMercenaryPayment(CommandSourceStack source, String faction) { int count = SettlementManager.forceMercenaryPaymentCheck(source.getServer(), faction); source.sendSuccess(() -> Component.literal("Forced mercenary payment check for " + faction + " across " + count + " tracked mercenary(ies)."), true); return 1; }

    private static int regionPos1(CommandSourceStack source, String id, String type) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(source); SettlementData.SettlementRecord s = requireManage(p, id); if (s == null) return 0; CampaignSystemsManager.setRegionPos1(p); source.sendSuccess(() -> Component.literal(type + " pos1 set for " + s.getName() + ". Stand at the opposite corner and use pos2."), false); return 1;
    }
    private static int regionPos2(CommandSourceStack source, String id, String type) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(source); SettlementData.SettlementRecord s = requireManage(p, id); if (s == null) return 0; CampaignSystemsData.RegionRecord region = CampaignSystemsManager.completeRegion(p); if (region == null || !region.valid()) { source.sendFailure(Component.literal("Set pos1 first in this dimension.")); return 0; }
        CampaignSystemsData extra = CampaignSystemsData.get(p.serverLevel());
        switch (type) {
            case "housing" -> { extra.addHousingArea(s.getId(), region); int beds = CampaignSystemsManager.recalculateHousing(p.serverLevel(), s); source.sendSuccess(() -> Component.literal("Dwelling registered. Housing is now " + beds + " bed(s)."), true); }
            case "muster" -> { extra.setMusterArea(s.getId(), region); s.setMusterGround(region.dimension(), region.min()); source.sendSuccess(() -> Component.literal("Muster Ground registered: " + region.horizontalArea() + " horizontal block(s), theoretical capacity " + region.horizontalArea() + " soldiers."), true); }
            case "prayer" -> { extra.setPrayerSite(s.getId(), region); source.sendSuccess(() -> Component.literal("Prayer Site registered for " + s.getName() + "."), true); }
            default -> { return 0; }
        }
        return 1;
    }
    private static int clearHousingAreas(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException { ServerPlayer p=player(source); SettlementData.SettlementRecord s=requireManage(p,id); if(s==null)return 0; CampaignSystemsData.get(p.serverLevel()).clearHousingAreas(s.getId()); s.setHousingCapacity(0); source.sendSuccess(() -> Component.literal("All registered dwellings cleared."), true); return 1; }
    private static int recalculateHousing(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException { ServerPlayer p=player(source); SettlementData.SettlementRecord s=requireManage(p,id); if(s==null)return 0; int beds=CampaignSystemsManager.recalculateHousing(p.serverLevel(),s); source.sendSuccess(() -> Component.literal("Housing recalculated from registered dwelling beds: " + beds + "."), true); return 1; }

    private static SettlementData.SettlementRecord requireManage(ServerPlayer player, String id) {
        SettlementData.SettlementRecord settlement = SettlementData.get(player.serverLevel()).getSettlement(id);
        if (settlement == null) {
            player.sendSystemMessage(Component.literal("Unknown settlement."));
            return null;
        }
        if (!SettlementManager.canManage(player, settlement)) {
            player.sendSystemMessage(Component.literal("You cannot manage that settlement."));
            return null;
        }
        return settlement;
    }

    private static SettlementData.SettlementRecord requireOwnerOrAdmin(ServerPlayer player, String id) {
        SettlementData.SettlementRecord settlement = SettlementData.get(player.serverLevel()).getSettlement(id);
        if (settlement == null) return null;
        if (!player.createCommandSourceStack().hasPermission(2) && settlement.getRole(player.getUUID()) != SettlementData.SettlementRole.OWNER) {
            player.sendSystemMessage(Component.literal("Only the owner or an administrator may do that."));
            return null;
        }
        return settlement;
    }
}
