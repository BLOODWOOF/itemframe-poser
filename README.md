# Item Frame Poser

Pose items in item frames with rotation, position, and scale controls.

Look at an item frame and press **K** to open the editor. You can rotate the item, move it around, scale it, save poses as presets, and copy poses between frames. The world keeps running while the menu is open, so you can see your changes as you make them.

## Features

- Rotate items on the X, Y, and Z axes
- Move items on the X, Y, and Z axes
- Uniform item scaling
- Toggle item frames as invisible, fixed, glowing, or invulnerable
- Copy, paste, and reset poses
- Save and load named presets
- Create and edit groups of item frames
- Works with vanilla item frames and [Fast Item Frames](https://modrinth.com/mod/fast-item-frames)
- Multiplayer pose synchronization
- Players without the mod still see item frames normally

## How to use

### Opening the editor

Look at an item frame from close range and press **K**.

The keybind can be changed in:

**Options → Controls → Item Frame Poser**

The editor stays on the left side of the screen so the item frame remains visible while you're editing it.

### Adjusting values

Scroll over a value to change it.

- **Shift** for larger adjustments
- **Ctrl** for smaller adjustments

### Groups

Groups let you edit multiple item frames together.

Select a numbered group, look at another item frame, and press **K** to add or remove it. You can also manage group members from the list in the editor.

**Pose Group** copies the current pose to every frame in the group.

Enable **Group Edit** to apply slider changes to the entire group.

### Maps

Groups work with multi-frame map displays as well. Scaling a group of maps scales the map artwork from the frame being edited, keeping the tiles aligned.

Other items in a group continue to scale from their individual frames.

### Glowing frames

Turning on **Glowing** uses one Glow Ink Sac per frame in Survival.

Creative mode does not consume Glow Ink Sacs.

### Multiplayer

Other players using Item Frame Poser can see your custom poses.

The following frame properties require Item Frame Poser to also be installed on the server:

- Invisible
- Fixed
- Glowing
- Invulnerable

Pose data such as rotation, position, and scale can still be seen by other players using the mod when the mod is only installed on the client.

Players without the mod will see the item frame normally.

For multiplayer pose synchronization, everyone using the mod should be running the latest version.

### Fast Item Frames

Sneak right-click is left unchanged so that [Fast Item Frames](https://modrinth.com/mod/fast-item-frames) can continue using it to hide item frames.

## Requirements

- Minecraft 26.2
- [Fabric Loader](https://fabricmc.net/)
- [Fabric API](https://modrinth.com/mod/fabric-api)

## License

[MIT License](LICENSE)
