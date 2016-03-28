
var isFDLPage = true;

if(!window.opener || !window.opener.isFDLPage) {
	pathLayout();
} else {
	if(!toOpen) {
		alert("Opened from another window, but no toOpen property set!")
	} else {
		pathLayout(toOpen);
	}
}