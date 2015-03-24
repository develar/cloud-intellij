/// <reference path="../typings/sockjs/sockjs.d.ts" />
/// <reference path="../typings/stomp.d.ts" />
/// <reference path="../typings/bluebird/bluebird.d.ts" />

"use strict"

import SockJS = require("sockjs")
import Stomp = require("stomp")
import Promise = require("bluebird")

export class StompConnector {
  private client: Stomp.Client

  private exchangeCommands: string
  private queue: string

  private messageIdCounter = 0
  private callbacks: { [key: number]: PromiseCallback; } = {}

  connect(host: string, user: string, password: string): Promise<void> {
    var url = "http://" + host + ":15674/stomp"
    var ws = new SockJS(url)
    this.client = Stomp.over(ws)
    // SockJS does not support heart-beat: disable heart-beats
    this.client.heartbeat.outgoing = 0
    this.client.heartbeat.incoming = 0
    this.exchangeCommands = "/exchange/d." + user

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
            if (correlationId == null) {
              // notification
              if (properties["app-id"] != this.queue) {
                // todo
              }
            }
            else if (properties["reply-to"] != null) {
              // request
              // todo
            }
            else if (properties["type"] == "eventResponse") {
              // response to broadcast request
              // todo
            }
            else {
              // response
              var data = frame.body.length == 0 ? {} : JSON.parse(frame.body)
              var promiseCallback = this.callbacks[correlationId]
              promiseCallback.resolve(data)
            }
          }
          catch (e) {
            console.log(e)
          }
        })

        resolve()
      }, (error) => {
        console.log(error)
        reject(error)
      })
    })
  }

  request<T>(service: string, method: string, message: any = {}): Promise<T> {
    return new Promise((resolve: (value: T) => void, reject: (error?: any) => void) => {
      var id = this.messageIdCounter++;
      this.callbacks[id] = new PromiseCallback(resolve, reject)
      this.client.send(this.exchangeCommands + "/" + service, {"reply-to": this.queue, "correlation-id": id, type: method}, JSON.stringify(message))
    })
  }
}

class PromiseCallback {
  constructor(public resolve: (value?: any) => void, public reject: (error?: any) => void) {
  }
}