properties(
       [buildDiscarder(logRotator( numToKeepStr: '5')),
    parameters([
        string(defaultValue: '', description: 'Export Instance UserName', name: 'Username'),
        password(defaultValue: '', description: 'Export Instance Password', name: 'Password'),
    ]),
        
    pipelineTriggers([])]
)

node () {
    
sh 'cat > test_artifact.txt'
sh 'curl -u ${Username}:${Password} -T  ${WORKSPACE}/test_artifact.txt https://artifactory.allstate.com/artifactory/list/CT-Mobile/test_artifact.txt'
sh 'curl -o -I -s -w "%{http_code}\n" -u ${Username}:${Password}  -T ${WORKSPACE}/test_artifact.txt  https://artifactory.allstate.com/artifactory/list/CT-Mobile/test_artifact.txt  > cmdresult'
http_status = readFile('cmdresult').trim()
echo "$http_status"

if (http_status == '201') 
         echo ("Agent Smith Work")
else 
   echo('Artifact is not uploaded to Artifactory') 
}