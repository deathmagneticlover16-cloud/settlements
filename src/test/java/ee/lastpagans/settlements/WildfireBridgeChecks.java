package ee.lastpagans.settlements;

import com.wildfire.main.GenderPlayer;
import ee.lastpagans.settlements.character.BreastAppearance;
import ee.lastpagans.settlements.character.CharacterBodyData;
import ee.lastpagans.settlements.character.CharacterGender;
import ee.lastpagans.settlements.compat.wildfire.SettlementsWildfireBridge;
import net.minecraftforge.fml.loading.FMLPaths;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Tests the shipped Wildfire API and injection targets without starting Minecraft. */
public final class WildfireBridgeChecks {
    private static int assertions;

    public static int run() {
        assertions = 0;
        try {
            Path testHome = Files.createTempDirectory("settlements-wildfire-checks-");
            FMLPaths.loadAbsolutePaths(testHome);
            GenderPlayer original = new GenderPlayer(UUID.randomUUID());
            CharacterBodyData body = new CharacterBodyData(1, CharacterGender.FEMALE,
                    new BreastAppearance(true, 0.7F, 0.1F, -0.2F, -0.1F, 0.08F, false),
                    false, true, false, true, 0.4F, 0.65F);
            SettlementsWildfireBridge.applyInMemory(original, body);
            check(SettlementsWildfireBridge.matches(original, body), "complete body applies through the real Wildfire API");
            GenderPlayer detached = new GenderPlayer(original.uuid);
            for (CharacterGender gender : CharacterGender.values()) {
                CharacterBodyData changed = body.withGender(gender);
                SettlementsWildfireBridge.applyInMemory(detached, changed);
                check(detached.getGender().name().equals(gender.name()), "exact Settlements/Wildfire mapping: " + gender);
                check(SettlementsWildfireBridge.matches(detached, changed), "Wildfire accepts complete " + gender + " body");
                if (gender == CharacterGender.MALE)
                    check(detached.getBustSize() == 0, "real Wildfire Male receives zero bust");
            }
            check(SettlementsWildfireBridge.matches(original, body), "detached body edits leave original Wildfire object untouched");
            check(original.getConfig() != detached.getConfig(), "detached bodies have independent configuration objects");
            SettlementsWildfireBridge.applyInMemory(detached, body);
            check(SettlementsWildfireBridge.matches(detached, body), "drift test starts with matching canonical state");
            detached.updateBustSize(0.2F);
            check(!SettlementsWildfireBridge.matches(detached, body), "body comparison detects actual Wildfire drift");
            try (var files = Files.walk(testHome)) {
                check(files.noneMatch(Files::isRegularFile), "in-memory body application creates no saved body files");
            }
            target("com/wildfire/main/networking/WildfireSync", "sendToServer", "(Lcom/wildfire/main/GenderPlayer;)V");
            target("com/wildfire/main/networking/PacketGenderInfo", "updatePlayerFromPacket", "(Lcom/wildfire/main/GenderPlayer;)V");
            target("com/wildfire/main/config/Configuration", "finish", "()V");
            target("com/wildfire/main/WildfireGender", "getPlayerById", "(Ljava/util/UUID;)Lcom/wildfire/main/GenderPlayer;");
            target("com/wildfire/main/WildfireGender", "getOrAddPlayerById", "(Ljava/util/UUID;)Lcom/wildfire/main/GenderPlayer;");
            target("com/wildfire/gui/screen/WildfireBreastCustomizationScreen", "init", "()V");
            ClassNode physics = read("com/wildfire/physics/BreastPhysics");
            for (String field : new String[]{"breastSize", "preBreastSize"})
                check(physics.fields.stream().anyMatch(value -> value.name.equals(field) && value.desc.equals("F")),
                        "portrait accessor matches Wildfire field " + field);
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect Wildfire integration", exception);
        }
        return assertions;
    }

    private static void target(String type, String method, String descriptor) throws IOException {
        check(read(type).methods.stream().anyMatch(value -> value.name.equals(method) && value.desc.equals(descriptor)),
                "Wildfire injection target exists: " + method + descriptor);
    }

    private static ClassNode read(String type) throws IOException {
        try (InputStream stream = WildfireBridgeChecks.class.getClassLoader().getResourceAsStream(type + ".class")) {
            if (stream == null) throw new IOException("Missing class " + type);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
            return node;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
