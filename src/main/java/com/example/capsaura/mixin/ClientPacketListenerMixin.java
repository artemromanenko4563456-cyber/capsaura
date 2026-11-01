package com.example.capsaura.mixin;

import com.example.capsaura.Velocity;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"))
    private void capsaura$velocityBefore(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        Velocity.before();
    }

    @Inject(method = "handleSetEntityMotion", at = @At("RETURN"))
    private void capsaura$velocityAfter(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        Velocity.after();
    }
}
