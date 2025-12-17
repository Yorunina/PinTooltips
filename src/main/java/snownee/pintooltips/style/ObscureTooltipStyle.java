package snownee.pintooltips.style;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.Rect2i;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import snownee.pintooltips.PinnedTooltip;
import snownee.pintooltips.mixin.interact.ClientTextTooltipAccess;

import java.util.List;

public class ObscureTooltipStyle implements TooltipStyle {
	private final List<ClientTooltipComponent> modifiedComponents;

	public ObscureTooltipStyle(List<ClientTooltipComponent> modifiedComponents) {
		this.modifiedComponents = modifiedComponents;
	}

	@Override
	public void updateSize(PinnedTooltip tooltip, int screenWidth, int screenHeight, Font font) {
		var width = 0;
		var height = 0;
		tooltip.linesPosition().clear();
		for (var component : modifiedComponents) {
			var componentWidth = component.getWidth(font);
			var componentHeight = component.getHeight();
			tooltip.linesPosition().put(new Rect2i(0, height, componentWidth, componentHeight), component);
			width = Math.max(width, componentWidth);
			height += componentHeight;
		}
		if (width != tooltip.size().x() || height != tooltip.size().y()) {
			tooltip.setSize(width, height);
		}
	}

	@Override
	public @Nullable Style getStyleAt(PinnedTooltip tooltip, double mouseX, double mouseY, Font font) {
		var relativeX = (int) (mouseX - tooltip.position().x());
		var relativeY = (int) (mouseY - tooltip.position().y());
		var line = tooltip.linesPosition().keySet().stream().filter(rect -> rect.contains(relativeX, relativeY)).findFirst().orElse(null);
		var component = tooltip.linesPosition().get(line);
		if (component instanceof ClientTextTooltipAccess textTooltip) {
			return font.getSplitter().componentStyleAtWidth(textTooltip.getText(), relativeX);
		}
		return null;
	}
}