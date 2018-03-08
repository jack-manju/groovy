properties(
    [buildDiscarder(logRotator( numToKeepStr: '5')), 

    parameters([
        string(defaultValue: 'nodejs && aws', description: 'Node labels', name: 'USED_NODES'),
        string(defaultValue: 'dev02-md-adidasgroup.demandware.net', description: 'Salesforce sandbox/development from where the export is run', name: 'DEV_INSTANCE'),
        string(defaultValue: '', description: 'Export Instance UserName', name: 'Export_Instance_Username'),
        password(defaultValue: '', description: 'Export Instance Password', name: 'Export_Instance_Password'),
        string(defaultValue: '', description: 'Provdie Export File Name', name: 'Export_File_Name'),
        string(defaultValue: '', description: 'Provdie Export Site ID', name: 'Export_Site_ID'),
        string(defaultValue: 'dev02-md-adidasgroup.demandware.net', description: 'Salesforce sandbox/development from where the export is run', name: 'SB_INSTANCE'),
        string(defaultValue: '', description: 'Import Instance UserName', name: 'Import_Instance_Username'),
        password(defaultValue: '', description: 'Import Instance Password', name: 'Import_Instance_Password'),
    ]),
        
    pipelineTriggers([])]
)

node(USED_NODES){
    
 wrap([$class: 'AnsiColorBuildWrapper', colorMapName: 'xterm']) {
        

stage ('Checkout build-suite repository') {
checkout changelog: false, poll: false, 
scm: [$class: 'GitSCM', branches: [[name: '*/new_market_rollout']], 
doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], 
userRemoteConfigs: [[credentialsId: '54b2dd8a-ae82-41c3-8850-fa7032af2cf2', url: 'https://tools.adidas-group.com/bitbucket/scm/edw/ecom-dw-build-grunt.git']]]

sh 'npm install'
}

stage ('Export Site Started') {
 sh 'grunt adi_fullSiteExport -build.project.name=adidas -build.project.version=0 -webdav.username=${Export_Instance_Username} -webdav.password=${Export_Instance_Password} -webdav.server=${DEV_INSTANCE} --export-name=${Export_File_Name} --exportUnits.sites=${Export_Site_ID} --export_filename=${Export_File_Name} '
}

stage ('Import of Exported Site Started') {
 sh 'grunt adi_fullSiteImport -build.project.name=adidas -build.project.version=0 -webdav.username=${Import_Instance_Username} -webdav.password=${Import_Instance_Password} -webdav.server=${SB_INSTANCE}  --export_filename=${Export_File_Name} -jen_workspace=${WORKSPACE} '
}

 }

}


