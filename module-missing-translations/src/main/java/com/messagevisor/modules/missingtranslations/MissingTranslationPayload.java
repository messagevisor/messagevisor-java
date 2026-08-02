package com.messagevisor.modules.missingtranslations;

import com.messagevisor.sdk.MessagevisorDiagnostic;

public record MissingTranslationPayload(
    String messageKey,
    String locale,
    String revision,
    String source,
    MessagevisorDiagnostic diagnostic) {}
