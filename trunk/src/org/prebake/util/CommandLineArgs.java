package org.prebake.util;

import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps argv from the command line to provide a key/value pair view.
 *
 * @author mikesamuel@gmail.com
 */
public class CommandLineArgs {
  private final List<Flag> flags = Lists.newArrayList();
  private final List<String> values;

  public CommandLineArgs(String... argv) {
    int i = 0, argc = argv.length;
    for (; i < argc; ++i) {
      String arg = argv[i];
      if (arg.length() < 2 || !arg.startsWith("-")) { break; }
      if ("--".equals(arg)) {
        ++i;
        break;
      }
      String name, value;
      int eq = arg.indexOf('=');
      if (eq >= 0) {
        name = arg.substring(0, eq);
        value = arg.substring(eq + 1);
      } else if (arg.startsWith("--")) {
        name = arg;
        value = (i + 1 < argc) ? argv[++i] : null;
      } else {
        name = arg;
        value = null;
      }
      flags.add(new Flag(name, value));
    }
    values = Lists.newArrayList(Arrays.asList(argv).subList(i, argc));
  }

  /** The flags as a mutable list. */
  public List<Flag> getFlags() { return flags; }

  /** The non-flag values as a mutable list. */
  public List<String> getValues() { return values; }

  public static final class Flag {
    /** non-null, e.g. <tt>--foo</tt> from <tt>--foo=bar</tt>. */
    public final String name;
    /** possibly null, e.g. <tt>bar</tt> from <tt>--foo=bar</tt>. */
    public final String value;

    public Flag(String name, String value) {
      assert name != null;
      this.name = name;
      this.value = value;
    }

    @Override
    public String toString() {
      return value == null ? name : name + "=" + value;
    }
  }

  /**
   * Takes a first look at command line flags and creates a logger.
   * This is done first, so that any further command line interpretation
   * is done with an appropriately configured logger.
   *
   * @param args the command line arguments.  Modified in place to remove
   *     any related to logger configuration.
   * @param logger modified in place.
   * @return false if the caller should dump usage and exit.  True to proceed.
   */
  public static boolean setUpLogger(CommandLineArgs args, Logger logger) {
    if (!args.getFlags().isEmpty() &&
        args.getFlags().get(0).name.matches("^-+(?:[?hH]|help)$")) {
      return false;
    }
    Iterator<CommandLineArgs.Flag> it = args.getFlags().iterator();
    Level logLevel = logger.getLevel();
    while (it.hasNext()) {
      CommandLineArgs.Flag flag = it.next();
      if ("-v".equals(flag.name)) {
        logLevel = Level.FINE;
        it.remove();
      } else if ("-vv".equals(flag.name)) {
        logLevel = Level.FINEST;
        it.remove();
      } else if ("-q".equals(flag.name)) {
        logLevel = Level.WARNING;
        it.remove();
      } else if ("-qq".equals(flag.name)) {
        logLevel = Level.SEVERE;
        it.remove();
      } else if ("--logLevel".equals(flag.name) && flag.value != null) {
        try {
          logLevel = Level.parse(flag.value);
        } catch (IllegalArgumentException ex) {
          logger.log(Level.WARNING, "Unknown log level {0}", flag.value);
          continue;
        }
        it.remove();
      }
    }
    logger.setLevel(logLevel);
    return true;
  }
}
