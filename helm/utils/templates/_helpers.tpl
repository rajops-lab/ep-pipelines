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
Secret mount content
*/}}
{{- define "secret_mount.content" -}}
{{- if .content -}}
{{ .content | b64enc }}
{{- else -}}
{{ .encodedContent }}
{{- end -}}
{{- end -}}

{{/*
Container content
*/}}
{{- define "container.content" -}}
{{- if .enabled -}}
{{- include "container.conditional-content" . | nindent 8 }}
{{- end }}
{{- end -}}

{{/*
Container conditional content
*/}}
{{- define "container.conditional-content" -}}
- name: {{ .name }}
  image: {{ .image }}:{{ .tag }}
  imagePullPolicy: {{ .imagePullPolicy }}
  {{- if .mounts}}
  {{- if .mounts.secret }}
  volumeMounts:
  {{- if .mounts.secret }}
  {{- range .mounts.secret }}
    - name: {{ $.name }}-mounts-secrets
      mountPath: {{ .target }}
      subPath: {{ .name }}
  {{- end }}
  {{- end }}
  {{- end }}
  {{- end }}
  command:
    {{- toYaml .command | nindent 4 }}
  env:
    {{- toYaml .env | nindent 4 }}
  resources:
    {{- toYaml .resources | nindent 4 }}
{{- end -}}

{{/*
Volume content
*/}}
{{- define "volume.content" -}}
{{- if .data.enabled -}}
{{- if .data.mounts -}}
{{- if .data.mounts.secret -}}
{{- include "volume.conditional-content" . | nindent 8 }}
{{- end -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Volume conditional content
*/}}
{{- define "volume.conditional-content" -}}
- name: {{ .data.name }}-mounts-secrets
  secret:
    secretName: {{ .appName }}-{{ .data.name }}-mounts-secrets
    items:
    {{- range .data.mounts.secret }}
      - key: {{ .name }}
        path: {{ .name }}
        mode: {{ .mode }}
    {{- end -}}
{{- end -}}