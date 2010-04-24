package org.prebake.core;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.collect.ImmutableList;

/**
 * A listener that is alerted when a named artifact's state changes.
 * An artifact is any input, output, or intermediate or ancillary structure
 * that can change and need to be updated as files under the client dirs change.
 * Artifacts include files,
 * {@link org.prebake.service.tools.ToolSignature tool signatures},
 * {@link org.prebake.service.plan.Product products}, and
 * {@link org.prebake.service.plan.Action actions}.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public interface ArtifactListener<ART> {

  /** Called when an artifact is destroyed. */
  void artifactDestroyed(String artifactName);

  /** Called when an artifact changes or comes into existence. */
  void artifactChanged(ART artifact);

  /** Provides factory methods for common listener types. */
  public static final class Factory {
    private Factory() { /* no-op */ }
    /** A listener that takes no action. */
    public static @Nonnull <ART> ArtifactListener<ART> noop() {
      return NoopListener.<ART>instance();
    }
    /** A listener that multicasts to other listeners. */
    public static @Nonnull <ART> ArtifactListener<ART> chain(
        ArtifactListener<ART> a, ArtifactListener<ART> b) {
      return chain(ImmutableList.of(a, b));
    }
    /** A listener that multicasts to other listeners. */
    public static @Nonnull <ART> ArtifactListener<ART> chain(
        Iterable<ArtifactListener<ART>> listeners) {
      ImmutableList.Builder<ArtifactListener<ART>> unrolled
          = ImmutableList.builder();
      for (ArtifactListener<ART> w : listeners) {
        if (w == null || w instanceof NoopListener<?>) { continue; }
        if (w instanceof ArtifactListenerChain<?>) {
          unrolled.addAll(((ArtifactListenerChain<ART>) w).listeners);
        } else {
          unrolled.add(w);
        }
      }
      ImmutableList<ArtifactListener<ART>> listenerList = unrolled.build();
      switch (listenerList.size()) {
        case 0: return NoopListener.<ART>instance();
        case 1: return listenerList.get(0);
        default: return new ArtifactListenerChain<ART>(listenerList);
      }
    }
    /**
     * A listener that logs an exceptions raised by the given listener and
     * continues on.
     */
    public static @Nonnull <ART> ArtifactListener<ART> loggingListener(
        final ArtifactListener<ART> listener, final Logger logger) {
      if (listener instanceof NoopListener<?>) { return listener; }
      return new ArtifactListener<ART>() {
        public void artifactChanged(ART artifact) {
          try {
            listener.artifactChanged(artifact);
          } catch (Exception ex) {
            logger.log(Level.SEVERE, "Exception during event dispatch", ex);
          }
        }

        public void artifactDestroyed(String artifactName) {
          try {
            listener.artifactDestroyed(artifactName);
          } catch (Exception ex) {
            logger.log(Level.SEVERE, "Exception during event dispatch", ex);
          }
        }
      };
    }
  }
}

@ParametersAreNonnullByDefault
final class NoopListener<ART> implements ArtifactListener<ART> {
  private NoopListener() { /* no-op */ }
  public void artifactChanged(ART artifact) { /* no-op */ }
  public void artifactDestroyed(String artifactName) { /* no-op */ }

  private static final NoopListener<?> INSTANCE = new NoopListener<Object>();

  @SuppressWarnings("unchecked")
  static @Nonnull <ART> NoopListener<ART> instance() {
    return (NoopListener<ART>) INSTANCE;
  }
}

@ParametersAreNonnullByDefault
final class ArtifactListenerChain<ART>
    implements ArtifactListener<ART> {
  final ImmutableList<ArtifactListener<ART>> listeners;
  ArtifactListenerChain(ImmutableList<ArtifactListener<ART>> listeners) {
    this.listeners = listeners;
  }
  public void artifactChanged(ART artifact) {
    RuntimeException rte = null;
    for (ArtifactListener<ART> w : listeners) {
      try {
       w.artifactChanged(artifact);
      } catch (RuntimeException ex) {
        if (rte == null) {
          rte = ex;
        } else {
          ex.printStackTrace();
        }
      }
    }
    if (rte != null) { throw rte; }
  }
  public void artifactDestroyed(String artifactName) {
    RuntimeException rte = null;
    for (ArtifactListener<ART> w : listeners) {
      try {
       w.artifactDestroyed(artifactName);
      } catch (RuntimeException ex) {
        if (rte == null) {
          rte = ex;
        } else {
          ex.printStackTrace();
        }
      }
    }
    if (rte != null) { throw rte; }
  }
}
