#!/usr/bin/env groovy
@Library(['common-utils']) _

// Thin stub: full LMRU CI lives in lmru-warchi-deploy (scripted pipeline via load).
// IMPORTANT: do not use Jenkins "Replay" with an old edited script — click Build Now.
def LMRU_DEPLOY_REPO = 'https://gitlab.lmru.tech/products/warchi/lmru-warchi-deploy.git'
def LMRU_DEPLOY_BRANCH = 'master'

node('dockerhost') {
    checkout scm

    dir('_lmru_deploy') {
        deleteDir()
        checkout([
                $class           : 'GitSCM',
                branches         : [[name: LMRU_DEPLOY_BRANCH]],
                userRemoteConfigs: [[
                                            url          : LMRU_DEPLOY_REPO,
                                            credentialsId: 'lm-sa-warchi'
                                    ]],
                extensions       : [[$class: 'CloneOption', shallow: true, depth: 1, noTags: true]]
        ])
    }

    def deployRoot = "${pwd()}/_lmru_deploy"
    env.LMRU_DEPLOY_DIR = deployRoot
    env.LMRU_SERVICE = 'warchi-mcp'

    sh "rm -rf .jenkins && cp -a '${deployRoot}/jenkins/warchi-mcp' .jenkins"

    load "${deployRoot}/pipelines/warchi-mcp/pipeline.groovy"
}
