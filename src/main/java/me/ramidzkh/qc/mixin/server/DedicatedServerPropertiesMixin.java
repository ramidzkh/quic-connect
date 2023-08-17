package me.ramidzkh.qc.mixin.server;

import me.ramidzkh.qc.server.ExtraServerProperties;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.dedicated.Settings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Properties;

@Mixin(DedicatedServerProperties.class)
public abstract class DedicatedServerPropertiesMixin extends Settings<DedicatedServerProperties>
        implements ExtraServerProperties {

    @Unique
    private int quicPort;

    public DedicatedServerPropertiesMixin(Properties properties) {
        super(properties);
    }

    @Override
    public int getQuicPort() {
        return quicPort;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void initUri(CallbackInfo callbackInfo) {
        quicPort = get("quic-port", -1);
    }
}
