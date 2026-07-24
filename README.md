# DAOT Aeronautics Compat

A small client-side compatibility mod that makes **Danny's AOT** grappling hooks work
correctly on **Create: Aeronautics** / **Sable** airships.

Without it, a hook that grabs a moving sub-level keeps its original world coordinate, so the
rope drifts and the player gets flung in the wrong direction. This mod follows the grabbed
point as the airship moves, keeping the hook attached where you aimed.

**Author:** armorberserk

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.x
- [Sinytra Connector](https://modrinth.com/mod/connector) + Forgified Fabric API
- [Sable](https://modrinth.com/mod/sable)
- [Danny's AOT](https://modrinth.com/mod/dannys-aot) (loaded through Connector)
- [Create: Aeronautics](https://modrinth.com/mod/create-aeronautics) (optional)

Drop the jar into `mods/` alongside the above. If AOT or Sable is missing the mod just stays
idle, it never crashes the game.

## Building

```
./gradlew build
```

The jar lands in `build/libs/`.

## License

Copyright (c) 2026 armorberserk. All rights reserved. See [LICENSE](LICENSE).
