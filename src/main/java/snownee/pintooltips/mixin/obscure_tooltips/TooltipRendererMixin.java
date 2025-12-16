package snownee.pintooltips.mixin.obscure_tooltips;

import java.awt.*;
import java.util.List;

import dev.obscuria.tooltips.client.TooltipRenderer;

import net.minecraft.client.gui.GuiGraphics;

import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import snownee.pintooltips.PinTooltips;
import snownee.pintooltips.PinnedTooltipsService;
import snownee.pintooltips.duck.PTGuiGraphics;

@Mixin(value = TooltipRenderer.class)
public class TooltipRendererMixin {

	@Inject(
			method = "render",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0)
	)
	private static void pin_tooltips$onRender(
			GuiGraphics graphics,
			Font font,
			List<ClientTooltipComponent> components,
			int mouseX,
			int mouseY,
			ClientTooltipPositioner positioner,
			CallbackInfoReturnable<Boolean> cir,
			@Local Vector2ic position) {
		if (PTGuiGraphics.of(graphics).pin_tooltips$getRenderingPinned()) {
			return;
		}
		PinTooltips.onRenderTooltip(
				font,
				components,
				position,
				PTGuiGraphics.of(graphics).pin_tooltips$getRenderingItemStack());
	}

	@Inject(
			method = "render", at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 1))
	private static void pin_tooltips$changeZOffset(
			GuiGraphics graphics,
			Font font,
			List<ClientTooltipComponent> components,
			int mouseX,
			int mouseY,
			ClientTooltipPositioner positioner,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (!PTGuiGraphics.of(graphics).pin_tooltips$getRenderingPinned() && !PinnedTooltipsService.INSTANCE.tooltips().isEmpty() || PTGuiGraphics.of(graphics).pin_tooltips$getRenderingPinnedEvent()) {
			graphics.pose().translate(0, 0, PinTooltips.getMaxZOffset());
		}
	}
}
