var fs = require("fs");
const java = require('java');
var baseDir = "java_libraries";

var dependencies = fs.readdirSync(baseDir);
dependencies.forEach(function(dependency){
    java.classpath.push(baseDir + "\\" + dependency);
})


var converter = java.newInstanceSync("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter","https://example.com/", false, 1);
var model = converter.convertSync("Duplex_A_20110907.ifc");
var s = model.listSubjectsSync();
var subjects = s.toListSync()


console.log("´subjects: "+subjects);