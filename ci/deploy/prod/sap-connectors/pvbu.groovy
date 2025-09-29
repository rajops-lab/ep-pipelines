library identifier: 'ep-pipelines@master', changelog: false, retriever: modernSCM(github(
    credentialsId: 'github-credentials', repository: 'ep-pipelines', repoOwner: 'tmlconnected'
))

String BUSINESS_UNIT = 'pvbu'

deployNamespacedApp {
    name = "sap-connector-${BUSINESS_UNIT}"
    application_file = 'common/sap-connector.yml'
    context_environment = 'prod'
    vars = 'common-vars.yml'
    custom_vars = "${BUSINESS_UNIT}/vars.yml"
    vault_vars = 'common-vault.yml'
    custom_vault_vars = "${BUSINESS_UNIT}/vault.yml"
    vault_credential = 'ansible-vault-password'
    kubeconfig = 'ep-kube-config'
    namespace = 'avant-garde'
    github_repo = 'tmlconnected/ep-sap-connector'
    ecr_repo_name = 'ep-sap-connector'
    ecr_repo_id = '384588637744'
    ecr_repo_host = '384588637744.dkr.ecr.ap-south-1.amazonaws.com'
    suggest_snapshot_tags = 'true'
    release_notes_slack_channel = 'ep-releases-prod'
    downstream_jobs = ['../../info/prod/what-is-running']
}