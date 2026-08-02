# Heracles demo quests

These files use the Heracles 1.21 quest format and are migration fixtures for
the 26.2 port. Copy the contents of `config/` into the development run's
`run/config/` directory once the quest loader has been re-enabled.

The demo contains three quests:

1. `welcome` exercises a dummy task through `/heracles dummy demo_welcome`.
2. `gather_logs` depends on `welcome`, tracks eight oak logs, and rewards bread.
3. `craft_table` also depends on `welcome`, creating a second tree branch.

They intentionally cover file loading, groups, dependencies, server-to-client
synchronization, commands, automatic task progress, and reward claiming.
