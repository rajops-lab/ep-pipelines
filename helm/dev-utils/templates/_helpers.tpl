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
{{- if .data.enabled -}}
{{- include "container.conditional-content" (dict "data" .data "appName" .appName) | nindent 8 }}
{{- end }}
{{- end -}}

{{/*
Container conditional content
*/}}
{{- define "container.conditional-content" -}}
- name: {{ .data.name }}
  image: {{ .data.image }}:{{ .data.tag }}
  imagePullPolicy: {{ .data.imagePullPolicy }}
  {{- if .data.mounts}}
  volumeMounts:
  {{- if .data.mounts.pgpass -}}
  {{- include "volume_mount.pgpass" .data }}
  {{- end -}}
  {{- range $secret := .data.mounts.secret }}
    {{- if .content }}
    - name: {{ $.appName }}-{{ .container }}-mounts-secrets
      mountPath: {{ .target }}
      subPath: {{ .name }}
    {{- else if .files }}
      {{- range .files }}
    - name: {{ $.appName }}-{{ $secret.container }}-mounts-secrets
      mountPath: /root/.helpers/{{ .name }}
      subPath: {{ .name }}
      {{- end }}
    {{- end }}
  {{- end }}
  {{- end }}
  command:
    {{- toYaml .data.command | nindent 4 }}
  env:
    {{- toYaml .data.env | nindent 4 }}
  resources:
    {{- toYaml .data.resources | nindent 4 }}
{{- end -}}

{{/*
Volume pgpass secret
*/}}
{{- define "volume.pgpass" -}}
- name: pgpass
  secret:
    secretName: pgpass
    items:
      - key: content
        path: .pgpass
        mode: 384
{{- end -}}

{{- define "volume_mount.pgpass"}}
    - name: pgpass
      mountPath: /root/.pgpass
      subPath: .pgpass
{{- end -}}

{{/*
Volume content
*/}}
{{- define "volume.content" -}}
{{- if .data.enabled -}}
{{- if .data.mounts -}}
{{- if .data.mounts.pgpass -}}
  {{- include "volume.pgpass" (dict "appName" $.appName) | nindent 8 }}
{{- end -}}
{{- range .data.mounts.secret -}}
{{- include "volume.conditional-content" (dict "secret" . "data" $.data "appName" $.appName) | nindent 8 }}
{{- end -}}
{{- end -}}
{{- end -}}
{{- end -}}


{{/*
Volume conditional content
*/}}
{{- define "volume.conditional-content" -}}
- name: {{ .appName }}-{{ .secret.container }}-mounts-secrets
  secret:
    secretName: {{ .appName }}-{{ .data.name }}-mounts-secrets
    items:
      {{- if .secret.content }}
      - key: {{ .secret.name }}
        path: {{ .secret.name }}
        mode: {{ .secret.mode }}
      {{- else if .secret.files }}
        {{- range .secret.files }}
      - key: {{ .name }}
        path: {{ .name }}
        mode: {{ .mode }}
        {{- end }}
      {{- end }}
{{- end -}}


{{/*
Include file content in a secret
*/}}
{{/*{{- define "file-content-to" -}}
{{- .Files.Get (printf "files/%s" .file) -}}
{{- end -}}
*/}}

{{/*
Include file content in a secret
*/}}
{{- define "file-content" -}}
{{- $filePath := printf "files/%s" .file }}
{{- $fileContent := .Files.Get $filePath }}
{{- if $fileContent }}
{{ $fileContent | b64enc }}
{{- else }}
{{- end }}
{{- end }}
