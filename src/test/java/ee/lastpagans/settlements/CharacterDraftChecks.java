package ee.lastpagans.settlements;

import ee.lastpagans.settlements.character.BreastAppearance;
import ee.lastpagans.settlements.character.CharacterBodyData;
import ee.lastpagans.settlements.character.CharacterGender;
import ee.lastpagans.settlements.client.CharacterCreationDraftSession;

import java.util.List;
import java.util.UUID;

/** Exercises the same session consumed by the GUI without constructing Minecraft screens. */
public final class CharacterDraftChecks {
    private static int assertions;

    public static int run() {
        assertions = 0;
        editorTransitions();
        traitNavigationAndRejection();
        completionAndDisconnect();
        submissionPackets();
        return assertions;
    }

    private static CharacterCreationDraftSession populated() {
        CharacterCreationDraftSession session = new CharacterCreationDraftSession(UUID.randomUUID());
        session.identity("Ada", "Marie", "Lovelace", CampaignDefinitions.RELIGIONS.get(3),
                CampaignDefinitions.ADDRESSES.get(1), CampaignDefinitions.PERSONAL_TITLES.get(2));
        session.gender(CharacterGender.FEMALE);
        return session;
    }

    private static void editorTransitions() {
        CharacterCreationDraftSession session = populated();
        var identity = session.draft();
        check(!session.beginEditor(), "No prevents opening editor");
        session.breasts(true);
        check(session.state() == CharacterCreationDraftSession.State.DRAFT && session.draft().body().breasts().enabled(),
                "Yes persists without automatically entering editor");
        check(session.beginEditor() && !session.beginEditor(), "editor opens only once");
        BreastAppearance breasts = new BreastAppearance(true, 0.73F, 0.11F, -0.2F, -0.15F, 0.08F, false);
        CharacterBodyData edited = new CharacterBodyData(1, CharacterGender.OTHER, breasts,
                false, false, false, true, 0.4F, 0.6F);
        session.finishEditor(edited, true);
        var returned = session.draft();
        check(returned.first().equals(identity.first()) && returned.middle().equals(identity.middle())
                        && returned.last().equals(identity.last()) && returned.faith().equals(identity.faith())
                        && returned.address().equals(identity.address()) && returned.title().equals(identity.title()),
                "all identity fields survive editor return and subsequent widget reconstruction");
        check(returned.body().breasts().equals(breasts) && returned.body().gender() == CharacterGender.FEMALE,
                "all breast values are captured while Settlements gender stays authoritative");
        check(!returned.body().hurtSounds() && !returned.body().breastPhysics() && !returned.body().showBreastsInArmor()
                        && returned.body().armorPhysicsOverride() && returned.body().bounceMultiplier() == 0.4F
                        && returned.body().floppiness() == 0.6F, "complete editor body settings survive");
        check(session.beginEditor(), "editor can be reopened");
        session.finishEditor(CharacterBodyData.defaults(), false);
        check(session.draft().equals(returned), "cancelled editor discards changes");
        session.breasts(false);
        check(session.draft().body().effectiveBustSize() == 0 && session.draft().body().breasts().bustSize() == 0.73F,
                "Yes to No hides bust but preserves edited values");
        session.breasts(true);
        check(session.draft().body().effectiveBustSize() == 0.73F, "No to Yes restores edited bust");
        for (CharacterGender gender : List.of(CharacterGender.FEMALE, CharacterGender.OTHER)) {
            session.gender(gender);
            session.breasts(false);
            check(session.draft().body().effectiveBustSize() == 0, gender + " supports No");
            session.breasts(true);
            check(session.draft().body().effectiveBustSize() == 0.73F, gender + " supports Yes");
            session.gender(CharacterGender.MALE);
            session.breasts(true);
            check(!session.draft().body().breasts().enabled() && session.draft().body().effectiveBustSize() == 0
                    && !session.beginEditor(), gender + " to Male enforces zero bust and blocks editor");
        }
    }

    private static void traitNavigationAndRejection() {
        CharacterCreationDraftSession session = populated();
        var original = session.draft();
        session.identity("  Ada ", "\u00a7Marie", " Lovelace ", original.faith(), original.address(), original.title());
        check(session.draft().displayName().equals("Ada Marie Lovelace"), "trait seed uses the server's normalized character name");
        check(!session.submit(), "incomplete traits cannot submit");
        check(session.reroll() && !session.reroll(), "single reroll enforced across screens");
        List<String> firstChoices = session.choices();
        check(session.choices().equals(firstChoices), "reconstruction preserves offered cards");
        check(!session.choose("invented-trait"), "unknown trait cannot be chosen");
        check(session.choose(firstChoices.get(0)), "first offered card accepted");
        var selected = session.selectedTraits();
        session.body(session.draft().body().withGender(CharacterGender.OTHER));
        session.identity(session.draft().first(), session.draft().middle(), session.draft().last(),
                CampaignDefinitions.RELIGIONS.get(1), session.draft().address(), session.draft().title());
        check(session.selectedTraits().equals(selected) && session.rerollRound() == 0,
                "back navigation and non-name edits preserve selected traits and spent reroll");
        while (!session.completeTraits()) check(session.choose(session.choices().get(0)), "next offered card accepted");
        check(CampaignDefinitions.validateTraitSelection(session.playerId(), session.draft().displayName(),
                session.selectedTraits(), session.rerollRound()), "retained traits are server-verifiable");
        var draft = session.draft();
        var traits = session.selectedTraits();
        check(session.submit() && !session.submit(), "one pending submission permitted");
        session.identity("Lost", "", "State", "", "", "");
        session.breasts(true);
        check(session.draft().equals(draft), "pending submission freezes draft");
        session.reject(UUID.randomUUID(), "stale error");
        check(session.state() == CharacterCreationDraftSession.State.SUBMITTING, "stale rejection ignored");
        session.reject(session.id(), "Name already in use");
        check(session.state() == CharacterCreationDraftSession.State.DRAFT && session.error().equals("Name already in use")
                        && session.draft().equals(draft) && session.selectedTraits().equals(traits) && session.rerollRound() == 0,
                "server rejection preserves complete draft, traits and reroll, and exposes error");
        check(session.submit(), "retained draft can be retried");
        session.reject(session.id(), "Change name");
        session.identity("Grace", draft.middle(), draft.last(), draft.faith(), draft.address(), draft.title());
        check(session.selectedTraits().isEmpty() && session.rerollUsed(), "rename resets seeded cards without granting another reroll");
        while (!session.completeTraits()) session.choose(session.choices().get(0));
        check(CampaignDefinitions.validateTraitSelection(session.playerId(), session.draft().displayName(),
                session.selectedTraits(), session.rerollRound()), "new-name cards remain server-verifiable");
        session.restartTraits();
        check(session.selectedTraits().isEmpty() && session.rerollUsed(), "trait recovery preserves spent reroll");
    }

    private static void completionAndDisconnect() {
        CharacterCreationDraftSession session = populated();
        while (!session.completeTraits()) session.choose(session.choices().get(0));
        check(session.submit(), "complete draft submits");
        CharacterBodyData canonical = session.draft().body().withGender(CharacterGender.MALE);
        session.accept(canonical);
        check(session.state() == CharacterCreationDraftSession.State.ACCEPTED && session.draft().body().equals(canonical),
                "canonical body installed before session completes");
        session.reject(session.id(), "late rejection");
        session.cancel();
        check(session.state() == CharacterCreationDraftSession.State.ACCEPTED && session.draft().body().equals(canonical),
                "late rejection and cleanup cannot restore stale body after acceptance");
        CharacterCreationDraftSession disconnected = populated();
        disconnected.breasts(true);
        disconnected.choose(disconnected.choices().get(0));
        check(disconnected.beginEditor(), "disconnect case begins in editor");
        disconnected.cancel();
        disconnected.finishEditor(CharacterBodyData.defaults(), true);
        check(disconnected.state() == CharacterCreationDraftSession.State.CANCELLED
                        && disconnected.draft() == null && disconnected.selectedTraits().isEmpty(),
                "disconnect destroys temporary draft and ignores late editor return");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }

    private static void submissionPackets() {
        CharacterCreationDraftSession session = populated();
        session.breasts(true);
        while (!session.completeTraits()) session.choose(session.choices().get(0));
        var draft = session.draft();
        var submit = new ee.lastpagans.settlements.network.SubmitCharacterPacket(session.id(), draft.first(),
                draft.middle(), draft.last(), draft.faith(), draft.address(), draft.title(), draft.body(),
                session.selectedTraits(), session.rerollRound());
        var rejection = new ee.lastpagans.settlements.network.CharacterSubmissionRejectedPacket(session.id(), "Name already in use");
        net.minecraft.network.FriendlyByteBuf buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            ee.lastpagans.settlements.network.SubmitCharacterPacket.encode(submit, buffer);
            check(ee.lastpagans.settlements.network.SubmitCharacterPacket.decode(buffer).equals(submit)
                    && buffer.readableBytes() == 0, "submission transports session ID and complete draft exactly");
            buffer.clear();
            ee.lastpagans.settlements.network.CharacterSubmissionRejectedPacket.encode(rejection, buffer);
            check(ee.lastpagans.settlements.network.CharacterSubmissionRejectedPacket.decode(buffer).equals(rejection)
                    && buffer.readableBytes() == 0, "rejection transports correlation ID and error message exactly");
        } finally {
            buffer.release();
        }
    }
}
