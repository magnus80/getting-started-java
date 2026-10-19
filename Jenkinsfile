def compileOutcome = 'skipped'
def testsOutcome = 'skipped'

pipeline {
    agent none

    parameters {
        string(name: 'BROWSER_SIZE', defaultValue: '1920x1080', description: 'Browser window size, WIDTHxHEIGHT')
        string(name: 'TELEGRAM_TOKEN_CREDENTIALS_ID', defaultValue: 'TELEGRAM_BOT_TOKEN', description: 'Secret text credentials ID with the Telegram bot token')
        string(name: 'TELEGRAM_CHAT_CREDENTIALS_ID', defaultValue: 'TELEGRAM_CHAT_ID', description: 'Secret text credentials ID with the Telegram chat ID')
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
    }

    environment {
        CHROME_VERSION = '153.0.8010.52'
        ALLURE_VERSION = '2.46.1'
        ALLURE_SHA256 = 'd25c519bbde940dc953cff8901130ce18b73013dc19a8f8b90b8b4b1c5eb252f'
        WINDOW_SIZE = "${params.BROWSER_SIZE ?: '1920x1080'}"
    }

    stages {
        stage('UI tests') {
            agent { label 'java-jdk21' }
            stages {
                stage('Validate parameters') {
                    steps {
                        stash name: 'notify-script', includes: 'ci/telegram_notify.py'
                        sh '''
                            if ! printf '%s' "$WINDOW_SIZE" | grep -Eq '^[0-9]{3,4}x[0-9]{3,4}$'; then
                              echo "Invalid BROWSER_SIZE: expected WIDTHxHEIGHT"
                              exit 1
                            fi
                        '''
                    }
                }

                stage('Set up Chrome') {
                    steps {
                        sh '''
                            set -eu
                            tools="$WORKSPACE/.ci-tools"
                            rm -rf "$tools" && mkdir -p "$tools"
                            base="https://storage.googleapis.com/chrome-for-testing-public/$CHROME_VERSION/linux64"
                            curl -fsSL -o "$tools/chrome.zip" "$base/chrome-linux64.zip"
                            curl -fsSL -o "$tools/chromedriver.zip" "$base/chromedriver-linux64.zip"
                            unzip -q "$tools/chrome.zip" -d "$tools"
                            unzip -q "$tools/chromedriver.zip" -d "$tools"
                            chrome_version="$("$tools/chrome-linux64/chrome" --version)"
                            driver_version="$("$tools/chromedriver-linux64/chromedriver" --version)"
                            echo "$chrome_version"
                            echo "$driver_version"
                            case "$chrome_version" in *"$CHROME_VERSION"*) ;; *) echo "Unexpected Chrome version"; exit 1 ;; esac
                            case "$driver_version" in *"$CHROME_VERSION"*) ;; *) echo "Unexpected ChromeDriver version"; exit 1 ;; esac
                        '''
                    }
                }

                stage('Install Allure CLI') {
                    steps {
                        sh '''
                            set -eu
                            tools="$WORKSPACE/.ci-tools"
                            curl -fsSL -o "$tools/allure.zip" \
                              "https://repo.maven.apache.org/maven2/io/qameta/allure/allure-commandline/$ALLURE_VERSION/allure-commandline-$ALLURE_VERSION.zip"
                            echo "$ALLURE_SHA256  $tools/allure.zip" | sha256sum --check --strict
                            unzip -q "$tools/allure.zip" -d "$tools"
                            actual="$("$tools/allure-$ALLURE_VERSION/bin/allure" --version)"
                            echo "Allure CLI $actual"
                            [ "$actual" = "$ALLURE_VERSION" ] || { echo "Expected Allure CLI $ALLURE_VERSION"; exit 1; }
                        '''
                    }
                }

                stage('Compile tests') {
                    steps {
                        script {
                            compileOutcome = 'failure'
                            sh './gradlew testClasses --console=plain'
                            compileOutcome = 'success'
                        }
                    }
                }

                stage('Run UI tests') {
                    steps {
                        script {
                            def exitCode = sh(returnStatus: true, script: '''
                                tools="$WORKSPACE/.ci-tools"
                                ./gradlew test --console=plain \
                                  -Dheadless=true \
                                  "-DbrowserSize=$WINDOW_SIZE" \
                                  "-Dselenide.browserBinary=$tools/chrome-linux64/chrome" \
                                  "-Dwebdriver.chrome.driver=$tools/chromedriver-linux64/chromedriver" \
                                  "-Dchromeoptions.args=--no-sandbox"
                            ''')
                            testsOutcome = exitCode == 0 ? 'success' : 'failure'
                            stash name: 'junit-results', includes: 'build/test-results/test/*.xml', allowEmpty: true
                            junit testResults: 'build/test-results/test/*.xml'
                            if (exitCode != 0 && currentBuild.currentResult != 'UNSTABLE') {
                                error("Gradle test task failed with exit code ${exitCode} although no JUnit test failed")
                            }
                        }
                    }
                }

                stage('Generate Allure report') {
                    steps {
                        sh '''
                            set -eu
                            if ! ls build/allure-results/*-result.json >/dev/null 2>&1; then
                              echo "No Allure test results"
                              exit 1
                            fi
                            "$WORKSPACE/.ci-tools/allure-$ALLURE_VERSION/bin/allure" generate build/allure-results -c -o build/allure-report
                        '''
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'build/allure-results/**, build/allure-report/**, build/test-results/test/**, build/reports/tests/test/**',
                            allowEmptyArchive: true
                }
            }
        }
    }

    post {
        always {
            script {
                def jobStatus = currentBuild.currentResult == 'ABORTED' ? 'cancelled' :
                        (currentBuild.currentResult == 'SUCCESS' ? 'success' : 'failure')
                try {
                    node('python') {
                        deleteDir()
                        unstash 'notify-script'
                        if (testsOutcome != 'skipped') {
                            unstash 'junit-results'
                        }
                        def exitCode = 0
                        withEnv([
                                "JOB_STATUS=${jobStatus}",
                                "COMPILE_OUTCOME=${compileOutcome}",
                                "TESTS_OUTCOME=${testsOutcome}",
                        ]) {
                            try {
                                withCredentials([
                                        string(credentialsId: params.TELEGRAM_TOKEN_CREDENTIALS_ID ?: 'TELEGRAM_BOT_TOKEN', variable: 'TELEGRAM_BOT_TOKEN'),
                                        string(credentialsId: params.TELEGRAM_CHAT_CREDENTIALS_ID ?: 'TELEGRAM_CHAT_ID', variable: 'TELEGRAM_CHAT_ID'),
                                ]) {
                                    exitCode = sh(returnStatus: true, script: '''
                                        command -v python3 >/dev/null || { echo "python3 is not available on this agent"; exit 1; }
                                        python3 ci/telegram_notify.py \
                                          --results-dir build/test-results/test \
                                          --job-status "$JOB_STATUS" \
                                          --compile-outcome "$COMPILE_OUTCOME" \
                                          --tests-outcome "$TESTS_OUTCOME" \
                                          --project "$JOB_NAME" \
                                          --ci Jenkins \
                                          --branch "${GIT_BRANCH:-unknown}" \
                                          --commit "${GIT_COMMIT:-unknown}" \
                                          --run-url "$BUILD_URL"
                                    ''')
                                }
                            } catch (Exception e) {
                                if (!(e.message ?: '').contains('Could not find credentials entry')) {
                                    throw e
                                }
                                echo "WARNING: Telegram credentials are not configured, notification skipped: ${e.message}"
                            }
                        }
                        if (exitCode != 0) {
                            echo 'Telegram notification failed'
                            currentBuild.result = 'FAILURE'
                        }
                    }
                } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                    throw e
                } catch (Exception e) {
                    echo "Telegram notification failed: ${e.message}"
                    currentBuild.result = 'FAILURE'
                }
            }
        }
    }
}
