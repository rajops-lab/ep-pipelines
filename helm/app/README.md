## Configuration

| Parameter | Description | Default | Comments |
|---|---|---|---|
| `replicaCount` | The number of replicas to create | `1` | |
| `kind` | The type of k8s controller used to deploy the application | `Deployment` | One of `Deployment` or `StatefulSet` |
| `app.image` | The repository which has the application image | `nginx` | |
| `app.tag` | The tag of the application image to be deployed | `latest` | |
| `app.port` | The port through which the application is served | `80` | |
| `image.pullPolicy` | The pull policy for the application image | `IfNotPresent` | |
| `env.plaintext` | Key value pairs for environment variables and their values | `{}` | To be used for where values can be visible in pod spec |
| `env.secret` | Key value pairs for environment variables and their values | `{}` | To be used for sensitive values that should not be visible in pod spec |
| `env.fieldref` | Key value pairs for environment variables and their values | `{}` | Can reference other pod attributes as env variables |
| `mounts.secret` | Optional configuration of secret content mounted as files | `[]` | |
| `mounts.secret[0].name` | Identifier of the secret in the context of the application | | |
| `mounts.secret[0].target` | Absolute path inside the container the secret should be mounted on | | |
| `mounts.secret[0].content` | Multiline content which will be mounted as a file in the container | | Not to be used with encodedContent |
| `mounts.secret[0].encodedContent` | Base64 encoded multiline content which will be mounted as a file in the container | | Not to be used with content |
| `resources.requests.cpu` | Resource requests for application container cpu | `50m` | |
| `resources.requests.memory` | Resource requests for application container memory | `100Mi` | |
| `podmonitors.endpoints` | Optional configuration for pod monitor endpoints | `[]` | |
| `podmonitors.endpoints[0].path` | Endpoint in the pod to be scraped | | |
| `podmonitors.endpoints[0].honor_labels` | Honour labels from the scraped endpoint | `true` | |
| `podmonitors.endpoints[0].interval` | Interval at which the pod should be scraped | `30s` | |
| `podmonitors.endpoints[0].port` | Port of the pod which should be scraped | `app` | |
| `podmonitors.endpoints` | Optional configuration for pod monitor endpoints | `[]` | |
| `service` | Optional configuration for exposing the application as a service | | |
| `service.port` | Port on which to expose the service | | |
| `hostAliases` | Optional configuration of host aliases to be made available to the containers in the pod | `[]` | |
| `hostAliases[0].ip` | Target IP for the host alias | | |
| `hostAliases[0].hostnames` | Array of hostnames that should resolve to target ip | | |
| `kong.name` | Name of the kong admin service | `kong-kong-admin` | |
| `kong.namespace` | Namespace in which the kong admin service is running | `kong` | |
| `kong.port` | Port on which the kong admin service is exposed | `8001` | |
| `kong.image` | The repository which has the kong-configure image | | |
| `kong.tag` | The tag of the kong-configure image to be deployed | | |
| `kong.command` | Command to be executed within the kong-configure container | `kong-configure` | |
| `kong.resources.requests.cpu` | Resource requests for kong-configure container cpu | `50m` | |
| `kong.resources.requests.memory` | Resource requests for kong-configure container memory | `100Mi` | |
| `kong.services` | Optional configuration of kong services for the application | `[]` | |
| `kong.services[0].name` | Name of the service in kong | | |
| `kong.services[0].authn` | Auth plugin to be enabled for the service | `none` | One of `none`, `jwt`, `basic-auth` or `oidc-auth` |
| `kong.services[0].user_info_endpoint` | User info endpoint of the OIDC provider | | Required if authn is `oidc-auth`, ignored otherwise |
| `kong.services[0].connect_timeout` | Connect timeout in seconds for the kong service | `60` | |
| `kong.services[0].write_timeout` | Write timeout in seconds for the kong service | `60` | |
| `kong.services[0].read_timeout` | Read timeout in seconds for the kong service | `60` | |
| `kong.services[0].service_path` | Path in the service to forward traffic to | | |
| `kong.services[0].routes.paths` | Paths in the incoming request to be routed to the kong service | | Not to be used with hosts |
| `kong.services[0].routes.strip_path` | Should route paths be removed before forwarding to service | `false` | Ignored for host based routes |
| `kong.services[0].routes.hosts` | Hosts in the incoming request to be routed to the kong service | | Not to be used with paths |
| `kong.services[0].routes.preserve_host` | Should route hosts be added as a header before forwarding to service | `false` | Ignored for path based routes |
| `cronjobs.http` | Optional configuration of scheduled http calls to the application | `[]` | |
| `cronjobs.http[0].name` | Identifier of the cron job | | |
| `cronjobs.http[0].schedule` | Cron expression for when the cronjob will run | | |
| `cronjobs.http[0].method` | HTTP method | | |
| `cronjobs.http[0].context` | HTTP URL context | | |
| `cronjobs.http[0].headers` | Key value pairs of HTTP headers | | |
