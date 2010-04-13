package org.prebake.core;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.collect.ImmutableList;

/**
 * A listener that is alerted when a named artifact's state changes.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public interface ArtifactListener<ART> {

  void artifactDestroyed(String artifactName);
  void artifactChanged(ART artifact);

  public static final class Factory {
    private Factory() { /* no-op */ }
    public static @Nonnull <ART> ArtifactListener<ART> noop() {
      return NoopListener.<ART>instance();
    }
    public static @Nonnull <ART> ArtifactListener<ART> chain(
        ArtifactListener<ART> a, ArtifactListener<ART> b) {
      return chain(ImmutableList.of(a, b));
    }
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
