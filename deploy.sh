#! /bin/bash

set -e
set -u
set -o pipefail
# lambda s3 deployment bucket and orc parser jar
DEPLOYMENT_BUCKET=orc-repo-test
JAR_NAME=OrcParserLambda-1.0.jar
# set the aws region and the aws profile (profile is set in ~/.aws/credentials)
export AWS_REGION=us-west-2
export AWS_PROFILE=personal
# build the ORC Parser maven project
echo "Building ORCParserFn Lambda..."
mvn clean package
echo "Uploading ORCParserFn jar to S3..."
aws s3 cp ./target/$JAR_NAME s3://$DEPLOYMENT_BUCKET
echo "Uploading is complete."
echo "Setting environment variables for ORC_DEPLOYMENT_BUCKET and ORC_JAR_NAME"
export ORC_DEPLOYMENT_BUCKET=$DEPLOYMENT_BUCKET
export ORC_JAR_NAME=$JAR_NAME


# Running CDK Project

# Starting point is the root directory. Need to chdir
cd ./cdk

# Install CDK
echo "Installing CDK..."
npm install aws-cdk

# Install Dependencies
echo "Installing dependencies..."
npm install

echo "Building CDK code..."
npm run build
# Deploy
echo "Deploying"
cdk deploy --require-approval never

