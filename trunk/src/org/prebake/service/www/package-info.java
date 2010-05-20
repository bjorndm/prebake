// Copyright 2010, Mike Samuel
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * An HTTP servlet that serves status information and documentation for tools
 * and plans, and dependency information for products.
 * <p>
 * It also provides a POST interface for continuous integration tools, and a
 * way for products to easily deploy HTML reports from tools like
 * findbugs, xunits, javadoc, doxygen, etc.
 * <p>
 * The HTTP servlet listens on the
 * {@link org.prebake.service.Config#getWwwPort www port} and services
 * requests to the following URL space:
 * <pre>
 * /
 *   index.html                             Starting page with links
 *   plan/
 *     index.html                           Plan graph and list of products
 *     plan.json                            JSON formatted plan summary.
 *     &lt;product-name<sub>0</sub>&gt;                      HTML product docs.
 *     &lt;product-name<sub>1</sub>&gt;
 *     ...
 *     &lt;product-name<sub>n</sub>&gt;
 *   tools/
 *     index.html                           List of tools.
 *     tools.json                           JSON formatted tool summary.
 *     &lt;tool-name<sub>0</sub>&gt;                         HTML tool docs.
 *     &lt;tool-name<sub>1</sub>&gt;
 *     ...
 *     &lt;tool-name<sub>n</sub>&gt;
 *   mirror/
 *     &lt;mirror of tree under prebake-www&gt;   Generated client reports.
 *   prebake-api/
 *     do                                   POST only API.
 *   log
 *     product
 *       &lt;product-name<sub>0</sub>&gt;
 *     tool
 *       &lt;tool-name<sub>0</sub>&gt;
 * </pre>
 *
 * <pre>
 * TODO: add a local-machine-trusted flag that allows users to connect from
 *   127.0.0.1 without auth_www.  Big scary warnings.
 * TODO: generate log files for tools and products and mirror them under log
 * TODO: show the logs for invalid tools and products on the doc pages.
 * TODO: interactive testbed for globs to test path transforms
 * TODO: interactive testbed for tools to see what command line they generate,
 *   and to get feedback on options
 * TODO: have plan graph page ping periodically to get the latest state.
 *   maybe have it ping the JSON page with a If-Modified-Since and generate the
 *   graph from the JSON.
 * TODO: make the plan graph embeddable -- as an iframe
 * TODO: for demos, have a page that shows the plan graph and a scrolling log
 *   of file watcher changes.
 * </pre>
 */
@javax.annotation.ParametersAreNonnullByDefault
package org.prebake.service.www;
