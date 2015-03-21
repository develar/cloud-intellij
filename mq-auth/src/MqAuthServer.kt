package org.intellij.mq.auth

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import io.netty.util.CharsetUtil
import io.netty.util.concurrent.GlobalEventExecutor
import java.net.InetSocketAddress
import java.util.Locale

public fun main(args: Array<String>) {
  val isLinux = System.getProperty("os.name")!!.toLowerCase(Locale.ENGLISH).startsWith("linux")
  val eventGroup = if (isLinux) EpollEventLoopGroup() else NioEventLoopGroup()
  val channelRegistrar = ChannelRegistrar()

  Runtime.getRuntime().addShutdownHook(Thread(Runnable {
    try {
      channelRegistrar.closeAndSyncUninterruptibly();
    }
    finally {
      eventGroup.shutdownGracefully().syncUninterruptibly()
    }
  }))

  val authRequestHandler = AuthRequestHandler()
  val serverBootstrap = ServerBootstrap()
  serverBootstrap.group(eventGroup).channel(if (isLinux) javaClass<EpollServerSocketChannel>() else javaClass<NioServerSocketChannel>()).childHandler(object : ChannelInitializer<Channel>() {
    override fun initChannel(channel: Channel) {
      channel.pipeline().addLast(channelRegistrar)
      channel.pipeline().addLast(HttpRequestDecoder(), HttpObjectAggregator(1048576 * 10), HttpResponseEncoder())
      channel.pipeline().addLast(authRequestHandler)
    }
  }).childOption<Boolean>(ChannelOption.SO_KEEPALIVE, true).childOption<Boolean>(ChannelOption.TCP_NODELAY, true)

  val serverChannel = serverBootstrap.bind(InetSocketAddress(80)).syncUninterruptibly().channel()
  channelRegistrar.addServerChannel(serverChannel)
  serverChannel.closeFuture().syncUninterruptibly()
}

val allow = Unpooled.copiedBuffer("allow", CharsetUtil.UTF_8)
val deny = Unpooled.copiedBuffer("deny", CharsetUtil.UTF_8)

ChannelHandler.Sharable
class AuthRequestHandler() : SimpleChannelInboundHandler<FullHttpRequest>() {
  override fun channelRead0(context: ChannelHandlerContext, message: FullHttpRequest) {
    val answer: ByteBuf
    val urlDecoder = QueryStringDecoder(message.uri())
    answer = when (urlDecoder.path()) {
      "/user" -> {
        // todo verify user - we should use password as a token and verify it (github - call /user and pass the token as "Authorization: 'token ' + token")
        allow
      }
      "/vhost" -> allow
      "/resource" -> {
        val type = urlDecoder.parameters().get("resource")!![0]!!
        if (type == "queue") {
          allow
        }
        else {
          assert(type == "exchange")
          val exchangeName = urlDecoder.parameters().get("name")!![0]!!
          val username = urlDecoder.parameters().get("username")!![0]!!
          if ((exchangeName.length() - username.length() == 2) &&
                  (exchangeName[1] == '.' && (exchangeName[0] == 'd' || exchangeName[0] == 't')) &&
                  exchangeName.regionMatches(false, 2, username, 0, username.length())) {
            allow
          }
          else {
            deny
          }
        }
      }
      else -> deny
    }

    context.writeAndFlush(DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, answer))
  }
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