# Third-party runtime components

The service's direct npm dependencies are installed from the npm registry and
retain their upstream licence files in `node_modules`:

| Component | Version | Licence | Project |
| --- | --- | --- | --- |
| Fastify | 5.12.7 | MIT | <https://github.com/fastify/fastify> |
| `@fastify/multipart` | 10.1.6 | MIT | <https://github.com/fastify/fastify-multipart> |
| `@fastify/swagger` | 9.9.1 | MIT | <https://github.com/fastify/fastify-swagger> |
| `@fastify/swagger-ui` | 6.1.4 | MIT | <https://github.com/fastify/fastify-swagger-ui> |
| `java-bridge` | 2.8.1 | MIT | <https://github.com/MarkusJx/node-java-bridge> |

Transitive dependency versions and integrity hashes are recorded in
`package-lock.json`. The shaded IFCtoLBD converter distribution carries its
aggregated Apache licence and notice resources. Repository-level source
provenance is recorded in `../THIRD_PARTY_PROVENANCE.md`.

The container copies Azul Zulu 21 from the official `azul-zulu` image. Its
licence and notice files remain in the copied Java runtime under
`/opt/java/zulu21/legal`. Azul's release and image information is available at
<https://docs.azul.com/core/release-notes> and
<https://hub.docker.com/_/azul-zulu>.
