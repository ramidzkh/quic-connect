package me.ramidzkh.qc.mixin.client;

import me.ramidzkh.qc.client.ServerAddressProperties;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerAddress.class)
public class ServerAddressMixin implements ServerAddressProperties {

    @Shadow
    @Final
    private static ServerAddress INVALID;

    @Unique
    private boolean quic;

    @Inject(method = "parseString", at = @At("HEAD"), cancellable = true)
    private static void parse(String address, CallbackInfoReturnable<ServerAddress> callbackInfoReturnable) {
        var index = address.indexOf("://");

        if (index == -1) {
            return;
        }

        var scheme = address.substring(0, index);
        var newAddress = ServerAddress.parseString(address.substring(index + 3));

        if (newAddress != INVALID) {
            switch (scheme) {
                case "minecraft" -> ((ServerAddressProperties) (Object) newAddress).setUseQuic(false);
                case "quic" -> ((ServerAddressProperties) (Object) newAddress).setUseQuic(true);
                default -> {
                    // TODO: API?
                }
            }
        }

        callbackInfoReturnable.setReturnValue(newAddress);
    }

    @ModifyVariable(method = "isValidAddress", at = @At("HEAD"), argsOnly = true, index = 0)
    private static String modify(String address) {
        var index = address.indexOf("://");

        if (index == -1) {
            return address;
        } else {
            return address.substring(index + 3);
        }
    }

    @Override
    public boolean getUseQuic() {
        return quic;
    }

    @Override
    public void setUseQuic(boolean quic) {
        this.quic = quic;
    }
}
