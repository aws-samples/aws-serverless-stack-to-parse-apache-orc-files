/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: MIT-0
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import * as cdk from "@aws-cdk/core";
import * as sqs from "@aws-cdk/aws-sqs";
import {ServicePrincipal} from "@aws-cdk/aws-iam";
import {Code, Function, Runtime, Tracing} from "@aws-cdk/aws-lambda";
import {SqsEventSource} from "@aws-cdk/aws-lambda-event-sources";
import * as s3 from "@aws-cdk/aws-s3";
import * as dynamodb from "@aws-cdk/aws-dynamodb";


export class orcParserStack extends cdk.Stack {
    constructor(scope: cdk.Construct, id: string, props?: cdk.StackProps) {
        super(scope, id, props);

        //get jar s3 details
       if(!(process.env.ORC_DEPLOYMENT_BUCKET && process.env.ORC_JAR_NAME)){
           console.error("Environment variable  ORC_DEPLOYMENT_BUCKET or ORC_JAR_NAME does not exist!")
           return;
       }
       const orcDeploymentBucketName = process.env.ORC_DEPLOYMENT_BUCKET;
       const orcJarName = process.env.ORC_JAR_NAME;

        const orcDeploymentBucket = s3.Bucket.fromBucketName(this, 'orcDeploymentBucket', orcDeploymentBucketName);

        // user details table
        const userDetailsTable = new dynamodb.Table(this, 'user-details', {
            partitionKey: { name: 'id', type: dynamodb.AttributeType.NUMBER }
        });

        // Lambda trigger queue
        const orcSqs = new sqs.Queue(this, "OrcQueue", {
            queueName: "OrcParserTrigger",
            visibilityTimeout: cdk.Duration.minutes(15),
        });

        // ORC parser Lambda
        const orcParserFn = new Function(this, "OrcParser", {
            runtime: Runtime.JAVA_11,
            code: Code.fromBucket(
                s3.Bucket.fromBucketName(this, "OrcJarBucket", orcDeploymentBucketName),
                orcJarName
            ),
            memorySize: 512,
            timeout: cdk.Duration.minutes(15),
            handler: "com.proserv.orcParser.OrcParserFn::handleRequest",
            tracing: Tracing.ACTIVE,
            environment:{
                USERS_TABLE_NAME: userDetailsTable.tableName
            }
        });

        orcParserFn.grantInvoke(new ServicePrincipal('sqs.amazonaws.com'))
        orcSqs.grantConsumeMessages(orcParserFn);
        // add the orc queue event source
        orcParserFn.addEventSource(
            new SqsEventSource(orcSqs, {
                batchSize: 1,
            })
        );
        // grant the lambda role read/write permissions to our table
        userDetailsTable.grantReadWriteData(orcParserFn);
        // grant the lambda to read from s3
        orcDeploymentBucket.grantRead(orcParserFn);

    }
}