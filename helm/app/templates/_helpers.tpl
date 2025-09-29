{{/* vim: set filetype=mustache: */}}

{{/*
Namespace.
*/}}
{{- define "app.namespace" -}}
{{- .Release.Namespace -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "app.name" -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "app.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "app.selectorLabels" -}}
ep.tatamotors.com/app: {{ include "app.name" . }}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "app.labels" -}}
helm.sh/chart: {{ include "app.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{ include "app.selectorLabels" . }}
{{- end -}}

{{/*
Kong routes
*/}}
{{- define "kong.route_paths" -}}
{{- if .paths -}}
{{- join "," .paths -}}
{{- end -}}
{{- end -}}
{{- define "kong.route_hosts" -}}
{{- if .hosts -}}
{{ join "," .hosts }}
{{- end -}}
{{- end -}}

{{/*
CORS plugin
*/}}
{{- define "cors.allowed_origins" -}}
{{- if .cors_allowed_origins -}}
{{ join "," .cors_allowed_origins }}
{{- end -}}
{{- end -}}

{{/*
Response transformer plugin for addition of headers
*/}}
{{- define "response_transformers.add_headers" -}}
{{- if .response_transformers -}}
{{ join "," .response_transformers }}
{{- end -}}
{{- end -}}

{{/*
Response transformer plugin for removing headers
*/}}
{{- define "response_transformers.remove_headers" -}}
{{- if .remove_headers -}}
{{ join "," .remove_headers }}
{{- end -}}
{{- end -}}


{{/*
Http headers
*/}}
{{- define "http.headers" -}}
{{- if . -}}
{{- range $key, $val := . }} --header "{{$key}}: {{$val}}"{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Secret mount content
*/}}
{{- define "secret_mount.content" -}}
{{- if .content -}}
{{ .content | b64enc }}
{{- else -}}
{{ .encodedContent }}
{{- end -}}
{{- end -}}


{{/* CPU Resources */}}
{{- define "cpu_namespace_limit" -}}
1
{{- end -}}
{{- define "cpu_namespace_request" -}}
20m
{{- end -}}
