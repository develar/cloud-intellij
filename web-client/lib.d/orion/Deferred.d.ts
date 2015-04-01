declare class Deferred {
  resolve(value: any): any;

  reject(error?: any): any;

  then(onFulfill: (data?: any) => void, onReject?: () => void, onProgress?: any): any;
}

declare module "orion/Deferred" {
  export = Deferred
}