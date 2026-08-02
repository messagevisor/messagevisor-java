package com.messagevisor.sdk;

public class MessagevisorException extends RuntimeException {
  public MessagevisorException(String message) {
    super(message);
  }

  public MessagevisorException(String message, Throwable cause) {
    super(message, cause);
  }
}
