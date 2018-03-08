#!groovy

// Build parameters which must be configured in the job
// and then passed down to this script as variables:
//
//  USED_NODES                  (mandatory)
//  ECOM_DW_BRANCH              (mandatory)
//  JIRA_ISSUE                  (mandatory)
//  SITES                       (mandatory)
//  SB_INSTANCE                 (mandatory)
//  SB_INSTANCE_CREDENTIALS     (mandatory)
//  REVIEWERS                   (mandatory)
//  PROJECT_NAME                (optional)
//
// ------- Pipeline Configuration -----
//
def project = 'adidas'
def siteExportFileName = 'export_units'
def postfix_feature_branch_ecom_dw = 'sync_configuration'

if ( params.containsKey("PROJECT_NAME") ) {
    project = PROJECT_NAME
}

properties(
    [buildDiscarder(logRotator( numToKeepStr: '5')), 

    parameters([
        string(defaultValue: 'nodejs && aws', description: 'Node labels', name: 'USED_NODES'), 
        string(defaultValue: 'dev', description: 'ECOM_DW branch name', name: 'ECOM_DW_BRANCH'),         
        string(defaultValue: '', description: '''The jira ticket number to persist the configuration change for.
A jira issue must exist upfront before a configuration change can be synced from the instance to the repository.''', name: 'JIRA_ISSUE'),
        text(defaultValue: '', description: '''Please provide comma-separated site IDs in the following format: adidas-AU,adidas-CZ etc.
If this parameter is left empty script will execute for all site IDs.''', name: 'SITES'),
        string(defaultValue: 'development-test-adidasgroup-eu.demandware.net', description: 'Salesforce sandbox from where the export is run', name: 'SB_INSTANCE'),
        credentials(credentialType: 'com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl', 
            description: 'Password is different for each instance', name: 'SB_INSTANCE_CREDENTIALS', required: true),
        string(defaultValue: 'ottoand, petruves, lohrtob, georggeo', description: 'Comma separated list of pull request reviewers.', name: 'REVIEWERS')
    ]),
        
    pipelineTriggers([])]
)


node(USED_NODES) {
    
    stage('Clean workspace') {
        /* make sure we clean things before we do anything */
        deleteDir()
    }

    ansiColor('xterm') {
        
        stage('checkout ecom-dw-build-grunt') {
            checkout scm
        }

        stage("checkout ecom-dw") {
            checkout poll: false,
                    scm: [
                        $class: 'GitSCM',
                        branches: [[name: ECOM_DW_BRANCH]],
                        doGenerateSubmoduleConfigurations: false,
                        extensions: [
                            [
                                $class: 'CloneOption',
                                depth: 0,
                                noTags: true,
                                reference: '',
                                shallow: true
                            ], 
                            [
                                $class: 'RelativeTargetDirectory', 
                                relativeTargetDir: 'ecom-dw'
                            ]
                        ],
                        submoduleCfg: [],
                        userRemoteConfigs: [
                            [
                                credentialsId: '4d5e3713-f94d-46e8-b62c-2adecb11ad67',
                                url: 'ssh://tools.adidas-group.com/edw/ecom-dw.git'
                            ]
                        ]
                    ]
        }

        dir('ecom-dw-build-grunt') {
            stage("npm install") {
                sh 'npm install'
            }

            stage("run site export") {
                withCredentials([usernamePassword(credentialsId: "${SB_INSTANCE_CREDENTIALS}", passwordVariable: 'password', usernameVariable: 'username')]) {
                    sh """grunt adi_exportUnitsConfigurationSync \
                        --build.project.name=${project} \
                        --webdav.server=${SB_INSTANCE} \
                        --webdav.username=${username} \
                        --webdav.password=${password} \
                        --export-name=${siteExportFileName} \
                        --exportUnits.sites= """
                }
            }
            
            stage("sync configuration") {
                sh """grunt adi_sync_configuration \
                        --build.project.name=${project} \
                        --export-name=${siteExportFileName}"""
                        
                dir('output/temp') {
                    stash name: 'site-templates', includes: '**/*', excludes: '*.zip'
                }
            }
        }
        
        dir('ecom-dw') {

            def featureBranchName = "${JIRA_ISSUE}_${postfix_feature_branch_ecom_dw}"

            stage('push changes') {
                dir('sites/development') {
                    unstash name: 'site-templates'
                }

                sshagent (credentials: ['4d5e3713-f94d-46e8-b62c-2adecb11ad67']) {
                    sh 'git config user.email \"ecom-dw@adidas-labs.net\"'
                    sh 'git config user.name \"Service User\"'
                    sh 'git status'
                    sh "git checkout -b ${featureBranchName}"
                    sh 'git add .'
                    sh "git commit -m \"${JIRA_ISSUE}: Commiting site templates\""
                    sh "git pull origin ${ECOM_DW_BRANCH} -X theirs"
                    sh "git pull origin ${featureBranchName} -X ours"
                    sh "git push origin ${featureBranchName}"
                }
            }
        }

        dir('ecom-dw-build-grunt') {

            def featureBranchName = "${JIRA_ISSUE}_${postfix_feature_branch_ecom_dw}"

            stage("submit pull request") {
                withCredentials([usernamePassword(credentialsId: '54b2dd8a-ae82-41c3-8850-fa7032af2cf2', passwordVariable: 'password', usernameVariable: 'username')]) {
                    sh '''grunt adi_bitbucket_create_pull_request \
                        --build.project.name=${project} \
                        --bitbucket.userName="${username}" \
                        --bitbucket.userPassword="${password}" \
                        --bitbucket.project="EDW" \
                        --bitbucket.repos="ecom-dw" \
                        --bitbucket.sourceBranch="${featureBranchName}" \
                        --bitbucket.targetBranch="${ECOM_DW_BRANCH}" \
                        --bitbucket.pullRequest.title="Automatically generated pull request for configuration synchronization." \
                        --bitbucket.pullRequest.description="Configuration synchronization for ${SITES}" \
                        --bitbucket.pullRequest.reviewers="${REVIEWERS}"'''
                }
            }
        }
    } // end AnsiColorBuildWrapper
} // end node