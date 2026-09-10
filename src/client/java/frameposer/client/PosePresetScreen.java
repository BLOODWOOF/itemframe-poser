package frameposer.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PosePresetScreen extends Screen {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int MUTED = 0xFFA0A0A0;
	private static final int PANEL = 0xC0101010;
	private static final int PANEL_W = 340;
	private static final int PANEL_H = 196;
	private static final int ROWS = 5;

	private final FramePoserScreen parent;
	private final List<PosePresets.SavedPose> poses;
	private int selected = -1;
	private int offset;
	private EditBox nameField;
	private final List<Button> rowButtons = new ArrayList<>();
	private int panelX;
	private int panelY;

	public PosePresetScreen(FramePoserScreen parent) {
		super(Component.translatable("frameposer.gui.presets.title"));
		this.parent = parent;
		this.poses = PosePresets.load();
	}

	@Override
	protected void init() {
		this.rowButtons.clear();
		this.panelX = 8;
		this.panelY = Math.max(8, (this.height - PANEL_H) / 2);

		int listX = this.panelX + 12;
		int y = this.panelY + 28;
		for (int i = 0; i < ROWS; i++) {
			int row = i;
			Button button = Button.builder(Component.empty(), clicked -> this.pick(row))
				.bounds(listX, y, 176, 20)
				.build();
			this.rowButtons.add(button);
			this.addRenderableWidget(button);
			y += 22;
		}

		int sideX = this.panelX + 198;
		this.nameField = new EditBox(this.font, sideX, this.panelY + 40, 128, 20, Component.translatable("frameposer.gui.presets.name"));
		this.nameField.setMaxLength(32);
		this.nameField.setHint(Component.translatable("frameposer.gui.presets.name"));
		this.addRenderableWidget(this.nameField);

		this.addRenderableWidget(Button.builder(Component.translatable("frameposer.gui.presets.save"), button -> this.saveNew())
			.bounds(sideX, this.panelY + 68, 128, 20)
			.build());
		this.addRenderableWidget(Button.builder(Component.translatable("frameposer.gui.presets.load"), button -> this.loadSelected())
			.bounds(sideX, this.panelY + 92, 128, 20)
			.build());
		this.addRenderableWidget(Button.builder(Component.translatable("frameposer.gui.presets.delete"), button -> this.deleteSelected())
			.bounds(sideX, this.panelY + 116, 128, 20)
			.build());

		int navY = this.panelY + 28 + ROWS * 22;
		this.addRenderableWidget(Button.builder(Component.literal("<"), button -> this.scroll(-1))
			.bounds(listX, navY, 20, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal(">"), button -> this.scroll(1))
			.bounds(listX + 156, navY, 20, 20).build());

		this.addRenderableWidget(Button.builder(Component.translatable("frameposer.gui.done"), button -> this.onClose())
			.bounds(this.panelX + PANEL_W / 2 - 40, this.panelY + PANEL_H - 26, 80, 20)
			.build());

		this.refreshRows();
	}

	private void pick(int row) {
		int index = this.offset + row;
		if (index >= 0 && index < this.poses.size()) {
			this.selected = index;
			this.nameField.setValue(this.poses.get(index).name());
			this.refreshRows();
		}
	}

	private void scroll(int dir) {
		int max = Math.max(0, this.poses.size() - ROWS);
		this.offset = Math.max(0, Math.min(max, this.offset + dir));
		this.refreshRows();
	}

	private void refreshRows() {
		for (int i = 0; i < this.rowButtons.size(); i++) {
			int index = this.offset + i;
			Button row = this.rowButtons.get(i);
			if (index < this.poses.size()) {
				String name = this.poses.get(index).name();
				row.setMessage(Component.literal(index == this.selected ? "> " + name : name));
				row.active = true;
			} else {
				row.setMessage(Component.empty());
				row.active = false;
			}
		}
	}

	private void saveNew() {
		String name = this.nameField.getValue().trim();
		if (name.isEmpty()) {
			name = "Pose " + (this.poses.size() + 1);
		}
		var snap = this.parent.snapshot();
		this.poses.add(new PosePresets.SavedPose(name, snap.pose(), snap.invisible()));
		PosePresets.saveAll(this.poses);
		this.selected = this.poses.size() - 1;
		this.offset = Math.max(0, this.poses.size() - ROWS);
		this.refreshRows();
	}

	private void loadSelected() {
		if (this.selected < 0 || this.selected >= this.poses.size()) {
			return;
		}
		this.parent.applyPreset(this.poses.get(this.selected));
		this.onClose();
	}

	private void deleteSelected() {
		if (this.selected < 0 || this.selected >= this.poses.size()) {
			return;
		}
		this.poses.remove(this.selected);
		this.selected = -1;
		PosePresets.saveAll(this.poses);
		this.scroll(0);
		this.refreshRows();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.panelX;
		int y = this.panelY;
		graphics.fill(x, y, x + PANEL_W, y + PANEL_H, PANEL);
		graphics.outline(x, y, PANEL_W, PANEL_H, 0x40FFFFFF);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int x = this.panelX;
		int y = this.panelY;
		graphics.centeredText(this.font, this.title, x + PANEL_W / 2, y + 8, WHITE);
		graphics.text(this.font, Component.translatable("frameposer.gui.presets.name"), x + 198, y + 28, MUTED, false);
		if (this.poses.isEmpty()) {
			graphics.text(this.font, Component.translatable("frameposer.gui.presets.empty"), x + 16, y + 88, MUTED, false);
		}
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
