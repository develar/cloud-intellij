"use strict"

import SockJS = require("sockjs")
import Stomp = require("stomp")
import Promise = require("bluebird")
import service = require("service")

export class StompConnector {
  private client: Stomp.Client

  private exchangeCommands: string
  private exchangeEvents: string
  private queue: string

  private messageIdCounter = 0
  private callbacks: { [key: number]: PromiseCallback; } = {}

  private eventHandlers: { [key: string]: Array<any>; } = {}

  connect(host: string, user: string, password: string): Promise<void> {
    var url = "http://" + host + "/stomp"
    var ws = new SockJS(url)
    this.client = Stomp.over(ws)
    // SockJS does not support heart-beat: disable heart-beats
    this.client.heartbeat.outgoing = 0
    this.client.heartbeat.incoming = 0
    this.exchangeCommands = "/exchange/d." + user
    this.exchangeEvents = "/exchange/t." + user

    this.client.debug = (message) => {
      console.log(message)
    }

    return new Promise<void>((resolve: () => void, reject: (error?: any) => void) => {
      this.client.connect(user, password, (frame) => {
        // we don't use default ("") exchange due to security reasons - user can send/receive messages only to/from own exchange
        this.queue = "sc" + frame.headers["session"].substring("session-".length)

        console.log("Connected to message broker:", url, user);
        this.client.subscribe(this.exchangeCommands + "/" + this.queue, (frame) => {
          try {
            var properties = frame.headers;
            var correlationId = properties["correlation-id"]
            var type = properties["type"]
            if (type != null) {
              if (type == "eventResponse") {
                // response to broadcast request
                console.error("Unsupported, todo")
              }
              else {
                // request
                console.error("Unsupported, todo")
              }
            }
            else if (correlationId == null || properties["reply-to"] != null) {
              // event
              if (properties["app-id"] != this.queue) {
                //this.eventHandlers[topic.name]
                console.error("Unsupported, todo")
              }
            }
            else {
              // response
              var data = frame.body.length == 0 ? {} : JSON.parse(frame.body)
              var promiseCallback = this.callbacks[correlationId]
              promiseCallback.resolve(data)
            }
          }
          catch (e) {
            console.error(e)
          }
        })

        resolve()
      }, (error) => {
        console.log(error)
        reject(error)
      })
    })
  }

  request<R>(service: service.Service<R>, message: any = {}): Promise<R> {
    return new Promise((resolve: (result: R) => void, reject: (error?: any) => void) => {
      var id = this.messageIdCounter++;
      if (id === Number.MAX_VALUE) {
        this.messageIdCounter = 0;
      }
      this.callbacks[id] = new PromiseCallback(resolve, reject)
      this.client.send(this.exchangeCommands + "/" + service.serviceName, {"reply-to": this.queue, "correlation-id": id, type: service.name}, JSON.stringify(message))
    })
  }

  notify(topic: service.Topic, message: any = {}): void {
    var headers: { [key: string]: any; }
    if (topic.responseTopic == null) {
      headers = {"app-id": this.queue};
    }
    else {
      headers = {
        "app-id": this.queue,
        "reply-to": this.queue,
        "correlation-id": topic.responseTopic
      }
    }
    this.client.send(this.exchangeEvents + "/" + topic.name, headers, JSON.stringify(message))
  }

  on(topic: service.Topic, handler: (data: any) => void): void {
    this.replyOn(topic, handler)
  }

  replyOn(topic: service.Topic, handler: (replyTo: string, correlationId: string, data: any) => void) {
    var list = this.eventHandlers[topic.name]
    if (list == null) {
      this.eventHandlers[topic.name] = [handler]
    }
    else {
      list.push(handler)
    }
  }

  replyToEvent(replyTo: string, correlationId: string, response: any): void {

  }
}

class PromiseCallback {
  constructor(public resolve: (value?: any) => void, public reject: (error?: any) => void) {
  }
}