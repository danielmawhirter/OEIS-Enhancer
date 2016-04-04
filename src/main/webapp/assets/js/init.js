
var isFDLPage = true;
var randIdentifier = Math.random();
//console.log("Window identifier: " + randIdentifier)

var highest = window;
while(highest.opener && highest.opener.isFDLPage) {
	highest = highest.opener;
}
//console.log("Identifier of highest parent: " + highest.randIdentifier);

var markedVertices = [];

pathLayout(window.toOpen, window.openMarked);