// Kept out of index.html so the page's Content-Security-Policy needs no inline scripts.
// BaseLayout has no top bar, so the page only ever loads this API's spec, never one from a URL typed in.
window.onload = function () {
  window.ui = SwaggerUIBundle({
    url: "v3",
    dom_id: "#swagger-ui",
    deepLinking: true,
    presets: [SwaggerUIBundle.presets.apis],
    layout: "BaseLayout"
  });
};
