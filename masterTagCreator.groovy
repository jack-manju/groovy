import groovy.json.JsonSlurper

node(used_nodes) {
    try {
        currentBuild.displayName = '#' + env.BUILD_NUMBER + '-' + sourceBranch
        //use Author name instead of build number

        //used for have the log in ansi color
        wrap([$class: 'AnsiColorBuildWrapper', colorMapName: 'xterm']) {
            stage 'Checkout'
            git branch: sourceBranch,
                credentialsId: '54b2dd8a-ae82-41c3-8850-fa7032af2cf2',
                url: "https://${repository}"

            stage 'create version tag'
            def versionJson = readFile('version.json')
            def jsonSlurper = new JsonSlurper()
            def versionNumber = jsonSlurper.parseText(versionJson).version
            jsonSlurper = null

            def repo = "https://${bitbucket_user}:${bitbucket_password}@${repository}"

            sh "git remote set-url origin ${repo}"
            sh "git config --global user.email \"ecom-dw@adidas-labs.net\""
            sh "git config --global user.name \"Service User\""
            sh "git tag -a '${versionNumber}' -m \"tagging ${sourceBranch}\""
            sh "git push origin ${versionNumber}"
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
