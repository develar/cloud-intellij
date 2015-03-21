package org.jetbrains.util.concurrency

public trait AsyncFunction<PARAM, RESULT> {
  public fun `fun`(param: PARAM): Promise<RESULT>
}