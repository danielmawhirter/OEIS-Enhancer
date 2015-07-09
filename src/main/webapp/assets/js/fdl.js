// "main"

// assign menu buttons to actions
document.getElementById("mainViewButton").onclick = hierarchyLayout;
document.getElementById("relationViewButton").onclick = relationLayout;
document.getElementById("addToRelationViewButton").onclick = addToRelationView;
document.getElementById("resumeForceButton").onclick = resumeForce;
document.getElementById("stopForceButton").onclick = stopForce;

var textField = document.getElementById("sequenceId");
var enableAddition = false, additionMade = false, additionPending = false;;
var primaryNodes = null;
var force = null;
var nodes = null, links = null;

function clearSVG() {
	d3.select("svg").remove();
	additionMade = false;
	enableAddition = false;
	if (force)
		force.stop();
	force = null;
	nodes = null;
	links = null;
}

function resumeForce() {
	if (force)
		force.resume();
}

function stopForce() {
	if (force)
		force.stop();
}

// Hierarchy View
// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
function hierarchyLayout() {
	clearSVG();
	var width = window.innerWidth, height = window.innerHeight, root;
	nodes = [];
	links = [];
	enableAddition = false;

	var e = document.getElementById("graphSelector");
	var selectedGraph = e.options[e.selectedIndex].value;

	force = d3.layout.force().linkDistance(120).charge(-120).gravity(.05).on(
			"tick", tick);

	var zoom = d3.behavior.zoom().scaleExtent([ 0.1, 10 ]).on("zoom", zoomed);

	var drag = d3.behavior.drag().origin(function(d) {
		return d;
	}).on("dragstart", dragstarted).on("drag", dragged).on("dragend", dragend);

	var currentZoomLevel = 1.0;

	var svg = d3.select("body").append("svg");
	var largestField = svg.append("g").attr("class", "fullField").call(zoom);
	largestField.append("rect");
	var zoomableField = largestField.append("g").attr("class", "zoomableField");
	var link_elements = zoomableField.append("g").attr("class", "links")
			.selectAll(".link");
	var node_elements = zoomableField.append("g").attr("class", "nodes")
			.selectAll(".node");

	d3.select(window).on("resize", resize);

	// loads hierarchy tree from json resource FILE
	d3.json("assets/json/" + selectedGraph + ".json", function(error, json) {
		if (error)
			throw error;

		root = json;
		resize();
		root.x = width / 2;
		root.y = height / 2;
		colorChildren(root);
		expand(root); // activate children of root
	});

	function update() {
		nodes = flatten(root);

		// Restart the force layout using current known linke
		/*
		 * force .nodes(nodes) .links(links) .start();
		 */
		force.stop();

		link_elements = link_elements.data(links, function(d) {
			return d.id;
		});
		link_elements.exit().remove();

		node_elements = node_elements.data(nodes, function(d) {
			return d.name;
		});
		node_elements.exit().remove();

		var nodeEnter = node_elements.enter().append("g").attr("class", "node")
				.on("click", click).call(drag);

		nodeEnter.append("circle").attr("r", size).attr("stroke-width",
				1.0 / currentZoomLevel + "px");

		nodeEnter.append("text")
		// .attr("dx", ".35em")
		.text(function(d) {
			return d.name.split(":")[0];
		}).attr("font-size", 1.0 / currentZoomLevel + "em");

		node_elements.select("circle").style("fill", function(d) {
			return d.color;
		});

		tick();
	}

	function updateLinks(error, addLinks) {
		if (error) {
			alert("Server unresponsive");
			return;
		}
		if (addLinks.error) {
			console.log("error");
			console.log(addLinks);
			return;
		}
		// console.log(addLinks);
		nodes = flatten(root);
		mergeInLinks(addLinks);

		link_elements = link_elements.data(links, function(d) {
			return d.id;
		});

		link_elements.exit().remove();

		link_elements.enter().insert("line", ".node").attr("class", "link")
				.attr("stroke-width", 1.5 / currentZoomLevel + "px");

		force.nodes(nodes).links(links).start();
	}

	function tick() {
		// updates drawn locations based on current positions in FDL
		link_elements.attr("x1", function(d) {
			return d.source.x;
		}).attr("y1", function(d) {
			return d.source.y;
		}).attr("x2", function(d) {
			return d.target.x;
		}).attr("y2", function(d) {
			return d.target.y;
		});

		node_elements.attr("transform", function(d) {
			return "translate(" + d.x + "," + d.y + ")";
		});
	}

	function resize() {
		width = window.innerWidth;
		height = window.innerHeight;
		force.size([ width, height ]).resume();
		svg.attr("viewBox", "0 0 " + width + " " + height);
		largestField.select("rect").attr("width", width).attr("height", height);
	}

	function zoomed() {
		currentZoomLevel = d3.event.scale;
		zoomableField.attr("transform", "translate(" + d3.event.translate
				+ ")scale(" + currentZoomLevel + ")");
		node_elements.select("circle").attr("r", size).attr("stroke-width",
				1.0 / currentZoomLevel + "px");
		node_elements.select("text").attr("font-size",
				1.0 / currentZoomLevel + "em");
		link_elements.attr("stroke-width", 1.5 / currentZoomLevel + "px");
	}

	d3.select("body").on(
			'keydown',
			function() {
				var step = 20;
				var key = d3.event.key || d3.event.keyCode; // safari doesn't
				// know .key
				if (document.activeElement == textField)
					return;
				switch (key) {
				case 65:
				case 37:
					zoom.translate([ zoom.translate()[0] + step,
							zoom.translate()[1] ]);
					break;
				case 68:
				case 39:
					zoom.translate([ zoom.translate()[0] - step,
							zoom.translate()[1] ]);
					break;
				case 87:
				case 38:
					zoom.translate([ zoom.translate()[0],
							zoom.translate()[1] + step ]);
					break;
				case 83:
				case 40:
					zoom.translate([ zoom.translate()[0],
							zoom.translate()[1] - step ]);
					break;
				default:
					return;
				}
				zoomableField.attr("transform", "translate(" + zoom.translate()
						+ ")scale(" + currentZoomLevel + ")");
			});

	function colorChildren(node) {
		var count = node.children.length, current = 0;
		scale = chroma.scale(
				[ "red", "orange", "yellow", "green", "blue", "indigo",
						"violet" ]).mode('lab');
		node.children.forEach(function(d) {
			d.color = scale((++current) / count).hex();
		});
	}

	function size(d) {
		if (d.size)
			return Math.max(Math.sqrt(d.size) / 5, 5.0) / currentZoomLevel;
		else
			return 5.0 / currentZoomLevel;
	}

	// Toggle children on click.
	function click(d) {
		if (d3.event.defaultPrevented)
			return; // ignore drag
		expand(d);
	}

	function dragstarted(d) {
		d3.event.sourceEvent.stopPropagation();
		d.fixed = true;
	}

	function dragged(d) {
		d.px += d3.event.dx;
        d.py += d3.event.dy;
        d.x += d3.event.dx;
        d.y += d3.event.dy; 
		tick();
	}
	
	function dragend(d) {
        tick();
	}
	
	function expand(n) {
		n.fixed = false;
		if (n.children) {
			var interests = [];
			var toRemove = [];
			for (i = 0; i < links.length; i++) {
				if (links[i].source_name === n.name) {
					toRemove.push(i);
					interests.push(links[i].target_name);
				} else if (links[i].target_name === n.name) {
					toRemove.push(i);
					interests.push(links[i].source_name);
				}
			}
			while (toRemove.length > 0) {
				links.splice(toRemove.pop(), 1);
			}
			n.active = false;
			n.children.forEach(function(d) {
				d.active = true;
				d.x = n.x + 40 * (Math.random() - 0.5);
				d.y = n.y + 40 * (Math.random() - 0.5);
				if (n != root)
					d.color = n.color;
				interests.push(d.name);
			});
			var queryString = "incidentEdges/" + selectedGraph + "/";
			var first = true;
			for (i = 0; i < interests.length; i++) {
				if (first)
					first = false;
				else
					queryString = queryString.concat("-");
				queryString = queryString.concat(interests[i]);
			}
			// console.log(queryString);
			console.log("node count: " + interests.length);
			d3.json(queryString, updateLinks);
			update();
		} else {
			if (isNaN(n.name))
				alert("Leaf id: " + n.name);
			else
				window.open("http://oeis.org/A" + n.name);
		}
	}

	// Returns the active antichain
	function flatten(root) {
		var nodes = [];

		function recurse(node) {
			if (node.active)
				nodes.push(node);
			else if (node.children)
				node.children.forEach(recurse);
		}

		recurse(root);
		return nodes;
	}
}
// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// End Hierarchy View

// Relation View
// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
function relationLayout() {
	clearSVG();
	console.log("starting path");
	var width = window.innerWidth, height = window.innerHeight, root;
	nodes = [];
	links = [];
	primaryNodes = [];
	enableAddition = true;
	additionMade = false;
	additionPending = false;

	force = d3.layout.force().linkDistance(120).charge(-120).gravity(.05).on(
			"tick", tick);

	var zoom = d3.behavior.zoom().scaleExtent([ 0.1, 10 ]).on("zoom", zoomed);

	var drag = d3.behavior.drag().origin(function(d) {
		return d;
	}).on("dragstart", dragstarted).on("drag", dragged).on("dragend", dragend);

	var currentZoomLevel = 1.0;

	var svg = d3.select("body").append("svg");
	var largestField = svg.append("g").attr("class", "fullField").call(zoom);
	largestField.append("rect");
	var zoomableField = largestField.append("g").attr("class", "zoomableField");
	var link_elements = zoomableField.append("g").attr("class", "links")
			.selectAll(".link");
	var node_elements = zoomableField.append("g").attr("class", "nodes")
			.selectAll(".node");

	d3.select(window).on("resize", resize);

	resize();

	function update() {
		console.log(additionMade, additionPending);
		console.log(links);
		force.nodes(nodes).links(links).start();

		link_elements = link_elements.data(links, function(d) {
			return d.id;
		});
		link_elements.exit().remove();
		link_elements.enter().insert("line", ".node").attr("class", "link")
				.attr("stroke-width", 1.5 / currentZoomLevel + "px");

		node_elements = node_elements.data(nodes, function(d) {
			return d.name;
		});
		node_elements.exit().remove();

		var nodeEnter = node_elements.enter().append("g").attr("class", "node")
				.on("dblclick", dblclick).call(drag);

		nodeEnter.append("circle").attr("r", size).attr("stroke-width",
				1.0 / currentZoomLevel + "px");

		nodeEnter.append("text")
		// .attr("dx", ".35em")
		.text(function(d) {
			return d.name.split(":")[0];
		}).attr("font-size", 1.0 / currentZoomLevel + "em");

		node_elements.select("circle").style("fill", function(d) {
			return d.color;
		});
		additionMade = false;
	}

	function tick() {
		// updates drawn locations based on current positions in FDL
		if (additionMade)
			update();
		link_elements.attr("x1", function(d) {
			return d.source.x;
		}).attr("y1", function(d) {
			return d.source.y;
		}).attr("x2", function(d) {
			return d.target.x;
		}).attr("y2", function(d) {
			return d.target.y;
		});

		node_elements.attr("transform", function(d) {
			return "translate(" + d.x + "," + d.y + ")";
		});
	}

	function resize() {
		width = window.innerWidth;
		height = window.innerHeight;
		force.size([ width, height ]).resume();
		svg.attr("viewBox", "0 0 " + width + " " + height);
		largestField.select("rect").attr("width", width).attr("height", height);
	}

	function zoomed() {
		currentZoomLevel = d3.event.scale;
		zoomableField.attr("transform", "translate(" + d3.event.translate
				+ ")scale(" + currentZoomLevel + ")");
		node_elements.select("circle").attr("r", size).attr("stroke-width",
				1.0 / currentZoomLevel + "px");
		node_elements.select("text").attr("font-size",
				1.0 / currentZoomLevel + "em");
		link_elements.attr("stroke-width", 1.5 / currentZoomLevel + "px");
	}
	
	function dblclick(d) {
		d.fixed = false;
	}

	function dragstarted(d) {
		d3.event.sourceEvent.stopPropagation();
		d.fixed = true;
	}

	function dragged(d) {
		d.px += d3.event.dx;
        d.py += d3.event.dy;
        d.x += d3.event.dx;
        d.y += d3.event.dy; 
		tick();
	}
	
	function dragend(d) {
        tick();
	}

	d3.select("body").on(
			'keydown',
			function() {
				var step = 20;
				var key = d3.event.key || d3.event.keyCode; // safari doesn't
				// know .key
				if (document.activeElement == textField)
					return;
				switch (key) {
				case 65:
				case 37:
					zoom.translate([ zoom.translate()[0] + step,
							zoom.translate()[1] ]);
					break;
				case 68:
				case 39:
					zoom.translate([ zoom.translate()[0] - step,
							zoom.translate()[1] ]);
					break;
				case 87:
				case 38:
					zoom.translate([ zoom.translate()[0],
							zoom.translate()[1] + step ]);
					break;
				case 83:
				case 40:
					zoom.translate([ zoom.translate()[0],
							zoom.translate()[1] - step ]);
					break;
				default:
					return;
				}
				zoomableField.attr("transform", "translate(" + zoom.translate()
						+ ")scale(" + currentZoomLevel + ")");
			});

	function size(d) {
		if (d.size)
			return Math.max(Math.sqrt(d.size) / 5, 5.0) / currentZoomLevel;
		else
			return 5.0 / currentZoomLevel;
	}

	// deal with d3, check boolean flag on tick
}
// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// End Relation View

// Add To Relation View
// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
function addToRelationView() {
	if (enableAddition) {
		if (additionPending) {
			alert("hold on, waiting for server");
			return;
		}
		if (isNaN(textField.value)) {
			alert(textField.value + " is not a sequence number");
			return;
		}
		additionPending = true;
		var addedSequence = textField.value;
		queryString = "pathAddition/" + textField.value + "/";
		if (primaryNodes.length == 0)
			queryString = queryString.concat("NONE");
		else {
			first = true;
			for (i = 0; i < primaryNodes.length; i++) {
				if (first)
					first = false;
				else {
					queryString = queryString.concat("-");
				}
				queryString = queryString.concat(primaryNodes[i]);
			}
		}
		console.log(queryString);
		d3.json(queryString, function(error, json) {
			if (error) {
				alert("Sequence " + textField.value + " not found");
				console.log(error);
				return;
			}
			force.stop();
			// console.log(json);
			if (json.nodes) {
				var newNodes = [];
				while (nodes.length > 0 && json.nodes.length > 0) {
					if (nodes[0].name == json.nodes[0].name) {
						newNodes.push(nodes.shift());
						json.nodes.shift();
					} else if (nodes[0].name < json.nodes[0].name) {
						newNodes.push(nodes.shift());
					} else {
						newNodes.push(json.nodes.shift());
					}
				}
				if (nodes.length == 0) {
					newNodes = newNodes.concat(json.nodes);
				}
				if (json.nodes.length == 0) {
					newNodes = newNodes.concat(nodes);
				}
				nodes = newNodes;
			}
			if (json.links) {
				mergeInLinks(json.links);
			}
			primaryNodes.push(addedSequence);
			additionMade = true;
			additionPending = false;
			force.resume();
		});
	} else {
		alert("This view is inactive");
	}
	// add to global nodes/links lists, set boolean flag, call force.resume
}
// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// End Add To Relation View

// merge arrays of links sorted lexically by id
function mergeInLinks(addLinks) {
	var newLinks = [];
	var nodeLookup = [];
	for (i = 0; i < nodes.length; i++) {
		nodeLookup[nodes[i].name] = nodes[i];
	}
	// merging avoid duplicates
	while (links.length > 0 && addLinks.length > 0) {
		if (!addLinks[0].source) {
			addLinks[0].source = nodeLookup[addLinks[0].source_name];
			addLinks[0].target = nodeLookup[addLinks[0].target_name];
		}
		if (links[0].id == addLinks[0].id) {
			newLinks.push(links.shift());
			addLinks.shift();
		} else if (links[0].id < addLinks[0].id) {
			newLinks.push(links.shift());
		} else {
			newLinks.push(addLinks.shift());
		}
	}
	if (links.length == 0) {
		while (addLinks.length > 0) {
			if (!addLinks[0].source) {
				addLinks[0].source = nodeLookup[addLinks[0].source_name];
				addLinks[0].target = nodeLookup[addLinks[0].target_name];
			}
			newLinks.push(addLinks.shift());
		}
	}
	if (addLinks.length == 0) {
		newLinks = newLinks.concat(links);
	}
	links = newLinks;
}
