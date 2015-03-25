declare class Deferred {
  resolve(value: any): any

  reject(error: any): any

  then(onFulfill: () => void, onReject: () => void, onProgress: any): any
}
export = Deferred;