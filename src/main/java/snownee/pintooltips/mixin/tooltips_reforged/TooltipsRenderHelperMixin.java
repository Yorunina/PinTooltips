package snownee.pintooltips.mixin.tooltips_reforged;

import java.util.List;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.gui.GuiGraphics;

import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.iafenvoy.tooltipsreforged.render.TooltipsRenderHelper;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import snownee.pintooltips.PinTooltips;
import snownee.pintooltips.PinnedTooltipsService;
import snownee.pintooltips.duck.PTGuiGraphics;

@Mixin(value = TooltipsRenderHelper.class)
public class TooltipsRenderHelperMixin {

	@Inject(
			method = "drawTooltip",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V")
	)
	private static void pin_tooltips$onRender(
			GuiGraphics context,
			Font font,
			List<ClientTooltipComponent> components,
			int x,
			int y,
			ClientTooltipPositioner positioner,
			CallbackInfo ci,
			@Local Vector2ic position) {
		if (PTGuiGraphics.of(context).pin_tooltips$getRenderingPinned()) {
			return;
		}
		PinTooltips.onRenderTooltip(
				font,
				components,
				position,
				PTGuiGraphics.of(context).pin_tooltips$getRenderingItemStack());
	}

	@Inject(
			method = "drawTooltip", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;drawManaged(Ljava/lang/Runnable;)V"))
	private static void pin_tooltips$changeZOffset(
			GuiGraphics context,
			Font textRenderer,
			List<ClientTooltipComponent> components,
			int x,
			int y,
			ClientTooltipPositioner positioner,
			CallbackInfo ci
	) {
		//	Render the unpinned tooltip on top of the pinned tooltip
		if (!PTGuiGraphics.of(context).pin_tooltips$getRenderingPinned() && !PinnedTooltipsService.INSTANCE.tooltips().isEmpty() || PTGuiGraphics.of(context).pin_tooltips$getRenderingPinnedEvent()) {
			context.pose().translate(0, 0, PinTooltips.getMaxZOffset());
		}
	}
}
