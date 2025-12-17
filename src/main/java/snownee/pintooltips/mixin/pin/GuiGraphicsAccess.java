package snownee.pintooltips.mixin.pin;

import java.util.List;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;

@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccess {
    @Invoker
    void callRenderTooltipInternal(
        Font font,
        List<ClientTooltipComponent> components,
        int mouseX,
        int mouseY,
        ClientTooltipPositioner tooltipPositioner
    );

	@Accessor(remap = false)
	void setTooltipStack(ItemStack itemStack);
}
