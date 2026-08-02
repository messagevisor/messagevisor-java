package com.messagevisor.sdk;

import java.util.List;

public final class MessagevisorCloseException extends MessagevisorException {
  private final List<Exception> errors;

  public MessagevisorCloseException(String message, List<Exception> errors) {
    super(message);
    this.errors = List.copyOf(errors);
  }

  public List<Exception> errors() {
    return errors;
  }
}
