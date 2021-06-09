package com.proserv.orcParser.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SqsMessage {
    @JsonProperty("s3-bucket-name")
    private String s3BucketName;
    @JsonProperty("orc-key")
    private String orcKey;
}
