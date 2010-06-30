/**
 * @fileoverview
 * Supporting JS for /plan/index.html
 * This is loosely adapted from third_party/foograph/test.html
 *
 * @overrides window
 * @requires Graph, Vertex, VertexStyle, canvasWrapper
 * @requires ForceDirectedVertexLayout, KamadaKawaiVertexLayout
 */

(function() {
  // Styles used for different kinds of nodes in the draw function below.
  // Dark green border around green.
  var staleNode = new VertexStyle(
      'ellipse', 80, 40, '#cccccc', '#333333', true);
  // Dark grey border around grey.
  var upToDateNode = new VertexStyle(
      'ellipse', 80, 40, '#aaffaa', '#004400', true);

  function noop() {}

  // Send an XMLHttpRequest to plan.json for graph data and layout the graph
  // when it arrives and refetches after some time.
  var lastJsonDrawn;
  function requestGraph() {
    var req = new XMLHttpRequest();
    req.onreadystatechange = function () {
      if (req.readyState === 4) {
        var json = req.responseText;
        req.onreadystatechange = noop;  // Work around GC problems.
        setTimeout(requestGraph, 2500 /*ms*/);
        if (req.status === 200) {
          if (lastJsonDrawn !== json) {
            lastJsonDrawn = json;
            drawGraph(JSON.parse(json));
          }
        }
      }
    };
    // Assumes current directory is in the plan directory
    req.open('GET', 'plan.json', true);
    req.send();
  }

  var selected;
  function scrollTo(productName) {
    var id = 'detail:' + productName;
    var newSelected = document.getElementById(id);
    if (selected) {
      selected.className = selected.className.replace(/\bselected\b/g, '');
    }
    selected = newSelected;
    if (selected) {
      selected.className = selected.className + ' selected';
    }
    window.location = '#' + id;
  }
  setTimeout(
      function () {
        var m = location.hash.match(/^#detail:(.+)$/);
        if (m) { scrollTo(m[1]); }
      }, 0);

  var canvasCont = document.getElementById('plan-graph-cont');
  var width = canvasCont.offsetWidth - 4;
  var height = canvasCont.offsetHeight - 4;

  // Draws a graph given JSON of the form fetchable from plan.json.
  function drawGraph(graphJson) {
    Math.resetRandom();

    var g = new Graph('Plan Graph', true/*directed*/);
    var vertices = {};
    for (var nodeName in graphJson.graph) {
      var nodeInfo = graphJson.graph[nodeName];
      var style = nodeInfo.upToDate ? upToDateNode : staleNode;
      var vertex = vertices[nodeName] = new Vertex(nodeName, -1, -1, style);
      g.insertVertex(vertex);
    }

    for (var nodeName in graphJson.graph) {
      var adjacentNodes = graphJson.graph[nodeName].requires;
      var startVertex = vertices[nodeName];
      for (var i = 0, n = adjacentNodes.length; i < n; ++i) {
        var endVertex = vertices[adjacentNodes[i]];
        var label = null;  // TODO: label with glob intersection.
        g.insertEdge(label, 3/*weight*/, startVertex, endVertex, null/*style*/);
      }
    }

    // Layout the graph
    new KamadaKawaiVertexLayout(width, height).layout(g);
    new ForceDirectedVertexLayout(width, height, 400, false/*re-randomize*/)
        .layout(g);

    // Set up the canvas
    var canvasCanvas = document.getElementById('plan-graph');
    canvasCanvas.style.width = width + 'px';
    canvasCanvas.style.height = height + 'px';
    canvasCanvas.width = width;
    canvasCanvas.height = height;

    // Register an onclick handler to handle clicks on nodes.
    canvasCanvas.onclick = function (event) {
      event = event || window.event;
      var x = event.clientX;
      var y = event.clientY;
      for (var el = canvasCanvas; el; el = el.offsetParent) {
        x -= el.offsetLeft;
        y += el.scrollTop - el.offsetTop;
      }
      for (var i = 0, n = g.vertices.length; i < n; ++i) {
        var vertex = g.vertices[i];
        var dx = x - vertex.x, dy = y - vertex.y;
        if (dx >= 0 && dx < vertex.style.width
            && dy >= 0 && dy < vertex.style.height) {
          var productName = vertex.label;
          scrollTo(productName);
          break;
        }
      }
    };

    // Draw the graph
    var canvas = new canvasWrapper(canvasCanvas);
    canvas.clear();
    g.plot(canvas);
    canvas.paint();
  }

  requestGraph();
})();
