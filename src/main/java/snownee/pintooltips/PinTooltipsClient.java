package snownee.pintooltips;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import snownee.pintooltips.mixin.pin.GuiGraphicsAccess;

public class PinTooltipsClient {
	public static void setRenderingItemStack(GuiGraphics graphics, ItemStack itemStack) {
		((GuiGraphicsAccess) graphics).setTooltipStack(itemStack);
	}
}