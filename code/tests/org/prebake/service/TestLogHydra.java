package org.prebake.service;

import org.prebake.util.Clock;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Logger;

public final class TestLogHydra extends LogHydra {
  private final Logger logger;
  public OutputStream[] wrappedInheritedProcessStreams;

  public TestLogHydra(Logger logger, Path logDir, Clock clock) {
    super(logDir, clock);
    this.logger = logger;
  }

  @Override
  protected void doInstall(
      OutputStream[] wrappedInheritedProcessStreams,
      Handler logHandler) {
    this.wrappedInheritedProcessStreams = wrappedInheritedProcessStreams;
    logger.addHandler(logHandler);
  }
}
