# AWS Serverless stack to Parse Apache ORC files

A complete serverless stack that parses a sample Hive ORC file and ingest the extracted data into DynamoDB.
It uses Lambda that is triggered by an sqs message to read ORC file from S3. AWS CDK is used for deployment.
This stack can be easily extended to use Event Bridge to automatically trigger the Lambda on file upload.

## Building
Before building the maven project, you need to set the AWS_REGION and AWS_PROFILE environment variables.
```bash
# set the aws region and the aws profile (profile is set in ~/.aws/credentials)
export AWS_REGION=<AWS REGION>
export AWS_PROFILE=<YOUR IAM USER PROFILE NAME>
mvn clean install
```

## Deployment

Update the following information in deploy.sh

```bash
# lambda s3 deployment bucket and orc parser jar
DEPLOYMENT_BUCKET=<YOUR S3 BUCKET>
JAR_NAME=OrcParserLambda-1.0.jar
# set the aws region and the aws profile (profile is set in ~/.aws/credentials)
export AWS_REGION=<AWS REGION>
export AWS_PROFILE=<YOUR IAM USER PROFILE NAME>
```
Allow executing deploy.sh then run the deploy.sh script
```bash
chmod 777 deploy.sh
./deploy.sh
```

## Local Testing
You can run the testing code in the test folder to test the parsing on a local file.
You need to have the AWS_REGION and AWS_PROFILE environment variables set. The sample orc file using in this code is located in the resources folder.
```bash
export AWS_REGION=<AWS REGION>
export AWS_PROFILE=<YOUR IAM USER PROFILE NAME>
mvn test
```
 The main class OrcParserFn.java also has a main method to make easy to debug from an IDE like IntelliJ. You need to set IS_LOCAL_TESTING environment variable to enable local testing  as well as USERS_TABLE_NAME in your IDE configuration.
 You can set the s3 bucket name and the orc file key at the top of the OrcParserFn.java.

 ## Testing the Lambda Function in AWS Console
 You can test the Lambda function in the AWS console using an event json like events/sqsEvent.json.


## AWS Resources
All the required AWS resources are created using AWS CDK. You can change the resources in cdk/lib/orcParserStack.ts to match your use case.

## Troubleshooting
If you faced issue when trying to install the cdk or the dependencies, run the clean.sh script and then run the deploy.sh script again.
```bash
chmod 777 clean.sh
./clean.sh
./deploy.sh
```