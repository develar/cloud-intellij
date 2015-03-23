package org.intellij.mq.auth

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
  private val deny: ByteBuf

  init {
    val allocator = ByteBufAllocator.DEFAULT

    var b = "allow".toByteArray()
    allow = allocator.ioBuffer(b.size()).writeBytes(b)

    b = "deny".toByteArray()
    deny = allocator.ioBuffer(b.size()).writeBytes(b)
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

  override fun channelReadComplete(context: ChannelHandlerContext) {
    context.flush()
  }

  override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
    cause.printStackTrace()
    context.close()
  }
}