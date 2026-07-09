const { DEFAULT_IFC_FILE, IFCtoLBD } = require("./IFCtoLBD");

const converter = new IFCtoLBD({
  baseUri: "https://example.com/building/",
  levels: [1],
});
const subjects = converter.convertToSubjects(process.argv[2] || DEFAULT_IFC_FILE);

console.log("subject count: " + subjects.length);
console.log("first subject: " + subjects[0]);
