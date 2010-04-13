package org.prebake.service;

import org.prebake.service.plan.PlanGraph;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.IOError;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Converts a plan graph to
 * <a href="http://www.graphviz.org/doc/info/lang.html">DOT format</a>.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
final class DotRenderer {
  static void render(
      final PlanGraph g, final Set<String> prods, final Appendable out)
      throws IOException {
    final List<String> roots = Lists.newArrayList(prods);
    // Eliminate any products that are dependencies of others.
    {
      final Set<String> notRoots = Sets.newHashSet();
      for (final String prod : prods) {
        if (notRoots.contains(prod)) { break; }
        g.walker(new Function<String, Void>() {
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
        g.walker(new Function<String, Void>() {
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
}
