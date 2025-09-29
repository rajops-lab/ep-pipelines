node {
  checkout scm
  
  // Use local files instead of Git checkout
  def workspace = pwd()  // Get current Jenkins workspace path for file operations
  println "Working in workspace: ${workspace}"  // Debug: Show where we're working from
  
  def jenkinsConfigFile = params.SEED_JOB_CONFIG_LOCATION  // Get config file path from job parameter

  println "jenkins config location: ${jenkinsConfigFile}"

  def config = readYaml(file: "$jenkinsConfigFile")

  def pipeline = load 'vars/seedJobUtils.groovy'
 
  def final_dsl = pipeline.createJob(config)
	
  jobDsl removedConfigFilesAction: 'DELETE', removedJobAction: 'DELETE', removedViewAction: 'DELETE', sandbox: true, scriptText: "$final_dsl"
}
