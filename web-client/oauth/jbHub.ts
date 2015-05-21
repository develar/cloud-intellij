import {
  Provider,
  ProviderOauth,
} from "./oauth"

export class JbHub implements Provider {
  public oauth: ProviderOauth
  public base: string

  constructor(public clientId: string, hubHost: string = "sso.jetbrains.com") {
    this.oauth = {
      auth: `https://${hubHost}/api/rest/oauth2/auth`,
      grant: `https://${hubHost}/api/rest/oauth2/token`,
    }

    this.base = `https://${hubHost}/api/rest/`
  }

  providerId = "jbHub"

  scope = {
    basic: "0-0-0-0-0"
  }

  get = {
    me: "users/me"
  }

  logout(): void {
  }
}