package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.CampaignDefinitions;
import ee.lastpagans.settlements.SettlementData;
import ee.lastpagans.settlements.character.CharacterBodyData;
import ee.lastpagans.settlements.character.CharacterGender;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** GUI-independent state shared by every screen in one character-creation flow. */
public final class CharacterCreationDraftSession {
    public enum State { DRAFT, EDITING, SUBMITTING, ACCEPTED, CANCELLED }
    public record CharacterDraft(String first, String middle, String last, String faith, String address,
                                 String title, CharacterBodyData body) {
        public String displayName() {
            return (SettlementData.cleanText(first, "", 24) + " " + SettlementData.cleanText(middle, "", 24)
                    + " " + SettlementData.cleanText(last, "", 24)).replaceAll("\\s+", " ").trim();
        }
    }

    private final UUID id = UUID.randomUUID();
    private final UUID playerId;
    private CharacterDraft draft;
    private final List<String> selected = new ArrayList<>();
    private int rerollRound = -1;
    private State state = State.DRAFT;
    private String error = "";

    public CharacterCreationDraftSession(UUID playerId) {
        this.playerId = playerId;
        CharacterBodyData body = CharacterBodyData.defaults();
        draft = new CharacterDraft("", "", "", CampaignDefinitions.RELIGIONS.get(0),
                addressFor(body.gender()), CampaignDefinitions.PERSONAL_TITLES.get(0), body);
    }

    public UUID id() { return id; }
    public UUID playerId() { return playerId; }
    public CharacterDraft draft() { return draft; }
    public State state() { return state; }
    public String error() { return error; }
    public List<String> selectedTraits() { return List.copyOf(selected); }
    public int round() { return Math.min(2, selected.size()); }
    public int rerollRound() { return rerollRound; }
    public boolean rerollUsed() { return rerollRound >= 0; }
    public boolean completeTraits() { return selected.size() == 3; }

    public void identity(String first, String middle, String last, String faith, String address, String title) {
        if (state != State.DRAFT) return;
        CharacterDraft next = new CharacterDraft(first, middle, last, faith, addressFor(draft.body().gender()), title, draft.body());
        // Trait cards are seeded by the complete display name. Preserve the spent reroll on a rename.
        if (!next.displayName().equalsIgnoreCase(draft.displayName())) {
            selected.clear();
            error = "";
        }
        draft = next;
    }

    public void body(CharacterBodyData body) {
        if (state != State.DRAFT || body == null) return;
        CharacterBodyData safe = body.sanitized();
        draft = new CharacterDraft(draft.first(), draft.middle(), draft.last(), draft.faith(),
                addressFor(safe.gender()), draft.title(), safe);
    }

    public void gender(CharacterGender gender) {
        if (state != State.DRAFT || gender == null) return;
        draft = new CharacterDraft(draft.first(), draft.middle(), draft.last(), draft.faith(), addressFor(gender),
                draft.title(), draft.body().withGender(gender).sanitized());
    }
    public void breasts(boolean enabled) { body(draft.body().withBreasts(draft.body().breasts().withEnabled(enabled))); }

    public boolean beginEditor() {
        if (state != State.DRAFT || draft.body().gender() == CharacterGender.MALE
                || !draft.body().breasts().enabled()) return false;
        state = State.EDITING;
        return true;
    }

    public void finishEditor(CharacterBodyData edited, boolean returnedToParent) {
        if (state != State.EDITING) return;
        state = State.DRAFT;
        if (returnedToParent && edited != null) {
            // Wildfire edits appearance only; Settlements owns the gender and Yes/No selection.
            body(edited.withGender(draft.body().gender())
                    .withBreasts(edited.breasts().withEnabled(draft.body().breasts().enabled())));
        }
    }

    public List<String> choices() {
        if (completeTraits()) return List.of();
        return CampaignDefinitions.traitChoices(playerId, draft.displayName(), round(), rerollRound == round(), selected);
    }

    public boolean reroll() {
        if (state != State.DRAFT || rerollUsed() || completeTraits()) return false;
        rerollRound = round();
        return true;
    }

    public boolean choose(String trait) {
        if (state != State.DRAFT || !choices().contains(trait)) return false;
        selected.add(trait);
        return true;
    }

    public void restartTraits() {
        if (state == State.DRAFT) { selected.clear(); error = ""; }
    }

    public boolean submit() {
        if (state != State.DRAFT || !completeTraits()) return false;
        state = State.SUBMITTING;
        error = "";
        return true;
    }

    public void reject(UUID sessionId, String message) {
        if (!id.equals(sessionId) || state != State.SUBMITTING) return;
        state = State.DRAFT;
        error = message;
    }

    public void accept(CharacterBodyData canonical) {
        if (state != State.SUBMITTING || canonical == null) return;
        state = State.DRAFT;
        body(canonical);
        state = State.ACCEPTED;
        error = "";
    }

    public void cancel() {
        if (state == State.ACCEPTED) return;
        state = State.CANCELLED;
        selected.clear();
        draft = null;
        error = "";
    }

    private static String addressFor(CharacterGender gender) {
        return switch (gender == null ? CharacterGender.MALE : gender) {
            case MALE -> "Male";
            case FEMALE -> "Female";
            case OTHER -> "Neutral";
        };
    }
}
