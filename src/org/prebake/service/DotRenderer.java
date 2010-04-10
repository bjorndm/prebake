package org.prebake.service;

import org.prebake.service.plan.PlanGraph;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.IOError;
import java.io.IOException;
import java.util.List;
import java.util.Set;

final class DotRenderer {
  static void render(
      final PlanGraph g, final Set<String> prods, final Appendable out)
      throws IOException {
    final List<String> roots = Lists.newArrayList(prods);
    // Eliminate any prods that are dependencies of others.
    {
      final Set<String> notRoots = Sets.newHashSet();
      for (final String prod : prods) {
        if (notRoots.contains(prod)) { break; }
        new Walker(g, new Function<String, Void>() {
          public Void apply(String node) {
            if (!node.equals(prod) && prods.contains(node)) {
              notRoots.add(node);
            }
            return null;
          }
        }).walk(prod);
      }
      roots.removeAll(notRoots);
    }
    out.append("digraph {\n");
    for (String root : roots) {
      try {
        new Walker(g, new Function<String, Void>() {
          public Void apply(String node) {
            try {
              out.append("  ");
              writeDotId(node);
              out.append(";\n");
              for (String dep : g.edges.get(node)) {
                out.append("  ");
                writeDotId(dep);
                out.append(" -> ");
                writeDotId(node);
                out.append(";\n");
              }
              // TODO: display intersection of inputs and outputs at edge
              // to help debug
            } catch (IOException ex) {
              throw new IOError(ex);
            }
            return null;
          }

          private void writeDotId(String id) throws IOException {
            out.append('"');
            out.append(id.replaceAll("[\\\\\"]", "\\\\$0"));
            out.append('"');
          }
        }).walk(root);
      } catch (IOError err) {
        throw (IOException) err.getCause();
      }
    }
    out.append("}\n");
  }

  private static class Walker {
    final PlanGraph g;
    final Function<String, ?> action;
    final Set<String> visited = Sets.newHashSet();

    Walker(PlanGraph g, Function<String, ?> action) {
      this.g = g;
      this.action = action;
    }

    void walk(String node) {
      if (!visited.add(node)) { return; }
      action.apply(node);
      for (String desc : g.edges.get(node)) { walk(desc); }
    }
  }
}
