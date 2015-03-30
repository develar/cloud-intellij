declare module "stomp" {
  function over(webSocket: any): Client

  export interface Client {
    debug: (message: string) => void

    heartbeat: any

    onreceive: (message: any) => void

    connect(login: string, passcode: string, connectCallback: (frame: Frame) => void, errorCallback?: (error: any) => void, host?: string): void

    subscribe(destination: string, callback: (frame: Frame) => void, headers?: any): Subscription

    send(destination: string, headers: any, data: string): void
  }

  export interface Frame {
    body: string
    command: string
    headers: { [key: string]: any; }
  }

  export interface Subscription {
    id: string

    unsubscribe(): void
  }
}