const { DEFAULT_IFC_FILE, IFCtoLBD } = require("./IFCtoLBD");

const converter = new IFCtoLBD({
  baseUri: "https://linkedbuildingdata.example/",
  hasGeometry: false,
  levels: [1, 2, 3],
});
const model = converter.convert(process.argv[2] || DEFAULT_IFC_FILE);
const subjects = converter.listSubjects(model);

console.log("converted IFC file: " + (process.argv[2] || DEFAULT_IFC_FILE));
console.log("subjects:");
subjects.slice(0, 10).forEach((subject) => console.log("- " + subject));
