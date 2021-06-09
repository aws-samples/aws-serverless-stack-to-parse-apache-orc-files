package com.proserv.orcParser.models;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import lombok.Data;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@Data
@DynamoDBTable(tableName="users")
public class User {
    @DynamoDBHashKey(attributeName="id")
    private long id;

    @DynamoDBAttribute(attributeName="firstName")
    private String firstName;

    @DynamoDBAttribute(attributeName="lastName")
    private String lastName;

    @DynamoDBAttribute(attributeName="emailAddress")
    private String emailAddress;

    @DynamoDBAttribute(attributeName="gender")
    private String gender;

    @DynamoDBAttribute(attributeName="country")
    private String country;

    @DynamoDBAttribute(attributeName="birthDate")
    private Date birthDate;

    @DynamoDBAttribute(attributeName="title")
    private String title;

    public String toString(){
        DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        String birthDayString = this.getBirthDate() != null ? dateFormat.format(this.getBirthDate()) : "";
        return "user: { id: " + this.getId() + ", "
                + "firstName: '" + this.getFirstName() + "', "
                + "lastName: '" + this.getLastName() + "', "
                + "email: '" + this.getEmailAddress() + "', "
                + "gender: '" + this.getGender() + "', "
                + "country: '" + this.getCountry() + "', "
                + "birthDate: '" + birthDayString + "', "
                + "title: '" + this.getTitle() + "' }";
    }

    public static String generateCsvHeaderString(){
        return "id,firstName,lastName,email,gender,country,birthDate,title";
    }

    public String toCsvRow(){
        DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        String birthDayString = this.getBirthDate() != null ? dateFormat.format(this.getBirthDate()) : "";
        return this.getId() + ","
                + this.getFirstName() + ","
                + this.getLastName() + ","
                + this.getEmailAddress() + ","
                + this.getGender() + ","
                + this.getCountry() + ","
                + birthDayString + ","
                + this.getTitle();
    }
}
