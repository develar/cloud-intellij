package org.jetbrains.flux.mqAuth

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.*

ChannelHandler.Sharable
class AuthRequestHandler() : SimpleChannelInboundHandler<HttpRequest>() {
  private val allow: ByteBuf
  private val allowMonitoring: ByteBuf
  private val deny: ByteBuf

  private val managementUser: String?

  private val devUserName: String?
  private val devUserToken: String?

  init {
    val allocator = ByteBufAllocator.DEFAULT
    allow = ioBuffer(allocator, "allow")
    allowMonitoring = ioBuffer(allocator, "allow monitoring")
    deny = ioBuffer(allocator, "deny")

    managementUser = System.getenv("MANAGEMENT_USER")

    devUserName = System.getenv("DEV_USER_NAME")
    devUserToken = System.getenv("DEV_USER_TOKEN")
  }

  private fun ioBuffer(allocator: ByteBufAllocator, string: String): ByteBuf {
    val b = string.toByteArray()
    return allocator.ioBuffer(b.size()).writeBytes(b)
  }

  fun dispose() {
    allow.release()
    deny.release()
  }

  override fun channelRead0(context: ChannelHandlerContext, request: HttpRequest) {
    System.out.println("${request.uri()}")

    val answer: ByteBuf
    val urlDecoder = QueryStringDecoder(request.uri())
    answer = when (urlDecoder.path()) {
      "/user" -> {
        val userName = getUserName(urlDecoder)
        if (userName == managementUser) {
          allowMonitoring
        }
        else {
          verifyUser(userName, urlDecoder.parameters().get("password")?.get(0))
        }
      }
      "/resource" -> {
        val type = urlDecoder.parameters().get("resource")!![0]!!
        assert(type == "exchange")
        val exchangeName = urlDecoder.parameters().get("name")!![0]!!
        val userName = getUserName(urlDecoder)
        if ((exchangeName.length() - userName.length() == 2) &&
          (exchangeName[1] == '.' && (exchangeName[0] == 'd' || exchangeName[0] == 't')) &&
          exchangeName.regionMatches(2, userName, 0, userName.length(), false)) {
          allow
        }
        else {
          deny
        }
      }
      else -> deny
    }

    System.out.println(if (answer == deny) "deny" else "allow")

    val keepAlive = HttpHeaderUtil.isKeepAlive(request)
    val byteBuf = answer.slice()
    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, byteBuf)
    response.headers().add(HttpHeaderNames.CACHE_CONTROL, "no-cache, no-store, must-revalidate, max-age=0")
    response.headers().add(HttpHeaderNames.PRAGMA, "no-cache")
    response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes())

    byteBuf.retain()
    val future = context.write(response)
    if (!keepAlive) {
      future.addListener(ChannelFutureListener.CLOSE)
    }
  }

  private fun verifyUser(user: String, token: String?): ByteBuf {
    if (user == devUserName) {
      return if (token == devUserToken) allow else deny
    }

    // todo verify user - we should use password as a token and verify it (github - call /user and pass the token as "Authorization: 'token ' + token")
    return allow
  }

  private fun getUserName(urlDecoder: QueryStringDecoder) = urlDecoder.parameters().get("username")!![0]!!

  override fun channelReadComplete(context: ChannelHandlerContext) {
    context.flush()
  }

  override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
    cause.printStackTrace()
    context.close()
  }
}