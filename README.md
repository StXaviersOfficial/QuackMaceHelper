# QuackMaceHelper v5.0.0 (Fabric 1.21.11)

Client-side Mace PvP utility mod for Minecraft Java Edition **1.21.11** (Fabric), engineered for **MJ Launcher** (PojavLauncher / Mojo v3) on Android with **Holy Gl4es (GL4ES)** rendering.

---

## How to Build via GitHub Actions (No PC / Gradle Wrapper Required)

1. **Unzip the archive**: Extract `quackmacehelper-5.0.0-src.zip`. Inside you will find the `QuackMaceHelper-5.0.0/` folder containing `.github/`, `src/`, `build.gradle`, `settings.gradle`, `gradle.properties`, and `README.md`.
2. **Create a GitHub repository**: Go to GitHub and create a new repository (public or private).
3. **Upload the project files**: Upload the **contents** of `QuackMaceHelper-5.0.0/` directly to the root of your repository so that `build.gradle`, `settings.gradle`, and `.github/workflows/build.yml` sit at the repository root.
4. **Push / Commit**: Commit the uploaded files to your branch (`main` or `master`).
5. **Let GitHub Actions compile the mod**:
   - Open the **Actions** tab in your GitHub repository.
   - The `build` workflow runs automatically on every push (or you can trigger it manually via **Run workflow**).
   - GitHub Actions provisions Temurin JDK 25 and Gradle 9.8.2, compiles the project against Yarn `1.21.11+build.6` with `--release 21`, and bundles Cloth Config automatically.
6. **Download and install the built `.jar`**:
   - Once the workflow turns green, scroll to the bottom of the workflow summary page and download the **Artifacts** zip (`Artifacts.zip`).
   - Extract `Artifacts.zip` and copy `quackmacehelper-5.0.0+1.21.11.jar` (not the `-sources.jar`) into `.minecraft/mods/` inside your MJ Launcher 1.21.11 Fabric instance.

---

## Technical Decisions & Architecture Notes

- **GL4ES Compatibility**: This mod performs zero custom OpenGL shader calls or incompatible framebuffer binds, keeping it 100% stable under **Holy Gl4es (GL4ES)** on ARM Mali-G68 MC4.
- **Real Player Actions Only**:
  - Slot changes call `player.getInventory().setSelectedSlot(slot)` immediately followed by `networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot))` so same-tick swaps during `END_CLIENT_TICK` or attack hooks resolve with the newly selected item on the server.
  - Attacks strictly use `client.interactionManager.attackEntity(player, target)` followed by `player.swingHand(Hand.MAIN_HAND)`.
  - Item usages (throwing wind charges, right-click swapping chestplate/elytra from hotbar) call `client.interactionManager.interactItem(player, hand)`.
  - Shield holding uses genuine keybind state ownership (`options.useKey.setPressed(...)`) guarded by a strict ownership flag (`holdingUseKey`) so normal eating, bow drawing, and manual shield blocking are never interrupted when the feature is idle.
- **1.21.11 Spear & Chestplate Detection**:
  - Swords are detected via `stack.isIn(ItemTags.SWORDS)`.
  - Axes are detected via `stack.getItem() instanceof AxeItem`.
  - Spears are detected via registry path matching (`Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_spear")` or `"spear"`) and filtered to ensure they do **not** have the `lunge` enchantment.
  - Chestplates are detected via `stack.isIn(ItemTags.CHEST_ARMOR)` and `DataComponentTypes.EQUIPPABLE` (`EquipmentSlot.CHEST`, excluding `Items.ELYTRA`).
- **Mace Damage Calculator**:
  - Evaluates all maces in hotbar slots `0..8` for the current fall height (`attacker.getY() - target.getY()`):
  - Base mace damage (`6.0`) + smash height bonus (`+4 HP/block` for first 3 blocks, `+2 HP/block` for next 5 blocks, `+1 HP/block` beyond) + `Density` (`+0.5 * level * blocksFallen`).
  - Evaluates target armor and armor toughness reduction using vanilla armor damage math, applying `Breach` (`-15%` armor effectiveness per level, clamped to `[0, 1]`).
  - Selects the hotbar slot yielding the highest post-armor effective damage.
- **Collision Priority Hierarchy**:
  1. **Master Kill Switch Off**: Completely disables all ticks, hooks, mouse deltas, and feature keybinds; only the Open Menu key and ModMenu integration remain active.
  2. **Always-On Shield Triggered**: Suspends all other features (no slot switches, no attacks) and holds the shield lock until the threat drops to `<= 2.0` blocks above the player, the feature is toggled off, or the player manually changes hotbar slots.
  3. **Gliding vs. Falling Split**: While `player.isGliding()`, only **Elytra Mace** may engage (`Auto Mace` stays idle). While falling (`!player.isGliding()` and `>= 3.0` blocks above target), **Auto Mace** owns the falling engagement.
  4. **Shield Draining Priority**: When the target's shield is raised and **Shield Draining** is enabled (and player is `3+` blocks above within range), Shield Draining owns all attack output at its configured CPS (`20..250`), suppressing Stun Slam's axe phase and Auto Mace's attack burst while allowing aim to continue.
  5. **Aim Ownership**: Features with dedicated aim (**Pearl Catch** snap, **Always-On Shield**, **Elytra Mace**, **Auto Mace**) suspend global **Aim Assist** while active and cleanly release aim state when finished. Pearl Catch's single-tick snap overrides combat aim for the throw tick and immediately yields back.
  6. **Attribute Swap vs. Stun Slam**: If both trigger on the same melee attack, **Attribute Swap** executes the same-tick swap chain first and queues **Stun Slam** behind it so slots never flicker.
