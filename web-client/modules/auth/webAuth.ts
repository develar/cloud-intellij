import Promise = require("bluebird")

import {
  login,
  init,
  Credential,
} from "./auth"

export function check(): Promise<Credential> {
  init(true)
  return login()
}