/*
properties(
    [buildDiscarder(logRotator( numToKeepStr: '5')), 

    parameters([
        string(defaultValue: 'nodejs&&aws', description: 'Node labels', name: 'USED_NODES'),
        string(defaultValue: 'dev02-md-adidasgroup.demandware.net', description: 'Salesforce sandbox/development from where the export is run', name: 'Export_Instance'),
        string(defaultValue: '', description: 'Export Instance UserName', name: 'Export_Instance_Username'),
        password(defaultValue: '', description: 'Export Instance Password', name: 'Export_Instance_Password'),
        choice(defaultValue: '',choices:'without_Catalog\nwith_Catalog'  ,description: 'Select Site_Export type', name: 'Site_Export'),
     //   string(defaultValue: '', description: 'Provdie Export File Name', name: 'Export_File_Name'),
        string(defaultValue: '', description: 'Provdie Export Site ID', name: 'Export_Site_ID'),
        string(defaultValue: 'dev02-md-adidasgroup.demandware.net', description: 'Salesforce sandbox/development from where the export is run', name: 'Import_Instance'),
        string(defaultValue: '', description: 'Import Instance UserName', name: 'Import_Instance_Username'),
        password(defaultValue: '', description: 'Import Instance Password', name: 'Import_Instance_Password'),
    ]),  
        
    pipelineTriggers([])]
) */

node(USED_NODES) {
    
    wrap([$class: 'AnsiColorBuildWrapper', colorMapName: 'xterm']) {
    stage('Clean workspace') {
        /* make sure we clean things before we do anything */
        deleteDir()
    }
    
    stage('Checkout Automation') {
    checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: '*/new_market_rollout']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '54b2dd8a-ae82-41c3-8850-fa7032af2cf2', url: 'https://tools.adidas-group.com/bitbucket/scm/edw/ecom-dw-build-grunt.git']]]
	copyArtifacts (filter: '*.json', fingerprintArtifacts: true, projectName: 'Initialize_New_Site', selector: workspace(), target: 'exports/adidas/ecom-dw/sites/');
        sh 'ls -la'
		sh 'npm install'
    }  
	
if(fileExists ('exports/adidas/ecom-dw/sites/replacements.md.json')) {
    stage('Export site') {
        switch(site_export) {
            case "without_Catalog":
            sh 'grunt exportUnits --build.project.name=adidas --webdav.server=${Export_Instance}  --webdav.username=${Export_Instance_Username} --webdav.password=${Export_Instance_Password} --export-name=site_template --exportUnits.sites=${Export_Site_ID}'
            break
            case "with_Catalog":
            sh 'grunt exportUnits_withCatalog --build.project.name=adidas --webdav.server=${Export_Instance}  --webdav.username=${Export_Instance_Username} --webdav.password=${Export_Instance_Password} --export-name=site_template --exportUnits.sites=${Export_Site_ID}'
            break
        }       
	   sh 'find "${WORKSPACE}/output/" -name "*.zip" -type f -delete'
    }

    stage('create a New site template from export'){
        sh 'npm -s run create_site_template -- -i --instance=${Import_Instance}'
    }

    stage('Import New site template'){
		sh 'find "${WORKSPACE}/output/" -type f -iname "*.zip" -exec basename {} .zip \';\' > outfile.txt '
		def filename = readFile "${WORKSPACE}/outfile.txt"
		sh 'grunt adi_newsitetemplateImport -build.project.name=adidas -build.project.version=0   -webdav.username=${Import_Instance_Username}  -webdav.password=${Import_Instance_Password} -webdav.server=${Import_Instance} -jen_workspace=${WORKSPACE} --export_filename='+ filename +' '
    }
    } else {
        currentBuild.result = 'ABORTED'
        error('Stopping early…')
    }
}
}