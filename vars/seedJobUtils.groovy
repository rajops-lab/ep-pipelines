def createJob(def config){
  def folders_dsls = folderDsls(config.folders)
	def jobs_dsls = jobDsls(config.jobs)
	def deploy_dsls = jobsDslsWithParams(config.deploy_jobs,config.deploy_params) 
 
	def final_dsl = folders_dsls.join("\n") + "\n" + jobs_dsls.join("\n")   + deploy_dsls.join("\n")
}

def folderDsls(def folders){
  folders.collect {
        """
            folder('${it.path}') {
                description('${it.description}')
            } 
        """
  }
}

def jobDsls(def jobs){
  jobs.collect {
    job(it,it.params)
  }
}

def jobsDslsWithParams(def jobs, def params){
  jobs.collect {
    job(it,params)
  }
}

def job(def job,def params){
    """
            pipelineJob('${job.path}') {
                ${autoTriggerWithGithubHook(job.auto_trigger_repo)}
                ${addParameter(params)}
                definition {
                    cps {
                        sandbox(true)
                        script(readFileFromWorkspace('${job.jenkinsfile}'))
                    }
                }
            }
        """
}

def autoTriggerWithGithubHook(def auto_trigger_repo) {
	if(auto_trigger_repo?.trim()) {
	    """
	    properties {
            githubProjectUrl('${auto_trigger_repo}')
            pipelineTriggers {
                triggers {
                    githubPush()
                }
            }
        }
	    """
	}
	else {
		""
	}
}
def addParameter(def params) {
	if(params == null || params.isEmpty()) {
     ""
  }
  else{
     def param_dsls = params.collect{
            "stringParam('${it.name}', '', 'Description')\n"
        }

    "parameters {\n" + param_dsls + "}"
	}
}
return this;
