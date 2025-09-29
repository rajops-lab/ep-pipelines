/**
 * Utility functions for seed job pipeline creation
 * Handles the creation of folders, build jobs, and deployment jobs
 */

/**
 * Main entry point for job creation
 * @param config The YAML configuration containing job definitions
 * @return Generated JobDSL script as a string
 */
def createJob(def config) {
    def folders_dsls = folderDsls(config.folders ?: [])
    def jobs_dsls = jobDsls(config.jobs ?: [])
    def deploy_dsls = jobsDslsWithParams(config.deploy_jobs ?: [], config.deploy_params ?: []) 
 
    def final_dsl = folders_dsls.join("\n") + "\n" + jobs_dsls.join("\n") + "\n" + deploy_dsls.join("\n")
    return final_dsl
}

/**
 * Creates folder DSLs from configuration
 * @param folders List of folder configurations
 * @return List of DSL scripts for folder creation
 */
def folderDsls(def folders) {
    folders.collect {
        """
            folder('${it.path}') {
                description('${it.description ?: "Created by seed job"}')
                // Support for folder properties if needed
                ${it.properties ?: ''}
            } 
        """
    }
}

/**
 * Creates job DSLs from configuration
 * @param jobs List of job configurations
 * @return List of DSL scripts for job creation
 */
def jobDsls(def jobs) {
    jobs.collect {
        job(it, it.params ?: [])
    }
}

/**
 * Creates job DSLs with shared parameters
 * @param jobs List of job configurations
 * @param params Shared parameters to apply to all jobs
 * @return List of DSL scripts for job creation
 */
def jobsDslsWithParams(def jobs, def params) {
    jobs.collect {
        job(it, params)
    }
}

/**
 * Creates a single job DSL
 * @param job Job configuration
 * @param params Parameters for the job
 * @return DSL script for job creation
 */
def job(def job, def params) {
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

/**
 * Adds GitHub trigger configuration
 * @param auto_trigger_repo GitHub repository URL for trigger
 * @return DSL script for GitHub trigger
 */
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

/**
 * Adds parameters to a job
 * @param params Parameters to add
 * @return DSL script for parameters
 */
def addParameter(def params) {
    if(params == null || params.isEmpty()) {
        ""
    }
    else {
        def param_dsls = params.collect {
            if (it.type == 'choice') {
                // Handle choice parameters with multiple values
                def choices = it.choices.collect { "'${it}'" }.join(', ')
                "choiceParam('${it.name}', [${choices}], '${it.description}')\n"
            } else {
                // Default to string parameter
                "stringParam('${it.name}', '${it.defaultValue ?: ''}', '${it.description}')\n"
            }
        }

        "parameters {\n" + param_dsls.join('') + "}"
    }
}

return this;
