package com.messagevisor.modules.missingtranslations;

@FunctionalInterface
public interface MissingTranslationHandler {
  void handle(MissingTranslationPayload payload);
}
