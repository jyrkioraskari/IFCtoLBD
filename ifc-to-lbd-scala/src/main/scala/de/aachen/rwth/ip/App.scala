package de.aachen.rwth.ip

import java.io.File
import java.net.URL
import org.apache.jena.rdf.model.Model
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter

object App {
  def main(args: Array[String]): Unit = {
    val ifcFileUrl: URL = getClass.getClassLoader.getResource("Duplex_A.ifc")
    if (ifcFileUrl != null) {
      try {
	    println(s"Resource: ${ifcFileUrl.toURI}")
        val ifcFile = new File(ifcFileUrl.toURI)
		println(s"Resource found at: ${ifcFile.getAbsolutePath}")

        val converter = new IFCtoLBDConverter("https://example.com/", false, 1)
        try {
          val model: Model = converter.convert(ifcFile.getAbsolutePath)
          model.write(System.out, "TTL")
        } finally {
          converter.close()
        }
      } catch {
        case e: Exception =>
          println(s"Error: ${e.getMessage}")
      }
    } else {
      println("Error: Unable to find the IFC file.")
    }
  }
}
