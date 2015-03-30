declare class PluginProvider {
  constructor(headers: any);

  registerService(id: string, b: any, c: any): void;

  connect(): void;

  registerServiceProvider(id: string, b: any, c: any);
}

declare module "orion/plugin" {
  export = PluginProvider
}