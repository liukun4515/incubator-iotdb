package org.apache.iotdb.db.api;

/**
 * Created by liukun on 19/3/7.
 */
public class IoTDBEngineException extends Exception {

  public IoTDBEngineException() {
  }

  public IoTDBEngineException(String message) {
    super(message);
  }

  public IoTDBEngineException(String message, Throwable cause) {
    super(message, cause);
  }

  public IoTDBEngineException(Throwable cause) {
    super(cause);
  }

  public IoTDBEngineException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
