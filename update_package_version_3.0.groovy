import groovy.json.*

properties(
       [buildDiscarder(logRotator( numToKeepStr: '5')),
    parameters([
        string(defaultValue: '', description: 'version', name: 'version'),
    ]),        
    pipelineTriggers([])]
)
node () {
 stage('Clean workspace') {
    cleanWs()  }

 stage ('Repo1') {
   dir('Repo1') {
       git branch: 'development', changelog: false, credentialsId: 'a4f58ec3-56ac-43f4-953c-d29b9a43c542', poll: false, url: 'git@github.com:parmar-gaurav/nodejs.git'
       change_verion()
       echo "${env.WORKSPACE}"
       sh (script:'git commit -am "version updated"')
       sh(script: 'git push')
   } }
 stage ('Repo2') {
    dir('Repo2') {
      git branch: 'development', changelog: false, credentialsId: 'a4f58ec3-56ac-43f4-953c-d29b9a43c542', poll: false, url: 'git@github.com:parmar-gaurav/test_repo.git'
      change_verion()
      echo "${env.WORKSPACE}"
      sh (script:'git commit -am "version updated"')
      sh(script: 'git push') 
    }
}}

void change_verion(){
      env.WORKSPACE = pwd()
      def json = readFile "${env.WORKSPACE}/package.json"
      def jsonSlurper = new JsonSlurper().parseText(json)
      echo "Current Version: ${jsonSlurper.version}"
      jsonSlurper.version = "${version}"
      println new JsonBuilder(jsonSlurper).toPrettyString()  
      def modified_json = JsonOutput.toJson(jsonSlurper)
      modified_json = JsonOutput.prettyPrint(modified_json)
      writeFile(file: env.WORKSPACE +'/package.json', text: modified_json)
}