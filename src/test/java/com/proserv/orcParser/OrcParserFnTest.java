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

import com.proserv.orcParser.models.User;
import org.apache.hadoop.conf.Configuration;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.List;

public class OrcParserFnTest {
    private final org.apache.hadoop.conf.Configuration hadoopConf;
    private final OrcParserFn orcParserFn;

    public OrcParserFnTest() {
        hadoopConf = new Configuration();;
        orcParserFn = new OrcParserFn();
    }

    @Test
    public void testParseOrc() throws IOException {
        URL orcResource = getClass().getClassLoader().getResource("userData.orc");
        List<User> users = this.orcParserFn.parseOrc(orcResource.getPath(), this.hadoopConf, null);
        writeUsersToCsv(users, "userData.csv");
        Assert.assertTrue(users.size() == 100);
    }

    public void writeUsersToCsv(List<User> users, String fileName){
        try (PrintWriter writer = new PrintWriter(new File(fileName))) {
            StringBuilder sb = new StringBuilder();
            sb.append(User.generateCsvHeaderString());
            for(User user : users){
                sb.append("\n");
                sb.append(user.toCsvRow());
            }
            writer.write(sb.toString());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}