declare module "hello" {
  interface Options {
    redirect_uri?: string
    display?: string
    scope?: string
    response_type?: string
    force?: boolean
    oauth_proxy?: string
    timeout?: number
    default_service?: string
  }

  interface HelloJSEventArgument {
    network: string;
    authResponse?: any;
  }

  interface HelloJSStatic {
    init(serviceAppIds: { [id: string]: string; }, defaultOptions?: Options):void;
    login(network: string, option?: Options, callback?: () => void): void;
    logout(network: string, callback?: () => void): void;
    on(eventName: string, event: (auth: HelloJSEventArgument) => void): HelloJSStatic;
    off(eventName: string, event: (auth: HelloJSEventArgument) => void): HelloJSStatic;
    getAuthResponse(network: string): any;
    service(network: string): HelloJSServiceDef;
    settings: Options;
    (network: string): Provider;
    init(servicesDef: { [id: string]: HelloJSServiceDef; }): void;

    use(provider: string): Provider
  }

  interface AuthResponse {
    access_token: string
  }

  interface LoginResult {
    authResponse: AuthResponse
  }

  interface Provider {
    login(option?: Options): Promise<LoginResult>

    logout(callback?: () => void): void

    getAuthResponse(): any

    api(path: string): Promise<any>
  }

  interface HelloJSOAuthDef {
    version: number;
    auth: string;
    request: string;
    token: string;
  }

  interface HelloJSServiceDef {
    name: string;
    oauth: HelloJSOAuthDef;
    scope?: { [id: string]: string; };
    scope_delim?: string;
    autorefresh?: boolean;
    base?: string;
    root?: string;
    get?: { [id: string]: any; }
    post?: { [id: string]: any; }
    del?: { [id: string]: string; }
    put?: { [id: string]: any; }
    wrap?: { [id: string]: (par: any) => void; }
    xhr?: (par: any) => void;
    jsonp?: (par: any) => void;
    form?: (par: any) => void;
    api?: (...par: any[]) => void;
  }

  var hello: HelloJSStatic;
  export = hello;
}
