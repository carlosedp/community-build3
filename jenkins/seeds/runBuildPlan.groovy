import java.text.SimpleDateFormat
import groovy.json.JsonSlurper

def date = new Date();
def dateFormat = new SimpleDateFormat("yyyy-MM-dd")
def dateString = dateFormat.format(date)

def scalaRepoUrl = "https://github.com/lampepfl/dotty.git"
scalaVersion = "3.0.0-RC3-bin-COMMUNITY-SNAPSHOT"  // TODO compute version from latest master
proxyHostname = "nginx-proxy"

dailiesRootPath = "/daily"
currentDateRootPath = "/daily/${dateString}"

def projectPath(String projectName) {
    return "${currentDateRootPath}/${projectName}"
}

def buildScalaCommand = "docker exec \${c.id} /build/build-revision.sh ${scalaRepoUrl} master ${scalaVersion} ${proxyHostname}"

def buildScalaJobScript = """
docker.image('communitybuild3/publish-scala').withRun("-it --network builds-network", "cat") { c ->
    echo 'building and publishing scala'
    sh "${buildScalaCommand}"
}
"""

def buildProjectCommand(Map project) {
  return """docker exec \${c.id} /build/build-revision.sh ${project.repoUrl} ${project.revision} ${scalaVersion} ${project.version} '${project.targets}' ${proxyHostname} >> /tmp/logs.txt"""
}

// Because the job will be triggered when ANY of its upstream dependencies finishes its build.
// We need to manually check if ALL the upstream jobs actually finished (not necessarily without errors).
// If some dependencies haven't finished running yet, we abort the job and let it be triggered again by some other upstream job.
def buildProjectJobScript(Map project) {
    return """
import java.time.*

def wasBuilt(String projectName) {
	def jenkins = jenkins.model.Jenkins.instance
	def job = jenkins.getItemByFullName("${currentDateRootPath}/\${projectName}")
	def lastBuild = job.getLastBuild()
	if(lastBuild == null) {
		return false
	} else {
		def status = job.getLastBuild().getResult().toString()
		return status in ['SUCCESS', 'FAILURE', 'UNSTABLE']
	}
}

def dependencies = ['${project.dependencies.join("','")}']
def allDependenciesWereBuilt = dependencies.every { wasBuilt(it) }

if(!allDependenciesWereBuilt) {
	currentBuild.result = 'ABORTED'
    error('Not all dependencies have been built yet')
}
node {
  def result = "SUCCESS"
  def restxt
  def logs
  docker.image('communitybuild3/executor').withRun("-it --network builds-network", "cat") { c ->
    echo "building and publishing ${project.name}"
    try {
      sh "${buildProjectCommand(project)}"
    } catch (err) {
      result = "FAILURE"
    }
    restxt = sh(
      script: "docker exec \${c.id} cat /build/res.txt",
      returnStdout: true
    )
    logs = sh(
      script: "docker exec \${c.id} cat /tmp/logs.txt",
      returnStdout: true
    )
  }
  writeFile(file: "res.txt", text: restxt)
  archiveArtifacts(artifacts: "res.txt")

  if (result == "FAILURE") {
    currentBuild.result = 'FAILURE'
  } else if (result == "SUCCESS") {
    currentBuild.result = 'SUCCESS'
  }

  LocalDateTime t = LocalDateTime.now();
  sh " curl -X POST -H \\"Content-Type: application/json\\" \\"elasticsearch:9200/community-build/doc\\" -d \\' { \\"res\\": \\"\${result}\\", \\"build_timestamp\\": \\"\${t as String}\\", \\"project_name\\": \\"${project.name}\\", \\"detailed_result\\": \${restxt}, \\"logs\\": \\"\${logs}\\" } \\'"
}
"""
}

////////////

// Prepare Jenkins directory structure
folder(dailiesRootPath)
folder(currentDateRootPath)


// Prepare and schedule publishing scala
def scalaJobPath = projectPath("lampepfl_dotty")
pipelineJob(scalaJobPath) {
  definition {
    cps {
      script(buildScalaJobScript)
    }
  }
}
queue(scalaJobPath)


// Prepare jobs for projects from the community build
// which will be then triggered by their dependencies
def jsonSlurper = new JsonSlurper()
def projectsFileText = readFileFromWorkspace("buildPlan.json")
def projects = jsonSlurper.parseText(projectsFileText)

for(project in projects) {
  def jobPath = projectPath(project.name)
  def upstreamJobPaths = project.dependencies.collect{ dep -> projectPath(dep) }.join(',')
  pipelineJob(jobPath){
    triggers {
      upstream(upstreamJobPaths, 'FAILURE')
	}
    definition {
      cps {
        script(buildProjectJobScript(project))
      }
    }
  }
}