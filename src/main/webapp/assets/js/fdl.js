// Path View
// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
var pathLayout = function(initial) {
	
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

	//Add Shortest Path
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
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
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//End Add Shortest Path

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
		
		//console.log(json);
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
		if (force && force.resume)
			force.resume();
		additionMade = true;
		additionPending = false;
	}

	function update() {
		force.nodes(nodes).links(links).start();

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
				.call(drag).on("click", click).on("contextmenu", rightClick);
		nodeEnter.append("circle").style("fill", fillColor);
		nodeEnter.append("text")
			.html(filteredText).attr("text-anchor", "left");
		
		landmark_elements = landmark_elements.data(nodes.filter(function(d) {
			return d.landmark;
		}), function(d) {
			return d.name;
		});
		landmark_elements.exit().remove();
		landmark_elements.enter().append("a").html(function(d) {
			var label = "<br>&bull; (" + d.name + ") " + d.description;
			return label.substring(0, 40);
		});

		additionMade = false;
		refreshAfterZoom();
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
			return 8.0 / currentZoomLevel + "px";
		else
			return 1.0 / currentZoomLevel + "px";
	}

	function stroke(d) {
		if (d.source && d.source.path && d.target && d.target.path)
			return "black";
		else
			return "#91B5FF";
	}

	function fillColor(d) {
		if (d.landmark)
			return "yellow"
		if (d.path) {
			if (primaryNodes.indexOf(d.name.split("-")[0]) != -1)
				return "red";
			return "blue";
		}
		return "hsl(0, 0%, " + (25 + 50 * d.nodeWeight) + "%)";
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
		if (d.landmark || d.path) {
			if (d.description) {
				return d.description;
			} else {
				return d.name;
			}
		}
		return "";
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
		if (currentZoomLevel > 5) { // all labels
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
		var egonetTitle = "Show Egonet";
		d3.contextMenu([
		                {
		                	title: "Open OEIS Page",
		                	action: function(elm, d, i) {
		                		console.log("Open OEIS Page for vertex: " + d.name);
		                		window.open("http://oeis.org/A" + d.name.split("-")[0]);
		        	        }
		        	    }, {
		        	    	title: function(d_a) {
		        	    		return "Show Egonet (" + d_a.neighborhoodSize + ")";
		        	    	},
		        	    	action: function(elm, d_a, i) {
		        	    		if(d_a.neighborhoodSize < 128 || confirm("Large egonet, confirm to open in this view")) {
			        	    		d3.json("centroidPathService/getEgonet?vertex=" + d_a.name.split("-")[0], mergeNodesLinks);
			        	    		console.log("Show egonet for vertex: " + d_a.name);
			        	    		d_a.path = true;
		        	    		}
		        	    	}
		        	    }, {
		        	    	title: function(d_a) {
		        	    		return "Open Egonet in New Window";
		        	    	},
		        	    	action: function(elm, d_a, i) {
		        	    		window.open("/").toOpen = d_a.name;
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
	
	if(initial) {
		d3.json("centroidPathService/getEgonetWithoutCenter?vertex=" + initial, mergeNodesLinks);
		document.title = "Egonet of " + initial + " (" + document.title + ")";
	}

}
// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// End Path View
