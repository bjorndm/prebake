package org.prebake.service.plan;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.collect.ImmutableList;

@ParametersAreNonnullByDefault
public interface ProductWatcher {
  void productDestroyed(String productName);
  void productDefined(Product product);

  public static final class Factory {
    private Factory() { /* no-op */ }
    public static ProductWatcher noop() { return new NoopProductWatcher(); }
    public static ProductWatcher chain(ProductWatcher... watchers) {
      ImmutableList.Builder<ProductWatcher> unrolled = ImmutableList.builder();
      for (ProductWatcher w : watchers) {
        if (w == null || w instanceof NoopProductWatcher) { continue; }
        if (w instanceof ProductWatcherChain) {
          unrolled.addAll(((ProductWatcherChain) w).watchers);
        } else {
          unrolled.add(w);
        }
      }
      ImmutableList<ProductWatcher> watcherList = unrolled.build();
      switch (watcherList.size()) {
        case 0: return new NoopProductWatcher();
        case 1: return watcherList.get(0);
        default: return new ProductWatcherChain(watcherList);
      }
    }
  }
}

final class NoopProductWatcher implements ProductWatcher {
  public void productDefined(Product product) { /* no-op */ }
  public void productDestroyed(String productName) { /* no-op */ }
}

final class ProductWatcherChain implements ProductWatcher {
  final ImmutableList<ProductWatcher> watchers;
  ProductWatcherChain(ImmutableList<ProductWatcher> watchers) {
    this.watchers = watchers;
  }
  public void productDefined(Product product) {
    RuntimeException rte = null;
    for (ProductWatcher w : watchers) {
      try {
       w.productDefined(product);
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
  public void productDestroyed(String productName) {
    RuntimeException rte = null;
    for (ProductWatcher w : watchers) {
      try {
       w.productDestroyed(productName);
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
