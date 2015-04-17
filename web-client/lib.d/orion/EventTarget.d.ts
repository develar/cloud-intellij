declare module "orion/EventTarget" {
  class EventTarget {
    dispatchEvent(event: any): void;
  }

  export = EventTarget
}