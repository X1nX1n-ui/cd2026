package com.cd.server;

public interface AiAnalysisProvider<T, R> {

    String buildSystemPrompt();

    String buildUserPrompt(T input);

    Class<R> getResultType();

    String getTaskType();
}