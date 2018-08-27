<%-- 
    Document   : jsp main page
    Created on : 16-Aug-2018, 10:03:13
    Author     : Kris_McGlinn
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
    "http://www.w3.org/TR/html4/loose.dtd">
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>IFC to LBD</title>
    </head>
    <link href="css/style.css" rel="stylesheet" type="text/css" media="all" />
 
    <body> 
        <div id = 'container'>
            <div><h1>Convert an IFC file to LBD format</h1></div>
            <form action="upload" method="post" enctype="multipart/form-data">
                <div id='file_input'>

                    <input type="file" name="file" /> (Large files may take several minutes to convert)
                    
                </div>
                <div id='input_panel'>
                    <div id='base_url'>
                        <b>Base URL: </b>
                        <input id='input_url' type="text" name='input_url' value="https://www.ugent.be/myAweseomeFirstBIMProject#"> 
                        <br></br>
                    </div>
                    <div>
                        <!--<ul id="accordion_null">-->
                            <!--<li>-->
                                <h2>Configuration Options</h2>
                                <div class="content" id="conf">
                                    <!-- Rounded switch -->
                                    <div id='configuration'>    
                                        <div id='product'>
                                            <div id='product_text_1'>
                                                <p>Building Product Ontology Instances</p>
                                                <a href="https://github.com/w3c-lbd-cg/product">https://github.com/w3c-lbd-cg/product</a>
                                            </div>
                                            <div id='product_switch_1'>
                                                <label class="switch">
                                                  <input type="checkbox" name='prod'>
                                                  <span class="slider round"></span>
                                                </label>
                                            </div>
                                            <div id='product_text_2'>
                                                <p>Seperate file</p>
                                            </div>
                                            <div id='product_switch_2'>
                                                <label class="switch">
                                                  <input type="checkbox" name='sep_prod'>
                                                  <span class="slider round"></span>
                                                </label>
                                            </div>
                                        </div>
                                        <div id='properties'>
                                            <div id='properties_text_1'>
                                                <p>Building related properties</p>
                                            </div>
                                            <div id='properties_switch_1'>
                                                <label class="switch">
                                                  <input type="checkbox" name='prop'>
                                                  <span class="slider round"></span>
                                                </label>
                                            </div>
                                            <div id='properties_radio_buttons'>
                                                <input type="radio" id="huey" name="drone" value = '1' checked='checked' />
                                                <label for="l1">Level 1</label>
                                                <input type="radio" id="huey" name="drone" value = '2' />
                                                <label for="l2">Level 2</label>
                                                <input type="radio" id="huey" name="drone" value = '3' />
                                                <label for="l3">Level 3</label>
                                            </div>
                                            <div id='properties_mid_1'>
                                                <div id='properties_text_2'>
                                                    <p>Blank nodes</p>
                                                </div>                               
                                                <div id='properties_switch_2'>
                                                    <label class="switch">
                                                      <input type="checkbox" name='blank_node'>
                                                      <span class="slider round"></span>
                                                    </label>
                                                </div>
                                            </div>
                                            <div id='properties_mid_2'>
                                                <div id='properties_text_3'>
                                                    <p>Seperate file</p>
                                                </div>
                                                <div id='properties_switch_3'>
                                                    <label class="switch">
                                                      <input type="checkbox">
                                                      <span class="slider round" name='sep_prop'></span>
                                                    </label>
                                                </div>
                                            </div>
                                        </div>

                                        <div id='geolocation'>
                                            <div id='geolocation_text_1'>
                                                <p>Include geolocation</p>
                                            </div>
                                            <div id='geolocation_switch_1'>
                                                <label class="switch">
                                                  <input name="geo_input" type="checkbox">
                                                  <span class="slider round"></span>
                                                </label>
                                            </div>
                                            <div id='geolocation_text_2'>
                                                <p>IFC2x3</p>
                                            </div>
                                            <div id='geolocation_switch_2'>
                                                <label class="switch">
                                                  <input name="ifc_version" type="checkbox">
                                                  <span class="slider round"></span>
                                                </label>
                                            </div>
                                            <div id='geolocation_text_3'>
                                                <p>IFC4</p>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            <!--</li>-->
<!--                        </ul>-->
                    </div>
                </div>
            <input id ='convert_button' type="submit" value="Convert IFC File" />    
            </form> 
        </div>
    </body>
</html>