declare module "stomp" {
  function over(webSocket: any): Client

  export interface Client {
    debug: (message: String) => void

    heartbeat: any

    connect(login: String, passcode: String, connectCallback: () => void, errorCallback?: (error: any) => void, host?: String): void

    subscribe(destination: String, callback: (data: any) => void, headers?: any): Subscription

    send(destination: String, headers: any, data: String): void
  }

  export interface Subscription {
    id: String

    unsubscribe(): void
  }
}