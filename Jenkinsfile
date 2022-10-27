// Lambda Template version 3.3
pipeline {
    options {
      ansiColor('xterm')
      disableConcurrentBuilds()
      skipStagesAfterUnstable()
      timeout(time: 1, unit: 'HOURS')
    }
    environment {
      COMPONENT_FAMILY = "RF-RePlatform"
      GIT_COMMIT_ID = ""
      GIT_COMMIT_MSG = ""
      GIT_REPO = "https://github.com/caporg/mcc-lap-api-authorizer"
      JOB_SUBMITTER = ""
      TICKET_NUM = "NRM_265444"
      SERVICE_NAME = "mcc-lap-api-authorizer"
    }
    agent { label 'linux' }
    stages {
        stage ('Initialize') {
            steps {
                parallel(
                    a: {
                        git branch: '${BRANCH_NAME}', url: "${GIT_REPO}",
                        credentialsId: 'SDA'
                        script {
                            echo "Capturing GIT COMMIT ID"
                            GIT_COMMIT_MSG = sh (script: 'git log -1 --pretty=%B ${GIT_COMMIT}', returnStdout: true).trim()
                            GIT_COMMIT_ID = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
                            echo "GIT COMMIT ID is: $GIT_COMMIT_ID"
                            echo "GIT COMMIT MESSAGE is:  $GIT_COMMIT_MSG"
                        }
                    },
                    b: {
                        step([$class: 'RunGlobalProcessNotifier',
                        // DA server to publish to, configured in global settings.
                        siteName: 'SDA',
                        // Use a build parameter to determine if a global process should be triggered in DA.
                        runGlobalProcessIf: 'true',
                        // Wait for a completion of a global process in DA and update job status based on the process result.
                        updateJobStatus: true,
                        // The name of the global process you want to execute.
                        globalProcessName: 'Component Creator',
                        // The name of the resource you want to execute the global process on.
                        resourceName: 'putllxscript10',
                        // Newline separated list of quoted properties and values i.e. prop1=${BUILD_NUMBER} to pass to DA global process.
                        globalProcessProperties: """
                        component.type=aws_LambdaUpdate_
                        component.name=${SERVICE_NAME}
                        component.template=MDW - AWS: Lambda - Update template
                        component.description=AWS Lambda Deployment
                        application.container=MDW - Cloud
                        resource.container=DEV - AWS
                        build.type=lambda
                        """
                        ])
                    }
                )
            }

            post {
                failure {
                    script {
                        error "Error while calling SDA process: Component Creator"
                    }
                }
            }
        }

        stage ('Artifactory Configuration for NPM') {
            when { expression { return fileExists ('package.json') } }
                steps {
                    rtServer (
                        id: "ARTIFACTORY_SERVER",
                        url: 'https://capartifactory.jfrog.io/capartifactory',
                        credentialsId: 'artifactory.creds'
                    )
                    rtNpmResolver (
                        id: "NPM Resolver",
                        serverId: "ARTIFACTORY_SERVER",
                        repo: "npm"
                    )
                    rtNpmDeployer (
                        id: "NPM Deployer",
                        serverId: "ARTIFACTORY_SERVER",
                        repo: "npm.cap.lib",
                    )
                    rtNpmInstall (
                        // Optional tool name from Jenkins configuration
                        tool: 'NodeJS_16.3.0',
                        // Optional path to the project root. If not set, the root of the workspace is assumed as the root project path.
                        args: '--verbose -d',
                        resolverId: "NPM Resolver"
                    )
                    rtBuildInfo (
                        // Optional - Maximum builds to keep in Artifactory.
                        maxBuilds: 10,
                        // Optional - Maximum days to keep the builds in Artifactory.
                        //maxDays: 2,
                        // Optional - List of build numbers to keep in Artifactory.
                        //doNotDiscardBuilds: ["3"],
                        // Optional (the default is false) - Also delete the build artifacts when deleting a build.
                        deleteBuildArtifacts: true
                        // Optional - Build name and build number. If not set, the Jenkins job's build name and build number are used.
                        //name: 'BUILD',
                        //number: 'BUILD_NUMBER'
                    )
            }
        }

        stage ('Artifactory Configuration for Maven') {
            when { expression { return fileExists ('pom.xml') } }
                steps {
                    rtServer (
                        id: "ARTIFACTORY_SERVER",
                        url: 'https://capartifactory.jfrog.io/capartifactory',
                        credentialsId: 'artifactory.creds'
                    )
                    rtMavenDeployer (
                        id: "MAVEN_DEPLOYER",
                        serverId: "ARTIFACTORY_SERVER",
                        releaseRepo: "maven.cap.lib",
                        snapshotRepo: "maven.cap.lib"
                    )
                    rtMavenResolver (
                        id: "MAVEN_RESOLVER",
                        serverId: "ARTIFACTORY_SERVER",
                        releaseRepo: "libs-release",
                        snapshotRepo: "libs-snapshot"
                    )
                    rtBuildInfo (
                        // Optional - Maximum builds to keep in Artifactory.
                        maxBuilds: 10,
                        // Optional - Maximum days to keep the builds in Artifactory.
                        //maxDays: 2,
                        // Optional - List of build numbers to keep in Artifactory.
                        //doNotDiscardBuilds: ["3"],
                        // Optional (the default is false) - Also delete the build artifacts when deleting a build.
                        deleteBuildArtifacts: true
                        // Optional - Build name and build number. If not set, the Jenkins job's build name and build number are used.
                        //name: 'BUILD',
                        //number: 'BUILD_NUMBER'
                    )
            }
        }

        stage ('Maven Build') {
            when { expression { return fileExists ('pom.xml') } }
                tools {
                    jdk 'Corretto-11'
                }
                steps {
                    withSonarQubeEnv('SonarQube') {
                        rtMavenRun (
                            tool: 'Maven 3.8.4', // Tool name from Jenkins configuration
                            pom: 'pom.xml',
                            goals: 'clean install sonar:sonar pmd:pmd pmd:cpd findbugs:findbugs checkstyle:checkstyle com.github.spotbugs:spotbugs-maven-plugin:3.1.12:spotbugs',
                            deployerId: "MAVEN_DEPLOYER",
                            resolverId: "MAVEN_RESOLVER"
                        )
                }
            }
        }


        stage ('Create Archive'){
            when { expression { return fileExists ('package.json') } }
                steps{
                    sh '''
                    npm run-script build
                    mkdir -p target
                    cp *.zip target/
                    '''
                }
        }

        stage('SonarQube Scanner for NPM') {
            when { expression { return fileExists ('package.json') } }
                tools {
                    jdk 'OpenJDK-11'
                }
                steps {
                    nodejs(nodeJSInstallationName: 'NodeJS_16.3.0', configId: '') {
                        script {
                            withSonarQubeEnv('SonarQube') {
                            def scannerHome = tool 'SonarQubeScanner';
                            sh "${scannerHome}/bin/sonar-scanner"
                        }
                    }
                }
            }
        }

        stage ('Code Analysis and SonarQube Verification') {
            steps {
                parallel(
                    a: {
                        recordIssues(
                            enabledForFailure: false, aggregatingResults: true, healthy: 10, unhealthy: 100, minimumSeverity: 'HIGH',
                            tools: [checkStyle(), cpd(), spotBugs(), findBugs()]
                        )
                    },
                    b: {
                        timeout(time: 2, unit: 'HOURS') {
                        waitForQualityGate abortPipeline: false
                        }
                    }
                )
            }
        }

        stage ('Publish Build Info to Artifactory / X-Ray') {
            steps {
                rtPublishBuildInfo (
                    serverId: "ARTIFACTORY_SERVER"
                )
            }
        }

        stage ('Push Properties') {
            steps {
                script {
                    JOB_SUBMITTER = "${currentBuild.getBuildCauses()[0].shortDescription} / ${currentBuild.getBuildCauses()[0].userId}"
                }
                step([$class: 'RunGlobalProcessNotifier',
                    // DA server to publish to, configured in global settings.
                    siteName: 'SDA',
                    // Use a build parameter to determine if a global process should be triggered in DA.
                    runGlobalProcessIf: 'true',
                    // Wait for a completion of a global process in DA and update job status based on the process result.
                    updateJobStatus: true,
                    // The name of the global process you want to execute.
                    globalProcessName: 'Component Property Injector',
                    // The name of the resource you want to execute the global process on.
                    resourceName: 'putllxscript10',
                    // Newline separated list of quoted properties and values i.e. prop1=${BUILD_NUMBER} to pass to DA global process.
                    globalProcessProperties: """
                    component.name=aws_LambdaUpdate_${SERVICE_NAME}
                    component.family=${COMPONENT_FAMILY}
                    build.name=${SERVICE_NAME}.BUILD_${BUILD_NUMBER}
                    build.number=${BUILD_NUMBER}
                    branch.name=${BRANCH_NAME}
                    job.submitter=${JOB_SUBMITTER}
                    git.commit.id=${GIT_COMMIT_ID}
                    git.message=${GIT_COMMIT_MSG}
                    git.repo=${GIT_REPO}
                    function.name=${SERVICE_NAME}
                    """
                ])
            }

            post {
                failure {
                    script {
                        error "Error while calling SDA process: Component Property Injector"
                    }
                }
            }
        }

        stage ('SDA Deployment') {
            steps {
                script {
                  echo "Setting Version Name"
                  echo env.BRANCH_NAME + ':' + env.BUILD_NUMBER
                  PARSED_BRANCH = sh(
                    returnStdout: true,
                    script: 'echo ${BRANCH_NAME} | sed "s/[^[:alnum:]]/_/g"'
                  )
                  VERSION_NAME = PARSED_BRANCH.trim() + ':' + env.BUILD_NUMBER
                  echo "Version Name is now: ${VERSION_NAME}"
                }
                step([$class: 'SerenaDAPublisher',
                // DA server to publish to, configured in global settings.
                siteName: 'SDA',

                // The name of the component in the DA server.
                component: 'aws_LambdaUpdate_' + env.SERVICE_NAME,
                // Base directory where the artifacts are located.
                baseDir: env.WORKSPACE + '/target',
                // The name of the version in the DA server.
                versionName: VERSION_NAME,
                // A new line separated list of file filters to select the files to publish.
                fileIncludePatterns: '''
                *.zip
                **/*.jar
                ''',
                // A new line separated list of file filters to exclude from publishing.
                fileExcludePatterns: '''
                **/*original*
                **/*tmp*
                **/.git
                ''',
                // Skip publishing (e.g. temporarily)
                skip: false,

                // Add status to this version in DA once it's uploaded.
                addStatus: true,
                // The full name of the status to apply to the Version.
                statusName: 'CICD',

                // Trigger a deployment of this version in DA once it's uploaded.
                deploy: true,
                // Use a build parameter to determine if a application process should be triggered in DA.
                deployIf: 'true',
                // Wait for a completion of an application process in DA and update job status based on the process result.
                deployUpdateJobStatus: true,
                // The name of the application in DA which will be used to deploy the version.
                deployApp: 'MDW - Cloud',
                // The name of the environment in DA to deploy to.
                deployEnv: 'MDW - DEV',
                // The name of the application process in DA which will be used to deploy the version.
                deployProc: 'AWS - Lambda Updates',
                ])
            }

            post {
                failure {
                    script {
                        error "Error while calling SDA deployment"
                    }
                }
            }
        }
    }

    post {
        always {
            logstashSend failBuild: false, maxLines: -1
            emailext attachLog: true,
            body: "${currentBuild.currentResult}: Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}\n More info at: ${env.BUILD_URL}",
            recipientProviders: [developers(), requestor()],
            subject: "Jenkins Build ${currentBuild.currentResult}: Job ${env.JOB_NAME}"
            }
        success {
            addBadge(icon: "success.gif", text: "Build Succeeded")
            script {
                def exists = fileExists 'package.json'
                if (exists) {
                    cd $WORKSPACE/target
                    sh "rm -rfv *.BUILD_$BUILD_NUMBER.tar.gz"
                } else {
                    echo 'No need to purge anything'
                }
            }
        }
        failure {
            addBadge(icon: "error.gif", text: "Build Failed")
            echo 'If ALL stages are Green then the DA Deployment step most likely caused this failure'
        }
        unstable {
            echo 'DA Deployment most Likely Caused This Failure'
        }
    }
}
