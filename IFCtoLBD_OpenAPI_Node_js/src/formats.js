export const OUTPUTS = Object.freeze({
  turtle: Object.freeze({ mediaType: "text/turtle", extension: "ttl", jenaFormat: "TURTLE", dataset: false }),
  jsonld: Object.freeze({ mediaType: "application/ld+json", extension: "jsonld", jenaFormat: "JSON-LD", dataset: false }),
  rdfxml: Object.freeze({ mediaType: "application/rdf+xml", extension: "rdf", jenaFormat: "RDF/XML", dataset: false }),
  trig: Object.freeze({ mediaType: "application/trig", extension: "trig", jenaFormat: "TRIG", dataset: true }),
});

const FORMAT_ALIASES = new Map([
  ["turtle", "turtle"], ["ttl", "turtle"], ["text/turtle", "turtle"],
  ["jsonld", "jsonld"], ["json-ld", "jsonld"], ["application/ld+json", "jsonld"],
  ["rdfxml", "rdfxml"], ["rdf/xml", "rdfxml"], ["application/rdf+xml", "rdfxml"],
  ["trig", "trig"], ["application/trig", "trig"],
]);

export function selectOutput(format, accept = "") {
  if (format !== undefined && String(format).trim() !== "") {
    const key = FORMAT_ALIASES.get(String(format).trim().toLowerCase());
    if (!key) throw new TypeError(`Unsupported RDF format: ${format}`);
    return OUTPUTS[key];
  }

  const accepted = String(accept)
    .split(",")
    .map((item, index) => {
      const [mediaType, ...parameters] = item.trim().toLowerCase().split(";");
      const q = parameters.find((parameter) => parameter.trim().startsWith("q="));
      return { mediaType, quality: q ? Number(q.trim().slice(2)) : 1, index };
    })
    .filter(({ quality }) => Number.isFinite(quality) && quality > 0)
    .sort((left, right) => right.quality - left.quality || left.index - right.index);

  for (const { mediaType } of accepted) {
    const key = FORMAT_ALIASES.get(mediaType);
    if (key) return OUTPUTS[key];
    if (mediaType === "*/*") return OUTPUTS.turtle;
  }
  return OUTPUTS.turtle;
}
