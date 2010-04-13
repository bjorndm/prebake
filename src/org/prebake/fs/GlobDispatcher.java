package org.prebake.fs;

import org.prebake.core.ArtifactListener;
import org.prebake.core.Glob;
import org.prebake.core.GlobSet;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

/**
 * Dispatches changed paths to listeners based on unions of globs.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
final class GlobDispatcher {
  private final Multimap<Glob, GlobUnion> globsContaining
      = Multimaps.synchronizedListMultimap(Multimaps.newListMultimap(
          Maps.<Glob, Collection<GlobUnion>>newHashMap(),
          new Supplier<List<GlobUnion>>() {
            public List<GlobUnion> get() { return Lists.newArrayList(); }
          }));
  private final Multimap<GlobUnion, ArtifactListener<GlobUnion>> listeners
      = Multimaps.synchronizedListMultimap(Multimaps.newListMultimap(
          Maps.<GlobUnion, Collection<ArtifactListener<GlobUnion>>>newHashMap(),
          new Supplier<List<ArtifactListener<GlobUnion>>>() {
            public List<ArtifactListener<GlobUnion>> get() {
              return Lists.newArrayList();
            }
          }));
  private final GlobSet gset = new GlobSet();
  private final Logger logger;

  /** @param logger receives exceptions thrown by listeners. */
  GlobDispatcher(Logger logger) { this.logger = logger; }

  void dispatch(Iterable<Path> paths) {
    // Order of dispatch here is non-deterministic
    Set<GlobUnion> unions = Sets.newLinkedHashSet();
    for (Path p : paths) {
      for (Glob g : gset.matching(p)) {
        synchronized (globsContaining) {
          unions.addAll(globsContaining.get(g));
        }
      }
    }
    for (GlobUnion union : unions) {
      synchronized (listeners) {
        for (ArtifactListener<GlobUnion> unionListener : listeners.get(union)) {
          try {
            unionListener.artifactChanged(union);
          } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "Internal error", ex);
          }
        }
      }
    }
  }

  void watch(GlobUnion union, ArtifactListener<GlobUnion> listener) {
    synchronized (listeners) {
      if (!listeners.containsKey(union)) {
        for (Glob glob : union.globs) {
          Collection<GlobUnion> unions = globsContaining.get(glob);
          if (unions.isEmpty()) { gset.add(glob); }
          unions.add(union);
        }
      }
      listeners.put(union, listener);
    }
  }

  void unwatch(GlobUnion union, ArtifactListener<GlobUnion> listener) {
    synchronized (listeners) {
      Collection<ArtifactListener<GlobUnion>> listenerList
          = listeners.get(union);
      listenerList.remove(listener);
      if (listenerList.isEmpty()) {
        for (Glob glob : union.globs) {
          Collection<GlobUnion> unions = globsContaining.get(glob);
          unions.remove(union);
          if (unions.isEmpty()) { gset.remove(glob); }
        }
      }
    }
  }

  @VisibleForTesting
  String unittestBackdoorGlobKeys() {
    List<Glob> globKeys;
    synchronized (globsContaining) {
      globKeys = Lists.newArrayList(globsContaining.keySet());
    }
    Collections.sort(globKeys);
    return globKeys.toString();
  }
}
