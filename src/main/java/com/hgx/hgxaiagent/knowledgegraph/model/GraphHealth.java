package com.hgx.hgxaiagent.knowledgegraph.model;

public record GraphHealth(
        boolean connected,
        String database,
        String message
) {
}
