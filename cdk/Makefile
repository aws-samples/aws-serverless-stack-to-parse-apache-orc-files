
###############################################################################################
# Simple Makefile to help developers build/deploy outside our CodePipeline/CodeDeploy process.
#
# It is assumed that your AWS Account ID is exported to your environemnt as AWS_ACCOUNT_ID
# If not, replace the AWS_ACCOUNT_ID below with your actual AWS Account ID along with
# the other variables based on your development/testing environment.
# 
# Modify the variables below according to your development environment 
################################################################################################

AWS_ACCOUNT=${AWS_ACCOUNT_ID)
AWS_PROFILE=personal

help: ## Shows the help
	@echo 'Usage: make <OPTIONS> ... <TARGETS>'
	@echo ''
	@echo 'Available targets are:'
	@echo ''
	@grep -E '^[ a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
	awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'
	@echo ''

deploy: build ## Deploys the stack to the default account and region
	cdk deploy --profile=$(AWS_PROFILE)

destroy: ## Removes the default stack 
	cdk destroy --profile=$(AWS_PROFILE)

node_modules:
	npm install 
 
build: node_modules ## Builds the CDK app 
	npm run build

clean: ## Removes all build, test artifacts
	rm -rf cdk.out
	rm -rf node_modules
	find . -name '*.js' -exec rm -fr {} + 
	find . -name '*d.ts' -exec rm -fr {} + 
	find . -name '*~' -exec rm -f {} +
