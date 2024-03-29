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
                            error('Repo Space is Empty') 
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
                                for (int i=0; i <= clusters.size(); ++i) {
                                
                                def cluster = clusters[i]
		                git branch:"master", url: "${params.REPO}"
                                 
		                withCredentials([string(credentialsId: "ocpserviceuser_creds${i}", variable: 'TOKEN')]) {
                                    sh "oc login --token ${TOKEN} ${cluster} --insecure-skip-tls-verify=true"
			            sh '''
				        project_name=`echo "${NAMESPACE}" | tr '[:upper:]' '[:lower:]'`
                                        ARGOCD_SERVER_PASSWORD=$(oc get secret openshift-gitops-cluster -n openshift-gitops -o jsonpath='{.data.admin\\.password}' | base64 -d)
                                        ARGOCD_ROUTE=$(oc -n openshift-gitops get route openshift-gitops-server -n openshift-gitops -o jsonpath='{.spec.host}')
                                        score=$(polaris audit --config /opt/check_yaml_valiation/custom_check.yaml --audit-path . --format score)										
			                if [ $(oc get project "${project_name}" | wc -l) -gt 0 ]; then
                                            oc project $project_name
                                        else
                                          "Project Not Found"
                                          exit 5
                                        fi
            
                                        if [[ $score -lt '80' ]];then
                                               echo "Yaml dosyanızda aşağıdaki eksiklikler bulunmaktadır."
					       polaris audit --config /opt/check_yaml_valiation/custom_check.yaml --audit-path . | grep -A 0 -B 8 false | grep -vE "Severity|Category|Details|true"
                                               #kube-score score * --output-format ci | grep -vE "NetworkPolicy|podAntiAffinity|PodDisruptionBudget"
                                               exit 15
                                        else                              
                                            argocd --insecure --grpc-web login ${ARGOCD_ROUTE}:443 --username admin --password ${ARGOCD_SERVER_PASSWORD}
                                            if [[ $(argocd app list | grep -c "${project_name}") -eq '0' ]];then
                                                argocd app create "${project_name}" --repo "${REPO}" --path . --revision "master" --project default --dest-namespace "${project_name}" --dest-server https://kubernetes.default.svc --directory-recurse
                                            fi
                                            argocd app sync "${project_name}"
                                        fi
			               '''
				 }//cred
                             }//for
			}//script
		      }//steps
		   }//stage
		}
}
