node {
    cleanWs()
    
    checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'a4f58ec3-56ac-43f4-953c-d29b9a43c542', url: 'https://github.com/parmar-gaurav/nodejs.git']]]
    
    def file1 = '${filename}'
    sh 'echo $filename | cut -f 1 -d \'.\' > cmdresult'
    result = readFile('cmdresult').trim()
    zip = '.zip'
    sh "echo ${result}-$BUILD_NUMBER$zip > newname"
    def newname1 = readFile('newname').trim()
    sh 'mv ' + file1 + ' ' + newname1    
}