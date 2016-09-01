// Path View
// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
var pathLayout = function(initial, openMarked, openList, subgraphMode) {
	
	// Add To Path View
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	function addToPathView() {
		if (additionPending) {
			alert("hold on, waiting for server");
			return;
		}
		if (isNaN(textField.value)) {
			alert(textField.value + " is not a sequence number");
			return;
		}
		if (primaryNodes.indexOf(textField.value) != -1) {
			alert("already added");
			return;
		}
		var addedSequence = textField.value;
		if(isNaN(addedSequence)) {
			alert(addedSequence + "is not a sequence number");
			return;
		}
		additionPending = true;
		hist.push(textField.value);
		textField.value = "";
		// console.log(nodes);

		primaryNodes.push(addedSequence);
		var peelLevel = parseInt(peelSelect.options[peelSelect.selectedIndex].text) || 0;
		d3.json("centroidPathService?vertex=" + addedSequence + "&peel=" + peelLevel, mergeNodesLinks);
		// add to global nodes/links lists, set boolean flag, call force.resume
	}
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// End Add To Path View

	// Add Shortest Path
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	function addShortestPath() {
		if (additionPending) {
			alert("hold on, waiting for server");
			return;
		}
		if (isNaN(textFieldOne.value)) {
			alert(textFieldOne.value + " is not a sequence number");
			return;
		}
		if (isNaN(textFieldTwo.value)) {
			alert(textFieldTwo.value + " is not a sequence number");
			return;
		}
		var seqOne = parseInt(textFieldOne.value);
		var seqTwo = parseInt(textFieldTwo.value);
		additionPending = true;
		primaryNodes.push(textFieldOne.value);
		primaryNodes.push(textFieldTwo.value);
		textFieldOne.value = "";
		textFieldTwo.value = "";

		d3.json("centroidPathService/shortestPath?one=" + seqOne + "&two=" + seqTwo, mergeNodesLinks);
		// add to global nodes/links lists, set boolean flag, call force.resume
	}
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// End Add Shortest Path

	// merge arrays of links sorted lexically by id
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	function mergeInLinks(addLinks) {
		var newLinks = [];
		var nodeLookup = [];
		for (i = 0; i < nodes.length; i++) {
			nodeLookup[nodes[i].name] = nodes[i];
		}
		addLinks.sort(function(a, b){
		    if(a.source_name < b.source_name) return -1;
		    if(a.source_name > b.source_name) return 1;
		    if(a.target_name < b.target_name) return -1;
		    if(a.target_name > b.target_name) return 1;
			return 0;
		});
		// merging avoid duplicates
		while (links.length > 0 && addLinks.length > 0) {
			if (!addLinks[0].source) {
				addLinks[0].source = nodeLookup[addLinks[0].source_name];
				addLinks[0].target = nodeLookup[addLinks[0].target_name];
				if(!(addLinks[0].source && addLinks[0].target)) {
					console.log(addLinks[0].source_name + " or " + addLinks[0].target_name + " not found");
				}
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
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	function mergeNodesLinks(error, json) {
		if (error) {
			alert("Sequence " + textField.value + " not found");
			console.log(error);
			additionPending = false;
			return;
		}
		
		// console.log(json);
		force.stop();
		if (json.nodes) {
			json.nodes.sort(function(a, b){
				if(a.name < b.name) return -1;
				if(a.name > b.name) return 1;
				return 0;
			});
			var newNodes = [];
			while (nodes.length > 0 && json.nodes.length > 0) {
				var name_node = nodes[0].name.split("-")[0];
				var name_json = json.nodes[0].name.split("-")[0];

				if (name_node == name_json) {
					var current = nodes.shift();
					var other = json.nodes.shift();
					current.path = current.path || other.path;
					current.description = current.description || other.description;
					current.landmark = current.landmark || other.landmark;
					newNodes.push(current);
				} else if (name_node < name_json) {
					newNodes.push(nodes.shift());
				} else {
					var temp = json.nodes.shift();
					temp.x = window.innerWidth / 2;
					temp.y = window.innerHeight / 2;
					newNodes.push(temp);
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
		additionMade = true;
		additionPending = false;
		force.resume();
	}
	
	document.getElementById("addToRelationViewButton").onclick = addToPathView;
	document.getElementById("addShortestPathButton").onclick = addShortestPath;
	
	document.getElementById("resumeForceButton").onclick = function() {
		if (force && force.resume)
			force.resume();
	};
	document.getElementById("stopForceButton").onclick = function() {
		if (force && force.stop)
			force.stop();
	};
	
	document.getElementById("openMarkedButton").onclick = function() {
		var csvVal = document.getElementById("batchList").value;
		if(csvVal.length == 0) {
			window.open("/").openMarked = true;
		} else {
			var split = csvVal.split(",");
			var listInts = [];
			for(var i = 0; i < split.length; i++) {
				var n = ~~Number(split[i]);
				if(isNaN(n) || (String(n) != split[i]) || n < 1) {
					alert("CSV input contains something that is not a valid sequence number");
					return;
				}
				listInts.push(n);
			}
			window.open("/").openList = listInts;
			document.getElementById("batchList").value = "";
		}
	};
	
	document.getElementById("markedSubgraphButton").onclick = function() {
		var csvVal = document.getElementById("batchList").value;
		if(csvVal.length == 0) {
			window.open("/").openMarked = true;
		} else {
			var split = csvVal.split(",");
			var listInts = [];
			for(var i = 0; i < split.length; i++) {
				var n = ~~Number(split[i]);
				if(isNaN(n) || (String(n) != split[i]) || n < 1) {
					alert("CSV input contains something that is not a valid sequence number");
					return;
				}
				listInts.push(n);
			}
			var w = window.open("/");
			w.openList = listInts;
			w.subgraphMode = true;
			document.getElementById("batchList").value = "";
		}
	};
	
	// input fields
	var textField = document.getElementById("sequenceId");
	var textFieldOne = document.getElementById("sequenceIdOne");
	var textFieldTwo = document.getElementById("sequenceIdTwo");
	var peelSelect = document.getElementById("peelSelection");
	
	// behaviors
	var force = d3.layout.force().linkDistance(120).charge(-120).gravity(.01).on(
			"tick", tick);
	var zoom = d3.behavior.zoom().scaleExtent([ 0.01, 100 ]).on("zoom", zoomed);
	var drag = d3.behavior.drag().origin(function(d) {
		return d;
	}).on("dragstart", dragstarted).on("drag", dragged).on("dragend", dragend);
	
	
	var width = window.innerWidth, height = window.innerHeight, root;
	var additionMade = false, additionPending = false;
	var primaryNodes = [];
	var nodes = [], links = [];
	var hist = [];
	
	document.getElementById("showTreeButton").onclick = function() {
		console.log("Show Tree");
		var p_level = parseInt(peelSelect.options[peelSelect.selectedIndex].text) || 0;
		d3.json("centroidPathService/getLandmarks?peel=" + p_level, mergeNodesLinks);
	}
	
	d3.json("centroidPathService/peelLevels", function(error, data) {
		if(error) {
			alert("Cannot get number of peel levels from server");
			console.log(error);
		}
		for(var i = 0; i < data; i++) {
			var option = document.createElement("option");
			option.text = i;
			peelSelect.add(option);
		}
	});

	var currentZoomLevel = 1.0;
	var fontSize = 0.75;
	var nodeSize = 3.0;
	var sizeMultiplier = 1.1;

	var svg = d3.select("body").append("svg");
	var largestField = svg.append("g").attr("class", "fullField").call(zoom);
	largestField.append("rect");
	var zoomableField = largestField.append("g").attr("class", "zoomableField");
	var link_elements = zoomableField.append("g").attr("class", "links")
			.selectAll(".link");
	var node_elements = zoomableField.append("g").attr("class", "nodes")
			.selectAll(".node");
	var landmark_elements = d3.select("#landmarkList").append("a").selectAll("a");
	var marked_elements = d3.select("#markedList").append("a").selectAll("a");

	d3.select(window).on("resize", resize);

	document.getElementById("incFontSize").onclick = function() {
		fontSize *= sizeMultiplier;
		refreshAfterZoom();
	};
	document.getElementById("decFontSize").onclick = function() {
		fontSize /= sizeMultiplier;
		refreshAfterZoom();
	};
	document.getElementById("incNodeSize").onclick = function() {
		nodeSize *= sizeMultiplier;
		refreshAfterZoom();
	};
	document.getElementById("decNodeSize").onclick = function() {
		nodeSize /= sizeMultiplier;
		refreshAfterZoom();
	};
	
	document.getElementById("relationViewButton").onclick = function() {
		console.log("Clear View");
		nodes = [];
		links = [];
		landmarks = [];
		hist = [];
		additionMade = true;
		additionPending = false;
		if (force && force.resume)
			force.resume();
	}
	
	function updateMarked() {
		for(var i = 0; i < nodes.length; i++) {
			for(var j = 0; j < highest.markedVertices.length; j++) {
				if(nodes[i].name == highest.markedVertices[j].name) {
					nodes[i].marked = true;
				}
			}
		}
		
		marked_elements = marked_elements.data(highest.markedVertices, function(d) {
			return d.name;
		});
		marked_elements.enter().append("a").html(function(d) {
			var label = "<br>&bull; (" + d.name + ") " + d.description;
			return label.substring(0, 40);
		});
	}

	function update() {
		force.nodes(nodes).links(links).start();
		updateMarked();
		link_elements = link_elements.data(links, function(d) {
			return d.id;
		}).attr("stroke-width", strokeWidth).style("stroke", stroke);
		link_elements.exit().remove();
		link_elements.enter().insert("path", ".node").attr("class", "link");

		node_elements = node_elements.data(nodes, function(d) {
			return d.name;
		})
		node_elements.exit().remove();
		var nodeEnter = node_elements.enter().append("g").attr("class", "node")
				.call(drag).on("click", click).on("contextmenu", rightClick).attr("id", function(d) {
					return "vertex_" + d.name;
				});
		nodeEnter.append("circle").style("fill", fillColor);
		nodeEnter.append("text").attr("text-anchor", "left");
		nodeEnter.append("polygon").attr("points", "5,0.5 2,9.9 9.5,3.9 0.5,3.9 8,9.9");
		
		landmark_elements = landmark_elements.data(nodes.filter(d => d.landmark),
			function(d) {
				return d.name;
			});
		landmark_elements.exit().remove();
		landmark_elements.enter().append("a").html(function(d) {
			var label = "<br>&bull; (" + d.name + ") " + d.description;
			return label.substring(0, 40);
		}).on("click", click);

		additionMade = false;
		updateLabelFraction();
		console.log("showing " + nodes.length + " nodes and " + links.length + " links")
	}

	function tick() {
		// updates drawn locations based on current positions in FDL
		if (additionMade)
			update();
		
		link_elements.attr('d', function(d) {
			return 'M' + d.source.x + ',' + d.source.y + 'L' + d.target.x + ','
					+ d.target.y;
		});

		node_elements.attr("transform", function(d) {
			return "translate(" + d.x + "," + d.y + ")";
		});
	}

	function strokeWidth(d) {
		if (d.source && d.source.path && d.target && d.target.path)
			return 3.0 / currentZoomLevel + "px";
		else
			return 0.6 / currentZoomLevel + "px";
	}

	function stroke(d) {
		if (d.source && d.source.path && d.target && d.target.path)
			return "black";
		else
			return "#91B5FF";
	}

	function fillColor(d) {
		if (d.landmark)
			return "rgb(0,0,0)"
		if (d.path) {
			if (primaryNodes.indexOf(d.name) != -1)
				return "rgb(85,85,85)";
			return "rgb(171,171,171)";
		}
		return "rgb(255,255,255)";
	}
	

	function size(d) {
		var baseSize = (d.landmarkWeight || -0.25) + 1.0;
		if(d.path)
			return 2 + nodeSize * baseSize * 2.25 / currentZoomLevel;
		else if(d.landmark)
			return 2 + nodeSize * baseSize * 1.8 / currentZoomLevel;
		else
			return 2 + nodeSize * baseSize / currentZoomLevel;
	}

	function allText(d) {
		return d.description || d.name;
	}

	function filteredText(d) {
		if (nodes.length < 128) {
			return d.description || d.name;
		} else {
			if (d.path) {
				return d.description || d.name;
			} else if (d.landmark) {
				var index = window.labeledLandmarks.indexOf(d.name);
				if(index >= 0) {
					return d.description || d.name;
				}
			}
			return "";
		}
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
		refreshAfterZoom();
	}

	function refreshAfterZoom() {
		node_elements.select("circle").attr("r", size).attr("stroke-width",
				1.0 / currentZoomLevel + "px");
		node_elements.select("polygon").attr("style", function(d) {
			if(d.marked) return "fill: red;";
			else return "fill: none;";
		}).attr("transform", "scale(" + 1.5/currentZoomLevel + "," + 1.5/currentZoomLevel + ")").attr("x", size);
		if (currentZoomLevel > 4) { // all labels
			node_elements.select("text").text(allText).attr("font-size",
					fontSize / currentZoomLevel + "em").attr("x", size);
		} else {
			node_elements.select("text").text(filteredText).attr("font-size",
					fontSize / currentZoomLevel + "em").attr("x", size);
		}
		link_elements.attr("stroke-width", strokeWidth).style("stroke", stroke);
	}

	function click(d_c) {
		if (d3.event.defaultPrevented)
			return;
		d_c.path = true;
		refreshAfterZoom();
		d3.contextMenu([
		                {
		                	title: function(d_a) {
		                		return (d_a.description || d_a.name).substring(0, 25);
		                	}, action: function(elm, d_a, i) {}
		        	    }, {
		        	    	title: function(d_a) {
		        	    		return "Show Egonet";
		        	    	},
		        	    	action: function(elm, d_a, i) {
			        	    	d3.json("centroidPathService/getEgonet?vertex=" + d_a.name.split("-")[0], mergeNodesLinks);
		        	    	}
		        	    }, {
		        	    	title: "Open Egonet in New Window",
		        	    	action: function(elm, d_a, i) {
		        	    		window.open("/").toOpen = d_a.name;
		        	    	}
		        	    }, {
		        	    	title: "Set as Source",
		        	    	action: function(elm, d_a, i) {
		        	    		textFieldOne.value = d_a.name;
		        	    	}
		        	    }, {
		        	    	title: "Set as Destination",
		        	    	action: function(elm, d_a, i) {
		        	    		textFieldTwo.value = d_a.name;
		        	    	}
		        	    }, {
		        	    	title: "Mark Vertex",
		        	    	action: function(elm, d_a, i) {
		        	    		highest.markedVertices.push(d_a);
		        	    		update();
		        	    	}
		        	    } ])(d_c);
	}
	
	function rightClick(d) {
		d3.event.preventDefault();
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
				var key = d3.event.key || d3.event.keyCode;
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
	
	resize();
	
	d3.select(".menu").on("mouseover", updateMarked);
	
	function updateLabelFraction() {
		var v = document.getElementById("labelFraction").value;
		var landmarks = nodes.filter(d => d.landmark);
		var sorted = landmarks.sort(function(a, b) {
		    return b.landmarkWeight - a.landmarkWeight;
		}).map(d => d.name);
		window.labeledLandmarks = sorted.slice(0, sorted.length * v / 100);
		refreshAfterZoom();
	}
	d3.select("#labelFraction").on("change", updateLabelFraction);
	
	if(initial) {
		d3.text("centroidPathService/description?vertex=" + initial, function(error, data) {
			if(error) {
				console.log(error);
				return;
			}
			d3.select("#descriptionField").html("Egonet of: (" + initial + ") " + data.substring(0, 30));
		});
		d3.json("centroidPathService/getEgonetWithoutCenter?vertex=" + initial, mergeNodesLinks);
		document.title = "Egonet of " + initial + " (" + document.title + ")";
	} else if(openMarked) {
		// console.log("Open marked vertices");
		// console.log(highest.markedVertices);
		var v = highest.markedVertices.map(d => d.name);
		d3.json("centroidPathService/getSubgraph")
			.header("Content-Type", "application/json")
			.post(JSON.stringify(v), mergeNodesLinks);
		primaryNodes = v;
		document.title = "Marked Vertices (" + document.title + ")";
	} else if(Array.isArray(openList)) {
		if(subgraphMode) {
			d3.json("centroidPathService/getSubgraphInduced")
			.header("Content-Type", "application/json")
			.post(JSON.stringify(openList), mergeNodesLinks);
		primaryNodes = openList;
		document.title = "Listed Vertices (" + document.title + ")";
		} else {
			d3.json("centroidPathService/getSubgraph")
				.header("Content-Type", "application/json")
				.post(JSON.stringify(openList), mergeNodesLinks);
			primaryNodes = openList;
			document.title = "Listed Vertices (" + document.title + ")";
		}
	}

}
// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// End Path View
