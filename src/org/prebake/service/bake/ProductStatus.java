package org.prebake.service.bake;

import org.prebake.core.Hash;
import org.prebake.service.plan.Product;

import java.util.concurrent.Future;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
final class ProductStatus {
  final String productName;
  Product product;
  /** Iff the product is built, non-null. */
  Future<Hash> buildFuture;

  ProductStatus(String productName) { this.productName = productName; }
}
