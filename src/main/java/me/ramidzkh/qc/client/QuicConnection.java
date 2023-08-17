package me.ramidzkh.qc.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamType;
import me.ramidzkh.qc.QuicConnect;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class QuicConnection {

    public static ChannelFuture connect(InetSocketAddress address, boolean useNativeTransport, Connection connection)
            throws ExecutionException, InterruptedException {
        useNativeTransport &= Epoll.isAvailable();

        var context = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(QuicConnect.APPLICATION_NAME)
                .build();

        var codec = new QuicClientCodecBuilder()
                .sslContext(context)
                .maxIdleTimeout(5 /* 30 */, TimeUnit.SECONDS)
                .initialMaxData(10000000)
                // As we don't want to support remote initiated streams just setup the limit for local initiated streams
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .build();

        var channel = new Bootstrap()
                .group(useNativeTransport ? Connection.NETWORK_EPOLL_WORKER_GROUP.get()
                        : Connection.NETWORK_WORKER_GROUP.get())
                .channel(useNativeTransport ? EpollDatagramChannel.class : NioDatagramChannel.class)
                .handler(codec)
                .bind(0)
                .sync()
                .channel();

        return QuicChannel.newBootstrap(channel)
                .streamHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(@NotNull ChannelHandlerContext ctx) {
                        // As we did not allow any remote initiated streams we will never see this method called.
                        ctx.close();
                    }
                })
                .remoteAddress(address)
                .connect()
                .get()
                .createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(@NotNull Channel channel) {
                        ChannelPipeline pipeline = channel.pipeline();
                        Connection.configureSerialization(pipeline, PacketFlow.CLIENTBOUND);
                        pipeline.addLast("packet_handler", connection);
                    }
                })
                .get()
                .parent().newSucceededFuture();
    }
}
