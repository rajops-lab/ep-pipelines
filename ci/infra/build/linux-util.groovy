library identifier: 'ep-pipelines@master', changelog: false, retriever: modernSCM(github(
    credentialsId: 'github-credentials', repository: 'ep-pipelines', repoOwner: 'tmlconnected'
))

buildDocker {
    code_repo = 'https://github.com/tmlconnected/ep-infrastructure.git'
    docker_file_dir = 'docker/linux-util'
    ecr_repo_name = 'ep-jenkins-master'
    ecr_repo = '384588637744.dkr.ecr.ap-south-1.amazonaws.com/ep-dev-utils'
    image_tag = 'jdk-image-tag:latest'
    aws_ecr_base_url = 'https://384588637744.dkr.ecr.ap-south-1.amazonaws.com'
}
