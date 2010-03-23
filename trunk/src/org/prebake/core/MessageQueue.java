package org.prebake.core;

import com.google.common.collect.Lists;

import java.util.List;

public class MessageQueue {
  private final List<String> messages;
  private boolean hasErrors;

  public MessageQueue(List<String> messages) {
    assert messages != null;
    this.messages = messages;
  }

  public MessageQueue() { this(Lists.<String>newArrayList()); }

  public void error(String message) {
    this.hasErrors = true;
    getMessages().add(message);
  }

  public List<String> getMessages() { return messages; }

  public boolean hasErrors() { return hasErrors; }
}
