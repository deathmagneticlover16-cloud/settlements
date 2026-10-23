# The Culling validation

Automated validation:

```text
./gradlew regressionChecks
```

The regression suite checks capture geometry and persistence, housing areas, research tier columns, banner validation, treasury arithmetic and serialization, dye mappings, packet round trips, recruit-template sanitation, character bodies, drafts, and the Wildfire bridge.

Required manual checks when convenient:

1. Form an independent settlement, deposit currency, collect tax, recruit units, and verify every change uses the same displayed State Treasury.
2. Form a faction and verify the independent balance transfers once to the new faction state.
3. Send aid, hire mercenaries, pay upkeep, negotiate lump treasury transfers, and process reparations.
4. Advance Village to Town, Town to City, and City to Major City using population, Stability, housing, and capture zones; no Prosperity requirement should appear.
5. Complete character creation in two steps and change Main Seat directly on the Character Sheet.
