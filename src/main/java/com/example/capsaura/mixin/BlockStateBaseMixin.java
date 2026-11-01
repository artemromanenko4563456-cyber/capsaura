package com.example.capsaura.mixin;

import com.example.capsaura.XRay;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

    // Скрытые блоки не рисуются
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void capsaura$xrayRenderShape(CallbackInfoReturnable<RenderShape> cir) {
        if (XRay.isHidden((BlockState) (Object) this)) {
            cir.setReturnValue(RenderShape.INVISIBLE);
        }
    }

    // Скрытые блоки не закрывают грани соседних блоков (иначе руды внутри камня остались бы невидимыми)
    @Inject(method = "canOcclude", at = @At("HEAD"), cancellable = true)
    private void capsaura$xrayCanOcclude(CallbackInfoReturnable<Boolean> cir) {
        if (XRay.isHidden((BlockState) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
