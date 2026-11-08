# Commands

Use in-game completion for the complete command tree.

- `/campaign` opens the campaign workspace.
- `/character open|create|seat` manages the player character. Players cannot alter marriage state.
- `/settlements open|list|create <settlement name>|rename|...` manages settlements, locations, members, claims, housing, capture zones, and banners.
- `/settlements admin marriage officiate <first> <second>` records an administrator-officiated marriage.
- `/settlements admin marriage dissolve <player>` dissolves an existing marriage and its political alliance effects.
- `/settlements treasury <id> <amount>` adds State Treasury funds for operators.
- `/settlements admin stats set|add <settlement> treasury <amount>` changes a settlement polity treasury.
- `/settlements admin faction treasury set|add <faction> <amount>` changes a faction treasury.

Settlement managers control automatic immigration from the Residents tab; the global switch and limits live in server configuration.

The `wealth` command, player marriage commands, separately editable Main Capture Zone, and obsolete stat aliases no longer exist. Prosperity, settler-contract, Levy Tradition, recurring-aid, and recurring-reparation commands do not exist.
