package org.prebake.service.bake;

import org.prebake.core.Hash;
import org.prebake.service.plan.Product;

import java.util.concurrent.Future;

final class ProductStatus {
  final String productName;
  Product product;
  Hash productHash;
  Hash toolHashes;
  Hash inputHashes;
  Future<Hash> buildFuture;

  ProductStatus(String productName) { this.productName = productName; }
}
