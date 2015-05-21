export class Store<T> {
  private storage: JsonStorage<any>

  constructor(storage: Storage = localStorage) {
    this.storage = new JsonStorage<any>("auth", storage)
  }

  public get(name: string): T {
    var data = this.storage.get()
    return data == null ? null : data[name]
  }

  public set(name: string, value: T): void {
    var data = this.storage.get()
    if (value == null) {
      if (data == null) {
        return
      }

      delete data[name]
    }
    else {
      if (data == null) {
        data = Object.create(null)
      }

      data[name] = value
    }
    this.storage.set(data)
  }
}

export class JsonStorage<T> {
  constructor(private key: string, private storage: Storage = localStorage) {
  }

  public get(): T {
    try {
      let serialized = this.storage.getItem(this.key)
      return serialized == null ? null : JSON.parse(serialized)
    }
    catch (e) {
      console.error(e)
      this.storage.removeItem(this.key)
      return null
    }
  }

  public set(value: T): void {
    if (value == null || Object.getOwnPropertyNames(value).length === 0) {
      this.storage.removeItem(this.key)
    }
    else {
      this.storage.setItem(this.key, JSON.stringify(value))
    }
  }
}