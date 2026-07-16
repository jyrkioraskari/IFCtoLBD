# Deployment

This application now uses Vaadin Flow and the Jakarta `jakarta.servlet`
API. Deploy the normal Maven artifact to Apache Tomcat 11.

The Maven build uses a stable WAR name:

```sh
mvn clean package
```

Deploy:

```text
target/IFCProps2ExcelOnline.war
```

Then open:

```text
http://localhost:8080/IFCProps2ExcelOnline/
```

References:

- https://tomcat.apache.org/whichversion.html
- https://vaadin.com/docs/latest/components/upload
