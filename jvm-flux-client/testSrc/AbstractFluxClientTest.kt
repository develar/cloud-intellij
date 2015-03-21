package org.eclipse.flux.client.java

/**
 * Test harness for FluxClient. Subclass this class to use it.
 */
public abstract class AbstractFluxClientTest : TestCase() {
  public abstract class ResponseHandler<T> {
    protected abstract fun handle(messageType: String, msg: JSONObject): T
  }

  private var timer: Timer? = null

  /**
   * Javascript style 'setTimeout' useful for tests that are doing 'callback' style things rather thread-style waiting.
   */
  public fun setTimeout(delay: Long, task: TimerTask) {
    timer().schedule(task, delay)
  }

  synchronized private fun timer(): Timer {
    if (timer == null) {
      timer = Timer()
    }
    return timer
  }

  private val processes = ArrayList<Process<*>>()

  protected abstract fun createConnection(user: String): MessageConnector

  override fun tearDown() {
    try {
      super.tearDown()

      //ensure all processes are terminated.
      synchronized (processes) {
        for (process in processes) {
          TestCase.assertTrue("Process not started", process.hasRun)
          TestCase.assertFalse("Poorly behaved tests, left a processes running", process.isAlive())
        }
      }
    }
    finally {
      //Make sure this gets executed no matter what or there will be Thread leakage!
      if (timer != null) {
        timer!!.cancel()
      }
    }
  }

  /**
   * A 'test process' is essentially a thread with some convenient methods to be
   * able to easily script test sequences that send / receive messages to / from
   * a flux connection. There is also a built-in timeout mechanism that ensures
   * no test process runs forever. To use this class simply subclass (typically
   * with a anonymous class) and implement the 'execute' method.
   *
   *
   * A well behaved process should terminate naturally without throwing an exception.
   * The test harness tries to detect if a process is not well behaved.
   */
  public abstract inner class Process<T>// It doesn't really make sense to create a Process if this process is never being run
  // so this almost certainly means there's a bug in the test that created the process.

  [throws(javaClass<Exception>())]
  (user: String) : Thread() {

    protected var conn: MessageConnector
    public val result: BasicFuture<T>
    var hasRun = false //To be able to detect mistakes in tests where a process is created but never started.
    {
      this.result = BasicFuture()
      this.conn = createConnection(user)
      this.conn.connectToChannelSync(user)
      this.result.setTimeout(STUCK_PROCESS_TIMEOUT)
      synchronized (processes) {
        processes.add(this)
      }
    }

    override fun run() {
      hasRun = true
      try {
        this.result.resolve(execute())
      }
      catch (e: Throwable) {
        result.reject(e)
      }
      finally {
        this.conn.disconnect()
      }
    }


    throws(javaClass<Exception>())
    public fun send(type: String, msg: JSONObject) {
      conn.send(type, msg)
    }

    /**
     * Asynchronously send a request and return a Future with the response.
     */
    throws(javaClass<Exception>())
    public fun <R> asendRequest(messageType: String, msg: JSONObject, responseHandler: ResponseHandler<R>): BasicFuture<R> {
      val response = object : SingleResponseHandler<R>(conn, responseType(messageType)) {
        throws(javaClass<Exception>())
        protected fun parse(messageType: String, message: JSONObject): R {
          return responseHandler.handle(messageType, message)
        }
      }
      conn.addMessageHandler(response)
      send(messageType, msg)
      return response.getFuture()
    }

    /**
     * Synchronously send a request and return the response.
     */
    throws(javaClass<Exception>())
    public fun <R> sendRequest(messageType: String, msg: JSONObject, responseHandler: ResponseHandler<R>): R {
      return asendRequest(messageType, msg, responseHandler).get()
    }

    private fun responseType(messageType: String): String {
      if (messageType.endsWith("Request")) {
        return messageType.substring(0, messageType.length() - "Request".length()) + "Response"
      }
      throw IllegalArgumentException("Not a 'Request' message type: " + messageType)
    }

    /**
     * Asynchronous receive. Returns a BasicFuture that resolves when message
     * is received.
     */
    public fun areceive(type: String): BasicFuture<JSONObject> {
      val result = BasicFuture<JSONObject>()
      result.setTimeout(TIMEOUT)
      once(object : MessageHandler(type) {
        public fun handle(type: String, message: JSONObject) {
          result.resolve(message)
        }
      })
      return result
    }

    /**
     * Synchronous receive. Blocks until message of given type is received.
     */
    throws(javaClass<Exception>())
    public fun receive(type: String): JSONObject {
      return areceive(type).get()
    }

    public fun once(messageHandler: MessageHandler) {
      conn.addMessageHandler(object : MessageHandler(messageHandler.getMessageType()) {
        public fun canHandle(type: String, message: JSONObject): Boolean {
          return messageHandler.canHandle(type, message)
        }

        public fun handle(type: String, message: JSONObject) {
          conn.removeMessageHandler(this)
          messageHandler.handle(type, message)
        }
      })
    }

    throws(javaClass<Exception>())
    protected abstract fun execute(): T
  }

  /**
   * Run a bunch of processes by starting them in the provided order. Once all processes are running,
   * block until all of them complete. If any one of the processes is terminated by an Exception then
   * 'run' guarantees that at least one of the exceptions is re-thrown
   */
  throws(javaClass<Exception>())
  public fun run(vararg processes: Process<*>) {
    for (process in processes) {
      process.start()
    }
    await(*processes)
  }

  throws(javaClass<Exception>())
  public fun await(vararg processes: Process<*>) {
    var error: Throwable? = null
    for (process in processes) {
      try {
        process.result.get()
      }
      catch (e: Throwable) {
        e.printStackTrace()
        if (error == null) {
          error = e
        }
      }

    }
    if (error != null) {
      throw ExceptionUtil.exception(error)
    }
    //Allthough the work the Processes are doing is 'finished' It is possible the threads themselves
    // are still 'busy' for a brief time thereafter so wait for the threads to die.
    for (process in processes) {
      process.join(500) //shouldn't be long (unless test is ill-behaved and process is 'stuck', but then it would
      // not be possible to reach this point, since at least a TimeoutException will be raised
      // above as a result of that 'stuck' Process's result.promise timing out.
    }
  }

  default object {

    /**
     * Limits the duration of various operations in the test harness so that we
     * can, for example also write 'negative' tests that succeed only if
     * certain messages are not received (within the timeout).
     */
    public val TIMEOUT: Long = 2000

    /**
     * The expectation in the test harness is that Processes are meant to terminate naturally
     * without raising exceptions, within a reasonable time. When a process gets stuck
     * this timeout kicks in to allow operations that are waiting for processes to terminate to proceed.
     */
    public val STUCK_PROCESS_TIMEOUT: Long = 60000

    public fun <T> assertError(expected: Class<out Throwable>, r: BasicFuture<T>) {
      var result: T? = null
      var error: Throwable? = null
      try {
        result = r.get()
      }
      catch (e: Throwable) {
        error = e
      }

      TestCase.assertTrue("Should have thrown " + expected.getName() + " but returned " + result, error != null && (expected.isAssignableFrom(error!!.javaClass) || expected.isAssignableFrom(ExceptionUtil.getDeepestCause(error).getClass())))
    }

    public fun <T> assertError(expectContains: String, r: BasicFuture<T>) {
      var result: T? = null
      var error: Throwable? = null
      try {
        result = r.get()
      }
      catch (e: Throwable) {
        error = e
      }

      TestCase.assertTrue("Should have thrown '..." + expectContains + "...' but returned " + result, error != null && (contains(error!!.getMessage(), expectContains) || contains(ExceptionUtil.getDeepestCause(error).getMessage(), expectContains)))
    }

    private fun contains(message: String?, expectContains: String): Boolean {
      return message != null && message.contains(expectContains)
    }
  }
}
