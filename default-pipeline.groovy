def call(Map pipelineParams){

	pipeline {
		agent any
		environment {
			path = '${env.WORKSPACE}\\pipelineParams.pipelineName\\Source'
			path2 = '${env.WORKSPACE}\\pipelineParams.pipelineName\\Build\\Scripts'
		}

		stages {
			stage('Clean workspace') {
				steps {
					echo 'Cleaning workspace...'
					cleanWs()
				}
			} 
			stage('Git Operations'){
				steps{
					echo 'Starting git operations...'
					dir("${env.WORKSPACE}/pipelineParams.pipelineName") {
						git url: pipelineParams.gitUrl, branch: pipelineParams.gitBranch
						bat "git gc"
						bat "git fetch"
						bat "git reset --hard origin/main"
						bat "git clean -f -d -x"
					}
				}
			}
			stage('Building'){
				steps{
					echo('Starting to build...')
					bat "nuget restore ${env.path}\pipelineParams.projSln"
					bat "msbuild ${env.path2}\\pipelineParams.buildProj"
				}
			}
			stage('Unit Testing'){
				steps{
					// Unit testing steps go here
					echo('Starting Unit Tests')
					
				}
			}
			stage('Signing Application'){
				steps{
					// Signing steps go here
					echo('signing application')

				}
			}
			stage('Deploying'){
				steps{
					// Deployment steps go here
					echo('Starting to deploy...')
				}
			}
			stage('Final Github Operations'){
				steps{
					dir("${env.WORKSPACE}/pipelineParams.pipelineName") {
					
					}
				}
			}
				
		}
	}
}