library identifier: 'ep-pipelines@master', changelog: false, retriever: modernSCM(github(
    credentialsId: 'github-credentials', repository: 'ep-pipelines', repoOwner: 'tmlconnected'
))

def PLANT = 'pune'

deployNamespacedApp {
    name = "ipms-connector-${PLANT}"
    application_file = 'prod/ipms-connector.yml'
    context_environment = 'ipms4/prod'
    vars = 'common-vars.yml'
    custom_vars = "${PLANT}/ipms-vars.yml"
    vault_vars = 'ipms-common-vault.yml'
    // custom_vault_vars = "${PLANT}/ipms-vault.yml"
    vault_credential = 'ansible-vault-password'
    kubeconfig = 'ep-kube-config'
    namespace = 'ep'
    github_repo = 'tmlconnected/ep-sap-connector'
    ecr_repo_name = 'ep-sap-connector'
    ecr_repo_id = '384588637744'
    ecr_repo_host = '384588637744.dkr.ecr.ap-south-1.amazonaws.com'
    suggest_snapshot_tags = 'true'
    release_notes_slack_channel = 'ep-releases-prod'
    downstream_jobs = ['../../../info/prod/what-is-running']
}