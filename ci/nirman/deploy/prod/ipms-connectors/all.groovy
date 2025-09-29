library identifier: 'ep-pipelines@master', changelog: false, retriever: modernSCM(github(
        credentialsId: 'github-credentials', repository: 'ep-pipelines', repoOwner: 'tmlconnected'
))

deployAllDownStreamJob {
    downstream_jobs = ['dharwad.groovy', 'jamshedpur.groovy', 'lucknow.groovy', 'pune.groovy']
}