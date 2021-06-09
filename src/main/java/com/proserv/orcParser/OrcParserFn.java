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

package com.proserv.orcParser;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proserv.orcParser.models.SqsMessage;
import com.proserv.orcParser.models.User;
import lombok.SneakyThrows;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.OrcFile;
import org.apache.orc.Reader;
import org.apache.orc.RecordReader;
import org.apache.orc.TypeDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for requests to Lambda function.
 */
public class OrcParserFn implements RequestHandler<SQSEvent, Object> {

    private static final Logger LOG = LoggerFactory.getLogger(OrcParserFn.class);

    // use this flag to test locally
    private static final boolean IS_LOCAL_TESTING = System.getenv("IS_LOCAL_TESTING") == null||
            System.getenv("IS_LOCAL_TESTING").isEmpty() ? false : Boolean.parseBoolean(System.getenv("IS_LOCAL_TESTING"));
    private static final String TEST_BUCKET_NAME = "orc-repo-test";
    private static final String TEST_ORC_FILE_KEY = "orc-files/userData.orc";

    private final ObjectMapper jsonMapper;

    private final org.apache.hadoop.conf.Configuration hadoopConf;
    private final AmazonDynamoDB dynamoDB;
    private final DynamoDBMapperConfig dbMapperConfig;
    private final DynamoDBMapper dbMapper;
    private final String usersTableName;

    public OrcParserFn() {
        LOG.info("Initializing Hadoop configuration...");
        this.usersTableName = System.getenv("USERS_TABLE_NAME");
        this.jsonMapper = new ObjectMapper();
        this.hadoopConf = new org.apache.hadoop.conf.Configuration();
        LOG.info("Initializing DynamoDB configuration...");
        this.dynamoDB = AmazonDynamoDBClientBuilder.standard().build();
        this.dbMapperConfig = DynamoDBMapperConfig.builder().withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.CLOBBER)
                .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
                .withPaginationLoadingStrategy(DynamoDBMapperConfig.PaginationLoadingStrategy.EAGER_LOADING)
                .withTableNameOverride(new DynamoDBMapperConfig.TableNameOverride(this.usersTableName))
                .build();
        this.dbMapper = new DynamoDBMapper(dynamoDB, dbMapperConfig);

    }

    @SneakyThrows
    @Override
    public Object handleRequest(SQSEvent request, Context context) {
        LOG.info("Parsing SQS message...");
        TransferManager transferManager = TransferManagerBuilder.standard().build();
        String bucketName, orcKey;
        if (IS_LOCAL_TESTING) {
            bucketName = TEST_BUCKET_NAME;
            orcKey = TEST_ORC_FILE_KEY;
        } else {
            // parse sqs message
            String rawMessage = request.getRecords().get(0).getBody();
            SqsMessage sqsMessage = this.jsonMapper.readValue(rawMessage, SqsMessage.class);
            LOG.info(sqsMessage.toString());
            bucketName = sqsMessage.getS3BucketName();
            orcKey = sqsMessage.getOrcKey();
        }
        LOG.info("ORC bucket Name: {}", bucketName);
        LOG.info("ORC file key: {}", orcKey);
        String orcFileName = orcKey.substring(orcKey.lastIndexOf('/') + 1);
        File orcFile = new File("/tmp/" + orcFileName);
        try {
            //download orc
            LOG.info("Downloading orc file to {}", orcFile.getPath());
            Download download = transferManager.download(bucketName, orcKey, orcFile);
            download.waitForCompletion();
            LOG.info("Downloading ORC file completed.");
            List<User> users = parseOrc(orcFile.getPath(), hadoopConf, null);
            LOG.info("Parsed {} users.", users.size());
            //storing to DynamoDB
            LOG.info("Storing users to DynamoDB...");
            this.dbMapper.batchSave(users);
            LOG.info("Storing users to DynamoDB is completed");
        } catch (AmazonServiceException | InterruptedException e) {
            e.printStackTrace();
            System.exit(1);
        } finally {
            transferManager.shutdownNow();
        }
        return null;
    }

    public List<User> parseOrc(String orcPath, org.apache.hadoop.conf.Configuration conf, FileSystem fs) throws IOException {
        LOG.info("Reading ORC...");
        List<User> users = new ArrayList<>();
        OrcFile.ReaderOptions options = OrcFile.readerOptions(conf);
        // in case a non standard file system is required such as S3 file system
        if (fs != null) {
            options = options.filesystem(fs);
        }
        Reader reader = OrcFile.createReader(new Path(orcPath), options);
        RecordReader records = reader.rows();
        // get the schema
        TypeDescription readSchema = reader.getSchema();
        VectorizedRowBatch batch = readSchema.createRowBatch();
        LOG.info("schema: {}", reader.getSchema());
        LOG.info("numCols: {}", batch.numCols);
        try {
            RecordReader rowIterator = reader.rows(reader.options()
                    .schema(readSchema));
            // define vectors based on column index and data type
            LongColumnVector idVector = (LongColumnVector) batch.cols[0];
            BytesColumnVector firstNameVector = (BytesColumnVector) batch.cols[1];
            BytesColumnVector lastNameVector = (BytesColumnVector) batch.cols[2];
            BytesColumnVector emailAddressVector = (BytesColumnVector) batch.cols[3];
            BytesColumnVector genderVector = (BytesColumnVector) batch.cols[4];
            BytesColumnVector countryVector = (BytesColumnVector) batch.cols[5];
            BytesColumnVector birthDateVector = (BytesColumnVector) batch.cols[6];
            BytesColumnVector titleVector = (BytesColumnVector) batch.cols[7];
            DateFormat birthDateFormatter = new SimpleDateFormat("MM/dd/yyyy");
            // read batches of data from ORC
            while (rowIterator.nextBatch(batch)) {
                for (int row = 0; row < batch.size; row++) {
                    User user = new User();
                    user.setId(idVector.vector[row]);
                    user.setFirstName(new String(firstNameVector.vector[row], firstNameVector.start[row], firstNameVector.length[row]));
                    user.setLastName(new String(lastNameVector.vector[row], lastNameVector.start[row], lastNameVector.length[row]));
                    user.setEmailAddress(new String(emailAddressVector.vector[row], emailAddressVector.start[row], emailAddressVector.length[row]));
                    user.setGender(new String(genderVector.vector[row], genderVector.start[row], genderVector.length[row]));
                    user.setCountry(new String(countryVector.vector[row], countryVector.start[row], countryVector.length[row]));
                    try {
                        user.setBirthDate(birthDateFormatter.parse(new String(birthDateVector.vector[row], birthDateVector.start[row], birthDateVector.length[row])));
                    } catch (ParseException ex) {
                        LOG.warn("User id {} has a null birth date value", user.getId());
                    }
                    user.setTitle(new String(titleVector.vector[row], titleVector.start[row], titleVector.length[row]));
                    LOG.info(user.toString());
                    users.add(user);
                }
            }
            rowIterator.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        records.close();
        return users;
    }

    public static void main(String[] args) {
        OrcParserFn orcParserFn = new OrcParserFn();
        orcParserFn.handleRequest(null, null);
    }
}
