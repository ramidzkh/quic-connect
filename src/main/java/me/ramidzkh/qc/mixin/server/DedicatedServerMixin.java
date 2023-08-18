package me.ramidzkh.qc.mixin.server;

import com.mojang.datafixers.DataFixer;
import me.ramidzkh.qc.server.ExtraServerProperties;
import me.ramidzkh.qc.server.QuicServerConnectionListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;

@Mixin(DedicatedServer.class)
public abstract class DedicatedServerMixin extends MinecraftServer {

    @Shadow
    @Final
    static Logger LOGGER;

    @Shadow
    public abstract DedicatedServerProperties getProperties();

    public DedicatedServerMixin(Thread thread, LevelStorageSource.LevelStorageAccess levelStorageAccess,
            PackRepository packRepository, WorldStem worldStem, Proxy proxy, DataFixer dataFixer, Services services,
            ChunkProgressListenerFactory chunkProgressListenerFactory) {
        super(thread, levelStorageAccess, packRepository, worldStem, proxy, dataFixer, services,
                chunkProgressListenerFactory);
    }

    @Inject(method = "initServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V", shift = At.Shift.AFTER))
    private void openQuic(CallbackInfoReturnable<Boolean> callbackInfoReturnable) throws IOException {
        var port = ((ExtraServerProperties) getProperties()).getQuicPort();

        if (port != -1) {
            LOGGER.info("Starting Quic bind on {}:{}", getLocalIp().isEmpty() ? "*" : getLocalIp(), port);
            InetAddress address = null;

            if (!getLocalIp().isEmpty()) {
                address = InetAddress.getByName(getLocalIp());
            }

            var accessor = (ServerConnectionListenerAccessor) getConnection();
            QuicServerConnectionListener.startQuicServerListener(this, accessor.getChannels(),
                    accessor.getConnections(), address, port);
        }
    }
}
