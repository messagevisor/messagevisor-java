package com.messagevisor.sdk;

public interface MessagevisorModuleApi {
  void setFlagResolver(FlagResolver resolver);

  void setVariationResolver(VariationResolver resolver);

  default String getRevision() {
    return getRevision(null);
  }

  String getRevision(String locale);

  MessagevisorUnsubscribe onDiagnostic(
      MessagevisorDiagnosticHandler handler, MessagevisorModuleDiagnosticOptions options);

  void reportDiagnostic(MessagevisorModuleReportedDiagnostic diagnostic);
}
