# Character creation continuation

Continues the supplied `6bb7ddd` checkpoint on `settlements-2.6.0-foundations`.

The creator, preview, trait chooser and Wildfire editor share one mutable session.
Text field responders and dropdown listeners update it as edits happen, so screen
initialization reads current values and never reconstructs the original empty draft.
Trait selections and the single spent reroll survive navigation. Changing the name
invalidates the cards seeded by that name without granting another reroll.

Breasts Yes/No is a persistent selection. The separate Edit button opens Wildfire.
Male forces breasts off and an effective bust of zero; Female and Other support both
options. The breast editor cannot change Settlements' selected gender.

Wildfire sees detached player objects during editing and portrait rendering. The
original player object, config and pending sync state are retained untouched. Returning
to the exact parent captures the edited body before restoring the original object.
Other exits discard editor changes, and leaving onboarding or disconnecting clears
the draft. Client-thread lookups expose temporary bodies without modifying Wildfire's
global registry, so an integrated server cannot read or broadcast a draft. Portrait
rendering restores its lookup context in a `finally` block and
sets both cached physics sizes so selection changes are visible in the same frame.

Draft configuration loading/rewriting, body saves and outbound Wildfire packets are
suppressed. Global preset management is disabled during draft editing; all stock
appearance sliders remain available. The original Wildfire configuration remains
available outside the temporary editor.

Submissions include a session ID. Server validation failures return a correlated error
packet without reopening an empty creator. The retained trait screen offers Go Back,
Choose traits again and Submit Character. Pending submission disables duplicate input.
The trait seed uses the same sanitized display name as server validation.

After validation, the server saves the nested Body record and sends its canonical body
before opening the character sheet. The client restores any temporary object before
installing and persisting canonical values. Observers receive the same canonical body
when tracking starts and when it changes. Managed bodies reject stale Wildfire packets
and periodically correct cached drift. Characters without Body data remain unmanaged;
administrative reset clears both owner and observer authority.

Network protocol is **40**. Clients and servers must use the same updated Settlements
JAR, with the separate Recruits and Wildfire mods installed as before.

## Automated validation

Use Java 17 and run `./gradlew build` (Windows: `gradlew.bat build`). The build includes
`regressionChecks` and creates the reobfuscated `build/libs/settlements-2.6.0.jar`.
New checks cover draft/editor transitions, trait navigation, rejection/retry,
acceptance, disconnect cleanup, submission and rejection packet round trips, exact
Wildfire gender mapping, full body application, detached object/config independence,
and the Wildfire methods/fields targeted by the new mixins.
The continuation's production build passed all **146 regression assertions**.

These checks exercise mapped classes without starting Minecraft. No Minecraft client,
dedicated server, in-game GUI, multiplayer session or gameplay check was run for this
continuation, as requested. The earlier startup evidence in VALIDATION-v2.6.0.txt
belongs to the inherited checkpoint and does not validate the new runtime mixins.

Mercenary company groups already reuse the same owner/name match and keep empty groups
after member removal in Recruits 1.15.2; that implementation is retained.
