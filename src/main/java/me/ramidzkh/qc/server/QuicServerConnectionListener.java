package me.ramidzkh.qc.server;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import me.ramidzkh.qc.QuicConnect;
import me.ramidzkh.qc.token.KeyedTokenHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.network.Connection;
import net.minecraft.network.RateKickingConnection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class QuicServerConnectionListener {

    public static void startQuicServerListener(MinecraftServer server, List<ChannelFuture> channels,
            List<Connection> connections, @Nullable InetAddress address, int port) {
        var useNativeTransport = Epoll.isAvailable() && server.isEpollEnabled();

        var config = FabricLoader.getInstance().getConfigDir().resolve("quic-connect");
        var keyFile = config.resolve("key.pem");
        var certificateFile = config.resolve("certificate.pem");

        var context = QuicSslContextBuilder.forServer(keyFile.toFile(), null, certificateFile.toFile())
                .applicationProtocols(QuicConnect.APPLICATION_NAME)
                .build();

        var codec = new QuicServerCodecBuilder()
                .sslContext(context)
                .maxIdleTimeout(5, TimeUnit.SECONDS)
                // Configure some limits for the maximal number of streams (and the data) that we want to handle.
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .tokenHandler(new KeyedTokenHandler(Util.make(new byte[32], ThreadLocalRandom.current()::nextBytes)))
                .streamHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(@NotNull Channel channel) {
                        ChannelPipeline pipeline = channel.pipeline();
                        Connection.configureSerialization(pipeline, PacketFlow.SERVERBOUND);
                        int pps = server.getRateLimitPacketsPerSecond();
                        Connection connection = pps > 0 ? new RateKickingConnection(pps)
                                : new Connection(PacketFlow.SERVERBOUND);
                        connections.add(connection);
                        pipeline.addLast("packet_handler", connection);
                        connection.setListener(new ServerHandshakePacketListenerImpl(server, connection));
                    }
                })
                .build();

        channels.add(new Bootstrap()
                .group(useNativeTransport ? Connection.NETWORK_EPOLL_WORKER_GROUP.get()
                        : Connection.NETWORK_WORKER_GROUP.get())
                .channel(useNativeTransport ? EpollDatagramChannel.class : NioDatagramChannel.class)
                .handler(codec)
                .bind(new InetSocketAddress(address, port))
                .syncUninterruptibly());
    }
}
