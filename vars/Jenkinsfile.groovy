node {
  checkout scm
 
  def jenkinsConfigFile = params.SEED_JOB_CONFIG_LOCATION

  println "jenkins config location: ${jenkinsConfigFile}"

  def config = readYaml(file: "$jenkinsConfigFile")

  def pipeline = load 'vars/seedJobUtils.groovy'
 
  def final_dsl = pipeline.createJob(config)
	
  jobDsl removedConfigFilesAction: 'DELETE', removedJobAction: 'DELETE', removedViewAction: 'DELETE', sandbox: true, scriptText: "$final_dsl"
}

 
