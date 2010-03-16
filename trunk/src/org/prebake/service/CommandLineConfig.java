package org.prebake.service;

import org.prebake.channel.JsonSink;
import org.prebake.core.DidYouMean;
import org.prebake.core.MessageQueue;

import java.io.IOException;
import java.nio.file.AccessMode;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.Attributes;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Configuration derived from command line arguments.
 *
 * @author mikesamuel@gmail.com
 */
final class CommandLineConfig implements Config {
  private final FileSystem fs;
  private final Path clientRoot;
  private final Pattern ignorePattern;
  private final Set<Path> planFiles = new LinkedHashSet<Path>();
  private final List<Path> toolDirs = new ArrayList<Path>();
  private final int umask;

  private static final short DEFAULT_UMASK = 0x1a0 /* octal 0640 */;
  private static final String DANGLING_MODIFIER_MSG;

  static {
    // Find a way to detect misuse of * and ? in a RegExp that are commonly
    // misapplied by people who confuse RegExps and Globs.
    String msga = null, msgb = null;
    try {
      Pattern.compile("*");
    } catch (PatternSyntaxException ex) {
      msga = ex.getMessage();
    }
    try {
      Pattern.compile("foo.???");
    } catch (PatternSyntaxException ex) {
      msgb = ex.getMessage();
    }
    DANGLING_MODIFIER_MSG = commonPrefix(msga, msgb);
  }

  enum FlagName {
    ROOT("--root"),
    IGNORE("--ignore"),
    TOOLS("--tools"),
    UMASK("--umask"),
    ;

    final String flag;

    FlagName(String flag) { this.flag = flag; }
  }

  CommandLineConfig(FileSystem fs, MessageQueue mq, String... argv) {
    this.fs = fs;
    {
      int argi;
      int argc = argv.length;
      Path clientRoot = null;
      Pattern ignorePattern = null;
      Integer umask = null;
      for (argi = 0; argi < argc; ++argi) {
        String arg = argv[argi];
        if (!arg.startsWith("-")) { break; }
        if ("--".equals(arg)) {
          ++argi;
          break;
        }
        int eq = arg.indexOf('=');
        String value;
        if (eq >= 0) {
          value = arg.substring(eq + 1);
          arg = arg.substring(0, eq);
        } else if (argi + 1 < argc) {
          value = argv[++argi];
        } else {
          mq.error("No value for arg " + arg);
          break;
        }
        FlagName name = null;
        for (FlagName fn : FlagName.values()) {
          if (fn.flag.equals(arg)) { name = fn; }
        }
        if (name != null) {
          switch (name)  {
            case ROOT:
              if (clientRoot == null) {
                try {
                  clientRoot = fs.getPath(value).toRealPath(false);
                } catch (IOException ex) {
                  mq.error("Bad root " + value);
                }
              } else {
                mq.error("Dupe arg " + arg);
              }
              break;
            case IGNORE:
              if (ignorePattern == null) {
                try {
                  ignorePattern = Pattern.compile(value, Pattern.DOTALL);
                } catch (PatternSyntaxException ex) {
                  String msg = ex.getMessage();
                  mq.error(msg);
                  if (msg.contains(DANGLING_MODIFIER_MSG)) {
                    mq.error(
                        "Ignore pattern is a regular expression, not a glob");
                  }
                }
              } else {
                mq.error("Dupe arg " + arg);
              }
              break;
            case UMASK:
              if (umask == null) {
                try {
                  umask = Integer.valueOf(value, 8);
                } catch (NumberFormatException ex) {
                  mq.error("umask " + value + " is not a valid octal number");
                }
              } else {
                mq.error("Dupe arg " + arg);
              }
              break;
            case TOOLS:
              for (String d : value.split(Pattern.quote(getPathSeparator()))) {
                if (!"".equals(d)) {
                  Path p = fs.getPath(d);
                  try {
                    p = p.toRealPath(false);
                    if (!toolDirs.contains(p)) {
                      toolDirs.add(p);
                    } else {
                      mq.error("Dupe tools dir " + p);
                    }
                  } catch (IOException ex) {
                    mq.error("Bad tools dir " + p);
                  }
                }
              }
              break;
            default: throw new RuntimeException(arg);
          }
        } else {
          FlagName[] flagNames = FlagName.values();
          String[] flags = new String[flagNames.length];
          for (int i = flags.length; --i >= 0;) {
            flags[i] = flagNames[i].flag;
          }
          DidYouMean.toMessageQueue("Unrecognized flag " + arg, arg, mq, flags);
        }
      }
      if (argi < argc) {
        for (; argi < argc; ++argi) {
          String arg = argv[argi];
          Path p = fs.getPath(arg);
          try {
            p = p.toRealPath(false);
            if (!planFiles.add(p)) {
              mq.error("Duplicate plan file " + p);
            }
          } catch (IOException ex) {
            mq.error("Bad plan file " + p);
          }
        }
      } else if (clientRoot != null) {
        planFiles.add(clientRoot.resolve("recipe.js"));
      }
      this.clientRoot = clientRoot;
      this.ignorePattern = ignorePattern;
      this.umask = umask != null ? umask.intValue() : DEFAULT_UMASK;
    }

    if (clientRoot == null) {
      mq.error("Please specify --root");
    } else {
      try {
        BasicFileAttributes attrs = Attributes.readBasicFileAttributes(
            clientRoot, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isDirectory()) {
          mq.error("Client root " + clientRoot + " is not a directory");
        } else {
          clientRoot.checkAccess(AccessMode.WRITE);
        }
      } catch (IOException ex) {
        mq.error("Client root " + clientRoot + " is not writable");
      }
    }
    for (Path planFile : planFiles) {
      boolean ok;
      try {
        BasicFileAttributes attrs = Attributes.readBasicFileAttributes(
            planFile, LinkOption.NOFOLLOW_LINKS);
        ok = attrs.isRegularFile();
      } catch (IOException ex) {
        ok = false;
      }
      if (!ok) {
        mq.error("Plan file " + planFile + " is not a file");
      }
    }
    for (Path toolDir : toolDirs) {
      boolean ok;
      try {
        BasicFileAttributes attrs = Attributes.readBasicFileAttributes(
            toolDir, LinkOption.NOFOLLOW_LINKS);
        ok = attrs.isDirectory();
      } catch (IOException ex) {
        ok = false;
      }
      if (!ok) {
        mq.error("Tool dir " + toolDir + " is not a directory");
      }
    }
    if (// Extra bits
        (umask & ~0x1ff) != 0
        // Not readable by self
        || (umask & 0x080) == 0) {
      mq.error("Invalid umask " + String.format("%04o", umask));
    }
  }

  public static String toArgv(Config config) {
    List<String> argv = new ArrayList<String>();
    Path root = config.getClientRoot();
    if (root != null) {
      argv.add(FlagName.ROOT.flag);
      argv.add(root.toString());
    }
    Pattern p = config.getIgnorePattern();
    if (p != null) {
      argv.add(FlagName.IGNORE.flag);
      argv.add(p.pattern());
    }
    List<Path> toolDirs = config.getToolDirs();
    if (!toolDirs.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      String sep = config.getPathSeparator();
      for (Path d : toolDirs) {
        if (sb.length() != 0) { sb.append(sep); }
        sb.append(d);
      }
      argv.add(FlagName.TOOLS.flag);
      argv.add(sb.toString());
    }
    int umask = config.getUmask();
    if (umask != DEFAULT_UMASK) {
      argv.add(FlagName.UMASK.flag);
      argv.add(Integer.toOctalString(umask));
    }
    int planStart = argv.size();
    boolean needsSep = false;
    for (Path pf : config.getPlanFiles()) {
      String pfs = pf.toString();
      if (pfs.startsWith("-")) { needsSep = true; }
      argv.add(pfs);
    }
    if (needsSep) { argv.add(planStart, "--"); }
    StringBuilder sb = new StringBuilder();
    JsonSink sink = new JsonSink(sb);
    try {
      sink.writeValue(argv);
      sink.close();
    } catch (IOException ex) {
      throw new RuntimeException("IOException writing to StringBuilder", ex);
    }
    return sb.toString();
  }

  public Path getClientRoot() { return clientRoot; }

  public Pattern getIgnorePattern() { return ignorePattern; }

  public Set<Path> getPlanFiles() {
    return Collections.unmodifiableSet(planFiles);
  }

  public List<Path> getToolDirs() {
    return Collections.unmodifiableList(toolDirs);
  }

  public int getUmask() { return umask; }

  private static String commonPrefix(String a, String b) {
    int n = Math.min(a.length(), b.length());
    int i = 0;
    while (i < n && a.charAt(i) == b.charAt(i)) { ++i; }
    return a.substring(0, i);
  }

  public String getPathSeparator() {
    return "\\".equals(fs.getSeparator()) ? ";" : ":";
  }
}
