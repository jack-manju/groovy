import groovy.json.JsonSlurper
import groovy.json.JsonOutput

node(used_nodes) {
    try {
	    wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [[password: jira_password, var: 'jira_password'],[password: bitbucket_password, var: 'bitbucket_password']]]) {
			currentBuild.displayName = '#' + env.BUILD_NUMBER + '-' + sourceBranch //use Author name instead of build number
			def isRelease = "${sourceBranch}".indexOf('hotfix/') == -1

			//used for have the log in ansi color
			wrap([$class: 'AnsiColorBuildWrapper', colorMapName: 'xterm']) {
				stage 'Checkout'
					sh 'mkdir -p cache/node_modules'
					sh 'mkdir -p build'

					dir('build') {
						deleteDir()
						checkout([
								$class                           : 'GitSCM',
								branches                         : [[name: '*/' + sourceBranch]],
								doGenerateSubmoduleConfigurations: false,
								extensions                       : [
										[
												$class   : 'CloneOption',
												noTags   : false,
												reference: '',
												shallow  : true,
												timeout  : 10
										]
								],
								submoduleCfg                     : [],
								userRemoteConfigs                : [
										[
												credentialsId: '54b2dd8a-ae82-41c3-8850-fa7032af2cf2',
												url          : "https://${bitbucket_url}/scm/${bitbucket_project}/${bitbucket_repo}.git"
										]
								]
						])
						sh "git config user.email \"Jenkins@adidas-group.com\""
						sh "git config user.name \"ecomdw_service\""

				stage 'NPM install'
					sh 'ln -s ../cache/node_modules node_modules'
					sh 'npm prune'
					sh "npm install"

				stage 'Bump version'
					sh "grunt bump:${release_type} --build.project.name=adidas"
					def versionJson = readFile('version.json')
					def jsonSlurper = new JsonSlurper()
					def versionNumber = jsonSlurper.parseText(versionJson).version
					jsonSlurper = null
					def versionName = 'DW Build Suite Release '
					if (!isRelease) {
						versionName = 'Hotfix - ' + versionName
					}
					versionName += versionNumber

				stage 'Prepare release notes'
					def changesRegExp = ""
					if (isRelease) {
						changesRegExp = "^Merge pull request #([0-9]+).*\\/([\\\\w]+-[\\\\d]+).* to develop"
					} else {
						changesRegExp = "(.*)"
					}
					sh "grunt adi_build_notification " +
						"--build.project.name=adidas " +
						"--jira.user=${jira_user} " +
						"--jira.password=${jira_password} " +
						"--build.project.version='${versionName}' " +
						"--build.notification.jiraApplyChanges=${jiraApplyChanges} " +
						"--bitbucket.userName=${bitbucket_user} " +
						"--bitbucket.userPassword=${bitbucket_password} " +
						"--bitbucket.project=${bitbucket_project} " +
						"--bitbucket.repos=${bitbucket_repo} " +
						"--build.notification.gitTagForStart=${previousReleaseTag} " +
						"--build.notification.gitTagForEnd=${sourceBranch} " +
						"--build.notification.changesRegExp='${changesRegExp}' "

				stage 'Push changes into bitBucket'
					def repo = "https://${bitbucket_user}:${bitbucket_password}@${bitbucket_url}/scm/${bitbucket_project}/${bitbucket_repo}.git"
					def releaseName = "${versionNumber}"
					sh "git checkout ${sourceBranch}"
					sh "git remote set-url origin ${repo}"
					sh 'git add .'
					sh "git commit -m 'Release ${releaseName}'"
					sh "git push"

				stage 'Create Pull Request'
					// Wait to avoid synchronization problems
					sleep 10
					def repository = [
							slug   : bitbucket_repo,
							name   : null,
							project: [
									key: bitbucket_project
							]
					]
					def pullRequest = [
							title      : "Automatically generated pull request after a successful Release build",
							description: "Preparation for release",
							state      : "OPEN",
							open       : true,
							closed     : false,
							fromRef    : [
									id        : "refs/heads/${sourceBranch}",
									repository: repository
							],
							toRef      : [
									id        : "refs/heads/master",
									repository: repository
							],
							locked     : false,
							reviewers  : []
					]
					def reviewersList = reviewers.tokenize(',')

					for (reviewer in reviewersList) {
						reviewer = reviewer.trim()
						if (reviewer) {
							pullRequest.reviewers.add([user: [name: reviewer]])
						}
					}
					def json = JsonOutput.toJson(pullRequest)
					def pullRequestEndpoint = "https://${bitbucket_url}/rest/api/1.0/projects/${bitbucket_project}/repos/${bitbucket_repo}/pull-requests"
					sh "curl -X POST -H \"Content-Type: application/json\" -u ${bitbucket_user}:${bitbucket_password} ${pullRequestEndpoint} -d '${json}' 2>&1"
				}
			}
		}
    } catch(Exception exception) {
        currentBuild.result = 'FAILURE'
        mail bcc: '',
            body: "Please go to: \n ${env.BUILD_URL} \n and take immediate actions",
            cc: '',
            charset: 'UTF-8',
            from: failedNotificationSender,
            mimeType: 'text/plain',
            replyTo: '',
            subject: "Job ${env.JOB_NAME} ${env.BUILD_NUMBER} HAS FAILED",
            to: failedNotificationReceivers

        error("Build failed")
    } finally {
        step([
            $class: 'LogParserPublisher',
            failBuildOnError: true,
            parsingRulesPath: '/var/lib/jenkins/log_minimal_parser',
            useProjectRule: false
        ])
    }
}
