import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import static java.lang.Integer.parseInt;
import java.util.List;
  
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import org.lbd.ifc2lbd.IFCtoLBDConverter;

/**
 * Servlet to handle File upload request from Client and
 * conversion of IFC  to BOT using translators developed by Jyrki Oraskari, Mathias Bonduel, Kris McGlinn
 * Created on : 16-Aug-2018
 * @author Kris McGlinn
 */
public class FileUploadHandler extends HttpServlet {
    
    private final String UPLOAD_DIRECTORY = "/home/user/ifcbot"; //this directory must exist on your server

    String name;   
    String file_path = "";
    String bot_path = "";  
    String base_url = "";
    String s = "";
    boolean geo, prod, sep_prod, prop, sep_prop, blank_node, ifc_version = false;
    int level = 1;
   
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
      
        
        //process only if its multipart content
        if(ServletFileUpload.isMultipartContent(request)){
            base_url = "";
            geo = false; 
            prod = false; 
            sep_prod = false; 
            prop = false;
            sep_prop = false; 
            blank_node = false;
            ifc_version = false;
            try {
                List<FileItem> multiparts = new ServletFileUpload(
                                         new DiskFileItemFactory()).parseRequest(request);
              
                for(FileItem item : multiparts){
                    if(!item.isFormField()){
                        name = new File(item.getName()).getName();
                        file_path = UPLOAD_DIRECTORY + File.separator + name;
                        item.write( new File(file_path));
                    }
                    else 
                    {
                        if(item.getFieldName().equals("input_url")){
                            base_url = item.getString();
                            System.out.println("Input URL is " + base_url);
                            
                        }
                        if(item.getFieldName().equals("geo_input")){
                            geo = true;
                            System.out.println("Geolocation is " + geo);
                        }
                        if(item.getFieldName().equals("prod")){
                            prod = true;
                            System.out.println("Products is " + prod);
                        }
                        if(item.getFieldName().equals("sep_prod")){
                            sep_prod = true;
                            System.out.println("Seperate Products is " + sep_prod);
                        }
                        if(item.getFieldName().equals("prop")){
                            prop = true;
                            System.out.println("Properties is " + prop);
                        }
                        if(item.getFieldName().equals("sep_prop")){
                            sep_prop = true;
                            System.out.println("Seperate Properties is " + sep_prop);
                        }
                        if(item.getFieldName().equals("blank_node")){
                            blank_node = true;
                            System.out.println("Blank Node is " + blank_node);
                        }
                        if(item.getFieldName().equals("drone")){
                            level = parseInt(item.getString());
                            System.out.println("Level is " + level);
//                            s = item.getString();
                        }
                        if(item.getFieldName().equals("ifc_version")){
                            ifc_version = true;
                            System.out.println("IFC Version is " + ifc_version);
                        }
                    }
                }
               multiparts = null;
               
               //File uploaded successfully
               //request.setAttribute("message", "File Uploaded Successfully for: " + file_path +", "+ base_url +", "+  name.substring(0,name.length() - 4) +".bot" +", "+  level +", "+  prod +", "+  sep_prod +", "+  prop +", "+  sep_prop +", "+  blank_node +", "+  geo);
               

               bot_path = UPLOAD_DIRECTORY + File.separator + name.substring(0,name.length() - 4) +".bot";
               IFCtoLBDConverter ifc2lbd = new IFCtoLBDConverter(file_path, base_url, name.substring(0,name.length() - 4) +".bot", level, prod, sep_prod, prop, sep_prop, blank_node, geo, ifc_version);
               FileOutputStream fo = new FileOutputStream(new File(bot_path));
               ifc2lbd.returnBOTModel().write(fo, "TTL");

               
               File downloadFile = new File(bot_path);

               OutputStream outStream;
                // obtains ServletContext
                try (FileInputStream inStream = new FileInputStream(downloadFile)

                ) {
                    // obtains ServletContext
                    ServletContext context = getServletContext();
                    // gets MIME type of the file
                    String mimeType = context.getMimeType(bot_path);
                    if (mimeType == null) {
                        // set to binary type if MIME mapping not found
                        mimeType = "application/octet-stream";
                    }   System.out.println("MIME type: " + mimeType);
                    // modifies response
                    response.setContentType(mimeType);
                    response.setContentLength((int) downloadFile.length());
                    // forces download
                    String headerKey = "Content-Disposition";
                    String headerValue = String.format("attachment; filename=\"%s\"", downloadFile.getName());
                    response.setHeader(headerKey, headerValue);
                    // obtains response's output stream
                    outStream = response.getOutputStream();
                    byte[] buffer = new byte[4096];
                    int bytesRead = -1;
                    while ((bytesRead = inStream.read(buffer)) != -1) {
                        outStream.write(buffer, 0, bytesRead);
                    }
                }
                outStream.close(); 
            } catch (Exception ex) {
               request.setAttribute("message", "File Upload Failed due to " + ex);
            }      return;    
         
        }
        
        else{
            request.setAttribute("message",
                                 "Sorry this Servlet only handles file upload request");
        }
        request.setAttribute("message", "File Converted Successfully for: " + file_path +", "+ base_url +", "+  name.substring(0,name.length() - 4) +".bot" +", "+  level +", "+  prod +", "+  sep_prod +", "+  prop +", "+  sep_prop +", "+  blank_node +", "+  geo);

        request.getRequestDispatcher("/result.jsp").forward(request, response);
        
     
    }

}


