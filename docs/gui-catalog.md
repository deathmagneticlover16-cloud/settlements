# Settlements GUI catalog

This catalog covers Settlements 2.6.0 client UI, including native screens, tabbed pages,
in-world editors, overlays, and third-party screens that Settlements intentionally opens or augments.

## Entry points

- **Campaign Overview** — `M`; the main navigation page.
- **Character Sheet** — `J`; opens the current character or character onboarding.
- **Settlement** — `N`; opens the currently selected settlement.
- Recruits faction and diplomacy screens are intercepted and redirected to the matching Settlements page.

## Full-screen pages

1. **Campaign Overview / Main Page** — character summary, faction banner, realm totals, notices, and links to every major system.
2. **Character Creator I: Identity and Body** — name, faith, title, Settlements gender, and Wildfire-backed body settings.
3. **Character Creator II: Preview** — final character preview before trait selection.
4. **Character Creator III: Trait Selection** — choose and reroll character traits, then submit the character.
5. **Character Sheet** — identity, portrait, stats, traits, holdings, faith donation, family, and Main Seat links.
6. **Edit Character Identity** — rename the character and edit the Main House.
7. **Choose Main Seat** — paginated list of personally owned settlements eligible to become the character's Main Seat.
8. **Found Settlement** — settlement ID and display-name creation form.
9. **Settlement Overview** — summary of the selected settlement.
10. **Settlement Residents** — resident counts, growth, housing, contracts, and contribution data.
11. **Settlement Treasury** — stored resources, tax policy, deposits, withdrawals, and upkeep chest controls.
12. **Settlement Effects** — active settlement modifiers and their sources/durations.
13. **Settlement Management: Locations** — Town Square, upkeep chest, barracks, housing, muster, and prayer-site controls.
14. **Settlement Management: Identity** — settlement name, ownership, and destructive settlement controls.
15. **Settlement Management: Claims** — linked Recruits claims and claim maintenance.
16. **Settlement Management: Capture** — primary and secondary capture-zone configuration.
17. **Settlement Management: Members** — settlement roles, membership, invitations, and transfers.
18. **Housing Areas Manager** — scrollable multi-area list with add, edit, delete, and world-view actions.
19. **Military: Forces** — professional manpower, templates, morale, and recruitment.
20. **Military: Levies** — levy capacity, mobilisation, cooldowns, and muster controls.
21. **Military: Support** — support-system links, including mercenary rosters.
22. **Mercenary Rosters** — company selection, hiring, wages, reliability, status, and contract details.
23. **Faction Setup: Identity** — faction name, team/unit colors, government, capital, and creation.
24. **Faction Setup: Banner Editor** — banner base color and layered pattern design.
25. **Faction Setup: Join and Invitations** — applications to public factions and pending invitations.
26. **Faction Overview** — faction identity, government, capital, resources, and military roster.
27. **Faction Members** — member list, applications, invitations, ranks, kicks, and leadership controls.
28. **Faction Settlements** — faction settlement list and capital selection.
29. **Faction Ranks** — rank permissions and visible title editing.
30. **Diplomacy: Relations** — formal state, political relations, faith compatibility, reliability, and treaty actions.
31. **Diplomacy: Declare Conflict** — conflict tier, participants, target, casus belli, and declaration summary.
32. **Diplomacy: Proposals** — incoming treaty and peace proposals.
33. **Diplomacy: Active Wars** — war list, intervention, ally calls, aid, and peace negotiation links.
34. **War Aid / Aid Ledger** — one-time or daily manpower, wealth, and food aid plus recurring-aid cancellation.
35. **Peace Treaty** — reparations, transfers, claims, faith conversion, independence, NAP terms, and war-score budget.
36. **Family and Marriage** — NPC candidates, player proposals, additional spouses, rejection, and divorce.
37. **Research** — active project, four bench-tier columns, technology details, start, and cancellation.
38. **World News** — filtered, scrollable reports for wars, diplomacy, settlements, factions, characters, economy, religion, and military events.

The 38 entries above are user-visible page states implemented by **19 native screen classes**. Tabs and setup modes are counted separately because they present distinct controls and content even when they share a Java screen class.

## Editors, overlays, and integrated UI

- **Wildfire body editor** — an authorized `WildfireBreastCustomizationScreen` opened from Character Creator I; Settlements keeps the draft and final body data authoritative.
- **Area Selection Editor** — in-world corner selection and geometry preview. Cylinder mode is available only for capture zones.
- **Housing Area World View** — renders all housing boxes and allows the aimed-at area to be deleted.
- **Transient Alert Overlay** — one expiring status message positioned away from the hotbar.
- **Campaign Toast** — Minecraft system toast used for campaign notifications.
- **World Map Settlement Markers** — settlement, capital, faction, population, and stability information drawn over the Recruits world map.

## Shared GUI components

- `ScaledCampaignScreen` — scaling, mouse-coordinate translation, common frame/panel rendering, shared tab strips, text fitting, and dropdown event ownership.
- `DropdownWidget` — shared dropdown selector.
- `TaxRateSlider` — settlement tax-policy slider.
- `PortraitRenderer` — shared player portrait rendering.
