def call(Map pipelineParams) {
    pipeline {
        agent any
        environment {
            gitUrl = "${pipelineParams.gitUrl}"
            proj = "${pipelineParams.proj}"
            projGit = "${env.proj}.git"
            projName = "${env.proj}.sln"
            projSln = "${env.WORKSPACE}\\Source\\${env.projName}"
            buildFlavor = 'Release'
            buildOutputFolder = "${env.WORKSPACE}\\Build\\Output\\${env.buildFlavor}"
            binariesToArchive = "${env.buildOutputFolder}\\Applications\\${env.proj}"
            binariesArchiveWorkingDirectory = "${env.WORKSPACE}\\Archives\\Binaries"
            installsArchiveWorkingDirectory = "${env.WORKSPACE}\\Archives\\Installs"
            scriptsArchiveWorkingDirectory = "${env.WORKSPACE}\\Archives\\Scripts"
            sourceArchiveWorkingDirectory = "${env.WORKSPACE}\\Archives\\Source"
            binariesArchiveFileName = "${env.WORKSPACE}\\Archives\\${env.proj}.Binaries.zip"
            sourceArchiveFileName = "${env.WORKSPACE}\\Archives\\${env.proj}.Source.zip"
            scriptsArchiveFileName = "${env.WORKSPACE}\\Archives\\${env.proj}.Scripts.zip"
            installsArchiveFileName = "${env.WORKSPACE}\\Archives\\${env.proj}.Installs.zip"
            branch = "${pipelineParams.branch}"
            deployFolder = "C:\\Archives\\${env.proj}"
            pathVersion = "./Build/Scripts/${env.proj}.version"
        }

        stages {
            stage('Create Workspace and Update Repository') {
                steps {
                    println('Cleaning workspace...')
                    cleanWs()
                    println('Starting git operations...')
                    withCredentials([gitUsernamePassword(credentialsId: 'github-credentials', gitToolName: 'Default')]) {
                        checkout scmGit(branches: [[name: '*/main']], extensions: [], userRemoteConfigs: [[credentialsId: 'github-credentials', url: "${env.gitUrl}"]])
                        powershell "git checkout '${env.branch}'"
                        powershell 'git gc'
                        powershell 'git fetch'
                        powershell "git reset --hard origin/'${env.branch}'"
                        powershell 'git clean -f -d -x'
                        println('Starting nuget restore')
                        powershell "nuget restore ${env.projSln}"
                    }
                }
            }
            stage('Version Source') {
                steps {
                    println('Starting to version source')
                    script {
                        def versionFile = readFile(env.pathVersion)
                        def items = []
                        def delimiter = '.'
                        println('Before version = ' + versionFile)
                        def int startIndex = 0
                        def int nextIndex = versionFile.indexOf(delimiter)
                        while (nextIndex >= 0) {
                            items << versionFile.substring(startIndex, nextIndex)
                            startIndex = nextIndex + delimiter.length()
                            nextIndex = versionFile.indexOf(delimiter, startIndex)
                        }
                        items << versionFile.substring(startIndex)

                        def lastItem = items[-1]
                        def incremented = Integer.parseInt(lastItem) + 1
                        items.set(items.size() - 1, incremented.toString())

                        def newVersion = items.join(delimiter)
                        writeFile file: pathVersion, text: newVersion
                        def versionFile2 = readFile(env.pathVersion)
                        println('New Version = ' + versionFile2)

                        changeAsmVer assemblyFile: '**/AssemblyInfo.cs',
                        versionPattern: versionFile2
                        powershell 'git add --all'
                        powershell "git commit -m '${env.project}: Version change for build v${versionFile2}-${env.branch}'"
                        powershell "git tag -f v'${versionFile2}'"
                    }
                }
            }
            stage('Build') {
                steps {
                    println('Starting to build...')
                    powershell "msbuild /p:GitBranch='${env.branch}' /p:Configuration='${env.buildFlavor}' /p:BuildInParallel=false ${env.projSln}"
                }
            }
            stage('Unit Testing') {
                steps {
                    println('Starting Unit Tests')
                    powershell "if (Test-Path '${env:WORKSPACE}\\**\\*.Tests.dll') {mstest /testcontainer:'${env:WORKSPACE}\\**\\*.Tests.dll' Remove-Item '${env.WORKSPACE}\\**\\*.Tests.dll' -Force} else {Write-Output 'No test files found.'}"
                }
            }
            stage('Clean Build') {
                steps {
                    println('Starting to clean build...')
                    powershell "Remove-Item '${env:buildOutputFolder}\\**\\setup.exe'"
                    powershell "Remove-Item '${env:buildOutputFolder}\\**\\*.vshost.exe'"
                    powershell "Remove-Item '${env:buildOutputFolder}\\**\\*.vshost.exe.manifest'"
                }
            }
            stage('Deploy Build') {
                steps {
                    println('Starting pre deploy steps...')
                    powershell "Copy-Item -Path '${env.WORKSPACE}\\Source\\Data\\*.sql' -Destination '${env.buildOutputFolder}\\Applications\\${env.proj}\\' -Recurse"
                    echo 'Deploying build content to the deploy folder'
                    powershell "mkdir '${env.buildOutputFolder}\\..\\Debug'"
                    powershell "Copy-Item -Path '${env.buildOutputFolder}\\*' -Destination '${env.buildOutputFolder}\\..\\Debug' -Recurse"
                }
            }
            stage('Archive Build') {
                steps {
                    echo 'Starting to archive...'
                    echo 'Creating folders for archive...'
                    powershell "mkdir '${env.WORKSPACE}\\Archives\\Binaries'"
                    powershell "mkdir '${env.WORKSPACE}\\Archives\\Installs'"
                    powershell "mkdir '${env.WORKSPACE}\\Archives\\Source'"
                    powershell "mkdir '${env.WORKSPACE}\\Archives\\Scripts'"

                    echo 'Starting archive of binaries'
                    powershell "Copy-Item -Path '${env.binariesToArchive}\\*.sql' -Destination '${env.binariesArchiveWorkingDirectory}' -Recurse"
                    powershell "Copy-Item -Path '${env.buildOutputFolder}\\Applications\\${env.proj}\\*.dll' -Destination '${env.binariesArchiveWorkingDirectory}' -Recurse"
                    powershell "Copy-Item -Path '${env.buildOutputFolder}\\Applications\\${env.proj}\\*.exe' -Destination '${env.binariesArchiveWorkingDirectory}' -Recurse"
                    powershell "Copy-Item -Path '${env.buildOutputFolder}\\Applications\\${env.proj}\\*.exe.config' -Destination '${env.binariesArchiveWorkingDirectory}' -Recurse"
                    powershell "Copy-Item -Path '${env.buildOutputFolder}\\Tools\\**\\*.exe' -Destination '${env.binariesArchiveWorkingDirectory}' -Recurse"
                    powershell "Copy-Item -Path '${env.buildOutputFolder}\\Tools\\**\\*.dll' -Destination '${env.binariesArchiveWorkingDirectory}' -Recurse"
                    powershell "Copy-Item -Path '${env.buildOutputFolder}\\Applications\\**\\wwwroot\\**\\**' -Destination '${env.binariesArchiveWorkingDirectory}' -Recurse"

                    echo 'Starting archive of source'
                    powershell "Copy-Item -Path '${env.WORKSPACE}\\Source\\**\\*.*' -Exclude '${env.WORKSPACE}\\Source\\**\\obj\\**\\*.*' -Destination '${env.sourceArchiveWorkingDirectory}' -Recurse"

                    echo 'Starting archive of installs'
                    powershell "Copy-Item -Path '${env.buildOutputFolder}\\Setup\\*' -Destination '${env.installsArchiveWorkingDirectory}' -Recurse"
                    powershell "Remove-Item '${env.installsArchiveWorkingDirectory}\\*.wixpdb' -Recurse"

                    //NOTE: there isnt a section for archiving scripts as there was any scripts to archive in openXDA
                    echo 'Starting to archive scripts....'

                    echo 'Starting to compress archives...'
                    powershell "Compress-Archive -Path '${env.binariesArchiveWorkingDirectory}' -DestinationPath '${binariesArchiveFileName}' "
                    powershell "Compress-Archive -Path '${env.installsArchiveWorkingDirectory}' -DestinationPath '${installsArchiveFileName}' "
                    powershell "Compress-Archive -Path '${env.scriptsArchiveWorkingDirectory}' -DestinationPath '${scriptsArchiveFileName}' "
                    powershell "Compress-Archive -Path '${env.sourceArchiveWorkingDirectory}' -DestinationPath '${sourceArchiveFileName}' "
                    powershell "if (-not (Test-Path '${env.deployFolder}')) { New-Item -ItemType Directory -Path '${env.deployFolder}' }"

                    powershell "if ( (Test-Path '${env.deployFolder}\\*.zip')) { Remove-Item '${env.deployFolder}\\*' -Recurse }"
                    powershell "if ( (Test-Path '${env.deployFolder}')) { New-Item -ItemType Directory -Path '${env.deployFolder}' }"
                //    powershell "if ( (Test-Path '${env.deployFolder}')) { New-Item -ItemType Directory -Path '${env.deployFolder}' }"
                //    powershell "if ( (Test-Path '${env.deployFolder}')) { New-Item -ItemType Directory -Path '${env.deployFolder}' }"

                    powershell "Move-Item -Path '${env.sourceArchiveFileName}' -Destination '${env.deployFolder}'"
                    powershell "Move-Item -Path '${env.binariesArchiveFileName}' -Destination '${env.deployFolder}'"
                    powershell "Move-Item -Path '${env.installsArchiveFileName}' -Destination '${env.deployFolder}'"
                    powershell "Move-Item -Path '${env.scriptsArchiveFileName}' -Destination '${env.deployFolder}'"
                    powershell "Remove-Item -Path '${env.WORKSPACE}\\Archives'"

                    echo 'Cleaning up...'
                    powershell "Get-ChildItem -Path '${env.buildOutputFolder}\\..\\Debug' -Recurse | Remove-Item -Recurse"
                    powershell "Remove-Item -Path '${env.buildOutputFolder}\\..\\Debug'"
                    powershell "Get-ChildItem -Path '${env.binariesArchiveWorkingDirectory}' -Recurse | Remove-Item -Recurse"
                    powershell "Remove-Item -Path '${env.binariesArchiveWorkingDirectory}'"
                    powershell "Get-ChildItem -Path '${env.installsArchiveWorkingDirectory}' -Recurse | Remove-Item -Recurse"
                    powershell "Remove-Item -Path '${env.installsArchiveWorkingDirectory}'"
                    powershell "Get-ChildItem -Path '${env.scriptsArchiveWorkingDirectory}' -Recurse | Remove-Item -Recurse"
                    powershell "Remove-Item -Path '${env.scriptsArchiveWorkingDirectory}'"
                    powershell "Get-ChildItem -Path '${env.sourceArchiveWorkingDirectory}' -Recurse | Remove-Item -Recurse"
                    powershell "Remove-Item -Path '${env.sourceArchiveWorkingDirectory}'"
                }
            }
        }
    }
}
