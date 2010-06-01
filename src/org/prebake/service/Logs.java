package org.prebake.service;

import java.util.logging.Logger;

/**
 * A bundle of logging related objects.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class Logs {
  public final HighLevelLog highLevelLog;
  public final Logger logger;
  public final LogHydra logHydra;

  public Logs(HighLevelLog highLevelLog, Logger logger, LogHydra logHydra) {
    if (highLevelLog == null || logger == null || logHydra == null) {
      throw new NullPointerException();
    }
    this.highLevelLog = highLevelLog;
    this.logger = logger;
    this.logHydra = logHydra;
  }
}
