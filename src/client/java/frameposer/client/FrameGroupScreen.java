package frameposer.client;

import frameposer.FrameGroupIds;
import frameposer.FrameHandle;
import frameposer.FrameLookup;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class FrameGroupScreen extends Screen {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int MUTED = 0xFFA0A0A0;
	private static final int PANEL = 0xC0101010;
	private static final int SELECTED = 0x40FFFFFF;
	private static final int PANEL_W = 380;
	private static final int PANEL_H = 252;
	private static final int ROWS = 6;

	private final FramePoserScreen parent;
	private final List<FrameGroups.Nearby> nearby = new ArrayList<>();
	private final List<Button> rowButtons = new ArrayList<>();
	private final List<Button> toggleButtons = new ArrayList<>();
	private final Button[] groupButtons = new Button[8];
	private NumberFieldBox rangeField;
	private Button editButton;
	private Button locateButton;
	private Button modifyButton;
	private FrameHandle selected;
	private int offset;
	private int panelX;
	private int panelY;

	public FrameGroupScreen(FramePoserScreen parent) {
		super(Component.translatable("frameposer.gui.groups.title"));
		this.parent = parent;
		if (parent.snapshot() != null) {
			this.selected = parent.snapshot().handle();
		}
	}

	@Override
	protected void init() {
		this.rowButtons.clear();
		this.toggleButtons.clear();
		this.panelX = 8;
		this.panelY = Math.max(8, (this.height - PANEL_H) / 2);

		int x = this.panelX + 12;
		int y = this.panelY + 24;
		for (int i = 0; i < FrameGroupIds.ALL.length; i++) {
			int index = i;
			String id = FrameGroupIds.ALL[i];
			ChatFormatting color = FrameGroups.COLORS[i];
			Button button = Button.builder(groupLabel(id, color), clicked -> {
				FrameGroups.select(id);
				this.refresh();
			}).bounds(x + i * 28, y, 26, 20)
				.tooltip(Tooltip.create(Component.translatable("frameposer.gui.tooltip.group_slot", id)))
				.build();
			this.groupButtons[i] = button;
			this.addRenderableWidget(button);
		}

		this.rangeField = new NumberFieldBox(this.font, this.panelX + 268, y, 44, 20, 1.0F, 4.0F, 32.0F);
		this.rangeField.setFloatValue(FrameGroups.range());
		this.rangeField.setResponder(text -> {
			FrameGroups.setRange(Math.round(this.rangeField.floatValue(FrameGroups.range())));
			this.reloadNearby();
			this.refreshRows();
		});
		this.rangeField.setTooltip(Tooltip.create(Component.translatable("frameposer.gui.tooltip.range")));
		this.addRenderableWidget(this.rangeField);

		int listY = this.panelY + 52;
		for (int i = 0; i < ROWS; i++) {
			int row = i;
			Button pick = Button.builder(Component.empty(), clicked -> this.selectRow(row))
				.bounds(x, listY, 230, 20)
				.build();
			this.rowButtons.add(pick);
			this.addRenderableWidget(pick);
			Button toggle = Button.builder(Component.literal("+"), clicked -> this.toggleRow(row))
				.bounds(x + 234, listY, 20, 20)
				.build();
			this.toggleButtons.add(toggle);
			this.addRenderableWidget(toggle);
			listY += 22;
		}

		int navY = this.panelY + 52 + ROWS * 22;
		this.addRenderableWidget(Button.builder(Component.literal("<"), button -> this.scroll(-1))
			.bounds(x, navY, 20, 20).build());
		this.locateButton = Button.builder(Component.translatable("frameposer.gui.groups.locate"), button -> this.locate())
			.bounds(x + 24, navY, 100, 20)
			.tooltip(Tooltip.create(Component.translatable("frameposer.gui.tooltip.locate")))
			.build();
		this.addRenderableWidget(this.locateButton);
		this.modifyButton = Button.builder(Component.translatable("frameposer.gui.groups.modify"), button -> this.modifySelected())
			.bounds(x + 128, navY, 100, 20)
			.tooltip(Tooltip.create(Component.translatable("frameposer.gui.tooltip.modify")))
			.build();
		this.addRenderableWidget(this.modifyButton);
		this.addRenderableWidget(Button.builder(Component.literal(">"), button -> this.scroll(1))
			.bounds(x + 234, navY, 20, 20).build());

		int bottom = this.panelY + PANEL_H - 26;
		this.addRenderableWidget(Button.builder(Component.translatable("frameposer.gui.groups.pose"), button -> this.parent.poseGroup())
			.bounds(x, bottom, 88, 20)
			.tooltip(Tooltip.create(Component.translatable("frameposer.gui.tooltip.pose_group")))
			.build());
		this.editButton = Button.builder(editLabel(), button -> {
			FrameGroups.setEditMode(!FrameGroups.editMode());
			button.setMessage(editLabel());
		}).bounds(x + 92, bottom, 100, 20)
			.tooltip(Tooltip.create(Component.translatable("frameposer.gui.tooltip.group_edit")))
			.build();
		this.addRenderableWidget(this.editButton);
		this.addRenderableWidget(Button.builder(Component.translatable("frameposer.gui.groups.clear"), button -> {
			if (this.minecraft != null && this.minecraft.level != null) {
				FrameGroups.clearGroup(this.minecraft.level, FrameGroups.selected());
				this.refresh();
			}
		}).bounds(x + 196, bottom, 58, 20)
			.tooltip(Tooltip.create(Component.translatable("frameposer.gui.tooltip.clear_group")))
			.build());
		this.addRenderableWidget(Button.builder(Component.translatable("frameposer.gui.done"), button -> this.onClose())
			.bounds(this.panelX + PANEL_W - 70, bottom, 58, 20).build());

		this.reloadNearby();
		this.refresh();
		this.applyGlow();
	}

	public void refresh() {
		this.reloadNearby();
		this.ensureVisible();
		this.refreshRows();
		for (int i = 0; i < this.groupButtons.length; i++) {
			if (this.groupButtons[i] != null) {
				String id = FrameGroupIds.ALL[i];
				this.groupButtons[i].setMessage(groupLabel(id, FrameGroups.COLORS[i]));
			}
		}
		if (this.editButton != null) {
			this.editButton.setMessage(editLabel());
		}
		this.updateButtons();
	}

	public void notice(FrameHandle handle) {
		this.selected = handle;
		this.applyGlow();
		this.refresh();
	}

	private void reloadNearby() {
		this.nearby.clear();
		if (this.minecraft == null || this.minecraft.level == null || this.parent.snapshot() == null) {
			return;
		}
		Vec3 origin = FrameLookup.origin(this.minecraft.level, this.parent.snapshot().handle());
		if (origin == null && this.minecraft.player != null) {
			origin = this.minecraft.player.position();
		}
		if (origin == null) {
			return;
		}
		this.nearby.addAll(FrameGroups.nearby(this.minecraft.level, origin, FrameGroups.range()));
		int max = Math.max(0, this.nearby.size() - ROWS);
		this.offset = Math.max(0, Math.min(max, this.offset));
	}

	private void refreshRows() {
		String group = FrameGroups.selected();
		for (int i = 0; i < this.rowButtons.size(); i++) {
			int index = this.offset + i;
			Button row = this.rowButtons.get(i);
			Button toggle = this.toggleButtons.get(i);
			if (index < this.nearby.size()) {
				FrameGroups.Nearby entry = this.nearby.get(index);
				boolean inGroup = FrameGroups.contains(group, entry.key());
				row.setMessage(rowLabel(entry, this.same(entry.handle())));
				row.active = true;
				toggle.setMessage(Component.literal(inGroup ? "-" : "+"));
				toggle.active = true;
			} else {
				row.setMessage(Component.empty());
				row.active = false;
				toggle.setMessage(Component.literal("+"));
				toggle.active = false;
			}
		}
	}

	private void selectRow(int row) {
		int index = this.offset + row;
		if (index < 0 || index >= this.nearby.size()) {
			return;
		}
		this.selected = this.nearby.get(index).handle();
		this.applyGlow();
		this.refreshRows();
		this.updateButtons();
	}

	private void locate() {
		if (this.selected == null || this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) {
			return;
		}
		this.applyGlow();
		Vec3 target = lookTarget(this.selected);
		if (target != null) {
			// armor poser does this for stands, we just look at the frame
			this.minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
		}
	}

	private void modifySelected() {
		if (this.selected == null) {
			return;
		}
		this.parent.jumpTo(this.selected);
		this.onClose();
	}

	private void toggleRow(int row) {
		int index = this.offset + row;
		if (index < 0 || index >= this.nearby.size() || this.minecraft == null || this.minecraft.level == null) {
			return;
		}
		FrameGroups.toggle(this.minecraft.level, this.nearby.get(index).handle());
		this.refresh();
	}

	private void scroll(int dir) {
		int max = Math.max(0, this.nearby.size() - ROWS);
		this.offset = Math.max(0, Math.min(max, this.offset + dir));
		this.refreshRows();
	}

	private void ensureVisible() {
		if (this.selected == null) {
			return;
		}
		for (int i = 0; i < this.nearby.size(); i++) {
			if (this.same(this.nearby.get(i).handle())) {
				if (i < this.offset) {
					this.offset = i;
				} else if (i >= this.offset + ROWS) {
					this.offset = i - ROWS + 1;
				}
				return;
			}
		}
	}

	private void applyGlow() {
		FrameGlowHandler.select(this.selected);
	}

	private void updateButtons() {
		boolean hasPick = this.selected != null;
		if (this.locateButton != null) {
			this.locateButton.active = hasPick;
		}
		if (this.modifyButton != null) {
			this.modifyButton.active = hasPick;
		}
	}

	private Vec3 lookTarget(FrameHandle handle) {
		if (this.minecraft == null || this.minecraft.level == null) {
			return null;
		}
		if (handle.block()) {
			return FrameLookup.origin(this.minecraft.level, handle);
		}
		Entity entity = this.minecraft.level.getEntity(handle.entityId());
		return entity == null ? null : entity.getBoundingBox().getCenter();
	}

	private boolean same(FrameHandle handle) {
		return this.selected != null && this.selected.equals(handle);
	}

	private static Component groupLabel(String id, ChatFormatting color) {
		String text = id.equals(FrameGroups.selected()) ? "[" + id + "]" : id;
		return Component.literal(text).withStyle(color);
	}

	private static Component editLabel() {
		return Component.translatable(FrameGroups.editMode() ? "frameposer.gui.groups.edit_on" : "frameposer.gui.groups.edit");
	}

	private static Component rowLabel(FrameGroups.Nearby entry, boolean picked) {
		ItemStack item = entry.item();
		String name = item == null || item.isEmpty()
			? "Empty"
			: item.getHoverName().getString();
		if (picked) {
			name = "> " + name;
		}
		if (name.length() > 22) {
			name = name.substring(0, 21) + "..";
		}
		if (entry.hidden()) {
			name = "(" + name + ")";
		}
		return Component.literal(name);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.rangeField != null && this.rangeField.isHovered()) {
			this.rangeField.nudge(scrollY);
			FrameGroups.setRange(Math.round(this.rangeField.floatValue(FrameGroups.range())));
			this.reloadNearby();
			this.refreshRows();
			return true;
		}
		this.scroll(scrollY > 0 ? -1 : 1);
		return true;
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
		graphics.text(this.font, Component.translatable("frameposer.gui.groups.range"), x + 236, y + 30, MUTED, false);
		if (this.nearby.isEmpty()) {
			graphics.text(this.font, Component.translatable("frameposer.gui.groups.empty"), x + 16, y + 88, MUTED, false);
		}
		int listY = y + 52;
		for (int i = 0; i < ROWS; i++) {
			int index = this.offset + i;
			if (index < this.nearby.size()) {
				if (this.same(this.nearby.get(index).handle())) {
					graphics.fill(x + 12, listY, x + 242, listY + 20, SELECTED);
				}
				ItemStack item = this.nearby.get(index).item();
				if (item != null && !item.isEmpty()) {
					graphics.item(item, x + 14, listY + 2);
				}
			}
			listY += 22;
		}
		graphics.text(
			this.font,
			Component.translatable("frameposer.gui.groups.selected", FrameGroups.selected()),
			x + 12,
			y + PANEL_H - 42,
			MUTED,
			false
		);
	}

	@Override
	public void onClose() {
		FrameGlowHandler.clear();
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
