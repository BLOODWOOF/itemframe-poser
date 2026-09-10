package frameposer.client;

import java.util.Locale;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public class NumberFieldBox extends EditBox {
	private final float step;
	private final float min;
	private final float max;

	public NumberFieldBox(Font font, int x, int y, int width, int height, float step, float min, float max) {
		super(font, x, y, width, height, Component.empty());
		this.step = step;
		this.min = min;
		this.max = max;
		this.setMaxLength(12);
	}

	@Override
	public void insertText(String input) {
		StringBuilder filtered = new StringBuilder();
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c == '-' || c == '.' || c >= '0' && c <= '9') {
				filtered.append(c);
			}
		}
		if (!filtered.isEmpty()) {
			super.insertText(filtered.toString());
		}
	}

	public float floatValue(float fallback) {
		String text = this.getValue().trim();
		if (text.isEmpty() || text.equals("-") || text.equals(".") || text.equals("-.")) {
			return fallback;
		}
		try {
			return Mth.clamp(Float.parseFloat(text), this.min, this.max);
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	public void setFloatValue(float value) {
		this.setValue(format(Mth.clamp(value, this.min, this.max)));
	}

	public void nudge(double scrollY) {
		float delta = this.step * stepMul();
		if (scrollY > 0.0) {
			delta = -delta;
		}
		this.setFloatValue(this.floatValue(0.0F) + delta);
	}

	public void stepBy(int direction) {
		this.setFloatValue(this.floatValue(0.0F) + this.step * stepMul() * direction);
	}

	private static float stepMul() {
		var window = Minecraft.getInstance().getWindow();
		if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
			|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
			return 10.0F;
		}
		if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
			|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
			return 0.1F;
		}
		return 1.0F;
	}

	public static String format(float value) {
		String text = String.format(Locale.ROOT, "%.3f", value);
		while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
			text = text.substring(0, text.length() - 1);
			if (text.endsWith(".")) {
				text = text.substring(0, text.length() - 1);
				break;
			}
		}
		if (text.isEmpty() || text.equals("-")) {
			return "0";
		}
		return text;
	}
}
