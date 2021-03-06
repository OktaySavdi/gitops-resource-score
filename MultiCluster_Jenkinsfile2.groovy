pipeline {
	//Servers
	agent { label 'GITOPS'}
	
	//Parameters
	parameters {
	string ( name: 'NAMESPACE', description: 'Enter the Project name to be installed' )
        string ( name: 'REPO', description: 'Enter the repo name' )
	}
	
	stages{
	    stage('control') {
            steps {
                script {
                    if (!params.NAMESPACE.isEmpty()) { 
			if (params.REPO.isEmpty()) { 
                            
                            error('repo Space is Empty') 
                        }
                    }
		    else { error('Proje Space is Empty') }
                }
            }
        }
	stage('action') {
		steps {
		  script {
	           def clusters = [
                       "https://api.mycluster1.mydomain:6443",
                       "https://api.mycluster2.mydomain:6443"
                       ]
                   for (int i=0; i < clusters.size(); ++i) {
                   def cluster = clusters[i]
		   git branch:"master", url: "${params.REPO}"
                    
		   withCredentials([string(credentialsId: "ocpserviceuser_creds${i}", variable: 'TOKEN')]) {
                       sh "oc login --token ${TOKEN} ${cluster} --insecure-skip-tls-verify=true"
					    
                           ARGOCD_SERVER_PASSWORD = sh(script: "oc get secret openshift-gitops-cluster -n openshift-gitops -o jsonpath='{.data.admin\\.password}' | base64 -d", returnStdout: true)
                           //println "ARGOCD_SERVER_PASSWORD=${ARGOCD_SERVER_PASSWORD}"
                           project_name = NAMESPACE.toLowerCase()
                           println project_name
                           score = sh(script: "polaris audit --config /opt/check_yaml_valiation/custom_check.yaml --audit-path . --format score", returnStdout: true).readLines()[0]
                           println score
                           ARGOCD_ROUTE = sh(script:"oc -n openshift-gitops get route openshift-gitops-server -n openshift-gitops -o jsonpath='{.spec.host}'",returnStdout:true)
                           println ARGOCD_ROUTE
                           
                       sh """
                           									
			   if [ \$(oc get project "${project_name}" | wc -l) -gt 0 ]; then
                               oc project $project_name
                           else
                             \"Project Not Found\"
                             exit 5
                           fi
                       """
                       
                           if(score.toInteger() < 80){
                                  println "Your yaml file has the following deficiencies"
                                  sh "polaris audit --config /opt/check_yaml_valiation/custom_check.yaml --audit-path . | grep -A 0 -B 8 false | grep -vE \"Severity|Category|Details|true\" "
                                  //kube-score score * --output-format ci | grep -vE "NetworkPolicy|podAntiAffinity|PodDisruptionBudget"
                                  error("Please make the relevant changes in your Yaml file")
                           }
                           else{   
                               argocdloginscript = """#!/bin/bash +x
                               argocd --insecure --grpc-web login ${ARGOCD_ROUTE}:443 --username admin --password ${ARGOCD_SERVER_PASSWORD}"""
                               
                               sh(script:argocdloginscript,returnStdout:false)
                               
                               projectcountscript = """#!/bin/bash
                               argocd app list | { grep -c ${project_name} || true; }
                               """
                               projectnumber = sh(script: projectcountscript,returnStdout:true).readLines()[0]
                               
                               println "projectnumber is $projectnumber"
                               
                               if(projectnumber == "0"){
                               sh "argocd app create ${project_name} --repo ${REPO} --path . --revision master --project default --dest-namespace ${project_name} --dest-server https://kubernetes.default.svc --directory-recurse"
                               }
                               
                               sh "argocd app sync ${project_name}"
                            }
						
		    }//cred
                  }//for
		}//script
	     }//steps
	}//stage
   }
}
