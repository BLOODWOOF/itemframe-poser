package frameposer.client;

import frameposer.FrameHandle;
import frameposer.FrameLookup;
import frameposer.FramePose;
import frameposer.FrameSnapshot;
import frameposer.GlowInk;
import frameposer.net.UpdateFramePosePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class FramePoserScreen extends Screen {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int MUTED = 0xFFA0A0A0;
	private static final int PANEL = 0xC0101010;
	private static final int SLOT = 0x80000000;
	private static final int PANEL_W = 360;
	private static final int PANEL_H = 218;
	private static FrameSnapshot clipboard;

	private FrameSnapshot snapshot;
	private final NumberFieldBox[] fields = new NumberFieldBox[7];
	private Button invisibleButton;
	private Button fixedButton;
	private Button glowingButton;
	private Button invulnerableButton;
	private boolean sending;
	private int panelX;
	private int panelY;
	private int sendDelay;
	private boolean packetWaiting;
	private boolean lastNetworked = true;
	private boolean lastCanGlow = true;

	public FramePoserScreen(FrameHandle handle) {
		super(Component.translatable("frameposer.gui.title"));
		Minecraft minecraft = Minecraft.getInstance();
		this.snapshot = FrameLookup.snapshot(minecraft.level, handle);
		if (this.snapshot != null && minecraft.level != null) {
			this.snapshot = this.snapshot.withPose(ClientFramePoses.overlay(minecraft.level, handle, this.snapshot.pose()));
		}
	}

	public static FramePoserScreen current;

	public static boolean owns(FrameHandle handle) {
		return current != null && current.snapshot != null && current.snapshot.handle().equals(handle);
	}

	public boolean isValid() {
		return this.snapshot != null;
	}

	public void retarget(FrameHandle from, FrameHandle to) {
		if (this.snapshot != null && this.snapshot.handle().equals(from)) {
			this.snapshot = this.snapshot.withHandle(to);
		}
	}

	@Override
	protected void init() {
		if (this.snapshot == null) {
			this.onClose();
			return;
		}

		current = this;
		// sit on the left so the frame stays visible in the middle of the view
		this.panelX = 8;
		this.panelY = Math.max(8, (this.height - PANEL_H) / 2);

		int left = this.panelX + 12;
		int right = this.panelX + 168;
		int toggleX = left + 92;
		int y = this.panelY + 68;

		this.invisibleButton = this.toggle(toggleX, y, this.snapshot.invisible(), "frameposer.gui.tooltip.invisible", value -> {
			this.snapshot = this.snapshot.withInvisible(value);
		});
		y += 22;
		this.fixedButton = this.toggle(toggleX, y, this.snapshot.pose().fixed(), "frameposer.gui.tooltip.fixed", value -> {
			this.snapshot = this.snapshot.withPose(this.snapshot.pose().withFixed(value));
		});
		y += 22;
		this.glowingButton = this.toggle(toggleX, y, this.snapshot.glowing(), "frameposer.gui.tooltip.glowing.ok", value -> {
			this.snapshot = this.snapshot.withGlowing(value);
		});
		y += 22;
		this.invulnerableButton = this.toggle(toggleX, y, this.snapshot.pose().invulnerable(), "frameposer.gui.tooltip.invulnerable", value -> {
			this.snapshot = this.snapshot.withPose(this.snapshot.pose().withInvulnerable(value));
		});

		float[] values = {
			this.snapshot.pose().rotX(),
			this.snapshot.pose().rotY(),
			this.snapshot.pose().rotZ(),
			this.snapshot.pose().offX(),
			this.snapshot.pose().offY(),
			this.snapshot.pose().offZ(),
			this.snapshot.pose().scale()
		};
		float[] steps = {1.0F, 1.0F, 1.0F, 0.05F, 0.05F, 0.05F, 0.05F};
		float[] mins = {-360.0F, -360.0F, -360.0F, -4.0F, -4.0F, -4.0F, 0.05F};
		float[] maxs = {360.0F, 360.0F, 360.0F, 4.0F, 4.0F, 4.0F, 8.0F};
		int[] fieldYs = {this.panelY + 38, this.panelY + 60, this.panelY + 82, this.panelY + 116, this.panelY + 138, this.panelY + 160, this.panelY + 116};
		int[] fieldXs = {right + 12, right + 12, right + 12, right + 12, right + 12, right + 12, right + 108};

		for (int i = 0; i < this.fields.length; i++) {
			this.addAxis(fieldXs[i], fieldYs[i], i, values[i], steps[i], mins[i], maxs[i]);
		}

		int bottom = this.panelY + PANEL_H - 26;
		this.addRenderableWidget(Button.builder(Component.translatable("frameposer.gui.copy"), button -> this.copy()).bounds(left, bottom, 50, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("frameposer.gui.paste"), button -> this.paste()).bounds(left + 54, bottom, 50, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("frameposer.gui.reset"), button -> this.reset()).bounds(left + 108, bottom, 50, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("frameposer.gui.presets"), button -> {
			if (this.minecraft != null) {
				this.minecraft.gui.setScreen(new PosePresetScreen(this));
			}
		}).bounds(left + 162, bottom, 58, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("frameposer.gui.done"), button -> this.onClose())
			.bounds(this.panelX + PANEL_W - 70, bottom, 58, 20).build());

		this.refreshGlowButton(true);
	}

	private void addAxis(int x, int y, int index, float value, float step, float min, float max) {
		NumberFieldBox field = new NumberFieldBox(this.font, x + 16, y, 52, 20, step, min, max);
		field.setFloatValue(value);
		field.setResponder(text -> this.markDirty());
		field.setTooltip(Tooltip.create(Component.translatable("frameposer.gui.tooltip.scroll")));
		this.fields[index] = field;
		this.addRenderableWidget(Button.builder(Component.literal("-"), button -> {
			field.stepBy(-1);
			this.markDirty();
		}).bounds(x, y, 14, 20).build());
		this.addRenderableWidget(field);
		this.addRenderableWidget(Button.builder(Component.literal("+"), button -> {
			field.stepBy(1);
			this.markDirty();
		}).bounds(x + 70, y, 14, 20).build());
	}

	private Button toggle(int x, int y, boolean value, String tooltipKey, java.util.function.Consumer<Boolean> setter) {
		Button button = Button.builder(onOff(value), clicked -> {
			boolean next = !labelIsOn(clicked.getMessage());
			clicked.setMessage(onOff(next));
			setter.accept(next);
			this.flushNow();
		}).bounds(x, y, 44, 20).tooltip(Tooltip.create(Component.translatable(tooltipKey))).build();
		this.addRenderableWidget(button);
		return button;
	}

	private static boolean labelIsOn(Component message) {
		return message.getString().equals(I18n.get("frameposer.gui.on"));
	}

	private static Component onOff(boolean value) {
		return Component.translatable(value ? "frameposer.gui.on" : "frameposer.gui.off");
	}

	private void markDirty() {
		if (this.sending) {
			return;
		}
		this.snapshot = this.snapshot.withPose(this.readPose());
		this.applyLocal();
		this.packetWaiting = true;
		this.sendDelay = 2;
	}

	private FramePose readPose() {
		FramePose current = this.snapshot.pose();
		return new FramePose(
			this.fields[0].floatValue(current.rotX()),
			this.fields[1].floatValue(current.rotY()),
			this.fields[2].floatValue(current.rotZ()),
			this.fields[3].floatValue(current.offX()),
			this.fields[4].floatValue(current.offY()),
			this.fields[5].floatValue(current.offZ()),
			this.fields[6].floatValue(current.scale()),
			current.fixed(),
			current.invulnerable()
		).sanitized();
	}

	void applyPreset(PosePresets.SavedPose preset) {
		this.sending = true;
		this.snapshot = this.snapshot.withPose(preset.pose()).withInvisible(preset.invisible());
		this.syncWidgets();
		this.sending = false;
		this.flushNow();
	}

	FrameSnapshot snapshot() {
		return this.snapshot.withPose(this.readPose());
	}

	private void copy() {
		clipboard = this.snapshot();
	}

	private void paste() {
		if (clipboard == null) {
			return;
		}
		this.sending = true;
		this.snapshot = this.snapshot
			.withPose(clipboard.pose())
			.withInvisible(clipboard.invisible());
		this.syncWidgets();
		this.sending = false;
		this.flushNow();
	}

	private void reset() {
		this.sending = true;
		this.snapshot = this.snapshot.withPose(FramePose.IDENTITY).withInvisible(false).withGlowing(this.snapshot.glowing());
		this.syncWidgets();
		this.sending = false;
		this.flushNow();
	}

	private void syncWidgets() {
		this.invisibleButton.setMessage(onOff(this.snapshot.invisible()));
		this.fixedButton.setMessage(onOff(this.snapshot.pose().fixed()));
		this.glowingButton.setMessage(onOff(this.snapshot.glowing()));
		this.invulnerableButton.setMessage(onOff(this.snapshot.pose().invulnerable()));
		this.fields[0].setFloatValue(this.snapshot.pose().rotX());
		this.fields[1].setFloatValue(this.snapshot.pose().rotY());
		this.fields[2].setFloatValue(this.snapshot.pose().rotZ());
		this.fields[3].setFloatValue(this.snapshot.pose().offX());
		this.fields[4].setFloatValue(this.snapshot.pose().offY());
		this.fields[5].setFloatValue(this.snapshot.pose().offZ());
		this.fields[6].setFloatValue(this.snapshot.pose().scale());
		this.refreshGlowButton(true);
	}

	private void refreshGlowButton(boolean force) {
		if (this.glowingButton == null || this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		Player player = this.minecraft.player;
		boolean canGlow = this.snapshot.glowing() || GlowInk.hasSac(player);
		boolean networked = ClientPlayNetworking.canSend(UpdateFramePosePayload.TYPE);
		if (!force && canGlow == this.lastCanGlow && networked == this.lastNetworked) {
			return;
		}
		this.lastCanGlow = canGlow;
		this.lastNetworked = networked;
		this.invisibleButton.active = networked;
		this.fixedButton.active = networked;
		this.invulnerableButton.active = networked;
		this.glowingButton.active = networked && canGlow;
		if (!networked) {
			Tooltip missing = Tooltip.create(Component.translatable("frameposer.gui.tooltip.client_only"));
			this.invisibleButton.setTooltip(missing);
			this.fixedButton.setTooltip(missing);
			this.invulnerableButton.setTooltip(missing);
			this.glowingButton.setTooltip(missing);
			return;
		}
		this.invisibleButton.setTooltip(Tooltip.create(Component.translatable("frameposer.gui.tooltip.invisible")));
		this.fixedButton.setTooltip(Tooltip.create(Component.translatable("frameposer.gui.tooltip.fixed")));
		this.invulnerableButton.setTooltip(Tooltip.create(Component.translatable("frameposer.gui.tooltip.invulnerable")));
		this.glowingButton.setTooltip(Tooltip.create(Component.translatable(
			canGlow ? "frameposer.gui.tooltip.glowing.ok" : "frameposer.gui.tooltip.glowing"
		)));
	}

	private void applyLocal() {
		if (this.minecraft == null || this.minecraft.level == null) {
			return;
		}
		ClientFramePoses.store(this.minecraft.level, this.snapshot.handle(), this.snapshot.pose());
	}

	private void flushNow() {
		this.applyLocal();
		this.sendPacket();
		this.packetWaiting = false;
		this.sendDelay = 0;
	}

	private void sendPacket() {
		if (this.snapshot == null || this.minecraft == null || this.minecraft.level == null) {
			return;
		}
		FramePose pose = this.fields[0] == null ? this.snapshot.pose() : this.readPose();
		this.snapshot = this.snapshot.withPose(pose);
		ClientFramePoses.store(this.minecraft.level, this.snapshot.handle(), pose);
		PoseCloud.push(this.minecraft.level, this.snapshot.handle(), pose);
		if (ClientPlayNetworking.canSend(UpdateFramePosePayload.TYPE)) {
			ClientPlayNetworking.send(new UpdateFramePosePayload(
				this.snapshot.handle(),
				pose,
				this.snapshot.invisible(),
				this.snapshot.glowing()
			));
		}
	}

	@Override
	public void tick() {
		super.tick();
		this.refreshGlowButton(false);
		if (this.packetWaiting) {
			this.sendDelay--;
			if (this.sendDelay <= 0) {
				this.sendPacket();
				this.packetWaiting = false;
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		for (NumberFieldBox field : this.fields) {
			if (field != null && field.isHovered()) {
				field.nudge(scrollY);
				this.markDirty();
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

		int slotX = x + 54;
		int slotY = y + 28;
		graphics.fill(slotX, slotY, slotX + 32, slotY + 32, SLOT);
		graphics.outline(slotX, slotY, 32, 32, 0x60FFFFFF);
		if (this.minecraft != null && this.minecraft.level != null && this.snapshot != null) {
			ItemStack stack = FrameLookup.item(this.minecraft.level, this.snapshot.handle());
			if (!stack.isEmpty()) {
				graphics.item(stack, slotX + 8, slotY + 8);
			}
		}

		graphics.text(this.font, Component.translatable("frameposer.gui.invisible"), x + 12, y + 68 + 6, WHITE, false);
		graphics.text(this.font, Component.translatable("frameposer.gui.fixed"), x + 12, y + 90 + 6, WHITE, false);
		graphics.text(this.font, Component.translatable("frameposer.gui.glowing"), x + 12, y + 112 + 6, WHITE, false);
		graphics.text(this.font, Component.translatable("frameposer.gui.invulnerable"), x + 12, y + 134 + 6, WHITE, false);

		int right = x + 168;
		graphics.text(this.font, Component.translatable("frameposer.gui.rotation"), right, y + 26, WHITE, false);
		graphics.text(this.font, Component.translatable("frameposer.gui.x"), right, y + 38 + 6, MUTED, false);
		graphics.text(this.font, Component.translatable("frameposer.gui.y"), right, y + 60 + 6, MUTED, false);
		graphics.text(this.font, Component.translatable("frameposer.gui.z"), right, y + 82 + 6, MUTED, false);
		graphics.text(this.font, Component.translatable("frameposer.gui.offset"), right, y + 104, WHITE, false);
		graphics.text(this.font, Component.translatable("frameposer.gui.x"), right, y + 116 + 6, MUTED, false);
		graphics.text(this.font, Component.translatable("frameposer.gui.y"), right, y + 138 + 6, MUTED, false);
		graphics.text(this.font, Component.translatable("frameposer.gui.z"), right, y + 160 + 6, MUTED, false);
		graphics.text(this.font, Component.translatable("frameposer.gui.scale"), right + 108, y + 104, WHITE, false);
	}

	@Override
	public void onClose() {
		if (this.packetWaiting) {
			this.sendPacket();
		}
		ClientFramePoses.flush();
		current = null;
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
