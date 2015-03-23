package org.intellij.mq.auth

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.util.concurrent.GlobalEventExecutor
import java.net.InetSocketAddress
import java.util.Locale

public fun main(args: Array<String>) {
  val isLinux = System.getProperty("os.name")!!.toLowerCase(Locale.ENGLISH).startsWith("linux")
  val eventGroup = if (isLinux) EpollEventLoopGroup() else NioEventLoopGroup()
  val channelRegistrar = ChannelRegistrar()

  val authRequestHandler = AuthRequestHandler()

  Runtime.getRuntime().addShutdownHook(Thread(Runnable {
    try {
      authRequestHandler.dispose()
      channelRegistrar.closeAndSyncUninterruptibly();
    }
    finally {
      eventGroup.shutdownGracefully().syncUninterruptibly()
    }
  }))

  val serverBootstrap = ServerBootstrap()
  serverBootstrap.group(eventGroup).channel(if (isLinux) javaClass<EpollServerSocketChannel>() else javaClass<NioServerSocketChannel>()).childHandler(object : ChannelInitializer<Channel>() {
    override fun initChannel(channel: Channel) {
      channel.pipeline().addLast(channelRegistrar)
      channel.pipeline().addLast(HttpServerCodec())
      channel.pipeline().addLast(authRequestHandler)
    }
  })

  val address = InetSocketAddress(80)
  val serverChannel = serverBootstrap.bind(address).syncUninterruptibly().channel()
  channelRegistrar.addServerChannel(serverChannel)
  System.out.println("Listening ${address.getHostName()}:${address.getPort()}")
  serverChannel.closeFuture().syncUninterruptibly()
}

ChannelHandler.Sharable
class ChannelRegistrar() : ChannelInboundHandlerAdapter() {
  private val openChannels = DefaultChannelGroup(GlobalEventExecutor.INSTANCE)

  public fun addServerChannel(serverChannel: Channel) {
    openChannels.add(serverChannel as ServerChannel)
  }

  override fun channelActive(context: ChannelHandlerContext) {
    // we don't need to remove channel on close - ChannelGroup do it
    openChannels.add(context.channel())

    super.channelActive(context)
  }

  public fun closeAndSyncUninterruptibly() {
    openChannels.close().syncUninterruptibly()
  }
}