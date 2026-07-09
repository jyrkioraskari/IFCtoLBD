const { IFCtoLBD } = require("./IFCtoLBD");

const converter = new IFCtoLBD();
const subjects = converter.convertToSubjects(process.argv[2]);

console.log("subjects: " + subjects);
