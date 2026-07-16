window.onload = function() {
  var swaggerUi = window.SwaggerUIBundle;
  var target = document.getElementById("swagger-ui");

  if (!swaggerUi) {
    target.textContent = "Swagger UI could not be loaded.";
    return;
  }

  fetch("openapi.json")
    .then(function(response) {
      if (!response.ok) {
        throw new Error("Could not load openapi.json");
      }
      return response.json();
    })
    .then(function(spec) {
      var operation = spec.paths
        && spec.paths["/convertIFCtoLBD"]
        && spec.paths["/convertIFCtoLBD"].post;

      if (operation && operation.requestBody && operation.requestBody.content) {
        operation.requestBody.required = true;
        operation.requestBody.content["multipart/form-data"] = {
          schema: {
            type: "object",
            required: ["ifcFile"],
            properties: {
              ifcFile: {
                type: "string",
                format: "binary",
                description: "an IFC file"
              }
            }
          }
        };

        if (operation.responses && operation.responses["201"] && operation.responses["201"].content) {
          Object.keys(operation.responses["201"].content).forEach(function(mediaType) {
            operation.responses["201"].content[mediaType].schema = {
              type: "string",
              format: "binary"
            };
          });
        }
      }

      spec.servers = [{
        url: window.location.pathname.split("/apidocs/")[0] + "/api"
      }];

      window.ui = swaggerUi({
        spec: spec,
        dom_id: "#swagger-ui",
        deepLinking: true,
        syntaxHighlight: {
          activated: false
        },
        presets: [
          swaggerUi.presets.apis,
          window.SwaggerUIStandalonePreset
        ],
        plugins: [
          swaggerUi.plugins.DownloadUrl
        ],
        layout: "StandaloneLayout"
      });
    })
    .catch(function(error) {
      target.textContent = error.message;
  });
};
