/// <reference path="../typings/sockjs/sockjs.d.ts" />
/// <reference path="../typings/stomp.d.ts" />
/// <reference path="../typings/bluebird/bluebird.d.ts" />

"use strict"

import SockJS = require("sockjs")
import Stomp = require("stomp")
import Promise = require("bluebird")

export class StompConnector {
  private client: Stomp.Client

  private exchangeCommands: String
  private queue: String

  private messageIdCounter = 0
  private callbacks: { [s: number]: PromiseCallback; } = {}

  connect(host: String, user: String, password: String): void {
    var url = "http://" + host + ":15674/stomp"
    var ws = new SockJS(url)
    this.client = Stomp.over(ws)
    // SockJS does not support heart-beat: disable heart-beats
    this.client.heartbeat.outgoing = 0
    this.client.heartbeat.incoming = 0
    this.queue = "/temp-queue/" + user
    this.exchangeCommands = "/exchange/d." + user
    this.client.debug = (message) => {
      console.log(message)
    }
    this.client.connect(user, password, () => {
      console.log("Connected to message broker:", url, user);
      this.client.subscribe(this.exchangeCommands, (data) => {

      })
    }, (error) => {
      console.log(error);
    });
  }

  request<T>(service: String, method: String, message: any = {}): Promise<T> {
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