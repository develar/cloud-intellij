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

  val address = InetSocketAddress(80)
  val serverChannel = serverBootstrap.bind(address).syncUninterruptibly().channel()
  channelRegistrar.addServerChannel(serverChannel)
  System.out.println("Listening ${address.getHostName()}:${address.getPort()}")
  serverChannel.closeFuture().syncUninterruptibly()
}

val allow = Unpooled.copiedBuffer("allow", CharsetUtil.UTF_8)
val deny = Unpooled.copiedBuffer("deny", CharsetUtil.UTF_8)

ChannelHandler.Sharable
class AuthRequestHandler() : SimpleChannelInboundHandler<FullHttpRequest>() {
  override fun channelRead0(context: ChannelHandlerContext, request: FullHttpRequest) {
    System.out.println("In ${request.uri()}")

    val answer: ByteBuf
    val urlDecoder = QueryStringDecoder(request.uri())
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

    System.out.println(if (answer == deny) "deny" else "allow")

    val keepAlive = HttpHeaderUtil.isKeepAlive(request)
    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, answer)
    response.headers().add(HttpHeaderNames.CACHE_CONTROL, "no-cache, no-store, must-revalidate, max-age=0")
    response.headers().add(HttpHeaderNames.PRAGMA, "no-cache")
    HttpHeaderUtil.setKeepAlive(response, keepAlive)
    HttpHeaderUtil.setContentLength(response, answer.readableBytes().toLong())
    val future = context.writeAndFlush(response)
    if (!keepAlive) {
      future.addListener(ChannelFutureListener.CLOSE)
    }
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